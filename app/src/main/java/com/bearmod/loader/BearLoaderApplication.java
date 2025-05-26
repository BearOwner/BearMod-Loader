package com.bearmod.loader;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bearmod.loader.auth.KeyAuthManager;
import com.bearmod.loader.cloud.CloudSyncManager;
import com.bearmod.loader.database.AppDatabase;
import com.bearmod.loader.download.DownloadManager;
import com.bearmod.loader.network.NetworkManager;
import com.bearmod.loader.patch.PatchManager;
import com.bearmod.loader.util.BuildValidator;

/**
 * Main application class for BearMod-Loader
 * Handles global application state and initialization
 */
public class BearLoaderApplication extends Application {

    private static BearLoaderApplication instance;
    private SharedPreferences preferences;

    // Development mode flag - set to false for production
    private static final boolean DEVELOPMENT_MODE = false;

    // Mock license key for development mode
    private static final String DEV_LICENSE_KEY = "DEVKEY-BEARMOD-LOADER-TEST";

    // Tag for logging
    private static final String TAG = "BearLoaderApp";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        preferences = getSharedPreferences("bear_loader_prefs", Context.MODE_PRIVATE);

        // Initialize components
        initializeComponents();
    }

    /**
     * Initialize application components
     */
    private void initializeComponents() {
        try {
            Log.d(TAG, "Starting component initialization");

            // Validate build
            Log.d(TAG, "Validating build...");
            BuildValidator.ValidationResult result = BuildValidator.validateBuild(this);
            if (result.isValid()) {
                Log.d(TAG, "Build validation successful");
                if (result.hasWarnings()) {
                    Log.w(TAG, "Build has warnings: " + result.getWarnings().size());
                }
            } else {
                Log.e(TAG, "Build validation failed with errors: " + result.getErrors().size());
                // Continue anyway since this is just initialization
            }

            // Initialize database
            Log.d(TAG, "Initializing database...");
            AppDatabase.getInstance(this);

            // Initialize NetworkManager
            Log.d(TAG, "Initializing NetworkManager...");
            NetworkManager.getInstance(this);

            // Initialize KeyAuth using direct implementation with timeout
            Log.d(TAG, "Initializing KeyAuth...");
            Thread keyAuthThread = getThread();

            // Wait for the thread to complete with a timeout
            try {
                keyAuthThread.join(3000); // 3 second timeout
                if (keyAuthThread.isAlive()) {
                    Log.w(TAG, "KeyAuth initialization timed out, continuing with app startup");
                    // Don't interrupt the thread, let it continue in the background
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "KeyAuth initialization interrupted: " + e.getMessage(), e);
            }

            // Initialize CloudSyncManager
            Log.d(TAG, "Initializing CloudSyncManager...");
            CloudSyncManager cloudSyncManager = CloudSyncManager.getInstance();
            cloudSyncManager.initialize(this);

            // Initialize DownloadManager
            Log.d(TAG, "Initializing DownloadManager...");
            DownloadManager downloadManager = DownloadManager.getInstance();
            downloadManager.initialize(this);

            // Initialize PatchManager
            Log.d(TAG, "Initializing PatchManager...");
            PatchManager patchManager = PatchManager.getInstance();
            patchManager.initialize(this);

            Log.d(TAG, "Component initialization completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during component initialization: " + e.getMessage(), e);
            // Continue anyway to allow the app to start
        }
    }

    @NonNull
    private Thread getThread() {
        KeyAuthManager keyAuthManager = KeyAuthManager.getInstance();

        // Create a thread to initialize KeyAuth with a timeout
        Thread keyAuthThread = new Thread(() -> {
            try {
                keyAuthManager.initialize(this, new KeyAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(com.bearmod.loader.auth.AuthResult result) {
                        Log.d(TAG, "KeyAuth initialized successfully");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "KeyAuth initialization failed: " + error);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error initializing KeyAuth: " + e.getMessage(), e);
            }
        });

        // Start the thread
        keyAuthThread.start();
        return keyAuthThread;
    }

    /**
     * Get application instance
     * @return Application instance
     */
    public static BearLoaderApplication getInstance() {
        return instance;
    }

    /**
     * Get shared preferences
     * @return SharedPreferences instance
     */
    public SharedPreferences getPreferences() {
        return preferences;
    }

    /**
     * Check if development mode is enabled
     * @return true if development mode is enabled, false otherwise
     */
    public boolean isDevelopmentMode() {
        Log.d(TAG, "Development mode check: " + DEVELOPMENT_MODE);
        return DEVELOPMENT_MODE;
    }

    /**
     * Check if user is logged in
     * @return true if logged in, false otherwise
     */
    public boolean isLoggedIn() {
        // In development mode, always return true
        if (DEVELOPMENT_MODE) {
            return true;
        }
        return preferences.getBoolean("is_logged_in", false);
    }

    /**
     * Set login status
     * @param isLoggedIn Login status
     */
    public void setLoggedIn(boolean isLoggedIn) {
        preferences.edit().putBoolean("is_logged_in", isLoggedIn).apply();
    }

    /**
     * Save license key
     * @param licenseKey License key
     */
    public void saveLicenseKey(String licenseKey) {
        preferences.edit().putString("license_key", licenseKey).apply();
    }

    /**
     * Get license key
     * @return License key
     */
    public String getLicenseKey() {
        // In development mode, return mock license key
        if (DEVELOPMENT_MODE) {
            return DEV_LICENSE_KEY;
        }
        return preferences.getString("license_key", "");
    }

    /**
     * Clear all user data
     */
    public void clearUserData() {
        preferences.edit()
                .remove("is_logged_in")
                .remove("license_key")
                .apply();
    }
}
