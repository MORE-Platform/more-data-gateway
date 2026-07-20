package io.redlink.more.data.util;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class GarminUtils {
    public static String garminOAuthState() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[random.nextInt(20, 31)];
        random.nextBytes(bytes);

        long currentTime = System.currentTimeMillis();
        byte[] timeBytes = ByteBuffer.allocate(Long.BYTES).putLong(currentTime).array();

        byte[] combined = new byte[bytes.length + timeBytes.length];
        System.arraycopy(bytes, 0, combined, 0, bytes.length);
        System.arraycopy(timeBytes, 0, combined, bytes.length, timeBytes.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    // The code verifier is a cryptographically random string
    // using the characters A-Z, a-z, 0-9,
    // and the punctuation characters -._~ (hyphen, period, underscore, and tilde),
    // between 43 and 128 characters long
    public static String createCodeVerifier() {
        SecureRandom random = new SecureRandom();
        int length = random.nextInt(43, 129);

        String allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder codeVerifier = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(allowedChars.length());
            codeVerifier.append(allowedChars.charAt(index));
        }

        return codeVerifier.toString();
    }

    // The code challenge is a BASE64-URL-encoded string of the SHA256 hash of the code verifier
    public static String createCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
