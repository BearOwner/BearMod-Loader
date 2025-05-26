package com.bearmod.loader.ui.download;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bearmod.loader.R;
import com.bearmod.loader.api.GitHubApiService;
import com.bearmod.loader.databinding.ActivityDownloadBinding;
import com.bearmod.loader.download.DownloadManager;
import com.bearmod.loader.model.PatchRelease;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Download activity
 * Handles downloading patches from cloud or GitHub
 */
public class DownloadActivity extends AppCompatActivity {

    private ActivityDownloadBinding binding;
    private GitHubApiService gitHubApiService;
    private DownloadManager downloadManager;

    private PatchRelease selectedRelease;
    private ReleaseAdapter releaseAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view binding
        binding = ActivityDownloadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize services
        gitHubApiService = GitHubApiService.getInstance();
        downloadManager = DownloadManager.getInstance();
        downloadManager.initialize(this);

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Set up RecyclerView
        RecyclerView rvReleases = findViewById(R.id.rv_releases);
        rvReleases.setLayoutManager(new LinearLayoutManager(this));
        releaseAdapter = new ReleaseAdapter(this, release -> {
            selectedRelease = release;
            setupDownloadInfo(release);
            binding.cardDownloadInfo.setVisibility(View.VISIBLE);
        });
        rvReleases.setAdapter(releaseAdapter);

        // Set up download button
        binding.btnDownload.setOnClickListener(v -> {
            if (selectedRelease != null) {
                startDownload(selectedRelease);
            } else {
                Toast.makeText(this, R.string.select_release_first, Toast.LENGTH_SHORT).show();
            }
        });

        // Set up cancel button
        binding.btnCancelDownload.setOnClickListener(v -> cancelDownload());

        // Load releases
        loadReleases();
    }

    /**
     * Load releases from GitHub
     */
    private void loadReleases() {
        // Show loading
        binding.progressLoading.setVisibility(View.VISIBLE);
        findViewById(R.id.rv_releases).setVisibility(View.GONE);
        binding.tvNoReleases.setVisibility(View.GONE);

        // Fetch releases
        gitHubApiService.fetchReleases(new GitHubApiService.ReleaseCallback() {
            @Override
            public void onSuccess(List<PatchRelease> releases) {
                // Hide loading
                binding.progressLoading.setVisibility(View.GONE);

                // Check if releases list is empty
                if (releases.isEmpty()) {
                    binding.tvNoReleases.setVisibility(View.VISIBLE);
                    findViewById(R.id.rv_releases).setVisibility(View.GONE);
                } else {
                    binding.tvNoReleases.setVisibility(View.GONE);
                    findViewById(R.id.rv_releases).setVisibility(View.VISIBLE);

                    // Update adapter
                    releaseAdapter.updateReleases(releases);
                }
            }

            @Override
            public void onError(String error) {
                // Hide loading
                binding.progressLoading.setVisibility(View.GONE);

                // Show error
                binding.tvNoReleases.setVisibility(View.VISIBLE);
                binding.tvNoReleases.setText(error);
                findViewById(R.id.rv_releases).setVisibility(View.GONE);

                // Show error message
                Toast.makeText(DownloadActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Set up download info
     * @param release Patch release
     */
    private void setupDownloadInfo(PatchRelease release) {
        // Format sizes
        DecimalFormat df = new DecimalFormat("#.#");
        String apkSize = df.format(release.getApkSizeMB());
        String obbSize = df.format(release.getObbSizeMB());
        String totalSize = df.format(release.getTotalSizeMB());

        // Set text
        binding.tvApkSize.setText(getString(R.string.apk_size, apkSize + " MB"));
        binding.tvObbSize.setText(getString(R.string.obb_size, obbSize + " MB"));
        binding.tvTotalSize.setText(getString(R.string.total_size, totalSize + " MB"));
    }

    /**
     * Start download
     * @param release Patch release to download
     */
    private void startDownload(PatchRelease release) {
        // Check if already downloading
        if (downloadManager.isDownloading()) {
            return;
        }

        // Update UI
        binding.cardDownloadProgress.setVisibility(View.VISIBLE);
        binding.btnDownload.setEnabled(false);
        binding.animationDownloadComplete.setVisibility(View.GONE);

        // Set progress listener with enhanced information
        downloadManager.setProgressListener((progress, downloadedMB, totalSizeMB, speedMBps, etaMinutes, etaSeconds) -> {
            // Format sizes and speed with one decimal place
            DecimalFormat df = new DecimalFormat("#.#");
            String downloadedSize = df.format(downloadedMB);
            String totalSize = df.format(totalSizeMB);
            String speed = df.format(speedMBps);

            // Format ETA
            String eta = String.format("%d:%02d", etaMinutes, etaSeconds);

            // Update progress bar
            binding.progressDownload.setProgress(progress);

            // Update download status with speed and ETA
            String statusText = getString(R.string.downloading_detailed,
                    downloadedSize + " MB",
                    totalSize + " MB",
                    speed + " MB/s",
                    eta);
            binding.tvDownloadStatus.setText(statusText);

            // Update percentage
            binding.tvDownloadPercentage.setText(progress + "%");
        });

        // Start download
        downloadManager.downloadPatch(release, new DownloadManager.DownloadListener() {
            @Override
            public void onSuccess(File downloadedFile) {
                // Update UI
                binding.btnDownload.setEnabled(true);
                binding.animationDownloadComplete.setVisibility(View.VISIBLE);

                // Show success message
                Toast.makeText(DownloadActivity.this,
                        getString(R.string.download_complete_path, downloadedFile.getAbsolutePath()),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                // Update UI
                binding.btnDownload.setEnabled(true);
                binding.cardDownloadProgress.setVisibility(View.GONE);

                // Show error message
                Toast.makeText(DownloadActivity.this,
                        getString(R.string.download_failed, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Cancel download
     */
    private void cancelDownload() {
        // Check if downloading
        if (!downloadManager.isDownloading()) {
            return;
        }

        // Cancel download
        downloadManager.cancelDownload();

        // Update UI
        binding.cardDownloadProgress.setVisibility(View.GONE);
        binding.btnDownload.setEnabled(true);

        // Show message
        Toast.makeText(this, R.string.download_cancelled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handle back button
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        // Check if downloading
        if (downloadManager.isDownloading()) {
            // Show confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.download_in_progress)
                    .setPositiveButton(R.string.cancel_download, (dialog, which) -> {
                        // Cancel download
                        cancelDownload();

                        // Finish activity
                        super.onBackPressed();
                    })
                    .setNegativeButton(R.string.continue_download, null)
                    .show();
        } else {
            // Finish activity
            super.onBackPressed();
        }
    }
}
