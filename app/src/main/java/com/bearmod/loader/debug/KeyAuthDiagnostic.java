package com.bearmod.loader.debug;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * KeyAuth Diagnostic Tool
 * Tests connectivity and configuration
 */
public class KeyAuthDiagnostic {
    private static final String TAG = "KeyAuthDiagnostic";

    // Your exact KeyAuth configuration (matching C++ implementation)
    private static final String APP_NAME = "com.bearmod.loader";
    private static final String APP_OWNER = "yLoA9zcOEF";
    private static final String APP_SECRET = "e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d";
    private static final String APP_VERSION = "1.3";

    private static final String CUSTOM_URL = "https://api.mod-key.click/";

    public static void runDiagnostic(Context context) {
        Log.i(TAG, "=== KeyAuth Diagnostic Starting ===");
        Log.i(TAG, "App Name: " + APP_NAME);
        Log.i(TAG, "Owner ID: " + APP_OWNER);
        Log.i(TAG, "Version: " + APP_VERSION);

        // Test custom domain only
        testUrl(CUSTOM_URL, "Custom Domain");
    }

    private static void testUrl(String url, String serverName) {
        Log.i(TAG, "\n=== Testing " + serverName + " ===");
        Log.i(TAG, "URL: " + url);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

        // Create test initialization request (matching C++ implementation)
        FormBody requestBody = new FormBody.Builder()
            .add("type", "init")
            .add("name", APP_NAME)
            .add("ownerid", APP_OWNER)
            .add("secret", APP_SECRET)
            .add("ver", APP_VERSION)
            .add("sessionid", "test-session-" + System.currentTimeMillis())
            .add("hash", "60885ac0cf1061079d5756a689630d13")
            .build();

        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("User-Agent", "KeyAuth-Android-Diagnostic")
            .build();

        try {
            Log.i(TAG, "Sending request...");
            Response response = client.newCall(request).execute();

            Log.i(TAG, "Response Code: " + response.code());
            Log.i(TAG, "Response Message: " + response.message());

            if (response.body() != null) {
                String responseBody = response.body().string();
                Log.i(TAG, "Response Body: " + responseBody);

                try {
                    JSONObject json = new JSONObject(responseBody);
                    boolean success = json.optBoolean("success", false);
                    String message = json.optString("message", "No message");

                    Log.i(TAG, "JSON Success: " + success);
                    Log.i(TAG, "JSON Message: " + message);

                    if (success) {
                        Log.i(TAG, "✅ " + serverName + " is working!");
                    } else {
                        Log.w(TAG, "⚠️ " + serverName + " responded but with error: " + message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to parse JSON response", e);
                }
            } else {
                Log.e(TAG, "❌ No response body");
            }

            response.close();

        } catch (IOException e) {
            Log.e(TAG, "❌ " + serverName + " connection failed: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "❌ " + serverName + " unexpected error: " + e.getMessage(), e);
        }
    }
}
