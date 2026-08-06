package com.securecardvault.service;

import com.securecardvault.database.DBConnection;
import com.securecardvault.utility.EncryptionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Payment {
    public String pay(String token, double amount) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.openConnection()) {
            String selectSql = "SELECT encrypted_card_number FROM card_vault WHERE token = ?";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setString(1, token);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        return "Token not found";
                    }

                    String encryptedCardNumber = resultSet.getString("encrypted_card_number");
                    String decryptedCardNumber = EncryptionUtil.decrypt(encryptedCardNumber);

                    if (decryptedCardNumber == null || decryptedCardNumber.isEmpty()) {
                        return "Invalid card data";
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

                    return "Payment Successful. Transaction ID: " + transactionId;
                }
            }
        }
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
