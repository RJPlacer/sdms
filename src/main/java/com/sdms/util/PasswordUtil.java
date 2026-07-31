package com.sdms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    public static String hash(String password) {
        try {
            SecureRandom sr = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            sr.nextBytes(salt);
            byte[] hashed = derive(password, salt, ITERATIONS);
            return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) { throw new RuntimeException("Hashing failed", e); }
    }

    public static boolean verify(String password, String stored) {
        try {
            if (stored == null) return false;
            if (stored.startsWith(PREFIX + "$")) {
                String[] parts = stored.split("\\$");
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, derive(password, salt, iterations));
            }

            // Compatibility with hashes created by releases before PBKDF2.
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] actual = md.digest(password.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) { return false; }
    }

    public static boolean needsRehash(String stored) {
        return stored == null || !stored.startsWith(PREFIX + "$" + ITERATIONS + "$");
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
