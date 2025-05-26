package com.bearmod.loader.auth;

import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hardware ID generator
 * Generates a unique hardware ID for device identification
 */
public class HWID {

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Get hardware ID
     * @return Hardware ID as hexadecimal string
     */
    public static String getHWID() {
        return bytesToHex(generateHWID());
    }

    /**
     * Generate hardware ID
     * @return Hardware ID as byte array
     */
    public static byte[] generateHWID() {
        try {
            MessageDigest hash = MessageDigest.getInstance("MD5");

            // Combine standard system properties with Android-specific identifiers
            String s = System.getProperty("os.name") + System.getProperty("os.arch") + System.getProperty("os.version")
                    + Runtime.getRuntime().availableProcessors() 
                    + Build.BOARD + Build.BRAND + Build.DEVICE 
                    + Build.HARDWARE + Build.MODEL + Build.PRODUCT;
                    
            return hash.digest(s.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new Error("Algorithm wasn't found.", e);
        }
    }

    /**
     * Convert hexadecimal string to byte array
     * @param s Hexadecimal string
     * @return Byte array
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert byte array to hexadecimal string
     * @param bytes Byte array
     * @return Hexadecimal string
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
