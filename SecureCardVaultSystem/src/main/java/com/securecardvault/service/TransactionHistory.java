package com.securecardvault.service;

import com.securecardvault.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TransactionHistory {
    public void displayHistory() throws SQLException {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.openConnection()) {
            String sql = "SELECT transaction_id, token, amount, status, transaction_date FROM transactions ORDER BY transaction_date DESC";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                 ResultSet resultSet = preparedStatement.executeQuery()) {

                System.out.println("\n----------------------------------------------------------");
                System.out.println("              Transaction History");
                System.out.println("----------------------------------------------------------");
                System.out.println();
                System.out.println("+-------------------------------------------------------+");
                System.out.println("| Transaction ID | Token | Amount | Status | Date       |");
                System.out.println("+-------------------------------------------------------+");

                boolean hasRows = false;
                while (resultSet.next()) {
                    hasRows = true;
                    String transactionId = resultSet.getString("transaction_id");
                    String token = resultSet.getString("token");
                    String amount = String.format("$%.2f", resultSet.getDouble("amount"));
                    String status = resultSet.getString("status");
                    String date = resultSet.getTimestamp("transaction_date").toLocalDateTime().toLocalDate().toString();

                    System.out.printf("| %-14s | %-6s | %-7s | %-6s | %-10s |%n",
                            transactionId, token, amount, status, date);
                }

                System.out.println("+-------------------------------------------------------+");
                if (!hasRows) {
                    System.out.println("| No transactions found                                 |");
                    System.out.println("+-------------------------------------------------------+");
                }

                System.out.println();
                System.out.println("                 [ Back to Home ]");
            }
        }
    }
}
