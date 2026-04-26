package com.shop;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductManager {

    // ─── ADD PRODUCT ──────────────────────────────────────────────────────────

    public static int addProduct(String brand, String category,
                                  String productType, double pricePerKg, String date) {
        String sql = "INSERT INTO products (brand, category, product_type, price_per_kg, date_added) VALUES (?,?,?,?,?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, brand);
            ps.setString(2, category);
            ps.setString(3, productType);
            ps.setDouble(4, pricePerKg);
            ps.setString(5, date);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
        return -1;
    }

    // ─── BAG SIZE OPERATIONS ──────────────────────────────────────────────────

    public static void addBagSize(int productId, double sizeKg, Double bagPrice,
                                   int sealedCount, int openedCount, double openedTotalKg) {
        String sql = """
            INSERT INTO bag_sizes (product_id, size_kg, bag_price, sealed_count,
            opened_count, opened_total_kg) VALUES (?,?,?,?,?,?)
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setDouble(2, sizeKg);
            if (bagPrice != null) ps.setDouble(3, bagPrice);
            else ps.setNull(3, Types.REAL);
            ps.setInt(4, sealedCount);
            ps.setInt(5, openedCount);
            ps.setDouble(6, openedTotalKg);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─── PACKET STOCK ─────────────────────────────────────────────────────────

    public static void addPacketStock(int productId, String packSize,
                                       double pricePerUnit, int unitCount) {
        String sql = "INSERT INTO packet_stock (product_id, pack_size, price_per_unit, unit_count) VALUES (?,?,?,?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, packSize);
            ps.setDouble(3, pricePerUnit);
            ps.setInt(4, unitCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─── VIEW ALL STOCK ───────────────────────────────────────────────────────

    public static void viewAllStock() {
        // Order by category then brand
        String sql = "SELECT * FROM products ORDER BY category, brand";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n==================== CURRENT STOCK ====================");
            boolean any = false;
            while (rs.next()) {
                any = true;
                int id = rs.getInt("id");
                String brand = rs.getString("brand");
                String category = rs.getString("category");
                String type = rs.getString("product_type");
                double pricePerKg = rs.getDouble("price_per_kg");

                System.out.println("\n[" + id + "] " + brand + " | " + category);

                if (type.equals("weight")) {
                    System.out.printf("    Price/kg: Rs.%.2f%n", pricePerKg);
                    showBagSizes(conn, id);
                } else {
                    showPacketStock(conn, id);
                }
            }
            if (!any) System.out.println("No products in stock.");
            System.out.println("\n=======================================================");

        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void showBagSizes(Connection conn, int productId) throws SQLException {
        String sql = "SELECT * FROM bag_sizes WHERE product_id=? ORDER BY size_kg";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, productId);
        ResultSet rs = ps.executeQuery();

        int totalSealed = 0;
        double totalKg = 0;

        while (rs.next()) {
            double sizeKg = rs.getDouble("size_kg");
            int sealed = rs.getInt("sealed_count");
            int opened = rs.getInt("opened_count");
            double openedKg = rs.getDouble("opened_total_kg");
            Double bagPrice = rs.getObject("bag_price") != null ? rs.getDouble("bag_price") : null;

            totalSealed += sealed;
            totalKg += sealed * sizeKg;
            if (opened > 0) totalKg += openedKg;

            String bagPriceStr = bagPrice != null ? String.format(" | Bag: Rs.%.2f", bagPrice) : "";
            String openedStr = opened > 0 ? String.format(" | %d opened (%.1fkg loose)", opened, openedKg) : "";

            System.out.printf("    %.0fkg bags: %d sealed%s%s%n",
                sizeKg, sealed, bagPriceStr, openedStr);
        }
        System.out.printf("    >> Total sealed: %d bags | Est. stock: %.1f kg%n", totalSealed, totalKg);
    }

    private static void showPacketStock(Connection conn, int productId) throws SQLException {
        String sql = "SELECT * FROM packet_stock WHERE product_id=?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, productId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            System.out.printf("    %s: %d units | Rs.%.2f/unit%n",
                rs.getString("pack_size"),
                rs.getInt("unit_count"),
                rs.getDouble("price_per_unit"));
        }
    }

    // ─── FIND PRODUCT ─────────────────────────────────────────────────────────

    // Returns [id, brand, category, product_type, price_per_kg] or null
    public static String[] findProduct(int id) {
        String sql = "SELECT * FROM products WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("brand"),
                    rs.getString("category"),
                    rs.getString("product_type"),
                    String.valueOf(rs.getDouble("price_per_kg"))
                };
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
        return null;
    }

    // Returns [bagSizeId, sizeKg, bagPrice(-1=none), sealedCount, openedCount, openedTotalKg]
    public static List<double[]> getBagSizes(int productId) {
        List<double[]> list = new ArrayList<>();
        String sql = "SELECT * FROM bag_sizes WHERE product_id=? ORDER BY size_kg";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double bagPrice = rs.getObject("bag_price") != null ? rs.getDouble("bag_price") : -1;
                list.add(new double[]{
                    rs.getInt("id"),
                    rs.getDouble("size_kg"),
                    bagPrice,
                    rs.getInt("sealed_count"),
                    rs.getInt("opened_count"),
                    rs.getDouble("opened_total_kg")
                });
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
        return list;
    }

    // ─── STOCK DEDUCTION FOR BILLING ──────────────────────────────────────────

    public static void deductSealedBags(int bagSizeId, int count) {
        String sql = "UPDATE bag_sizes SET sealed_count = sealed_count - ? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    public static void deductOpenedKg(int bagSizeId, double kg) {
        // Deduct kg from opened_total_kg
        String sql = "UPDATE bag_sizes SET opened_total_kg = MAX(0, opened_total_kg - ?) WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, kg);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    public static void deductPacketUnits(int packetId, int count) {
        String sql = "UPDATE packet_stock SET unit_count = unit_count - ? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, packetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─── MARK BAG AS OPENED ───────────────────────────────────────────────────

    public static void openBag(int bagSizeId, double remainingKg,
                                boolean autoDeductSealed,
                                boolean previousSoldOut, int currentOpenedCount) {
        try (Connection conn = Database.getConnection()) {
            if (previousSoldOut || currentOpenedCount == 0) {
                // Replace: reset opened count to 1, set kg
                String sql = "UPDATE bag_sizes SET opened_count=1, opened_total_kg=?" +
                             (autoDeductSealed ? ", sealed_count = sealed_count - 1" : "") +
                             " WHERE id=?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setDouble(1, remainingKg);
                ps.setInt(2, bagSizeId);
                ps.executeUpdate();
            } else {
                // Add to existing: increment opened count, add kg
                String sql = "UPDATE bag_sizes SET opened_count = opened_count + 1, " +
                             "opened_total_kg = opened_total_kg + ?" +
                             (autoDeductSealed ? ", sealed_count = sealed_count - 1" : "") +
                             " WHERE id=?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setDouble(1, remainingKg);
                ps.setInt(2, bagSizeId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // ─── EDIT OPERATIONS ─────────────────────────────────────────────────────

    public static void updateBrand(int productId, String newBrand) {
        update("UPDATE products SET brand=? WHERE id=?", newBrand, productId);
        System.out.println("  Brand updated.");
    }

    public static void updateCategory(int productId, String newCategory) {
        update("UPDATE products SET category=? WHERE id=?", newCategory, productId);
        System.out.println("  Category updated.");
    }

    public static void updateDate(int productId, String newDate) {
        update("UPDATE products SET date_added=? WHERE id=?", newDate, productId);
        System.out.println("  Date updated.");
    }

    public static void updatePrice(int productId, double newPrice) {
        String sql = "UPDATE products SET price_per_kg=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, productId);
            ps.executeUpdate();
            System.out.println("  Price updated.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void updateBagPrice(int bagSizeId, double bagPrice) {
        String sql = "UPDATE bag_sizes SET bag_price=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (bagPrice > 0) ps.setDouble(1, bagPrice);
            else ps.setNull(1, Types.REAL);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
            System.out.println("  Bag price updated.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void setSealedCount(int bagSizeId, int count) {
        String sql = "UPDATE bag_sizes SET sealed_count=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void addSealedBags(int bagSizeId, int count) {
        String sql = "UPDATE bag_sizes SET sealed_count = sealed_count + ? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
            System.out.println("  Stock updated.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void setOpenedCount(int bagSizeId, int count) {
        String sql = "UPDATE bag_sizes SET opened_count=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void setOpenedKg(int bagSizeId, double kg) {
        String sql = "UPDATE bag_sizes SET opened_total_kg=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, kg);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void addPacketUnits(int packetId, int count) {
        String sql = "UPDATE packet_stock SET unit_count = unit_count + ? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, packetId);
            ps.executeUpdate();
            System.out.println("  Units added.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    public static void deleteBagSize(int bagSizeId) {
        String sql = "DELETE FROM bag_sizes WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bagSizeId);
            ps.executeUpdate();
            System.out.println("  Bag size removed.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void deleteItem(int productId) {
        try (Connection conn = Database.getConnection()) {
            PreparedStatement ps1 = conn.prepareStatement("DELETE FROM bag_sizes WHERE product_id=?");
            ps1.setInt(1, productId);
            ps1.executeUpdate();
            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM packet_stock WHERE product_id=?");
            ps2.setInt(1, productId);
            ps2.executeUpdate();
            PreparedStatement ps3 = conn.prepareStatement("DELETE FROM products WHERE id=?");
            ps3.setInt(1, productId);
            ps3.executeUpdate();
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    // ─── RETURN STOCK ─────────────────────────────────────────────────────────

    public static void returnSealed(int bagSizeId, int count) {
        addSealedBags(bagSizeId, count);
    }

    public static void returnOpenedWeight(int bagSizeId, double returnedKg) {
        String sql = """
            UPDATE bag_sizes SET opened_count = opened_count + 1,
            opened_total_kg = opened_total_kg + ? WHERE id=?
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, returnedKg);
            ps.setInt(2, bagSizeId);
            ps.executeUpdate();
            System.out.println("  Return accepted. " + returnedKg + "kg added to opened stock.");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void returnPacket(int packetId, int count) {
        addPacketUnits(packetId, count);
        System.out.println("  " + count + " units returned to stock.");
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────

    private static void update(String sql, String val, int id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, val);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }
}
