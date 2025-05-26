package com.bearmod.loader.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.bearmod.loader.BearLoaderApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.Cache;

import okhttp3.ConnectionSpec;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Direct implementation of KeyAuth API
 * This class handles authentication directly with the KeyAuth API
 */
public class DirectKeyAuthManager {

    private static final String TAG = "DirectKeyAuthManager";

    // Official KeyAuth API URLs
    private static final String API_URL = "https://keyauth.win/api/1.2/";
    private static final String ALTERNATE_API_URL = "https://keyauth.cc/api/1.2/";

    // KeyAuth API endpoint for files (assuming it's the same as main API)
    private static final String KEYAUTH_FILE_API_URL = "https://keyauth.win/api/1.3/";

    // Custom KeyAuth API URL (if configured)
    private String customApiUrl = null;
    private String customLoginUrl = null;

    // API request timeout constants
    private static final int CONNECT_TIMEOUT = 10; // seconds
    private static final int READ_TIMEOUT = 10; // seconds
    private static final int WRITE_TIMEOUT = 10; // seconds

    // API request retry constants
    private static final int MAX_API_RETRIES = 3;
    private static final long RETRY_DELAY = 2000; // milliseconds

    // Cache expiration time
    private static final long CACHE_EXPIRATION = 24 * 60 * 60 * 1000; // 24 hours

    // Application details
    private static final String APP_NAME = "com.bearmod.loader";
    private static final String APP_OWNER = "yLoA9zcOEF";
    private static final String APP_VERSION = "1.0";

    // API response cache
    private final Map<String, CachedResponse> responseCache = new HashMap<>();

    // Singleton instance
    private static DirectKeyAuthManager instance;

    // OkHttpClient for API requests
    private final OkHttpClient client;

    // Executor for background tasks
    private final Executor executor = Executors.newSingleThreadExecutor();

    // Handler for main thread callbacks
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Initialization state
    private boolean isInitialized = false;

    // Session ID
    private String sessionId;

    // Shared Preferences key for session
    private static final String PREFS_NAME = "bearmod_prefs";
    private static final String SESSION_ID_KEY = "session_id";

    // Constants for secure storage
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "bearmod_session_key";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final String IV_SEPARATOR = "]";

    // Session refresh constants
    private static final long SESSION_REFRESH_INTERVAL = 15 * 60 * 1000; // 15 minutes
    private long lastSessionRefreshTime = 0;
    private int initRetryCount = 0;
    private static final int MAX_INIT_RETRIES = 3;

    /**
     * Cached API response class
     */
    private static class CachedResponse {
        private final JSONObject response;
        private final long timestamp;

        CachedResponse(JSONObject response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION;
        }

        JSONObject getResponse() {
            return response;
        }
    }

    /**
     * Private constructor to enforce singleton pattern
     */
    private DirectKeyAuthManager() {
        // Create OkHttpClient with logging and improved configuration
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Configure TLS for secure connections
        ConnectionSpec connectionSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .build();

        // Create a response cache (10MB)
        Cache responseCache = null;
        try {
            File cacheDir = new File(System.getProperty("java.io.tmpdir"), "http_cache");
            responseCache = new Cache(cacheDir, 10 * 1024 * 1024); // 10MB cache
        } catch (Exception e) {
            Log.e(TAG, "Could not create HTTP cache: " + e.getMessage());
        }

        client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectionSpecs(java.util.Collections.singletonList(connectionSpec))
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .cache(responseCache)
                .build();
    }

    /**
     * Get instance
     * @return DirectKeyAuthManager instance
     */
    public static synchronized DirectKeyAuthManager getInstance() {
        if (instance == null) {
            instance = new DirectKeyAuthManager();
        }
        return instance;
    }

    // Flag to track if we're using alternate URLs
    private boolean usingAlternateUrls = false;

    /**
     * Make an API request with caching and retry logic
     *
     * @param requestBody Request body
     * @param cacheKey    Cache key (null for no caching)
     * @return JSONObject response
     * @throws IOException   If request fails
     * @throws JSONException If response parsing fails
     */
    private JSONObject makeApiRequest(RequestBody requestBody, String cacheKey) throws IOException, JSONException {
        return makeApiRequest(requestBody, cacheKey, 0, false);
    }

