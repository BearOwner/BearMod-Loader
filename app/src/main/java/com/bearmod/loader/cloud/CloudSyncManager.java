package com.bearmod.loader.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.bearmod.loader.model.Patch;
import com.bearmod.loader.repository.PatchRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Cloud sync manager
 * Handles synchronization with cloud services for real-time updates
 */
public class CloudSyncManager {

    private static CloudSyncManager instance;
    private Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Sync intervals in milliseconds
    private static final long SYNC_INTERVAL_NORMAL = 5 * 60 * 1000; // 5 minutes
    private static final long SYNC_INTERVAL_FAST = 60 * 1000; // 1 minute
    private static final long SYNC_INTERVAL_REALTIME = 15 * 1000; // 15 seconds

    // Current sync interval
    private long currentSyncInterval = SYNC_INTERVAL_NORMAL;

    // Sync handler and runnable
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final Runnable syncRunnable = this::syncPatches;

    // WebSocket for real-time updates
    private WebSocketManager webSocketManager;

    // Listeners
    private final List<CloudSyncListener> listeners = new ArrayList<>();

    /**
     * Private constructor to enforce singleton pattern
     */
    private CloudSyncManager() {
        // Private constructor
    }

    /**
     * Get CloudSyncManager instance
     * @return CloudSyncManager instance
     */
    public static synchronized CloudSyncManager getInstance() {
        if (instance == null) {
            instance = new CloudSyncManager();
        }
        return instance;
    }

    /**
     * Initialize cloud sync
     * @param context Application context
     */
    public void initialize(Context context) {
        this.context = context.getApplicationContext();

        // Initialize WebSocket manager
        webSocketManager = new WebSocketManager(context);
        webSocketManager.addListener(new WebSocketManager.WebSocketEventListener() {
            @Override
            public void onConnected() {
                addLogEntry("WebSocket connected");
            }

            @Override
            public void onDisconnected(String reason) {
                addLogEntry("WebSocket disconnected: " + reason);
            }

            @Override
            public void onError(String error) {
                addLogEntry("WebSocket error: " + error);
            }

            @Override
            public void onPatchUpdate(List<Patch> patches) {
                addLogEntry("Received real-time patch update: " + patches.size() + " patches");

                // Notify listeners on main thread
                handler.post(() -> {
                    for (CloudSyncListener listener : listeners) {
                        listener.onSyncComplete(patches);
                    }
                });
            }
        });

        // Connect to WebSocket for real-time updates
        webSocketManager.connect();

        // Start periodic sync
        startPeriodicSync();
    }

    /**
     * Start periodic sync
     */
    public void startPeriodicSync() {
        // Remove any existing callbacks
        syncHandler.removeCallbacks(syncRunnable);

        // Schedule periodic sync
        syncHandler.postDelayed(syncRunnable, currentSyncInterval);
    }

    /**
     * Stop periodic sync
     */
    public void stopPeriodicSync() {
        syncHandler.removeCallbacks(syncRunnable);

        // Disconnect WebSocket
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
    }

    /**
     * Set sync interval mode
     * @param mode Sync interval mode
     */
    public void setSyncIntervalMode(SyncIntervalMode mode) {
        switch (mode) {
            case NORMAL:
                currentSyncInterval = SYNC_INTERVAL_NORMAL;
                break;
            case FAST:
                currentSyncInterval = SYNC_INTERVAL_FAST;
                break;
            case REALTIME:
                currentSyncInterval = SYNC_INTERVAL_REALTIME;
                break;
        }

        // Restart sync with new interval
        startPeriodicSync();

        // Log sync interval change
        addLogEntry("Sync interval changed to: " + mode.name());
    }

    /**
     * Sync interval modes
     */
    public enum SyncIntervalMode {
        NORMAL,
        FAST,
        REALTIME
    }

    // Log buffer for lazy loading
    private final List<String> logBuffer = new ArrayList<>();
    private static final int MAX_LOG_BUFFER_SIZE = 100;

    /**
     * Add log entry to buffer
     * @param logEntry Log entry to add
     */
    private void addLogEntry(String logEntry) {
        synchronized (logBuffer) {
            // Add timestamp to log entry
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String formattedLog = "[" + timestamp + "] " + logEntry;

            // Add to buffer
            logBuffer.add(formattedLog);

            // Trim buffer if it exceeds max size
            if (logBuffer.size() > MAX_LOG_BUFFER_SIZE) {
                logBuffer.remove(0);
            }
        }
    }

    /**
     * Get log entries
     * @return Copy of log buffer
     */
    public List<String> getLogEntries() {
        synchronized (logBuffer) {
            return new ArrayList<>(logBuffer);
        }
    }

    /**
     * Sync patches with cloud
     */
    public void syncPatches() {
        // Log sync start
        addLogEntry("Starting cloud sync...");

        // Get repository instance
        PatchRepository repository = PatchRepository.getInstance(context);

        // Sync patches from repository
        repository.syncPatches(new PatchRepository.PatchCallback() {
            @Override
            public void onSuccess(List<Patch> patches) {
                // Log completion
                addLogEntry("Cloud sync completed successfully. Found " + patches.size() + " patches.");

                // Notify listeners on main thread
                handler.post(() -> {
                    for (CloudSyncListener listener : listeners) {
                        listener.onSyncComplete(patches);
                    }
                });

                // Schedule next sync with current interval
                syncHandler.postDelayed(syncRunnable, currentSyncInterval);
            }

            @Override
            public void onError(String error) {
                // Log error
                addLogEntry("Cloud sync failed: " + error);

                // Notify listeners of error on main thread
                handler.post(() -> {
                    for (CloudSyncListener listener : listeners) {
                        listener.onSyncError(error);
                    }
                });

                // Schedule next sync with current interval (even if this one failed)
                syncHandler.postDelayed(syncRunnable, currentSyncInterval);
            }
        });
    }

    /**
     * Create mock patches for demonstration
     * @return List of mock patches
     */
    private List<Patch> createMockPatches() {
        List<Patch> patches = new ArrayList<>();

        // Add mock patches
        patches.add(new Patch(
                "1",
                "Memory Patch v1.2",
                "This patch modifies memory values to enhance gameplay",
                "1.0.5",
                "2023-06-15",
                Patch.PatchStatus.UP_TO_DATE
        ));

        patches.add(new Patch(
                "2",
                "Speed Hack v2.0",
                "Increases movement speed and reduces cooldowns",
                "1.0.5",
                "2023-06-10",
                Patch.PatchStatus.UPDATE_AVAILABLE
        ));

        patches.add(new Patch(
                "3",
                "Resource Modifier v1.5",
                "Modifies resource generation and collection rates",
                "1.0.4",
                "2023-05-28",
                Patch.PatchStatus.NOT_INSTALLED
        ));

        // Add a new patch that wasn't in the original list
        patches.add(new Patch(
                "4",
                "UI Enhancement v1.0",
                "Improves user interface and adds custom elements",
                "1.0.5",
                "2023-06-20",
                Patch.PatchStatus.NOT_INSTALLED
        ));

        return patches;
    }

    /**
     * Add cloud sync listener
     * @param listener Cloud sync listener
     */
    public void addListener(CloudSyncListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove cloud sync listener
     * @param listener Cloud sync listener
     */
    public void removeListener(CloudSyncListener listener) {
        listeners.remove(listener);
    }

    /**
     * Cloud sync listener interface
     */
    public interface CloudSyncListener {
        void onSyncComplete(List<Patch> patches);
        void onSyncError(String error);
    }
}
