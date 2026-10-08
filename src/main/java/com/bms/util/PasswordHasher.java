package com.bms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Turns passwords into salted hashes, using only what is built into Java.
 * A hash cannot be turned back into the password, so even someone who reads
 * the database file cannot see the real passwords.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;   // makes guessing passwords slow
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {
    }

    /** Creates a random salt (a random value mixed into the hash). */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hashes the password together with the salt. */
    public static String hash(String password, String salt) {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available on this computer.", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Checks a typed password against the stored salt and hash. */
    public static boolean verify(String password, String salt, String expectedHash) {
        byte[] actual = hash(password, salt).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        // MessageDigest.isEqual compares in constant time (safer than String.equals)
        return MessageDigest.isEqual(actual, expected);
    }
}
