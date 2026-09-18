package org.example.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashUtil {

    public static String sha256(String text)
        throws Exception {

        MessageDigest digest =
            MessageDigest.getInstance("SHA-256");

        byte[] hash =
            digest.digest(
                text.getBytes(StandardCharsets.UTF_8)
            );

        StringBuilder result = new StringBuilder();

        for (byte b : hash) {
            result.append(
                String.format("%02x", b)
            );
        }

        return result.toString();
    }
}
