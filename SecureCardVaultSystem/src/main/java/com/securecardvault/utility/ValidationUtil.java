package com.securecardvault.utility;

public class ValidationUtil {
    public static boolean isValidCardNumber(String cardNumber) {
        return cardNumber != null
                && cardNumber.trim().matches("\\d{16}");
    }

    public static boolean isValidCvv(String cvv) {
        return cvv != null && cvv.trim().matches("\\d{3}");
    }

    public static boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null || !expiryDate.trim().matches("^(0[1-9]|1[0-2])/(\\d{2})$")) {
            return false;
        }

        String[] parts = expiryDate.trim().split("/");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]) + 2000;

        java.time.YearMonth current = java.time.YearMonth.now();
        java.time.YearMonth expiry = java.time.YearMonth.of(year, month);

        return !expiry.isBefore(current);
    }

    public static boolean isValidName(String name) {
        return name != null
                && name.trim().length() >= 3
                && name.trim().length() <= 50
                && name.trim().matches("[A-Za-z ]+");
    }
}