    /**
     * Make an API request with caching and retry logic
     * @param requestBody Request body
     * @param cacheKey Cache key (null for no caching)
     * @param retryCount Current retry count
     * @param tryAlternate Whether to try the alternate URL
     * @return JSONObject response
     * @throws IOException If request fails
     * @throws JSONException If response parsing fails
     */
    private JSONObject makeApiRequest(RequestBody requestBody, String cacheKey, int retryCount, boolean tryAlternate) throws IOException, JSONException {
        // Check cache first if cacheKey is provided
        if (cacheKey != null && !cacheKey.isEmpty()) {
            CachedResponse cachedResponse = responseCache.get(cacheKey);
            if (cachedResponse != null && !cachedResponse.isExpired()) {
                Log.d(TAG, "Using cached response for: " + cacheKey);
                return cachedResponse.getResponse();
            }
        }

        // Determine which URL to use
        String apiUrl;
        if (customApiUrl != null) {
            // Use custom URL if set
            apiUrl = customApiUrl;
        } else if (tryAlternate || usingAlternateUrls) {
            // Use alternate URL if requested or if we've switched to it
            apiUrl = ALTERNATE_API_URL;
        } else {
            // Use default URL
            apiUrl = API_URL;
        }

        // Create request
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .build();

        Log.d(TAG, "Making API request to: " + apiUrl);

        // Execute request
        Response response = client.newCall(request).execute();

        // Handle response
        if (!response.isSuccessful()) {
            Log.e(TAG, "API request failed: " + response.code());

            // Parse error response if available
            String errorBody = "";
            try {
                errorBody = response.body().string();
                Log.e(TAG, "Error response: " + errorBody);

                // Check if error suggests using alternate URL
                if (!tryAlternate &&
                    (errorBody.contains("Use keyauth.win") ||
                     errorBody.contains("keyauth.cc") ||
                     errorBody.contains("Cloudflare") ||
                     errorBody.contains("rate limit") ||
                     response.code() == 403 ||
                     response.code() == 503 ||
                     response.code() == 404 ||
                     response.code() == 429)) {
                    Log.d(TAG, "Switching to alternate KeyAuth URL due to error: " + response.code());
                    toggleApiUrl();
                    return makeApiRequest(requestBody, cacheKey, retryCount, true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading error response: " + e.getMessage());
            }

            // Retry if we haven't exceeded max retries
            if (retryCount < MAX_API_RETRIES) {
                Log.d(TAG, "Retrying API request (attempt " + (retryCount + 1) + ")");

                // Wait before retrying
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Sleep interrupted: " + e.getMessage());
                }

                return makeApiRequest(requestBody, cacheKey, retryCount + 1, tryAlternate);
            }

            throw new IOException("API request failed with code: " + response.code() + " - " + errorBody);
        }

        // Parse response
        String responseBody = response.body().string();
        JSONObject json = new JSONObject(responseBody);

        // Check if response indicates an error
        if (!json.optBoolean("success", false)) {
            String message = json.optString("message", "Unknown error");
            Log.e(TAG, "API error: " + message);

            // Check if error suggests using alternate URL
            if (!tryAlternate &&
                (message.contains("keyauth.win") ||
                 message.contains("keyauth.cc") ||
                 message.contains("Cloudflare") ||
                 message.contains("rate limit") ||
                 message.contains("Invalid session") ||
                 message.contains("Session expired") ||
                 message.contains("not found"))) {
                Log.d(TAG, "Switching to alternate KeyAuth URL based on error message: " + message);
                toggleApiUrl();
                return makeApiRequest(requestBody, cacheKey, retryCount, true);
            }
        }

        // Cache response if cacheKey is provided
        if (cacheKey != null && !cacheKey.isEmpty() && json.optBoolean("success", false)) {
            responseCache.put(cacheKey, new CachedResponse(json));
            Log.d(TAG, "Cached response for: " + cacheKey);
        }

        return json;
    }

    /**
     * Force use of the primary API URL
     */
    public void forceUsePrimaryUrl() {
        usingAlternateUrls = false;
        customApiUrl = null;
        customLoginUrl = null;
        Log.d(TAG, "Forced use of primary KeyAuth URL: " + API_URL);
    }

    /**
     * Force use of the alternate API URL
     */
    public void forceUseAlternateUrl() {
        usingAlternateUrls = true;
        customApiUrl = null;
        customLoginUrl = null;
        Log.d(TAG, "Forced use of alternate KeyAuth URL: " + ALTERNATE_API_URL);
    }

    /**
     * Toggle between primary and alternate API URLs
     * @return The URL that was switched to
     */
    public String toggleApiUrl() {
        usingAlternateUrls = !usingAlternateUrls;
        customApiUrl = null;
        customLoginUrl = null;

        String currentUrl = usingAlternateUrls ? ALTERNATE_API_URL : API_URL;
        Log.d(TAG, "Toggled to " + (usingAlternateUrls ? "alternate" : "primary") + " KeyAuth URL: " + currentUrl);

        return currentUrl;
    }

    /**
     * Initialize KeyAuth
     * @param context Application context
     * @return true if initialization was successful, false otherwise
     */
    public synchronized boolean initialize(Context context) {
        // Force use of the primary URL (keyauth.win)
        forceUsePrimaryUrl();

        // If already initialized and session is still valid, return true
        if (isInitialized && isSessionValid() &&
            (System.currentTimeMillis() - lastSessionRefreshTime < SESSION_REFRESH_INTERVAL)) {
            Log.d(TAG, "KeyAuth already initialized with valid session");
            return true;
        }

        // Reset retry count if this is a fresh initialization
        if (!isInitialized) {
            initRetryCount = 0;
        }

        // Check if we have a saved session ID
        boolean hasSessionId = loadSessionId(context);

        // If we have a session ID, check if it's valid
        if (hasSessionId) {
            Log.d(TAG, "Found saved session ID (encrypted)");

            if (isSessionValid()) {
                Log.d(TAG, "Saved session is valid, using it");
                isInitialized = true;
                lastSessionRefreshTime = System.currentTimeMillis();
                return true;
            } else {
                Log.d(TAG, "Saved session is invalid, creating new session");
            }
        } else {
            Log.d(TAG, "No saved session found, creating new session");
        }

        // Reset initialization state
        isInitialized = false;

        // Generate a new session ID
        sessionId = generateSessionId();
        Log.d(TAG, "Initializing KeyAuth with new session ID");

        // Create form body for initialization with KeyAuth
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("type", "init")
                .add("name", APP_NAME)
                .add("ownerid", APP_OWNER)
                .add("ver", APP_VERSION)
                .add("sessionid", sessionId);

        try {
            // Make API request with retry logic
            JSONObject json = makeApiRequest(formBuilder.build(), null);

            boolean success = json.getBoolean("success");
            if (success) {
                isInitialized = true;
                lastSessionRefreshTime = System.currentTimeMillis();
                Log.d(TAG, "KeyAuth initialized successfully");

                // Save the session ID securely
                saveSessionId(context);
                return true;
            } else {
                String message = json.getString("message");
                Log.e(TAG, "KeyAuth initialization failed: " + message);

                // Retry initialization if we haven't exceeded max retries
                if (initRetryCount < MAX_INIT_RETRIES) {
                    initRetryCount++;
                    Log.d(TAG, "Retrying initialization (attempt " + initRetryCount + ")");
                    // Wait a moment before retrying
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Sleep interrupted: " + e.getMessage());
                    }
                    return initialize(context);
                }

                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing KeyAuth: " + e.getMessage());

            // Retry initialization if we haven't exceeded max retries
            if (initRetryCount < MAX_INIT_RETRIES) {
                initRetryCount++;
                Log.d(TAG, "Retrying initialization (attempt " + initRetryCount + ")");
                // Wait a moment before retrying
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e2) {
                    Log.e(TAG, "Sleep interrupted: " + e2.getMessage());
                }
                return initialize(context);
            }

            return false;
        }
    }

