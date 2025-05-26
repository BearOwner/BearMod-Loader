package com.bearmod.loader.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bearmod.loader.BearLoaderApplication;
import com.bearmod.loader.R;
import com.bearmod.loader.auth.KeyAuthManager;
import com.bearmod.loader.databinding.ActivitySettingsBinding;
import com.bearmod.loader.ui.auth.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Settings activity
 * Handles app settings and configuration
 */
public class SettingsActivity extends AppCompatActivity {
    
    private ActivitySettingsBinding binding;
    private KeyAuthManager keyAuthManager;
    private SharedPreferences preferences;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize view binding
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        // Initialize KeyAuth manager
        keyAuthManager = KeyAuthManager.getInstance();
        
        // Get preferences
        preferences = BearLoaderApplication.getInstance().getPreferences();
        
        // Set up switches
        setupSwitches();
        
        // Set up click listeners
        setupClickListeners();
    }
    
    /**
     * Set up switches
     */
    private void setupSwitches() {
        // Set up stealth mode switch
        boolean stealthMode = preferences.getBoolean("stealth_mode", true);
        binding.switchStealth.setChecked(stealthMode);
        binding.switchStealth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("stealth_mode", isChecked).apply();
        });
        
        // Set up auto login switch
        boolean autoLogin = preferences.getBoolean("remember_me", true);
        binding.switchAutoLogin.setChecked(autoLogin);
        binding.switchAutoLogin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("remember_me", isChecked).apply();
        });
    }
    
    /**
     * Set up click listeners
     */
    private void setupClickListeners() {
        // Set up clear cache button
        binding.layoutClearCache.setOnClickListener(v -> {
            // Show confirmation dialog
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.clear_cache)
                    .setMessage(R.string.clear_cache_confirm)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        // Clear cache
                        clearCache();
                        
                        // Show message
                        Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        
        // Set up reset config button
        binding.layoutResetConfig.setOnClickListener(v -> {
            // Show confirmation dialog
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reset_config)
                    .setMessage(R.string.reset_config_confirm)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        // Reset configuration
                        resetConfig();
                        
                        // Show message
                        Toast.makeText(this, R.string.config_reset, Toast.LENGTH_SHORT).show();
                        
                        // Update switches
                        setupSwitches();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        
        // Set up logout button
        binding.btnLogout.setOnClickListener(v -> {
            // Show confirmation dialog
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.logout)
                    .setMessage(R.string.logout_confirm)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        // Logout
                        logout();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }
    
    /**
     * Clear cache
     */
    private void clearCache() {
        // TODO: Implement actual cache clearing
        // This is a mock implementation for demonstration
    }
    
    /**
     * Reset configuration
     */
    private void resetConfig() {
        // Reset configuration preferences
        preferences.edit()
                .putBoolean("stealth_mode", true)
                .putBoolean("remember_me", true)
                .apply();
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
        finishAffinity();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        // Handle back button
        onBackPressed();
        return true;
    }
}
