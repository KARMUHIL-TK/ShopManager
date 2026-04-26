package com.shop;

import java.sql.*;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:shop.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initialize() {

        String products = """
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                brand TEXT NOT NULL,
                category TEXT NOT NULL,
                product_type TEXT NOT NULL,
                price_per_kg REAL DEFAULT 0,
                date_added TEXT NOT NULL
            );
        """;

        String bagSizes = """
            CREATE TABLE IF NOT EXISTS bag_sizes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                size_kg REAL NOT NULL,
                bag_price REAL,
                sealed_count INTEGER DEFAULT 0,
                opened_count INTEGER DEFAULT 0,
                opened_total_kg REAL DEFAULT 0,
                FOREIGN KEY (product_id) REFERENCES products(id)
            );
        """;

        String packetStock = """
            CREATE TABLE IF NOT EXISTS packet_stock (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                pack_size TEXT NOT NULL,
                price_per_unit REAL NOT NULL,
                unit_count INTEGER DEFAULT 0,
                FOREIGN KEY (product_id) REFERENCES products(id)
            );
        """;

        String bills = """
            CREATE TABLE IF NOT EXISTS bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_name TEXT DEFAULT 'Walk-in',
                total_amount REAL NOT NULL,
                bill_date TEXT NOT NULL
            );
        """;

        String billItems = """
            CREATE TABLE IF NOT EXISTS bill_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bill_id INTEGER NOT NULL,
                brand TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit TEXT NOT NULL,
                price_used REAL NOT NULL,
                line_total REAL NOT NULL,
                FOREIGN KEY (bill_id) REFERENCES bills(id)
            );
        """;

        String returns = """
            CREATE TABLE IF NOT EXISTS returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                brand TEXT NOT NULL,
                return_type TEXT NOT NULL,
                size_kg REAL,
                quantity REAL NOT NULL,
                unit TEXT NOT NULL,
                return_date TEXT NOT NULL
            );
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(products);
            stmt.execute(bagSizes);
            stmt.execute(packetStock);
            stmt.execute(bills);
            stmt.execute(billItems);
            stmt.execute(returns);
            System.out.println("[DB] Database ready.");
        } catch (SQLException e) {
            System.out.println("[DB ERROR] " + e.getMessage());
        }
    }
}
