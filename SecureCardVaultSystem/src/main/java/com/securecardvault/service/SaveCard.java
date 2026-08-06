package com.securecardvault.service;

import com.securecardvault.database.DBConnection;
import com.securecardvault.model.Card;
import com.securecardvault.utility.EncryptionUtil;
import com.securecardvault.utility.TokenGenerator;
import com.securecardvault.utility.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SaveCard {
    public String save(Card card) throws SQLException {
        if (!ValidationUtil.isValidName(card.getCardHolderName())) {
            throw new IllegalArgumentException("Invalid card holder name");
        }
        if (!ValidationUtil.isValidCardNumber(card.getCardNumber())) {
            throw new IllegalArgumentException("Invalid card number");
        }
        if (!ValidationUtil.isValidExpiryDate(card.getExpiryDate())) {
            throw new IllegalArgumentException("Invalid expiry date");
        }
        if (!ValidationUtil.isValidCvv(card.getCvv())) {
            throw new IllegalArgumentException("Invalid CVV");
        }

        String token = TokenGenerator.generateToken();
        String encryptedCardNumber = EncryptionUtil.encrypt(card.getCardNumber());

        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.openConnection()) {
            String sql = "INSERT INTO card_vault (card_holder_name, encrypted_card_number, expiry_date, token, created_date) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, card.getCardHolderName());
                preparedStatement.setString(2, encryptedCardNumber);
                preparedStatement.setString(3, card.getExpiryDate());
                preparedStatement.setString(4, token);
                preparedStatement.executeUpdate();
            }
        }

        return "Card Saved Successfully\nToken: " + token;
    }
}
