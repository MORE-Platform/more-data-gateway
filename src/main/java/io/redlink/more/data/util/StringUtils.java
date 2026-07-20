package io.redlink.more.data.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class StringUtils {
    public static String anonymize(String s) {
        return anonymize(s, 4);
    }

    public static String anonymize(String s, int maxVisible) {
        if (s == null) {
            return null;
        }
        int visibleLength = Math.min(maxVisible, s.length() - s.length() / 2);
        String anonym;
        if (s.isEmpty()) {
            anonym = s;
        } else if (visibleLength <= 0) {
            anonym = new String(new char[s.length()]).replace('\0', '*');
        } else {
            anonym = new String(new char[s.length() - visibleLength]).replace('\0', '*') +
                    s.substring(s.length() - visibleLength);
        }
        return anonym;
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String base64Decode(String input) {
        return new String(Base64.getDecoder().decode(input));
    }
}