    /**
     * Login with license key
     * @param licenseKey License key
     * @param callback Callback for login result
     */
    public void login(String licenseKey, AuthCallback callback) {
        Context context = BearLoaderApplication.getInstance();

        // Check if in development mode
        if (BearLoaderApplication.getInstance().isDevelopmentMode()) {
            // In development mode, create a mock successful response
            createMockSuccessResponse(callback);
            return;
        }

        // Force use of the primary URL (keyauth.win)
        forceUsePrimaryUrl();

        // Validate license key format
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            handler.post(() -> callback.onError("License key cannot be empty"));
            return;
        }

        // Format license key (add hyphens if missing)
        String formattedLicenseKey = formatLicenseKey(licenseKey);

        // Execute in background thread to avoid blocking UI
        executor.execute(() -> {
            try {
                // Check if KeyAuth is initialized, initialize if not
                if (!isInitialized) {
                    Log.d(TAG, "KeyAuth not initialized, initializing now");
                    boolean initSuccess = initialize(context);

                    if (!initSuccess) {
                        handler.post(() -> callback.onError("Failed to initialize KeyAuth. Please check your internet connection and try again."));
                        return;
                    }
                }

                // Check if session needs refresh (periodic refresh to prevent expiration)
                if (System.currentTimeMillis() - lastSessionRefreshTime > SESSION_REFRESH_INTERVAL) {
                    Log.d(TAG, "Session refresh interval exceeded, refreshing session");

                    if (!refreshSession(context)) {
                        Log.e(TAG, "Failed to refresh session, reinitializing");
                        boolean initSuccess = initialize(context);

                        if (!initSuccess) {
                            handler.post(() -> callback.onError("Session expired and refresh failed. Please try again."));
                            return;
                        }
                    }
                } else if (!isSessionValid()) {
                    Log.d(TAG, "Session is not valid, refreshing session");

                    if (!refreshSession(context)) {
                        Log.e(TAG, "Failed to refresh session, reinitializing");
                        boolean initSuccess = initialize(context);

                        if (!initSuccess) {
                            handler.post(() -> callback.onError("Session expired. Please try again."));
                            return;
                        }
                    }
                } else {
                    Log.d(TAG, "Session is valid, proceeding with authentication");
                }

                // Get HWID
                String hwid = HWID.getHWID();
                Log.d(TAG, "Using HWID: " + hwid);

                // Remove any hyphens from the license key for API request
                String apiKey = formattedLicenseKey.replace("-", "");

                // Create form body for the request using KeyAuth API
                FormBody.Builder formBuilder = new FormBody.Builder()
                        .add("type", "license")
                        .add("key", apiKey)
                        .add("hwid", hwid)
                        .add("name", APP_NAME)
                        .add("ownerid", APP_OWNER)
                        .add("ver", APP_VERSION)
                        .add("sessionid", sessionId);

                Log.d(TAG, "KeyAuth login request with key: " + maskLicenseKey(apiKey));

                // Create cache key based on license key (don't cache login responses by default)
                String cacheKey = null; // "login_" + apiKey;

                // Make API request with retry logic
                JSONObject json;
                try {
                    json = makeApiRequest(formBuilder.build(), cacheKey);
                } catch (IOException e) {
                    String errorMsg = "Failed to verify license: Network error";
                    Log.e(TAG, errorMsg + ": " + e.getMessage());

                    // Try to refresh session and retry once
                    if (refreshSession(context)) {
                        retryLogin(formattedLicenseKey, callback);
                        return;
                    }

                    handler.post(() -> callback.onError(errorMsg));
                    return;
                } catch (JSONException e) {
                    String errorMsg = "Failed to parse server response";
                    Log.e(TAG, errorMsg + ": " + e.getMessage());
                    handler.post(() -> callback.onError(errorMsg));
                    return;
                }

                boolean success = json.getBoolean("success");
                if (success) {
                    // Get license information from KeyAuth response
                    // Check if license exists in the response
                    if (!json.has("info") || json.isNull("info")) {
                        handler.post(() -> callback.onError("Invalid license key. Please check your license key."));
                        return;
                    }

                    // Get license info
                    String licenseInfo = json.optString("info", "");

                    // Parse expiry date from KeyAuth response
                    // KeyAuth doesn't provide a standard format for expiry dates in license responses
                    // We'll extract it from the info string or use a default
                    String expiryString = "";
                    String registrationDate = "";

                    // Try to extract dates from the info string
                    try {
                        // Check if info contains JSON data
                        if (licenseInfo.startsWith("{") && licenseInfo.endsWith("}")) {
                            JSONObject infoJson = new JSONObject(licenseInfo);
                            expiryString = infoJson.optString("expiry", "");
                            registrationDate = infoJson.optString("created", "");
                        } else {
                            // Try to extract dates from the info string using regex
                            Pattern expiryPattern = Pattern.compile("expiry[:\\s]+(\\d{4}-\\d{2}-\\d{2})");
                            Matcher expiryMatcher = expiryPattern.matcher(licenseInfo);
                            if (expiryMatcher.find()) {
                                expiryString = expiryMatcher.group(1);
                            }

                            Pattern createdPattern = Pattern.compile("created[:\\s]+(\\d{4}-\\d{2}-\\d{2})");
                            Matcher createdMatcher = createdPattern.matcher(licenseInfo);
                            if (createdMatcher.find()) {
                                registrationDate = createdMatcher.group(1);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing license info: " + e.getMessage());
                    }

                    // If we couldn't extract dates, use defaults
                    if (expiryString.isEmpty()) {
                        // Default to 30 days from now
                        expiryString = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
                    }

                    if (registrationDate.isEmpty()) {
                        // Default to today
                        registrationDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(new Date());
                    }

                    // Parse expiry date
                    Date expiryDate;
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        expiryDate = dateFormat.parse(expiryString);

                        // Check if license is expired
                        if (expiryDate.before(new Date())) {
                            handler.post(() -> callback.onError("License has expired. Please renew your subscription."));
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing expiry date: " + e.getMessage());
                        // Use a default expiry date if parsing fails
                        expiryDate = new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000); // 30 days
                    }

                    // Log license details
                    Log.d(TAG, "License validated successfully. Expires: " + expiryString);

                    // Save license key and login status
                    BearLoaderApplication.getInstance().saveLicenseKey(formattedLicenseKey);
                    BearLoaderApplication.getInstance().setLoggedIn(true);

                    // Update last session refresh time
                    lastSessionRefreshTime = System.currentTimeMillis();

                    // Create auth result
                    final AuthResult result = new AuthResult(
                            true,
                            "Login successful",
                            expiryDate,
                            registrationDate
                    );

                    // Return success on main thread
                    handler.post(() -> callback.onSuccess(result));
                } else {
                    // Get error message from KeyAuth response
                    String message = json.has("message") ? json.getString("message") : "Unknown error";
                    Log.e(TAG, "License validation failed: " + message);

                    // Provide more user-friendly error messages for KeyAuth errors
                    String userMessage;
                    if (message.contains("invalid") || message.contains("not found") || message.contains("key not found")) {
                        userMessage = "Invalid license key. Please check and try again.";
                    } else if (message.contains("expired")) {
                        userMessage = "Your license has expired. Please renew your subscription.";
                    } else if (message.contains("hwid") || message.contains("hardware")) {
                        userMessage = "This license is already in use on another device.";
                    } else if (message.contains("banned")) {
                        userMessage = "This license has been banned. Please contact support.";
                    } else if (message.contains("session")) {
                        // Session error - try to refresh the session
                        if (refreshSession(context)) {
                            retryLogin(formattedLicenseKey, callback);
                            return;
                        }
                        userMessage = "Session error. Please try again.";
                    } else {
                        userMessage = "License validation failed: " + message;
                    }

                    // Return error on main thread
                    final String finalMessage = userMessage;
                    handler.post(() -> callback.onError(finalMessage));
                }
            } catch (Exception e) {
                Log.e(TAG, "Login error: " + e.getMessage());
                // Return error on main thread with more specific message
                String errorMessage = "Login failed: ";
                if (e instanceof IOException) {
                    errorMessage += "Network error. Please check your internet connection.";
                } else if (e instanceof JSONException) {
                    errorMessage += "Invalid server response. Please try again.";
                } else {
                    errorMessage += e.getMessage();
                }

                final String finalErrorMessage = errorMessage;
                handler.post(() -> callback.onError(finalErrorMessage));
            }
        });
    }

