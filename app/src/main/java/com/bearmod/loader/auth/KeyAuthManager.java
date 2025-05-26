package com.bearmod.loader.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bearmod.loader.BearLoaderApplication;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * KeyAuth authentication manager using official KeyAuth Java SDK
 * Handles user authentication with robust error handling and retry logic
 */
public class KeyAuthManager {

    private static final String TAG = "KeyAuthManager";

    private static KeyAuthManager instance;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // KeyAuth configuration (matching C++ implementation)
    private static final String APP_NAME = "com.bearmod.loader";
    private static final String APP_OWNER = "yLoA9zcOEF";
    private static final String APP_SECRET = "e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d";
    private static final String APP_VERSION = "1.3";

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final long EXPONENTIAL_BACKOFF_MULTIPLIER = 2;

    // HTTP client for KeyAuth API
    private OkHttpClient httpClient;
    private boolean isInitialized = false;
    private String sessionId;

    // KeyAuth API endpoint (using standard KeyAuth)
    private static final String KEYAUTH_API_URL = "https://keyauth.win/api/1.3/";

    private KeyAuthManager() {
        // Private constructor to enforce singleton pattern
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        // Generate session ID
        sessionId = generateSessionId();
    }

    /**
     * Generate a random session ID
     */
    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate Hardware ID for device identification
     * Uses Android device identifiers to create a unique HWID
     */
    private String generateHWID(Context context) {
        try {
            // Get Android ID (most reliable identifier)
            String androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );

            // Get device serial number (if available)
            String serial = "";
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    serial = android.os.Build.getSerial();
                } else {
                    serial = android.os.Build.SERIAL;
                }
            } catch (Exception e) {
                // Serial might not be available, use device model instead
                serial = android.os.Build.MODEL;
            }

            // Combine identifiers to create unique HWID
            String combined = androidId + "-" + serial + "-" + android.os.Build.FINGERPRINT;

            // Hash the combined string for consistency and privacy
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes("UTF-8"));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().substring(0, 32); // Return first 32 characters

        } catch (Exception e) {
            Log.e(TAG, "Error generating HWID: " + e.getMessage(), e);
            // Fallback to a device-specific identifier
            return android.os.Build.FINGERPRINT.hashCode() + "";
        }
    }

    /**
     * Generate application hash for integrity checking
     * Similar to the official KeyAuth implementation
     */
    private String generateAppHash(Context context) {
        try {
            // Get application package info
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);

            // Create a hash based on package name, version, and signature
            String combined = packageInfo.packageName + "-" +
                             packageInfo.versionName + "-" +
                             packageInfo.versionCode + "-" +
                             APP_NAME + "-" + APP_VERSION;

            // Hash the combined string
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(combined.getBytes("UTF-8"));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error generating app hash: " + e.getMessage(), e);
            // Fallback hash
            return "fallback_hash_" + APP_NAME.hashCode();
        }
    }

    /**
     * Get KeyAuthManager instance
     * @return KeyAuthManager instance
     */
    public static synchronized KeyAuthManager getInstance() {
        if (instance == null) {
            instance = new KeyAuthManager();
        }
        return instance;
    }

    /**
     * Initialize KeyAuth with robust error handling and retry logic
     * @param context Application context
     * @param callback Callback for initialization result
     */
    public void initialize(Context context, AuthCallback callback) {
        if (isInitialized) {
            if (callback != null) {
                callback.onSuccess(new AuthResult(true, "KeyAuth already initialized", null, null));
            }
            return;
        }

        executor.execute(() -> initializeWithRetry(context, callback, 0));
    }

    /**
     * Initialize KeyAuth with exponential backoff retry logic
     */
    private void initializeWithRetry(Context context, AuthCallback callback, int retryCount) {
        try {
            Log.i(TAG, "=== KeyAuth Initialization Attempt " + (retryCount + 1) + "/" + MAX_RETRIES + " ===");
            Log.i(TAG, "App Name: " + APP_NAME);
            Log.i(TAG, "Owner ID: " + APP_OWNER);
            Log.i(TAG, "Version: " + APP_VERSION);
            Log.i(TAG, "Session ID: " + sessionId);

            // Use the fixed hash as specified
            String appHash = "60885ac0cf1061079d5756a689630d13";
            Log.i(TAG, "Using App Hash: " + appHash);

            // Create initialization request (GET request with query parameters)
            String apiUrl = KEYAUTH_API_URL + "?type=init&name=" + APP_NAME +
                           "&ownerid=" + APP_OWNER + "&ver=" + APP_VERSION + "&hash=" + appHash;

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("User-Agent", "KeyAuth")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    boolean success = json.optBoolean("success", false);
                    if (success) {
                        // Update session ID with the one returned by KeyAuth
                        String returnedSessionId = json.optString("sessionid", null);
                        if (returnedSessionId != null && !returnedSessionId.isEmpty()) {
                            sessionId = returnedSessionId;
                            Log.d(TAG, "Updated session ID: " + sessionId);
                        }

                        isInitialized = true;
                        Log.d(TAG, "KeyAuth initialized successfully");

                        handler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(new AuthResult(true, "KeyAuth initialized successfully", null, null));
                            }
                        });
                        return;
                    } else {
                        String message = json.optString("message", "Unknown error");
                        throw new Exception("KeyAuth initialization failed: " + message);
                    }
                } else {
                    throw new Exception("HTTP request failed: " + response.code());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize KeyAuth (attempt " + (retryCount + 1) + "): " + e.getMessage(), e);

            if (retryCount < MAX_RETRIES - 1) {
                // Retry with exponential backoff
                long delay = RETRY_DELAY_MS * (long) Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, retryCount);
                Log.d(TAG, "Retrying KeyAuth initialization in " + delay + "ms");

                handler.postDelayed(() -> {
                    executor.execute(() -> initializeWithRetry(context, callback, retryCount + 1));
                }, delay);
            } else {
                // All retries exhausted
                String errorMessage = getUserFriendlyErrorMessage(e);
                Log.e(TAG, "KeyAuth initialization failed after " + MAX_RETRIES + " attempts");

                handler.post(() -> {
                    if (callback != null) {
                        callback.onError(errorMessage);
                    }
                });
            }
        }
    }

    /**
     * Login with license key with retry logic and robust error handling
     * @param licenseKey License key
     * @param callback Callback for login result
     */
    public void login(String licenseKey, AuthCallback callback) {
        // Validate input
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            handler.post(() -> callback.onError("Please enter a valid license key"));
            return;
        }

        // Check if KeyAuth is initialized
        if (!isInitialized) {
            handler.post(() -> callback.onError("Authentication service not available. Please try again later."));
            return;
        }

        // Execute login in background thread with retry logic
        executor.execute(() -> loginWithRetry(licenseKey.trim(), callback, 0));
    }

    /**
     * Login with retry logic
     */
    private void loginWithRetry(String licenseKey, AuthCallback callback, int retryCount) {
        try {
            Log.d(TAG, "Attempting login (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");
            Log.d(TAG, "License key being sent: " + licenseKey);

            // Validate license format
            if (!isValidLicenseFormat(licenseKey)) {
                handler.post(() -> callback.onError("Invalid license key format"));
                return;
            }

            // Generate HWID for this device
            String hwid = generateHWID(BearLoaderApplication.getInstance());

            // Create license login request (GET request with query parameters)
            String apiUrl = KEYAUTH_API_URL + "?type=license&key=" + licenseKey +
                           "&sessionid=" + sessionId + "&name=" + APP_NAME +
                           "&ownerid=" + APP_OWNER + "&hwid=" + hwid;

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("User-Agent", "KeyAuth")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    boolean success = json.optBoolean("success", false);
                    if (success) {
                        // Get license information from response
                        JSONObject info = json.optJSONObject("info");
                        String expiryString = null;
                        String registrationDate = null;

                        if (info != null) {
                            expiryString = info.optString("expiry", null);
                            registrationDate = info.optString("createdate", null);
                        }

                        // Parse expiry date with robust error handling
                        Date expiryDate = parseExpiryDate(expiryString);

                        // Check if license is expired
                        if (expiryDate != null && expiryDate.before(new Date())) {
                            handler.post(() -> callback.onError("Your license has expired. Please renew your subscription."));
                            return;
                        }

                        // Save license key and login status
                        BearLoaderApplication.getInstance().saveLicenseKey(licenseKey);
                        BearLoaderApplication.getInstance().setLoggedIn(true);

                        // Create auth result
                        final AuthResult result = new AuthResult(
                                true,
                                "Login successful",
                                expiryDate,
                                registrationDate
                        );

                        Log.d(TAG, "Login successful for license: " + licenseKey.substring(0, Math.min(8, licenseKey.length())) + "...");
                        handler.post(() -> callback.onSuccess(result));

                    } else {
                        String message = json.optString("message", "License validation failed");
                        throw new Exception(message);
                    }
                } else {
                    throw new Exception("HTTP request failed: " + response.code());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Login error (attempt " + (retryCount + 1) + "): " + e.getMessage(), e);

            if (retryCount < MAX_RETRIES - 1 && isRetryableError(e)) {
                // Retry with exponential backoff for network errors
                long delay = RETRY_DELAY_MS * (long) Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, retryCount);
                Log.d(TAG, "Retrying login in " + delay + "ms");

                handler.postDelayed(() -> {
                    executor.execute(() -> loginWithRetry(licenseKey, callback, retryCount + 1));
                }, delay);
            } else {
                // Return user-friendly error message
                String errorMessage = getUserFriendlyErrorMessage(e);
                handler.post(() -> callback.onError(errorMessage));
            }
        }
    }

    /**
     * Check if license is valid with retry logic and robust error handling
     * @param callback Callback for validation result
     */
    public void validateLicense(AuthCallback callback) {
        // Check if KeyAuth is initialized
        if (!isInitialized) {
            handler.post(() -> callback.onError("Authentication service not available. Please try again later."));
            return;
        }

        // Get license key
        String licenseKey = BearLoaderApplication.getInstance().getLicenseKey();

        // Check if license key exists
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            handler.post(() -> callback.onError("No license key found. Please log in again."));
            return;
        }

        // Execute validation in background thread with retry logic
        executor.execute(() -> validateLicenseWithRetry(licenseKey.trim(), callback, 0));
    }

    /**
     * Validate license with retry logic
     */
    private void validateLicenseWithRetry(String licenseKey, AuthCallback callback, int retryCount) {
        try {
            Log.d(TAG, "Validating license (attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ")");

            // Generate HWID for this device
            String hwid = generateHWID(BearLoaderApplication.getInstance());

            // Create license validation request (GET request with query parameters)
            String apiUrl = KEYAUTH_API_URL + "?type=license&key=" + licenseKey +
                           "&sessionid=" + sessionId + "&name=" + APP_NAME +
                           "&ownerid=" + APP_OWNER + "&hwid=" + hwid;

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("User-Agent", "KeyAuth")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    boolean success = json.optBoolean("success", false);
                    if (success) {
                        // Get license information from response
                        JSONObject info = json.optJSONObject("info");
                        String expiryString = null;
                        String registrationDate = null;

                        if (info != null) {
                            expiryString = info.optString("expiry", null);
                            registrationDate = info.optString("createdate", null);
                        }

                        // Parse expiry date with robust error handling
                        Date expiryDate = parseExpiryDate(expiryString);

                        // Check if license is expired
                        if (expiryDate != null && expiryDate.before(new Date())) {
                            // Clear login status for expired license
                            BearLoaderApplication.getInstance().setLoggedIn(false);
                            handler.post(() -> callback.onError("Your license has expired. Please renew your subscription."));
                            return;
                        }

                        // Create auth result
                        final AuthResult result = new AuthResult(
                                true,
                                "License valid",
                                expiryDate,
                                registrationDate
                        );

                        Log.d(TAG, "License validation successful");
                        handler.post(() -> callback.onSuccess(result));

                    } else {
                        String message = json.optString("message", "License validation failed");
                        throw new Exception(message);
                    }
                } else {
                    throw new Exception("HTTP request failed: " + response.code());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "License validation error (attempt " + (retryCount + 1) + "): " + e.getMessage(), e);

            if (retryCount < MAX_RETRIES - 1 && isRetryableError(e)) {
                // Retry with exponential backoff for network errors
                long delay = RETRY_DELAY_MS * (long) Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, retryCount);
                Log.d(TAG, "Retrying license validation in " + delay + "ms");

                handler.postDelayed(() -> {
                    executor.execute(() -> validateLicenseWithRetry(licenseKey, callback, retryCount + 1));
                }, delay);
            } else {
                // Clear login status on validation failure
                BearLoaderApplication.getInstance().setLoggedIn(false);

                // Return user-friendly error message
                String errorMessage = getUserFriendlyErrorMessage(e);
                handler.post(() -> callback.onError(errorMessage));
            }
        }
    }

    /**
     * Logout user
     */
    public void logout() {
        try {
            // Reset session for next login
            sessionId = generateSessionId();
            isInitialized = false;

            Log.d(TAG, "User logged out successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: " + e.getMessage());
        } finally {
            // Clear user data regardless of logout success
            BearLoaderApplication.getInstance().clearUserData();
        }
    }

    /**
     * Format expiry date
     * @param date Expiry date
     * @return Formatted date string
     */
    public String formatExpiryDate(Date date) {
        if (date == null) {
            return "No expiry date";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * Get remaining days until expiry
     * @param expiryDate Expiry date
     * @return Remaining days (-1 if no expiry date)
     */
    public int getRemainingDays(Date expiryDate) {
        if (expiryDate == null) {
            return -1; // Indicate no expiry date available
        }
        long diff = expiryDate.getTime() - System.currentTimeMillis();
        return (int) (diff / (24 * 60 * 60 * 1000));
    }

    /**
     * Validate license format
     */
    private boolean isValidLicenseFormat(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return false;
        }

        // Basic format validation - adjust based on your license key format
        String trimmed = licenseKey.trim();
        return trimmed.length() >= 8 && trimmed.length() <= 64 &&
               trimmed.matches("^[a-zA-Z0-9\\-_]+$");
    }

    /**
     * Parse expiry date with robust error handling
     */
    private Date parseExpiryDate(String expiryString) {
        if (expiryString == null || expiryString.trim().isEmpty()) {
            return null;
        }

        try {
            String trimmed = expiryString.trim();

            // Try parsing as Unix timestamp first (KeyAuth typically uses this)
            try {
                long timestamp = Long.parseLong(trimmed);
                // If it's a reasonable timestamp (after year 2000)
                if (timestamp > 946684800) { // Jan 1, 2000
                    return new Date(timestamp * 1000L); // Convert to milliseconds
                }
            } catch (NumberFormatException ignored) {
                // Not a timestamp, try other formats
            }

            // Try different date formats
            String[] formats = {
                "yyyy-MM-dd",
                "yyyy-MM-dd HH:mm:ss",
                "MM/dd/yyyy",
                "dd/MM/yyyy",
                "yyyy/MM/dd"
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
                    return dateFormat.parse(trimmed);
                } catch (Exception ignored) {
                    // Try next format
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse expiry date: " + expiryString, e);
        }

        return null;
    }

    /**
     * Check if error is retryable (network-related)
     */
    private boolean isRetryableError(Exception e) {
        if (e == null) return false;

        String message = e.getMessage();
        if (message == null) return false;

        message = message.toLowerCase();
        return message.contains("network") ||
               message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("unreachable") ||
               message.contains("socket") ||
               message.contains("io exception");
    }

    /**
     * Convert technical errors to user-friendly messages
     * Based on official KeyAuth error patterns
     */
    private String getUserFriendlyErrorMessage(Exception e) {
        if (e == null) {
            return "An unknown error occurred. Please try again.";
        }

        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "An unknown error occurred. Please try again.";
        }

        // Convert to lowercase for easier matching
        String lowerMessage = message.toLowerCase();

        // KeyAuth-specific error messages (based on official implementation)
        if (lowerMessage.contains("invalid license") || lowerMessage.contains("license not found")) {
            return "Invalid license key. Please check your license key and try again.";
        }
        if (lowerMessage.contains("expired") || lowerMessage.contains("subscription expired")) {
            return "Your license has expired. Please renew your subscription.";
        }
        if (lowerMessage.contains("banned") || lowerMessage.contains("blacklisted") || lowerMessage.contains("hwid blacklisted")) {
            return "Your account has been suspended. Please contact support.";
        }
        if (lowerMessage.contains("hwid mismatch") || lowerMessage.contains("hardware id")) {
            return "Hardware ID mismatch. Please contact support if you've changed devices.";
        }
        if (lowerMessage.contains("session") && lowerMessage.contains("invalid")) {
            return "Session expired. Please restart the application.";
        }
        if (lowerMessage.contains("application not found") || lowerMessage.contains("invalid application")) {
            return "Application configuration error. Please contact support.";
        }
        if (lowerMessage.contains("version") && lowerMessage.contains("invalid")) {
            return "Application version mismatch. Please update the application.";
        }

        // Network-related errors
        if (e instanceof IOException || lowerMessage.contains("timeout") ||
            lowerMessage.contains("connection") || lowerMessage.contains("network") ||
            lowerMessage.contains("unreachable") || lowerMessage.contains("socket")) {
            return "Network connection error. Please check your internet connection and try again.";
        }

        // HTTP status code errors
        if (lowerMessage.contains("429")) {
            return "Too many requests. Please wait a moment and try again.";
        }
        if (lowerMessage.contains("500") || lowerMessage.contains("502") ||
            lowerMessage.contains("503") || lowerMessage.contains("504")) {
            return "Server temporarily unavailable. Please try again in a few minutes.";
        }

        // Default fallback
        return "Authentication failed. Please try again or contact support if the problem persists.";
    }

    /**
     * Auth callback interface
     */
    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(String error);
    }
}
