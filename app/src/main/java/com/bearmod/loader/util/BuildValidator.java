package com.bearmod.loader.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Build validator
 * Validates build integrity and version compatibility
 */
public class BuildValidator {

    // Minimum supported Android version
    private static final int MIN_ANDROID_VERSION = Build.VERSION_CODES.N; // Android 7.0

    // Expected app signature (SHA-256 fingerprint)
    private static final String EXPECTED_SIGNATURE = "01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF";

    // Minimum required game versions
    private static final List<String> SUPPORTED_GAME_VERSIONS = new ArrayList<>();

    static {
        SUPPORTED_GAME_VERSIONS.add("1.0.5");
        SUPPORTED_GAME_VERSIONS.add("1.0.4");
        SUPPORTED_GAME_VERSIONS.add("1.0.3");
    }

    /**
     * Validate build
     * @param context Application context
     * @return Validation result
     */
    public static ValidationResult validateBuild(Context context) {
        ValidationResult result = new ValidationResult();

        // Check Android version
        if (Build.VERSION.SDK_INT < MIN_ANDROID_VERSION) {
            result.addError("Unsupported Android version. Minimum required: Android 7.0 (Nougat)");
        }

        // Check app signature in release builds
        if (!com.bearmod.loader.BuildConfig.DEBUG) {
            try {
                String signature = getAppSignature(context);
                if (!EXPECTED_SIGNATURE.equals(signature)) {
                    result.addError("Invalid app signature. This app may have been tampered with.");
                }
            } catch (Exception e) {
                result.addError("Failed to verify app signature: " + e.getMessage());
            }
        }

        // Check for root access
        if (isRooted()) {
            result.addWarning("Device appears to be rooted. This may cause compatibility issues.");
        }

        // Check for emulator
        if (isEmulator()) {
            result.addWarning("App is running in an emulator. Some features may not work correctly.");
        }

        return result;
    }

    /**
     * Validate game version compatibility
     * @param gameVersion Game version to validate
     * @return true if supported, false otherwise
     */
    public static boolean isGameVersionSupported(String gameVersion) {
        return SUPPORTED_GAME_VERSIONS.contains(gameVersion);
    }

    /**
     * Get supported game versions
     * @return List of supported game versions
     */
    public static List<String> getSupportedGameVersions() {
        return new ArrayList<>(SUPPORTED_GAME_VERSIONS);
    }

    /**
     * Get app signature
     * @param context Application context
     * @return App signature (SHA-256 fingerprint)
     * @throws Exception If signature retrieval fails
     */
    private static String getAppSignature(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        PackageInfo packageInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (packageInfo.signingInfo == null) {
                throw new Exception("No signing info found");
            }

            Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
            if (signatures.length == 0) {
                throw new Exception("No signatures found");
            }

            return getSignatureFingerprint(signatures[0]);
        } else {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                throw new Exception("No signatures found");
            }

            return getSignatureFingerprint(packageInfo.signatures[0]);
        }
    }

    /**
     * Get signature fingerprint
     * @param signature Signature to get fingerprint for
     * @return Signature fingerprint (SHA-256)
     * @throws NoSuchAlgorithmException If SHA-256 algorithm is not available
     * @throws CertificateException If certificate parsing fails
     */
    private static String getSignatureFingerprint(Signature signature) throws NoSuchAlgorithmException, CertificateException {
        byte[] signatureBytes = signature.toByteArray();

        // Get certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(signatureBytes));

        // Get fingerprint
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = md.digest(cert.getEncoded());

        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fingerprint.length; i++) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02X", fingerprint[i] & 0xFF));
        }

        return sb.toString();
    }

    /**
     * Check if device is rooted
     * @return true if rooted, false otherwise
     */
    private static boolean isRooted() {
        // Check for common root indicators
        String[] rootPaths = {
                "/system/app/Superuser.apk",
                "/system/xbin/su",
                "/system/bin/su",
                "/sbin/su",
                "/system/xbin/busybox"
        };

        for (String path : rootPaths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if app is running in an emulator
     * @return true if emulator, false otherwise
     */
    private static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

    /**
     * Validation result
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        /**
         * Add error
         * @param error Error message
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Add warning
         * @param warning Warning message
         */
        public void addWarning(String warning) {
            warnings.add(warning);
        }

        /**
         * Check if validation passed
         * @return true if no errors, false otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Get errors
         * @return List of errors
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Get warnings
         * @return List of warnings
         */
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        /**
         * Check if has warnings
         * @return true if has warnings, false otherwise
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
