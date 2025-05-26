package com.bearmod.loader.auth;

import java.util.Date;

/**
 * Authentication result
 * Contains information about authentication status
 */
public class AuthResult {
    
    private final boolean success;
    private final String message;
    private final Date expiryDate;
    private final String registrationDate;
    
    /**
     * Constructor
     * @param success Success status
     * @param message Result message
     * @param expiryDate License expiry date
     * @param registrationDate Registration date
     */
    public AuthResult(boolean success, String message, Date expiryDate, String registrationDate) {
        this.success = success;
        this.message = message;
        this.expiryDate = expiryDate;
        this.registrationDate = registrationDate;
    }
    
    /**
     * Check if authentication was successful
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Get result message
     * @return Result message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Get license expiry date
     * @return Expiry date
     */
    public Date getExpiryDate() {
        return expiryDate;
    }
    
    /**
     * Get registration date
     * @return Registration date
     */
    public String getRegistrationDate() {
        return registrationDate;
    }
}