    /**
     * Retry login after session refresh
     * @param licenseKey License key
     * @param callback Callback for login result
     */
    private void retryLogin(String licenseKey, AuthCallback callback) {
        Log.d(TAG, "Retrying login after session refresh");

        // Get HWID
        String hwid = HWID.getHWID();

        try {
            // Remove any hyphens from the license key for API request
            String apiKey = licenseKey.replace("-", "");

            // Create form body for the request using KeyAuth API
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("type", "license")
                    .add("key", apiKey)
                    .add("hwid", hwid)
                    .add("name", APP_NAME)
                    .add("ownerid", APP_OWNER)
                    .add("ver", APP_VERSION)
                    .add("sessionid", sessionId);

            Log.d(TAG, "KeyAuth retry login request");

            // Make API request with retry logic (no caching for login)
            JSONObject json = makeApiRequest(formBuilder.build(), null);

            boolean success = json.getBoolean("success");
            if (success) {
                // Get license information from KeyAuth response
                // Check if license exists in the response
                if (!json.has("info") || json.isNull("info")) {
                    handler.post(() -> callback.onError("Invalid license key. Please check your license key."));
                    return;
                }

                // Get license info
                String licenseInfo = json.optString("info", "");

                // Parse expiry date from KeyAuth response
                // KeyAuth doesn't provide a standard format for expiry dates in license responses
                // We'll extract it from the info string or use a default
                String expiryString = "";
                String registrationDate = "";

                // Try to extract dates from the info string
                try {
                    // Check if info contains JSON data
                    if (licenseInfo.startsWith("{") && licenseInfo.endsWith("}")) {
                        JSONObject infoJson = new JSONObject(licenseInfo);
                        expiryString = infoJson.optString("expiry", "");
                        registrationDate = infoJson.optString("created", "");
                    } else {
                        // Try to extract dates from the info string using regex
                        Pattern expiryPattern = Pattern.compile("expiry[:\\s]+(\\d{4}-\\d{2}-\\d{2})");
                        Matcher expiryMatcher = expiryPattern.matcher(licenseInfo);
                        if (expiryMatcher.find()) {
                            expiryString = expiryMatcher.group(1);
                        }

                        Pattern createdPattern = Pattern.compile("created[:\\s]+(\\d{4}-\\d{2}-\\d{2})");
                        Matcher createdMatcher = createdPattern.matcher(licenseInfo);
                        if (createdMatcher.find()) {
                            registrationDate = createdMatcher.group(1);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing license info: " + e.getMessage());
                }

                // If we couldn't extract dates, use defaults
                if (expiryString.isEmpty()) {
                    // Default to 30 days from now
                    expiryString = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
                }

                if (registrationDate.isEmpty()) {
                    // Default to today
                    registrationDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(new Date());
                }

                // Parse expiry date
                Date expiryDate;
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    expiryDate = dateFormat.parse(expiryString);

                    // Check if license is expired
                    if (expiryDate.before(new Date())) {
                        handler.post(() -> callback.onError("License has expired. Please renew your subscription."));
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing expiry date: " + e.getMessage());
                    // Use a default expiry date if parsing fails
                    expiryDate = new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000); // 30 days
                }

                // Save license key and login status
                BearLoaderApplication.getInstance().saveLicenseKey(licenseKey);
                BearLoaderApplication.getInstance().setLoggedIn(true);

                // Update last session refresh time
                lastSessionRefreshTime = System.currentTimeMillis();

                // Create auth result
                final AuthResult result = new AuthResult(
                        true,
                        "Login successful",
                        expiryDate,
                        registrationDate
                );

                // Return success on main thread
                handler.post(() -> callback.onSuccess(result));
            } else {
                // Get error message from KeyAuth response
                String message = json.has("message") ? json.getString("message") : "Unknown error";
                Log.e(TAG, "License validation failed: " + message);

                // Provide more user-friendly error messages for KeyAuth errors
                String userMessage;
                if (message.contains("invalid") || message.contains("not found") || message.contains("key not found")) {
                    userMessage = "Invalid license key. Please check and try again.";
                } else if (message.contains("expired")) {
                    userMessage = "Your license has expired. Please renew your subscription.";
                } else if (message.contains("hwid") || message.contains("hardware")) {
                    userMessage = "This license is already in use on another device.";
                } else if (message.contains("banned")) {
                    userMessage = "This license has been banned. Please contact support.";
                } else if (message.contains("session")) {
                    // Session error - we already tried to refresh, so just report the error
                    userMessage = "Session error. Please restart the app and try again.";
                } else {
                    userMessage = "License validation failed: " + message;
                }

                // Return error on main thread
                final String finalMessage = userMessage;
                handler.post(() -> callback.onError(finalMessage));
            }
        } catch (IOException e) {
            Log.e(TAG, "Retry login network error: " + e.getMessage());
            // Return error on main thread with more specific message
            handler.post(() -> callback.onError("Network error. Please check your internet connection."));
        } catch (JSONException e) {
            Log.e(TAG, "Retry login parsing error: " + e.getMessage());
            // Return error on main thread
            handler.post(() -> callback.onError("Invalid server response. Please try again."));
        } catch (Exception e) {
            Log.e(TAG, "Retry login error: " + e.getMessage());
            // Return error on main thread
            handler.post(() -> callback.onError("Login retry failed: " + e.getMessage()));
        }
    }

    /**
     * Format license key with hyphens if needed
     * @param licenseKey License key to format
     * @return Formatted license key
     */
    private String formatLicenseKey(String licenseKey) {
        // Remove any existing hyphens and whitespace
        String cleanKey = licenseKey.replace("-", "").replace(" ", "");

        // If key is already properly formatted or too short, return as is
        if (cleanKey.length() < 5) {
            return cleanKey;
        }

        // Format key with hyphens every 6 characters (KeyAuth standard format)
        StringBuilder formattedKey = new StringBuilder();
        for (int i = 0; i < cleanKey.length(); i++) {
            if (i > 0 && i % 6 == 0) {
                formattedKey.append('-');
            }
            formattedKey.append(cleanKey.charAt(i));
        }

        return formattedKey.toString();
    }

    /**
     * Mask license key for logging (show only first and last 4 characters)
     * @param licenseKey License key to mask
     * @return Masked license key
     */
    private String maskLicenseKey(String licenseKey) {
        if (licenseKey == null || licenseKey.length() <= 8) {
            return "****";
        }

        return licenseKey.substring(0, 4) + "****" + licenseKey.substring(licenseKey.length() - 4);
    }

    /**
     * Check if using alternate URL
     * @return true if using alternate URL, false otherwise
     */
    public boolean isUsingAlternateUrl() {
        return usingAlternateUrls;
    }

    /**
     * Set custom KeyAuth API URL
     * @param domain Custom domain (e.g., "https://api.example.com")
     */
    public void setCustomDomain(String domain) {
        if (domain != null && !domain.isEmpty()) {
            // Ensure domain ends with a slash
            if (!domain.endsWith("/")) {
                domain += "/";
            }

            // Ensure domain has https:// prefix
            if (!domain.startsWith("https://") && !domain.startsWith("http://")) {
                domain = "https://" + domain;
            }

            // Set custom URLs
            this.customApiUrl = domain + "api/1.2/";
            this.customLoginUrl = domain + "api/1.2/";

            Log.d(TAG, "Set custom KeyAuth domain: " + domain);
        } else {
            // Clear custom domain
            this.customApiUrl = null;
            this.customLoginUrl = null;
            Log.d(TAG, "Cleared custom KeyAuth domain");
        }
    }

    /**
     * Get the primary API URL
     * @return Primary API URL
     */
    public static String getApiUrl() {
        return API_URL;
    }

    /**
     * Get the alternate API URL
     * @return Alternate API URL
     */
    public static String getAlternateApiUrl() {
        return ALTERNATE_API_URL;
    }

    /**
     * Get custom KeyAuth API URL
     * @return Custom API URL or null if not set
     */
    public String getCustomDomain() {
        if (customApiUrl != null) {
            // Extract domain from URL
            String url = customApiUrl;
            url = url.replace("https://", "").replace("http://", "");
            int endIndex = url.indexOf("/");
            if (endIndex > 0) {
                return url.substring(0, endIndex);
            }
            return url;
        }
        return null;
    }

    /**
     * Check if using custom URL
     * @return true if using custom URL, false otherwise
     */
    public boolean isUsingCustomUrl() {
        return customApiUrl != null;
    }

    /**
     * Create a mock successful response for development mode
     * @param callback Callback for login result
     */
    private void createMockSuccessResponse(AuthCallback callback) {
        // Create a future expiry date (30 days from now)
        Date expiryDate = new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000);

        // Create a past registration date (1 year ago)
        Date registrationDate = new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000);
        String registrationDateStr = formatExpiryDate(registrationDate);

