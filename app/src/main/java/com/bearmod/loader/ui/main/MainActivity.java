package com.bearmod.loader.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bearmod.loader.BearLoaderApplication;
import com.bearmod.loader.R;
import com.bearmod.loader.auth.AuthResult;
import com.bearmod.loader.auth.KeyAuthManager;

import com.bearmod.loader.cloud.CloudSyncManager;
import com.bearmod.loader.databinding.ActivityMainBinding;
import com.bearmod.loader.model.Patch;
import com.bearmod.loader.patch.PatchManager;
import com.bearmod.loader.repository.PatchRepository;
import com.bearmod.loader.ui.auth.LoginActivity;
import com.bearmod.loader.ui.download.DownloadActivity;
import com.bearmod.loader.ui.patch.PatchExecutionActivity;
import com.bearmod.loader.ui.settings.SettingsActivity;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Main activity (Dashboard)
 * Displays available patches and target app selection
 */
public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        CloudSyncManager.CloudSyncListener {

    private ActivityMainBinding binding;
    private PatchAdapter patchAdapter;
    private KeyAuthManager keyAuthManager;
    private CloudSyncManager cloudSyncManager;
    private PatchManager patchManager;

    // Mock data for demonstration
    private final List<String> targetApps = Arrays.asList(
            "Game App v1.0.5",
            "Game App v1.0.4",
            "Game App v1.0.3"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize managers
        keyAuthManager = KeyAuthManager.getInstance();
        cloudSyncManager = CloudSyncManager.getInstance();
        cloudSyncManager.initialize(this);
        cloudSyncManager.addListener(this);

        patchManager = PatchManager.getInstance();
        patchManager.initialize(this);

        // Set up toolbar
        setSupportActionBar(binding.toolbar);

        // Set up navigation drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Set up navigation view
        binding.navView.setNavigationItemSelectedListener(this);

        // Set up target app spinner
        ArrayAdapter<String> targetAppAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, targetApps);
        binding.spinnerTargetApp.setAdapter(targetAppAdapter);

        // Set up scan offsets button
        binding.btnScanOffsets.setOnClickListener(v -> scanOffsets());

        // Set up download patches button
        binding.btnDownloadPatches.setOnClickListener(v -> {
            Intent intent = new Intent(this, DownloadActivity.class);
            startActivity(intent);
        });

        // Set up RecyclerView with lambda functions for click handling
        binding.rvPatches.setLayoutManager(new LinearLayoutManager(this));
        patchAdapter = new PatchAdapter(
                this,
                this::onPatchClick,  // Lambda for patch click
                this::onApplyPatchClick  // Lambda for apply patch click
        );
        binding.rvPatches.setAdapter(patchAdapter);

        // Set up SwipeRefreshLayout
        binding.swipeRefresh.setOnRefreshListener(() -> {
            // Sync patches from cloud
            cloudSyncManager.syncPatches();
        });
        binding.swipeRefresh.setColorSchemeResources(
                R.color.primary,
                R.color.accent,
                R.color.primary_dark
        );

        // Load patches
        loadPatches();

        // Update license info in navigation header
        updateLicenseInfo();
    }

    /**
     * Update license info in navigation header
     */
    private void updateLicenseInfo() {
        keyAuthManager.validateLicense(new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess(AuthResult result) {
                // Get license info
                String expiryDate = keyAuthManager.formatExpiryDate(result.getExpiryDate());
                int remainingDays = keyAuthManager.getRemainingDays(result.getExpiryDate());

                // Update navigation header
                View headerView = binding.navView.getHeaderView(0);
                if (headerView != null) {
                    headerView.findViewById(R.id.tv_license_info).post(() -> {
                        if (headerView.findViewById(R.id.tv_license_info) != null) {
                            String licenseText;
                            if (remainingDays == -1) {
                                licenseText = "License: Active (No expiry)";
                            } else {
                                licenseText = getString(R.string.days_remaining, remainingDays);
                            }
                            ((android.widget.TextView) headerView.findViewById(R.id.tv_license_info))
                                    .setText(licenseText);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                // Handle error
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Load patches with lazy loading
     */
    private void loadPatches() {
        // Show shimmer effect
        binding.shimmerLayout.setVisibility(View.VISIBLE);
        binding.rvPatches.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);

        // Get repository instance
        PatchRepository repository = PatchRepository.getInstance(this);

        // Observe patches from repository for lazy loading
        repository.getAllPatches().observe(this, patches -> {
            // Only update UI if we have patches and aren't currently syncing
            if (patches != null && !patches.isEmpty() && binding.shimmerLayout.getVisibility() != View.VISIBLE) {
                updatePatchesUI(patches);
            }
        });

        // Sync patches from cloud
        cloudSyncManager.syncPatches();
    }

    /**
     * Update patches UI
     * @param patches Patches to display
     */
    private void updatePatchesUI(List<Patch> patches) {
        // Hide shimmer effect
        binding.shimmerLayout.setVisibility(View.GONE);

        // Check if patches list is empty
        if (patches.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.rvPatches.setVisibility(View.GONE);
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.rvPatches.setVisibility(View.VISIBLE);

            // Update adapter
            patchAdapter.updatePatches(patches);
        }
    }

    /**
     * Handle cloud sync completion
     * @param patches Synced patches
     */
    @Override
    public void onSyncComplete(List<Patch> patches) {
        // Stop refresh animation
        binding.swipeRefresh.setRefreshing(false);

        // Update UI with patches
        updatePatchesUI(patches);

        // Show snackbar with update info
        Snackbar.make(binding.getRoot(), R.string.patches_updated, Snackbar.LENGTH_SHORT).show();

        // Save patches to repository
        PatchRepository repository = PatchRepository.getInstance(this);
        for (Patch patch : patches) {
            repository.savePatch(patch, new PatchRepository.PatchCallback() {
                @Override
                public void onSuccess(List<Patch> patches) {
                    // Patch saved successfully
                }

                @Override
                public void onError(String error) {
                    // Log error but don't show to user
                    Log.e("MainActivity", "Error saving patch: " + error);
                }
            });
        }
    }

    /**
     * Handle cloud sync error
     * @param error Error message
     */
    @Override
    public void onSyncError(String error) {
        // Stop refresh animation
        binding.swipeRefresh.setRefreshing(false);

        // Show error message
        Toast.makeText(this, getString(R.string.sync_error, error), Toast.LENGTH_LONG).show();

        // Get repository instance
        PatchRepository repository = PatchRepository.getInstance(this);

        // Try to load patches from local database
        repository.getLatestPatches("1.0.5", new PatchRepository.PatchCallback() {
            @Override
            public void onSuccess(List<Patch> patches) {
                if (patches != null && !patches.isEmpty()) {
                    // Update UI with patches from database
                    updatePatchesUI(patches);
                } else {
                    // Load mock patches as fallback if database is empty
                    updatePatchesUI(createMockPatches());
                }
            }

            @Override
            public void onError(String error) {
                // Load mock patches as fallback
                updatePatchesUI(createMockPatches());
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

        return patches;
    }

    /**
     * Scan offsets
     */
    private void scanOffsets() {
        // Get selected target app
        String targetApp = (String) binding.spinnerTargetApp.getSelectedItem();

        // Show toast
        Toast.makeText(this,
                "Scanning offsets for " + targetApp,
                Toast.LENGTH_SHORT).show();

        // Show scanning dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.scanning_offsets)
                .setMessage(getString(R.string.scanning_offsets_for, targetApp))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            // Already on dashboard, do nothing
        } else if (id == R.id.nav_download) {
            // Navigate to download activity
            Intent intent = new Intent(this, DownloadActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_settings) {
            // Navigate to settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            // Logout
            logout();
        }

        // Close drawer
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Logout user
     */
    private void logout() {
        // Logout with KeyAuth
        keyAuthManager.logout();

        // Navigate to login activity
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Close drawer if open
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Handle patch click event
     * @param patch Clicked patch
     */
    private void onPatchClick(Patch patch) {
        // Show patch details
        Toast.makeText(this,
                "Patch details: " + patch.getName(),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle apply patch click event
     * @param patch Patch to apply
     */
    private void onApplyPatchClick(Patch patch) {
        // Navigate to patch execution activity
        Intent intent = new Intent(this, PatchExecutionActivity.class);
        intent.putExtra("patch_id", patch.getId());
        intent.putExtra("patch_name", patch.getName());
        startActivity(intent);
    }
}
