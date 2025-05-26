package com.bearmod.loader.ui.splash;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.C;

import androidx.appcompat.app.AppCompatActivity;

import com.bearmod.loader.R;
import com.bearmod.loader.databinding.ActivitySplashBinding;
import com.bearmod.loader.ui.auth.LoginActivity;

import com.bearmod.loader.util.BuildValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Splash activity
 * Displays splash screen and validates build
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private boolean videoCompleted = false;
    private boolean validationCompleted = false;
    private boolean shouldProceed = false;
    private long splashStartTime;

    private static final long VIDEO_DURATION = 6000; // 6 seconds fallback
    private static final long FALLBACK_DURATION = 3000; // 3 seconds fallback
    private static final long MINIMUM_SPLASH_TIME = 4000; // Minimum 4 seconds to see video



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Record splash start time
        splashStartTime = System.currentTimeMillis();

        // Enable full-screen immersive mode
        enableFullScreen();

        // Initialize view binding
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d("SplashActivity", "onCreate called");

        // Initialize ExoPlayer and PlayerView
        playerView = binding.playerView;
        initializeExoPlayer();

        // Setup and start video
        setupVideo();

        // Debug video view after layout
        binding.getRoot().post(() -> {
            debugVideoView();
        });

        // Validate build after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(this::validateBuild, 1000);
    }

    /**
     * Initialize ExoPlayer
     */
    private void initializeExoPlayer() {
        Log.d("SplashActivity", "Initializing ExoPlayer");

        // Create ExoPlayer instance
        exoPlayer = new ExoPlayer.Builder(this)
                .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                .build();

        // Set player to PlayerView
        playerView.setPlayer(exoPlayer);

        // Configure PlayerView
        playerView.setUseController(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);

        // Add player event listener
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d("SplashActivity", "ExoPlayer state changed: " + playbackState);

                switch (playbackState) {
                    case Player.STATE_READY:
                        Log.d("SplashActivity", "ExoPlayer ready, starting playback");
                        hideLoadingOverlay();
                        showOverlayContent();
                        break;
                    case Player.STATE_ENDED:
                        Log.d("SplashActivity", "ExoPlayer playback ended");
                        videoCompleted = true;
                        onVideoCompleted();
                        break;
                    case Player.STATE_BUFFERING:
                        Log.d("SplashActivity", "ExoPlayer buffering");
                        break;
                    case Player.STATE_IDLE:
                        Log.d("SplashActivity", "ExoPlayer idle");
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e("SplashActivity", "ExoPlayer error: " + error.getMessage(), error);
                showFallbackAnimation();
            }
        });

        Log.d("SplashActivity", "ExoPlayer initialized successfully");
    }

    /**
     * Debug video view configuration
     */
    private void debugVideoView() {
        Log.d("SplashActivity", "=== EXOPLAYER DEBUG ===");
        Log.d("SplashActivity", "PlayerView visibility: " + playerView.getVisibility());
        Log.d("SplashActivity", "PlayerView dimensions: " + playerView.getWidth() + "x" + playerView.getHeight());
        Log.d("SplashActivity", "PlayerView position: (" + playerView.getX() + ", " + playerView.getY() + ")");

        if (exoPlayer != null) {
            Log.d("SplashActivity", "ExoPlayer isPlaying: " + exoPlayer.isPlaying());
            Log.d("SplashActivity", "ExoPlayer currentPosition: " + exoPlayer.getCurrentPosition());
            Log.d("SplashActivity", "ExoPlayer duration: " + exoPlayer.getDuration());
            Log.d("SplashActivity", "ExoPlayer playbackState: " + exoPlayer.getPlaybackState());
        }
        Log.d("SplashActivity", "========================");
    }

    /**
     * Enable full-screen immersive mode
     */
    private void enableFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }



    /**
     * Setup and start video playback with ExoPlayer
     */
    private void setupVideo() {
        try {
            Log.d("SplashActivity", "Setting up ExoPlayer video playback");

            // Create video URI
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.bear_intro_mobile);
            Log.d("SplashActivity", "Video URI: " + videoUri.toString());

            // Create MediaItem and set to player
            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            exoPlayer.setMediaItem(mediaItem);

            // Prepare and start playback
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            Log.d("SplashActivity", "ExoPlayer prepared and set to play");

            // Set timeout for video loading (fallback after 3 seconds)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!videoCompleted && exoPlayer.getCurrentPosition() == 0) {
                    Log.w("SplashActivity", "Video loading timeout, showing fallback");
                    showFallbackAnimation();
                }
            }, 3000);

        } catch (Exception e) {
            Log.e("SplashActivity", "Error setting up ExoPlayer: " + e.getMessage(), e);
            showFallbackAnimation();
        }
    }

    /**
     * Hide loading overlay
     */
    private void hideLoadingOverlay() {
        binding.videoLoadingOverlay.animate()
                .alpha(0.0f)
                .setDuration(500)
                .withEndAction(() -> {
                    binding.videoLoadingOverlay.setVisibility(View.GONE);
                    Log.d("SplashActivity", "Loading overlay hidden");
                })
                .start();
    }

    /**
     * Show overlay content with animations
     */
    private void showOverlayContent() {
        // Animate app name
        binding.tvAppName.animate()
                .alpha(1.0f)
                .setDuration(1000)
                .setStartDelay(500)
                .start();

        // Animate app version
        binding.tvAppVersion.animate()
                .alpha(1.0f)
                .setDuration(600)
                .setStartDelay(700)
                .start();

        // Animate progress bar
        binding.progressBar.animate()
                .alpha(1.0f)
                .setDuration(600)
                .setStartDelay(2500)
                .start();

        // Animate loading text
        binding.tvLoading.animate()
                .alpha(1.0f)
                .setDuration(600)
                .setStartDelay(2800)
                .start();
    }

    /**
     * Show fallback animation if video fails
     */
    private void showFallbackAnimation() {
        Log.d("SplashActivity", "Showing fallback animation");

        // Hide video view (use playerView for ExoPlayer)
        playerView.setVisibility(View.GONE);

        // Show fallback logo
        binding.ivLogo.animate()
                .alpha(1.0f)
                .setDuration(1000)
                .start();

        // Show overlay content
        showOverlayContent();

        // Set fallback completion timer
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            videoCompleted = true;
            onVideoCompleted();
        }, FALLBACK_DURATION);
    }

    /**
     * Handle video completion
     */
    private void onVideoCompleted() {
        Log.d("SplashActivity", "Video completed, validation completed: " + validationCompleted);

        if (validationCompleted && shouldProceed) {
            // Both video and validation are complete, proceed to login
            proceedToLogin();
        }
    }

    /**
     * Validate build
     */
    private void validateBuild() {
        // Add debug logging
        Log.d("SplashActivity", "Starting build validation");

        // Validate build
        BuildValidator.ValidationResult result = BuildValidator.validateBuild(this);

        if (result.isValid()) {
            // Check for warnings
            if (result.hasWarnings()) {
                // Show warnings
                Log.d("SplashActivity", "Build validation has warnings: " + result.getWarnings().size());
                showWarnings(result.getWarnings());
            } else {
                // No warnings, mark validation as complete
                Log.d("SplashActivity", "Build validation successful");
                validationCompleted = true;
                shouldProceed = true;

                // Check if video is also complete
                if (videoCompleted) {
                    proceedToLogin();
                }
            }
        } else {
            // Show errors
            Log.e("SplashActivity", "Build validation failed with errors: " + result.getErrors().size());
            showErrors(result.getErrors());
        }
    }

    /**
     * Show errors
     * @param errors List of errors
     */
    private void showErrors(List<String> errors) {
        // Build error message
        StringBuilder errorMessage = new StringBuilder();
        for (String error : errors) {
            errorMessage.append("• ").append(error).append("\n");
        }

        // Show error dialog
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(errorMessage.toString())
                .setPositiveButton(R.string.exit, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Show warnings
     * @param warnings List of warnings
     */
    private void showWarnings(List<String> warnings) {
        // Build warning message
        StringBuilder warningMessage = new StringBuilder();
        for (String warning : warnings) {
            warningMessage.append("• ").append(warning).append("\n");
        }

        // Show warning dialog
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(warningMessage.toString())
                .setPositiveButton(R.string.continue_anyway, (dialog, which) -> {
                    dialog.dismiss();
                    validationCompleted = true;
                    shouldProceed = true;

                    // Check if video is also complete
                    if (videoCompleted) {
                        proceedToLogin();
                    }
                })
                .setNegativeButton(R.string.exit, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Proceed to login or main activity
     */
    private void proceedToLogin() {
        Log.d("SplashActivity", "Both video and validation completed, checking minimum time");

        // Calculate elapsed time
        long elapsedTime = System.currentTimeMillis() - splashStartTime;
        long remainingTime = MINIMUM_SPLASH_TIME - elapsedTime;

        if (remainingTime > 0) {
            Log.d("SplashActivity", "Waiting " + remainingTime + "ms more to meet minimum splash time");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startLoginActivity();
            }, remainingTime);
        } else {
            Log.d("SplashActivity", "Minimum time met, proceeding to login");
            // Add a small delay for smooth transition
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startLoginActivity();
            }, 500);
        }
    }

    /**
     * Start login activity
     */
    private void startLoginActivity() {
        Log.d("SplashActivity", "Starting LoginActivity");
        try {
            // Start login activity
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);

            // Apply fade out transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

            // Finish splash activity
            finish();
        } catch (Exception e) {
            Log.e("SplashActivity", "Error starting LoginActivity: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting login: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up ExoPlayer resources
        if (exoPlayer != null) {
            try {
                // Explicitly detach the player from the view
                playerView.setPlayer(null);
                exoPlayer.stop();
                exoPlayer.release();
                exoPlayer = null;
                Log.d("SplashActivity", "ExoPlayer released");
            } catch (Exception e) {
                Log.e("SplashActivity", "Error releasing ExoPlayer: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Pause ExoPlayer if playing
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Resume ExoPlayer if not completed
        if (exoPlayer != null && !videoCompleted) {
            exoPlayer.play();
        }
    }
}
