package com.bearmod.loader.download;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import androidx.work.WorkRequest;

import com.bearmod.loader.model.PatchRelease;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Download manager
 * Handles downloading patches from cloud or GitHub
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static final String DOWNLOAD_WORK_NAME = "patch_download_work";

    private static DownloadManager instance;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Context context;
    private boolean isDownloading = false;
    private UUID currentWorkId;
    private DownloadProgressListener progressListener;
    private WorkManager workManager;

    /**
     * Private constructor to enforce singleton pattern
     */
    private DownloadManager() {
        // Private constructor
    }

    /**
     * Get DownloadManager instance
     * @return DownloadManager instance
     */
    public static synchronized DownloadManager getInstance() {
        if (instance == null) {
            instance = new DownloadManager();
        }
        return instance;
    }

    /**
     * Initialize download manager
     * @param context Application context
     */
    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(context);

        // Observe any existing work
        observeOngoingDownloads();
    }

    /**
     * Observe ongoing downloads
     * This allows the app to reconnect to ongoing downloads after process death
     */
    private void observeOngoingDownloads() {
        LiveData<List<WorkInfo>> workInfoLiveData = workManager.getWorkInfosForUniqueWorkLiveData(DOWNLOAD_WORK_NAME);

        // This would typically be observed by a ViewModel or Activity
        // For demonstration, we'll just log the status
        workInfoLiveData.observeForever(workInfoList -> {
            if (workInfoList != null && !workInfoList.isEmpty()) {
                WorkInfo workInfo = workInfoList.get(0);

                if (workInfo.getState() == WorkInfo.State.RUNNING) {
                    // We have an ongoing download
                    isDownloading = true;
                    currentWorkId = workInfo.getId();
                    Log.d(TAG, "Reconnected to ongoing download: " + currentWorkId);
                } else if (workInfo.getState().isFinished()) {
                    // Download finished (completed, failed, or cancelled)
                    isDownloading = false;
                    currentWorkId = null;
                    Log.d(TAG, "Download finished with state: " + workInfo.getState());
                }
            }
        });
    }

    /**
     * Validate patch URL to ensure it's from a trusted source
     * @param url URL to validate
     * @return True if URL is valid, false otherwise
     */
    public boolean validatePatchUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // List of trusted domains
        String[] trustedDomains = {
                "github.com",
                "api.github.com",
                "raw.githubusercontent.com",
                "bearmod.com",
                "cdn.bearmod.com",
                "storage.googleapis.com",
                "firebasestorage.googleapis.com"
        };

        // Check if URL is from a trusted domain
        for (String domain : trustedDomains) {
            if (url.startsWith("https://" + domain) ||
                url.startsWith("https://www." + domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Download patch using WorkManager for reliable background processing
     * @param release Patch release to download
     * @param listener Download listener
     */
    public void downloadPatch(PatchRelease release, DownloadListener listener) {
        // Check if already downloading
        if (isDownloading) {
            listener.onError("Download already in progress");
            return;
        }

        // Validate download URL
        if (!validatePatchUrl(release.getDownloadUrl())) {
            listener.onError("Invalid or untrusted download URL");
            return;
        }

        try {
            // Create file name
            String fileName = release.getName().replaceAll("\\s+", "-").toLowerCase() + ".zip";

            // Create input data for WorkManager
            Data inputData = new Data.Builder()
                    .putString("downloadUrl", release.getDownloadUrl())
                    .putString("fileName", fileName)
                    .putDouble("totalSizeMB", release.getTotalSizeMB())
                    .build();

            // Create work request
            OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(PatchDownloadWorker.class)
                    .setInputData(inputData)
                    .build();

            // Store work ID for cancellation
            currentWorkId = downloadWorkRequest.getId();

            // Set progress listener for the worker
            PatchDownloadWorker.setProgressListener((progress, downloadedMB, totalSizeMB, speedMBps, etaMinutes, etaSeconds) -> {
                if (progressListener != null) {
                    // Forward progress updates to our listener with enhanced information
                    handler.post(() -> progressListener.onProgressUpdate(
                            progress, downloadedMB, totalSizeMB, speedMBps, etaMinutes, etaSeconds));
                }
            });

            // Observe work status
            workManager.getWorkInfoByIdLiveData(currentWorkId).observeForever(workInfo -> {
                if (workInfo != null) {
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        // Get output file path
                        String filePath = workInfo.getOutputData().getString("filePath");
                        if (filePath != null) {
                            File outputFile = new File(filePath);
                            handler.post(() -> {
                                isDownloading = false;
                                listener.onSuccess(outputFile);
                            });
                        }
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        // Get error message
                        String error = workInfo.getOutputData().getString("error");
                        if (error == null) {
                            error = "Download failed";
                        }
                        final String finalError = error;
                        handler.post(() -> {
                            isDownloading = false;
                            listener.onError(finalError);
                        });
                    } else if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                        handler.post(() -> {
                            isDownloading = false;
                            listener.onError("Download cancelled");
                        });
                    }
                }
            });

            // Enqueue work (replace any existing work)
            workManager.enqueueUniqueWork(
                    DOWNLOAD_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    downloadWorkRequest);

            // Update state
            isDownloading = true;

            // Initial progress update
            if (progressListener != null) {
                handler.post(() -> progressListener.onProgressUpdate(0, 0, release.getTotalSizeMB(), 0, 0, 0));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download", e);
            listener.onError("Failed to start download: " + e.getMessage());
        }
    }

    /**
     * Cancel download
     */
    public void cancelDownload() {
        if (isDownloading && currentWorkId != null) {
            // Cancel the work
            workManager.cancelWorkById(currentWorkId);

            // Update state
            isDownloading = false;

            Log.d(TAG, "Download cancelled: " + currentWorkId);
        }
    }

    /**
     * Check if download is in progress
     * @return true if downloading, false otherwise
     */
    public boolean isDownloading() {
        return isDownloading;
    }

    /**
     * Set download progress listener
     * @param listener Download progress listener
     */
    public void setProgressListener(DownloadProgressListener listener) {
        this.progressListener = listener;
    }

    // Removed simulateDownloadProgress method as we now use real downloads with WorkManager

    /**
     * Download listener interface
     */
    public interface DownloadListener {
        void onSuccess(File downloadedFile);
        void onError(String error);
    }

    /**
     * Download progress listener interface with enhanced information
     */
    public interface DownloadProgressListener {
        /**
         * Called when download progress is updated
         * @param progress Download progress (0-100)
         * @param downloadedMB Downloaded size in MB
         * @param totalSizeMB Total size in MB
         * @param speedMBps Download speed in MB/s
         * @param etaMinutes Estimated time remaining (minutes)
         * @param etaSeconds Estimated time remaining (seconds)
         */
        void onProgressUpdate(int progress, double downloadedMB, double totalSizeMB,
                             double speedMBps, int etaMinutes, int etaSeconds);
    }
}
