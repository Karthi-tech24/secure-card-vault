
package com.securecardvault.utility;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private EncryptionUtil() {
    }

    private static SecretKey getKey() {
        String configuredKey = System.getProperty(
                "CARD_VAULT_KEY",
                System.getenv().getOrDefault("CARD_VAULT_KEY", "")
        );

        if (configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "CARD_VAULT_KEY must be configured before using encryption"
            );
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(configuredKey);

            if (keyBytes.length != AES_KEY_SIZE) {
                throw new IllegalArgumentException(
                        "The decoded encryption key must be exactly 32 bytes"
                );
            }

            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "CARD_VAULT_KEY must be a valid Base64-encoded 32-byte key",
                    exception
            );
        }
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    getKey(),
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to encrypt card data",
                    exception
            );
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return "";
        }

        try {
            String[] parts = encryptedText.split(":", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid encrypted data format"
                );
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);

            if (iv.length != IV_LENGTH) {
                throw new IllegalArgumentException(
                        "Invalid initialization vector length"
                );
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getKey(),
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] decrypted = cipher.doFinal(cipherText);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to decrypt card data",
                    exception
            );
        }
    }
}