        // Create auth result
        final AuthResult result = new AuthResult(
                true,
                "Development mode login successful",
                expiryDate,
                registrationDateStr
        );

        // Save license key and login status
        BearLoaderApplication.getInstance().saveLicenseKey(BearLoaderApplication.getInstance().getLicenseKey());
        BearLoaderApplication.getInstance().setLoggedIn(true);

        // Return success on main thread after a short delay to simulate network request
        handler.postDelayed(() -> callback.onSuccess(result), 500);
    }

    /**
     * Check if license is valid
     * @param callback Callback for validation result
     */
    public void validateLicense(AuthCallback callback) {
        Context context = BearLoaderApplication.getInstance();

        // Check if in development mode
        if (BearLoaderApplication.getInstance().isDevelopmentMode()) {
            // In development mode, create a mock successful response
            createMockSuccessResponse(callback);
            return;
        }

        // Force use of the primary URL (keyauth.win)
        forceUsePrimaryUrl();

        // Execute in background thread to avoid blocking UI
        executor.execute(() -> {
            try {
                // Check if KeyAuth is initialized, initialize if not
                if (!isInitialized) {
                    Log.d(TAG, "KeyAuth not initialized during validation, initializing now");
                    boolean initSuccess = initialize(context);

                    if (!initSuccess) {
                        handler.post(() -> callback.onError("Failed to initialize KeyAuth. Please check your internet connection and try again."));
                        return;
                    }
                }

                // Check if session needs refresh (periodic refresh to prevent expiration)
                if (System.currentTimeMillis() - lastSessionRefreshTime > SESSION_REFRESH_INTERVAL) {
                    Log.d(TAG, "Session refresh interval exceeded during validation, refreshing session");
                    refreshSession(context);
                }

                // Get license key
                String licenseKey = BearLoaderApplication.getInstance().getLicenseKey();

                // Check if license key exists
                if (licenseKey == null || licenseKey.isEmpty()) {
                    handler.post(() -> callback.onError("No license key found"));
                    return;
                }

                // Validate license on main thread
                handler.post(() -> login(licenseKey, callback));
            } catch (Exception e) {
                Log.e(TAG, "Error during license validation: " + e.getMessage());
                handler.post(() -> callback.onError("Validation error: " + e.getMessage()));
            }
        });
    }

    /**
     * Logout user
     */
    public void logout() {
        Context context = BearLoaderApplication.getInstance();

        // Clear user data
        BearLoaderApplication.getInstance().clearUserData();

        // Clear session ID
        sessionId = null;
        isInitialized = false;

        // Clear session from preferences
        clearSessionId(context);

        Log.d(TAG, "User logged out, session cleared");
    }

    /**
     * Generate a session ID
     * @return Random session ID using UUID
     */
    private String generateSessionId() {
        // Generate a UUID for session ID
        return UUID.randomUUID().toString();
    }

    /**
     * Save session ID securely using Android KeyStore
     * @param context Application context
     */
    private void saveSessionId(Context context) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        try {
            // Encrypt the session ID
            String encryptedSessionId = encryptString(sessionId);

            // Save the encrypted session ID to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(SESSION_ID_KEY, encryptedSessionId).apply();

            Log.d(TAG, "Session ID encrypted and saved securely");
        } catch (Exception e) {
            Log.e(TAG, "Error saving session ID: " + e.getMessage());

            // Fallback to plain text storage if encryption fails
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(SESSION_ID_KEY, sessionId).apply();

            Log.w(TAG, "Fallback: Session ID saved in plain text");
        }
    }

    /**
     * Load session ID securely from SharedPreferences
     * @param context Application context
     * @return true if session ID was loaded, false otherwise
     */
    private boolean loadSessionId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedSessionId = prefs.getString(SESSION_ID_KEY, null);

        if (savedSessionId == null || savedSessionId.isEmpty()) {
            return false;
        }

        try {
            // Try to decrypt the session ID (it might be encrypted)
            sessionId = decryptString(savedSessionId);
            Log.d(TAG, "Session ID loaded and decrypted successfully");
            return true;
        } catch (Exception e) {
            // If decryption fails, it might be stored in plain text
            Log.w(TAG, "Error decrypting session ID, trying plain text: " + e.getMessage());

            // Use the plain text value
            sessionId = savedSessionId;

            // Check if it looks like a valid session ID (UUID format)
            if (sessionId.length() > 30 && sessionId.contains("-")) {
                Log.d(TAG, "Session ID loaded from plain text");
                return true;
            } else {
                Log.e(TAG, "Invalid session ID format");
                return false;
            }
        }
    }

    /**
     * Clear session ID from SharedPreferences
     * @param context Application context
     */
    private void clearSessionId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(SESSION_ID_KEY).apply();
        Log.d(TAG, "Session ID cleared from preferences");
    }

    /**
     * Encrypt a string using Android KeyStore
     * @param plainText Text to encrypt
     * @return Encrypted text (Base64 encoded with IV)
     */
    private String encryptString(String plainText) {
        try {
            // Get or create the secret key
            SecretKey secretKey = getOrCreateSecretKey();

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            // Get the IV
            byte[] iv = cipher.getIV();

            // Encrypt the text
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            String ivString = Base64.encodeToString(iv, Base64.DEFAULT);
            String encryptedString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

            // Return combined string
            return ivString + IV_SEPARATOR + encryptedString;
        } catch (Exception e) {
            Log.e(TAG, "Encryption error: " + e.getMessage());
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    /**
     * Decrypt a string using Android KeyStore
     * @param encryptedText Encrypted text (Base64 encoded with IV)
     * @return Decrypted text
     */
    private String decryptString(String encryptedText) {
        try {
            // Split IV and encrypted data
            String[] parts = encryptedText.split(IV_SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted text format");
            }

            // Decode IV and encrypted data
            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(parts[1], Base64.DEFAULT);

            // Get the secret key
            SecretKey secretKey = getOrCreateSecretKey();

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Decrypt the data
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // Return decrypted string
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption error: " + e.getMessage());
            throw new RuntimeException("Error decrypting data", e);
        }
    }

    /**
     * Get or create a secret key for encryption/decryption
     * @return Secret key
     */
    private SecretKey getOrCreateSecretKey() {
        try {
            // Check if key exists
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            if (keyStore.containsAlias(KEY_ALIAS)) {
                // Key exists, retrieve it
                KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                        KEY_ALIAS, null);
                return secretKeyEntry.getSecretKey();
            } else {
                // Key doesn't exist, create it
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build();

                keyGenerator.init(keyGenParameterSpec);
                return keyGenerator.generateKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting/creating secret key: " + e.getMessage());
            throw new RuntimeException("Error accessing keystore", e);
        }
    }

    /**
     * Get current session ID
     * @return Current session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Format expiry date
     * @param date Expiry date
     * @return Formatted date string
     */
    public String formatExpiryDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * Check if the current session is valid
     * @return true if session is valid, false otherwise
     */
    public boolean isSessionValid() {
        if (sessionId == null || sessionId.isEmpty()) {
            Log.d(TAG, "Session ID is null or empty");
            return false;
        }

        try {
            // Create form body for session check
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("sessionid", sessionId)
                    .add("name", APP_NAME)
                    .add("ownerid", APP_OWNER)
                    .add("ver", APP_VERSION)
                    .add("type", "check");

            Log.d(TAG, "Checking session validity");

            // Create cache key for session check (cache for 5 minutes)
            String cacheKey = "session_check_" + sessionId;

            // Make API request with retry logic
            JSONObject json = makeApiRequest(formBuilder.build(), cacheKey);
            boolean isValid = json.getBoolean("success");

            if (isValid) {
                Log.d(TAG, "Session is valid");
                // Update last session refresh time
                lastSessionRefreshTime = System.currentTimeMillis();
            } else {
                Log.e(TAG, "Session is invalid: " + json.getString("message"));
            }

            return isValid;
        } catch (Exception e) {
            Log.e(TAG, "Error checking session: " + e.getMessage());
            return false;
        }
    }

    /**
     * Report usage to the license server
     * @param featureName Name of the feature being used
     * @param usageData Additional usage data (optional)
     */
    public void reportUsage(String featureName, String usageData) {
        // Skip if in development mode
        if (BearLoaderApplication.getInstance().isDevelopmentMode()) {
            return;
        }

        // Get license key
        String licenseKey = BearLoaderApplication.getInstance().getLicenseKey();
        if (licenseKey == null || licenseKey.isEmpty()) {
            Log.e(TAG, "Cannot report usage: No license key available");
            return;
        }

        // Execute in background thread
        executor.execute(() -> {
            try {
                // Create form body for usage reporting with KeyAuth
                FormBody.Builder formBuilder = new FormBody.Builder()
                        .add("type", "log")
                        .add("message", "Feature used: " + featureName)
                        .add("name", APP_NAME)
                        .add("ownerid", APP_OWNER)
                        .add("ver", APP_VERSION)
                        .add("sessionid", sessionId);

                if (usageData != null && !usageData.isEmpty()) {
                    formBuilder.add("pcuser", usageData);
                }

                // Use the standard KeyAuth API URL
                String usageUrl = API_URL;

                // Create request
                Request request = new Request.Builder()
                        .url(usageUrl)
                        .post(formBuilder.build())
                        .build();

                // Execute request directly without using makeApiRequest
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Usage reported successfully for feature: " + featureName);
                    } else {
                        Log.e(TAG, "Failed to report usage: " + response.code());
                    }
                }
                Log.d(TAG, "Usage reported for feature: " + featureName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to report usage: " + e.getMessage());
                // Store for later reporting if needed
            }
        });
    }

    /**
     * Refresh the session
     * @param context Application context
     * @return true if session was refreshed successfully, false otherwise
     */
    public boolean refreshSession(Context context) {
        Log.d(TAG, "Refreshing session...");

        // Force use of the primary URL (keyauth.win)
        forceUsePrimaryUrl();

        // Generate a new session ID
        sessionId = generateSessionId();
        Log.d(TAG, "Generated new session ID");

        try {
            // Create form body for initialization
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("type", "init")
                    .add("name", APP_NAME)
                    .add("ownerid", APP_OWNER)
                    .add("ver", APP_VERSION)
                    .add("sessionid", sessionId);

            // Make API request with retry logic (no caching for session refresh)
            JSONObject json = makeApiRequest(formBuilder.build(), null);
            boolean success = json.getBoolean("success");

            if (success) {
                Log.d(TAG, "Session refreshed successfully");
                saveSessionId(context);
                isInitialized = true;
                lastSessionRefreshTime = System.currentTimeMillis();

                // Clear response cache when session is refreshed
                responseCache.clear();

                return true;
            } else {
                String message = json.getString("message");
                Log.e(TAG, "Session refresh failed: " + message);
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Network error refreshing session: " + e.getMessage());
            return false;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing session refresh response: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing session: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get remaining days until expiry
     * @param expiryDate Expiry date
     * @return Remaining days
     */
    public int getRemainingDays(Date expiryDate) {
        long diff = expiryDate.getTime() - System.currentTimeMillis();
        return (int) (diff / (24 * 60 * 60 * 1000));
    }

    /**
     * Auth callback interface
     */
    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(String error);
    }

    /**
     * Download a file from KeyAuth
     * @param fileId The ID of the file to download
     * @param callback Callback for download result
     */
    public void downloadFile(String fileId, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                // Check if KeyAuth is initialized and session is valid
                if (!isInitialized || sessionId == null) {
                    handler.post(() -> callback.onError("KeyAuth not initialized or session invalid"));
                    return;
                }

                // Construct the download URL
                String downloadUrl = KEYAUTH_FILE_API_URL +
                        "?type=file" +
                        "&fileid=" + fileId +
                        "&sessionid=" + sessionId +
                        "&name=" + APP_NAME +
                        "&ownerid=" + APP_OWNER;

                // Create the download request
                Request request = new Request.Builder()
                        .url(downloadUrl)
                        .get()
                        .build();

                Log.d(TAG, "Downloading file from: " + downloadUrl);

                // Execute the request
                Response response = client.newCall(request).execute();

                // Handle the response
                if (response.isSuccessful() && response.body() != null) {
                    byte[] fileBytes = response.body().bytes();
                    Log.d(TAG, "File downloaded successfully: " + fileBytes.length + " bytes");
                    handler.post(() -> callback.onSuccess(fileBytes));
                } else {
                    String error = "Failed to download file: " + response.code();
                    if (response.body() != null) {
                        error += " - " + response.body().string();
                    }
                    Log.e(TAG, error);
                    final String finalError = error;
                    handler.post(() -> callback.onError(finalError));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during file download: " + e.getMessage(), e);
                final String finalErrorMessage = "Error during file download: " + e.getMessage();
                handler.post(() -> callback.onError(finalErrorMessage));
            }
        });
    }

    /**
     * Callback interface for file download
     */
    public interface DownloadCallback {
        void onSuccess(byte[] fileBytes);
        void onError(String error);
    }
}
