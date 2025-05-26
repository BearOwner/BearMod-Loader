package com.bearmod.loader.ui.patch;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bearmod.loader.R;
import com.bearmod.loader.databinding.ActivityPatchExecutionBinding;
import com.bearmod.loader.model.Patch;
import com.bearmod.loader.patch.PatchManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Patch execution activity
 * Handles patch execution and displays logs
 */
public class PatchExecutionActivity extends AppCompatActivity implements PatchManager.PatchLogListener {

    private ActivityPatchExecutionBinding binding;
    private PatchManager patchManager;
    private final StringBuilder logs = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String patchId;
    private String patchName;
    private Patch patch; // This would normally be loaded from a repository

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view binding
        binding = ActivityPatchExecutionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize patch manager
        patchManager = PatchManager.getInstance();
        patchManager.initialize(this);
        patchManager.setLogListener(this);

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Get patch info from intent
        patchId = getIntent().getStringExtra("patch_id");
        patchName = getIntent().getStringExtra("patch_name");

        // Create mock patch (in a real app, this would be loaded from a repository)
        patch = new Patch(
                patchId,
                patchName,
                "This is a mock patch for demonstration",
                "1.0.5",
                "2023-06-15",
                Patch.PatchStatus.UP_TO_DATE
        );

        // Set patch name
        binding.tvPatchName.setText(patchName);

        // Set up start/stop patching button
        binding.btnStartPatching.setOnClickListener(v -> {
            if (patchManager.isPatching()) {
                stopPatching();
            } else {
                startPatching();
            }
        });

        // Initialize logs
        appendLog("INFO", "Ready to start patching");
    }

    /**
     * Start patching
     */
    private void startPatching() {
        // Check if patching is already in progress
        if (patchManager.isPatching()) {
            return;
        }

        // Update UI
        binding.btnStartPatching.setText(R.string.stop_patching);
        showLoading(true);

        // Clear logs
        logs.setLength(0);
        binding.tvLogs.setText("");

        // Get execution mode
        boolean isRootMode = binding.radioRoot.isChecked();

        // Log start
        appendLog("INFO", "Starting patch execution");
        appendLog("INFO", "Mode: " + (isRootMode ? "Root" : "Non-Root"));

        // Start patching
        patchManager.startPatching(patch, "com.example.targetapp", isRootMode, new PatchManager.PatchOperationListener() {
            @Override
            public void onSuccess(String message) {
                // Update UI
                binding.btnStartPatching.setText(R.string.start_patching);
                showLoading(false);

                // Show success message
                Toast.makeText(PatchExecutionActivity.this, R.string.patching_complete, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                // Log error
                appendLog("ERROR", "Patching failed: " + error);

                // Update UI
                binding.btnStartPatching.setText(R.string.start_patching);
                showLoading(false);

                // Show error message
                Toast.makeText(PatchExecutionActivity.this,
                        getString(R.string.patching_failed, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Stop patching
     */
    private void stopPatching() {
        // Check if patching is in progress
        if (!patchManager.isPatching()) {
            return;
        }

        // Log stop
        appendLog("INFO", "Stopping patching...");

        // Stop patching
        patchManager.stopPatching(new PatchManager.PatchOperationListener() {
            @Override
            public void onSuccess(String message) {
                // Update UI
                binding.btnStartPatching.setText(R.string.start_patching);
                showLoading(false);

                // Log success
                appendLog("INFO", "Patching stopped successfully");
            }

            @Override
            public void onError(String error) {
                // Log error
                appendLog("ERROR", "Error stopping patching: " + error);

                // Update UI
                binding.btnStartPatching.setText(R.string.start_patching);
                showLoading(false);
            }
        });
    }

    /**
     * Handle log message from PatchManager
     * @param message Log message
     */
    @Override
    public void onLogMessage(String message) {
        // Append log message
        appendLog("INFO", message);
    }

    /**
     * Append log message
     * @param level Log level
     * @param message Log message
     */
    private void appendLog(String level, String message) {
        // Get current time
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String time = sdf.format(new Date());

        // Format log message
        String logMessage = String.format("[%s] [%s] %s\n", time, level, message);

        // Append to logs
        logs.append(logMessage);

        // Update UI on main thread
        handler.post(() -> {
            binding.tvLogs.setText(logs.toString());

            // Scroll to bottom
            binding.cardLogs.post(() -> {
                View scrollView = binding.cardLogs.getChildAt(0);
                if (scrollView != null) {
                    scrollView.scrollTo(0, scrollView.getHeight());
                }
            });
        });
    }

    /**
     * Show/hide loading
     * @param show Show loading
     */
    private void showLoading(boolean show) {
        binding.progressPatching.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.viewOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handle back button
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        // Check if patching is in progress
        if (patchManager.isPatching()) {
            // Show confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.patching_in_progress)
                    .setPositiveButton(R.string.stop_patching, (dialog, which) -> {
                        // Stop patching
                        stopPatching();

                        // Finish activity
                        super.onBackPressed();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            // Finish activity
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop patching if still in progress
        if (patchManager.isPatching()) {
            patchManager.stopPatching(new PatchManager.PatchOperationListener() {
                @Override
                public void onSuccess(String message) {
                    // Do nothing
                }

                @Override
                public void onError(String error) {
                    // Do nothing
                }
            });
        }
    }
}
