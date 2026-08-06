package com.securecardvault.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {
    private static final String MYSQL_URL = System.getProperty("DB_URL", System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/secure_card_vault"));
    private static final String MYSQL_USER = System.getProperty("DB_USER", System.getenv().getOrDefault("DB_USER", "root"));
    private static final String MYSQL_PASSWORD = System.getProperty("DB_PASSWORD", System.getenv().getOrDefault("DB_PASSWORD", ""));
    private static final String FALLBACK_URL = System.getProperty("DB_FALLBACK_URL", System.getenv().getOrDefault("DB_FALLBACK_URL", "jdbc:h2:file:./target/card_vault_db;MODE=MySQL;AUTO_SERVER=TRUE"));
    private static final String FALLBACK_USER = System.getProperty("DB_FALLBACK_USER", System.getenv().getOrDefault("DB_FALLBACK_USER", "sa"));
    private static final String FALLBACK_PASSWORD = System.getProperty("DB_FALLBACK_PASSWORD", System.getenv().getOrDefault("DB_FALLBACK_PASSWORD", ""));

    private Connection connection;

    public DBConnection() {
    }

    public Connection openConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = connect();
            initializeSchema(connection);
        }
        return connection;
    }

    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private Connection connect() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
        } catch (ClassNotFoundException | SQLException exception) {
            try {
                Class.forName("org.h2.Driver");
                return DriverManager.getConnection(FALLBACK_URL, FALLBACK_USER, FALLBACK_PASSWORD);
            } catch (ClassNotFoundException fallbackException) {
                throw new SQLException("Unable to connect to the configured database and no embedded fallback driver is available", fallbackException);
            }
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS card_vault (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        card_holder_name VARCHAR(100) NOT NULL,
                        encrypted_card_number TEXT NOT NULL,
                        expiry_date VARCHAR(10) NOT NULL,
                        token VARCHAR(100) NOT NULL UNIQUE,
                        created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        transaction_id VARCHAR(50) NOT NULL UNIQUE,
                        token VARCHAR(100) NOT NULL,
                        amount DOUBLE NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }
}
