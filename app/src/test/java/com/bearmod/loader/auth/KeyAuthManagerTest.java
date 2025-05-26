package com.bearmod.loader.auth;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

/**
 * Unit tests for authentication components
 * Note: These tests focus on data classes that don't require Android context
 */
public class KeyAuthManagerTest {

    // Note: KeyAuthManager tests are skipped in unit tests due to Android dependencies
    // These would be better suited for instrumented tests or integration tests

    @Test
    public void testAuthResultCreation() {
        Date testDate = new Date();
        AuthResult result = new AuthResult(true, "Test message", testDate, "2022-01-01");

        assertTrue("Should be successful", result.isSuccess());
        assertEquals("Should have correct message", "Test message", result.getMessage());
        assertEquals("Should have correct expiry date", testDate, result.getExpiryDate());
        assertEquals("Should have correct registration date", "2022-01-01", result.getRegistrationDate());
    }
}
