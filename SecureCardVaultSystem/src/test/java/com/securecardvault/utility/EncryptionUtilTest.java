
package com.securecardvault.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionUtilTest {

    @Test
    void encryptAndDecryptReturnsOriginalText() {
        String key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        System.setProperty("CARD_VAULT_KEY", key);

        String original = "test-card-number";
        String encrypted = EncryptionUtil.encrypt(original);

        assertNotEquals(original, encrypted);
        assertEquals(original, EncryptionUtil.decrypt(encrypted));
    }

    @Test
    void encryptionUsesDifferentIvForEachCall() {
        System.setProperty(
                "CARD_VAULT_KEY",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );

        String first = EncryptionUtil.encrypt("test-value");
        String second = EncryptionUtil.encrypt("test-value");

        assertNotEquals(first, second);
        assertEquals("test-value", EncryptionUtil.decrypt(first));
        assertEquals("test-value", EncryptionUtil.decrypt(second));
    }

    @Test
    void missingKeyIsRejected() {
        String previous = System.getProperty("CARD_VAULT_KEY");

        try {
            System.clearProperty("CARD_VAULT_KEY");
            // This test assumes CARD_VAULT_KEY is not set in the environment.
            if (System.getenv("CARD_VAULT_KEY") == null
                    || System.getenv("CARD_VAULT_KEY").isBlank()) {
                assertThrows(
                        IllegalStateException.class,
                        () -> EncryptionUtil.encrypt("test-value")
                );
            }
        } finally {
            if (previous == null) {
                System.clearProperty("CARD_VAULT_KEY");
            } else {
                System.setProperty("CARD_VAULT_KEY", previous);
            }
        }
    }
}