package com.bearmod.loader.download;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker class for downloading patches in the background
 * Uses WorkManager for reliable background processing
 */
public class PatchDownloadWorker extends Worker {
    
    private static final String TAG = "PatchDownloadWorker";
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // Progress tracking
    private static DownloadProgressListener progressListener;
    
    /**
     * Constructor
     * @param context Application context
     * @param workerParams Worker parameters
     */
    public PatchDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    /**
     * Set progress listener
     * @param listener Download progress listener
     */
    public static void setProgressListener(DownloadProgressListener listener) {
        progressListener = listener;
    }
    
    @NonNull
    @Override
    public Result doWork() {
        // Get input data
        String downloadUrl = getInputData().getString("downloadUrl");
        String fileName = getInputData().getString("fileName");
        double totalSizeMB = getInputData().getDouble("totalSizeMB", 0);
        
        if (downloadUrl == null || fileName == null) {
            Log.e(TAG, "Missing required parameters");
            return Result.failure();
        }
        
        // Validate URL
        if (!DownloadManager.getInstance().validatePatchUrl(downloadUrl)) {
            Log.e(TAG, "Invalid or untrusted download URL: " + downloadUrl);
            return Result.failure(new Data.Builder()
                    .putString("error", "Invalid or untrusted download URL")
                    .build());
        }
        
        try {
            // Create download directory
            File downloadDir = new File(getApplicationContext().getExternalFilesDir(null), "downloads");
            if (!downloadDir.exists()) {
                if (!downloadDir.mkdirs()) {
                    Log.e(TAG, "Failed to create download directory");
                    return Result.failure(new Data.Builder()
                            .putString("error", "Failed to create download directory")
                            .build());
                }
            }
            
            // Create output file
            File outputFile = new File(downloadDir, fileName);
            
            // Download the file
            boolean success = downloadFile(downloadUrl, outputFile, totalSizeMB);
            
            if (isCancelled.get()) {
                // Delete partial file if download was cancelled
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                return Result.failure(new Data.Builder()
                        .putString("error", "Download cancelled")
                        .build());
            }
            
            if (!success) {
                return Result.failure(new Data.Builder()
                        .putString("error", "Download failed")
                        .build());
            }
            
            // Return success with file path
            return Result.success(new Data.Builder()
                    .putString("filePath", outputFile.getAbsolutePath())
                    .build());
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return Result.failure(new Data.Builder()
                    .putString("error", "Download failed: " + e.getMessage())
                    .build());
        }
    }
    
    /**
     * Download file from URL
     * @param downloadUrl URL to download from
     * @param outputFile Output file
     * @param totalSizeMB Total size in MB (for progress calculation)
     * @return true if download was successful, false otherwise
     */
    private boolean downloadFile(String downloadUrl, File outputFile, double totalSizeMB) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        
        try {
            // Open connection
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // Get file size
            int fileSize = connection.getContentLength();
            if (fileSize <= 0) {
                fileSize = (int) (totalSizeMB * 1024 * 1024); // Convert MB to bytes
            }
            
            // Connect
            connection.connect();
            
            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP " + responseCode);
                return false;
            }
            
            // Open input stream
            input = connection.getInputStream();
            
            // Open output stream
            output = new FileOutputStream(outputFile);
            
            // Download file
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            long lastProgressUpdateTime = System.currentTimeMillis();
            long startTime = System.currentTimeMillis();
            
            while ((bytesRead = input.read(buffer)) != -1) {
                // Check if download was cancelled
                if (isCancelled.get()) {
                    return false;
                }
                
                // Write to file
                output.write(buffer, 0, bytesRead);
                
                // Update progress
                totalBytesRead += bytesRead;
                
                // Calculate progress percentage
                int progress = fileSize > 0 ? (int) ((totalBytesRead * 100) / fileSize) : 0;
                
                // Calculate download speed and ETA
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProgressUpdateTime >= 500 || progress >= 100) { // Update every 500ms
                    lastProgressUpdateTime = currentTime;
                    
                    // Calculate download speed (bytes per second)
                    double elapsedTimeSeconds = (currentTime - startTime) / 1000.0;
                    double downloadSpeedBps = elapsedTimeSeconds > 0 ? totalBytesRead / elapsedTimeSeconds : 0;
                    
                    // Calculate estimated time remaining (seconds)
                    double remainingBytes = fileSize - totalBytesRead;
                    double etaSeconds = downloadSpeedBps > 0 ? remainingBytes / downloadSpeedBps : 0;
                    
                    // Convert to MB for display
                    final double downloadedMB = totalBytesRead / (1024.0 * 1024.0);
                    final double totalSizeMBFinal = fileSize > 0 ? fileSize / (1024.0 * 1024.0) : totalSizeMB;
                    final double speedMBps = downloadSpeedBps / (1024.0 * 1024.0);
                    final int etaMinutes = (int) (etaSeconds / 60);
                    final int etaSeconds2 = (int) (etaSeconds % 60);
                    
                    // Update progress on main thread
                    final int finalProgress = progress;
                    mainHandler.post(() -> {
                        if (progressListener != null) {
                            progressListener.onProgressUpdate(
                                    finalProgress, 
                                    downloadedMB, 
                                    totalSizeMBFinal,
                                    speedMBps,
                                    etaMinutes,
                                    etaSeconds2);
                        }
                    });
                }
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return false;
        } finally {
            // Close streams
            try {
                if (output != null) output.close();
                if (input != null) input.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
            
            // Disconnect
            if (connection != null) connection.disconnect();
        }
    }
    
    @Override
    public void onStopped() {
        super.onStopped();
        isCancelled.set(true);
    }
    
    /**
     * Download progress listener interface with enhanced information
     */
    public interface DownloadProgressListener {
        void onProgressUpdate(int progress, double downloadedMB, double totalSizeMB, 
                             double speedMBps, int etaMinutes, int etaSeconds);
    }
}
