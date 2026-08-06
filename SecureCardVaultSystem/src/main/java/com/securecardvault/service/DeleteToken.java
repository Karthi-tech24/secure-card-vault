package com.securecardvault.service;

import com.securecardvault.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DeleteToken {
    public String delete(String token) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.openConnection()) {
            String sql = "DELETE FROM card_vault WHERE token = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, token);
                int rowsDeleted = preparedStatement.executeUpdate();

                if (rowsDeleted > 0) {
                    return "Token Deleted Successfully";
                }
            }
        }
        return "Token not found";
    }
}
