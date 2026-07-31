package com.sdms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OnlineVerificationServiceTest {

    @Test
    void extractsSecureTokenFromVerificationUrl() {
        String token = "9d576330-f537-4c86-a790-9fcdd49950ee";
        assertEquals(token, OnlineVerificationService.extractVerificationToken(
            "https://verify.example.org/verify/" + token));
        assertEquals(token, OnlineVerificationService.extractVerificationToken(
            "https://verify.example.org/verify/" + token + "/"));
        assertNull(OnlineVerificationService.extractVerificationToken("ATH-2026-0001"));
        assertNull(OnlineVerificationService.extractVerificationToken(
            "https://verify.example.org/verify/short"));
    }
}
