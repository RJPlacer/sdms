package com.sdms.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    @Test
    void hashesAndVerifiesWithPbkdf2() {
        String hash = PasswordUtil.hash("correct horse battery staple");

        assertTrue(hash.startsWith("pbkdf2-sha256$"));
        assertTrue(PasswordUtil.verify("correct horse battery staple", hash));
        assertFalse(PasswordUtil.verify("wrong password", hash));
        assertFalse(PasswordUtil.needsRehash(hash));

        String symbolPasswordHash = PasswordUtil.hash("#Catdog12317");
        assertTrue(PasswordUtil.verify("#Catdog12317", symbolPasswordHash));
    }

    @Test
    void acceptsLegacySha256HashesForMigration() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] value = digest.digest("legacy password".getBytes(StandardCharsets.UTF_8));
        String legacy = Base64.getEncoder().encodeToString(salt) + ":"
            + Base64.getEncoder().encodeToString(value);

        assertTrue(PasswordUtil.verify("legacy password", legacy));
        assertFalse(PasswordUtil.verify("wrong password", legacy));
        assertTrue(PasswordUtil.needsRehash(legacy));
    }
}
