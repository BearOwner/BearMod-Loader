package com.keyauth.api;

/**
 * Mock implementation of KeyAuthApp for development purposes
 * This is a placeholder class to make the project buildable
 */
public class KeyAuthApp {
    
    private boolean initialized = false;
    private String response = "";
    private String expiryDate = "2025-12-31";
    private String registrationDate = "2023-01-01";
    
    /**
     * Constructor
     * @param appName Application name
     * @param appOwner Application owner
     * @param appVersion Application version
     */
    public KeyAuthApp(String appName, String appOwner, String appVersion) {
        // Mock implementation
    }
    
    /**
     * Initialize KeyAuth
     * @return true if initialization was successful, false otherwise
     */
    public boolean init() {
        initialized = true;
        return true;
    }
    
    /**
     * Check if KeyAuth is initialized
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get response message
     * @return Response message
     */
    public String getResponse() {
        return response;
    }
    
    /**
     * Validate license key
     * @param licenseKey License key to validate
     * @return true if license is valid, false otherwise
     */
    public boolean license(String licenseKey) {
        // Mock implementation - always return true for development
        return true;
    }
    
    /**
     * Get expiry date
     * @return Expiry date
     */
    public String getExpiryDate() {
        return expiryDate;
    }
    
    /**
     * Get registration date
     * @return Registration date
     */
    public String getRegistrationDate() {
        return registrationDate;
    }
    
    /**
     * Logout user
     */
    public void logout() {
        // Mock implementation
    }
}
