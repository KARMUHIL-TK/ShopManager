package com.shop;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReturnManager {

    public static void processReturn(java.util.Scanner sc) {
        System.out.println("\n--- RETURN STOCK ---");
        ProductManager.viewAllStock();

        System.out.print("Enter Product ID: ");
        String idInput = sc.nextLine().trim();
        if (Main.isExit(idInput)) return;

        int productId;
        try { productId = Integer.parseInt(idInput); }
        catch (NumberFormatException e) { System.out.println("  [!] Invalid."); return; }

        String[] product = ProductManager.findProduct(productId);
        if (product == null) { System.out.println("  [!] Product not found."); return; }

        String brand = product[1];
        String category = product[2];
        String type = product[3];

        System.out.println("  Product: " + brand + " | " + category);

        if (type.equals("weight")) {
            processWeightReturn(sc, productId, brand);
        } else {
            processPacketReturn(sc, productId, brand);
        }
    }

    private static void processWeightReturn(java.util.Scanner sc,
                                             int productId, String brand) {
        System.out.println("\n  Return type:");
        System.out.println("  1. Sealed bag return");
        System.out.println("  2. Opened / partial return (kg measured)");

        String choice;
        while (true) {
            System.out.print("  Choice [1/2]: ");
            choice = sc.nextLine().trim();
            if (Main.isExit(choice)) return;
            if (choice.equals("1") || choice.equals("2")) break;
            System.out.println("  [!] Enter 1 or 2.");
        }

        List<double[]> bagSizes = ProductManager.getBagSizes(productId);
        if (bagSizes.isEmpty()) { System.out.println("  [!] No bag sizes found."); return; }

        int bagChoice = Main.chooseBagSize(bagSizes);
        if (bagChoice < 0) return;

        double[] bag = bagSizes.get(bagChoice);
        int bagSizeId = (int) bag[0];
        double sizeKg = bag[1];

        if (choice.equals("1")) {
            // Sealed return
            int count;
            while (true) {
                System.out.print("  How many sealed bags returned: ");
                String v = sc.nextLine().trim();
                if (Main.isExit(v)) return;
                try {
                    count = Integer.parseInt(v);
                    if (count > 0) break;
                    System.out.println("  [!] Must be > 0.");
                } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
            }
            ProductManager.returnSealed(bagSizeId, count);
            System.out.printf("  %d sealed %.0fkg bag(s) of %s returned.%n", count, sizeKg, brand);

        } else {
            // Opened/partial return — kg mandatory
            double currentKg = bag[5];
            int currentOpened = (int) bag[4];
            if (currentOpened > 0) {
                System.out.printf("  Current opened stock: %d bags, %.1fkg remaining.%n",
                    currentOpened, currentKg);
            }

            double returnedKg;
            while (true) {
                System.out.print("  Measured kg of returned goods (mandatory): ");
                String v = sc.nextLine().trim();
                if (Main.isExit(v)) return;
                try {
                    returnedKg = Double.parseDouble(v);
                    if (returnedKg > 0) break;
                    System.out.println("  [!] Must be > 0.");
                } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
            }

            ProductManager.returnOpenedWeight(bagSizeId, returnedKg);
            System.out.printf("  %.2fkg of %s returned to opened stock.%n", returnedKg, brand);
        }

        logReturn(productId, brand, choice.equals("1") ? "sealed" : "opened", sizeKg,
            choice.equals("1") ? 1 : 0);
    }

    private static void processPacketReturn(java.util.Scanner sc,
                                             int productId, String brand) {
        System.out.println("\n  Note: Only sealed/unopened packets accepted for return.");

        String sql = "SELECT * FROM packet_stock WHERE product_id=?";
        List<String[]> packetInfo = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            System.out.println("  Packet sizes:");
            int i = 1;
            while (rs.next()) {
                System.out.printf("    [%d] %s%n", i++, rs.getString("pack_size"));
                packetInfo.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("pack_size")
                });
            }
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); return; }

        if (packetInfo.isEmpty()) { System.out.println("  [!] No packet sizes."); return; }

        int choice;
        while (true) {
            System.out.print("  Which packet? Enter []: ");
            String v = sc.nextLine().trim();
            if (Main.isExit(v)) return;
            try {
                choice = Integer.parseInt(v) - 1;
                if (choice >= 0 && choice < packetInfo.size()) break;
                System.out.println("  [!] Invalid.");
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }

        int count;
        while (true) {
            System.out.print("  How many units returned: ");
            String v = sc.nextLine().trim();
            if (Main.isExit(v)) return;
            try {
                count = Integer.parseInt(v);
                if (count > 0) break;
                System.out.println("  [!] Must be > 0.");
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }

        ProductManager.returnPacket(Integer.parseInt(packetInfo.get(choice)[0]), count);
        System.out.printf("  %d x %s %s returned.%n",
            count, brand, packetInfo.get(choice)[1]);

        logReturn(productId, brand, "sealed_packet", 0, count);
    }

    private static void logReturn(int productId, String brand, String returnType,
                                   double sizeKg, double quantity) {
        String sql = """
            INSERT INTO returns (product_id, brand, return_type, size_kg,
            quantity, unit, return_date) VALUES (?,?,?,?,?,?,date('now'))
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, brand);
            ps.setString(3, returnType);
            ps.setDouble(4, sizeKg);
            ps.setDouble(5, quantity);
            ps.setString(6, returnType.contains("packet") ? "unit" : "bag");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[ERROR logging return] " + e.getMessage());
        }
    }
}
