package com.securecardvault.utility;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;

    private static SecretKey getKey() {
        try {
            String configuredKey = System.getProperty("CARD_VAULT_KEY", System.getenv().getOrDefault("CARD_VAULT_KEY", ""));
            if (configuredKey.isBlank()) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(AES_KEY_SIZE);
                configuredKey = Base64.getEncoder().encodeToString(keyGenerator.generateKey().getEncoded());
                System.setProperty("CARD_VAULT_KEY", configuredKey);
            }

            byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Invalid encryption key length");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize encryption", exception);
        }
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt card data", exception);
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return "";
        }

        if (!encryptedText.contains(":")) {
            return new String(Base64.getDecoder().decode(encryptedText), StandardCharsets.UTF_8);
        }

        try {
            String[] parts = encryptedText.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt card data", exception);
        }
    }
}
