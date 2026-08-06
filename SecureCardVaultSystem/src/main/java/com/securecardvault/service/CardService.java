package com.securecardvault.service;

import com.securecardvault.database.DBConnection;
import com.securecardvault.model.Card;
import com.securecardvault.utility.EncryptionUtil;
import com.securecardvault.utility.TokenGenerator;
import com.securecardvault.utility.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CardService {
    public Map<String, Object> saveCard(Card card) throws SQLException {
        if (!ValidationUtil.isValidName(card.getCardHolderName())) {
            throw new IllegalArgumentException("Card holder name must contain letters and spaces only");
        }
        if (!ValidationUtil.isValidCardNumber(card.getCardNumber())) {
            throw new IllegalArgumentException("Card number must contain exactly 16 digits");
        }
        if (!ValidationUtil.isValidExpiryDate(card.getExpiryDate())) {
            throw new IllegalArgumentException("Expiry date must be in MM/YY format");
        }
        if (!ValidationUtil.isValidCvv(card.getCvv())) {
            throw new IllegalArgumentException("CVV must contain exactly 3 digits");
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Card saved successfully");
        response.put("token", token);
        return response;
    }

    public Map<String, Object> makePayment(String token, double amount) throws SQLException {
        Map<String, Object> response = new LinkedHashMap<>();
        DBConnection dbConnection = new DBConnection();

        try (Connection connection = dbConnection.openConnection()) {
            String selectSql = "SELECT encrypted_card_number FROM card_vault WHERE token = ?";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setString(1, token);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        response.put("success", false);
                        response.put("message", "Token not found");
                        return response;
                    }

                    String encryptedCardNumber = resultSet.getString("encrypted_card_number");
                    String decryptedCardNumber = EncryptionUtil.decrypt(encryptedCardNumber);

                    if (decryptedCardNumber == null || decryptedCardNumber.isEmpty()) {
                        response.put("success", false);
                        response.put("message", "Invalid card data");
                        return response;
                    }

                    String transactionId = generateTransactionId(connection);
                    String insertSql = "INSERT INTO transactions (transaction_id, token, amount, status, transaction_date) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                        insertStatement.setString(1, transactionId);
                        insertStatement.setString(2, token);
                        insertStatement.setDouble(3, amount);
                        insertStatement.setString(4, "Success");
                        insertStatement.executeUpdate();
                    }

                    response.put("success", true);
                    response.put("message", "Payment successful");
                    response.put("transactionId", transactionId);
                    response.put("status", "SUCCESS");
                    response.put("amount", amount);
                    return response;
                }
            }
        }
    }

    public Map<String, Object> getTransactionHistory() throws SQLException {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> transactions = new ArrayList<>();

        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.openConnection()) {
            String sql = "SELECT transaction_id, token, amount, status, transaction_date FROM transactions ORDER BY transaction_date DESC";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> transaction = new LinkedHashMap<>();
                    transaction.put("transactionId", resultSet.getString("transaction_id"));
                    transaction.put("token", resultSet.getString("token"));
                    transaction.put("amount", resultSet.getDouble("amount"));
                    transaction.put("status", resultSet.getString("status"));
                    transaction.put("date", resultSet.getTimestamp("transaction_date").toString());
                    transactions.add(transaction);
                }
            }
        }

        response.put("success", true);
        response.put("transactions", transactions);
        return response;
    }

    public Map<String, Object> deleteToken(String token) throws SQLException {
        Map<String, Object> response = new LinkedHashMap<>();
        DBConnection dbConnection = new DBConnection();

        try (Connection connection = dbConnection.openConnection()) {
            String sql = "DELETE FROM card_vault WHERE token = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, token);
                int rowsDeleted = preparedStatement.executeUpdate();
                if (rowsDeleted > 0) {
                    response.put("success", true);
                    response.put("message", "Token deleted successfully");
                    return response;
                }
            }
        }

        response.put("success", false);
        response.put("message", "Token not found");
        return response;
    }

    private String generateTransactionId(Connection connection) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTRING(transaction_id, 4) AS UNSIGNED)) AS max_id FROM transactions";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                int maxId = resultSet.getInt("max_id");
                return String.format("TXN%03d", maxId + 1);
            }
        }
        return "TXN001";
    }
}
