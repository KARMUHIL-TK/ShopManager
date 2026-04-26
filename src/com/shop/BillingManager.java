package com.shop;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BillingManager {

    static class BillItem {
        String brand, category, description, unit;
        double quantity, priceUsed, lineTotal;

        BillItem(String brand, String category, String description,
                 double quantity, String unit, double priceUsed) {
            this.brand = brand;
            this.category = category;
            this.description = description;
            this.quantity = quantity;
            this.unit = unit;
            this.priceUsed = priceUsed;
            this.lineTotal = quantity * priceUsed;
        }

        void printLine(int index) {
            System.out.printf("  %d. %-12s %-10s %-22s %6.3f %-5s x Rs.%-8.2f = Rs.%.2f%n",
                index, brand, category, description,
                quantity, unit, priceUsed, lineTotal);
        }
    }

    public static void startBilling(java.util.Scanner sc) {
        List<BillItem> items = new ArrayList<>();
        System.out.println("\n--- NEW BILL ---");

        System.out.print("Customer name (Enter for Walk-in): ");
        String customer = sc.nextLine().trim();
        if (customer.isEmpty()) customer = "Walk-in";

        String date = Main.readDate();
        if (date == null) return;

        while (true) {
            System.out.println("\n--- ADD ITEM TO BILL ---");
            ProductManager.viewAllStock();

            System.out.print("Enter Product ID (0 to finish): ");
            String pidInput = sc.nextLine().trim();
            if (Main.isExit(pidInput)) return;

            int productId;
            try { productId = Integer.parseInt(pidInput); }
            catch (NumberFormatException e) { System.out.println("  [!] Invalid."); continue; }
            if (productId == 0) break;

            String[] product = ProductManager.findProduct(productId);
            if (product == null) { System.out.println("  [!] Product not found."); continue; }

            String brand = product[1];
            String category = product[2];
            String type = product[3];
            double pricePerKg = Double.parseDouble(product[4]);

            if (type.equals("weight")) {
                addWeightItem(sc, items, productId, brand, category, pricePerKg);
            } else {
                addPacketItem(sc, items, productId, brand, category);
            }

            System.out.print("\nAdd another item? [1=Yes / 0=No]: ");
            String more = sc.nextLine().trim();
            if (!more.equals("1")) break;
        }

        if (items.isEmpty()) { System.out.println("No items. Bill cancelled."); return; }

        printBillPreview(items, customer, date);

        System.out.print("\nEdit any item price? [1=Yes / 0=No]: ");
        if (sc.nextLine().trim().equals("1")) {
            editItemPrice(sc, items);
            printBillPreview(items, customer, date);
        }

        System.out.print("Confirm and save bill? [1=Yes / 0=No]: ");
        if (sc.nextLine().trim().equals("1")) {
            saveBill(items, customer, date);
        } else {
            System.out.println("Bill cancelled. Stock not deducted.");
        }
    }

    private static void addWeightItem(java.util.Scanner sc, List<BillItem> items,
                                       int productId, String brand, String category,
                                       double pricePerKg) {
        List<double[]> bagSizes = ProductManager.getBagSizes(productId);
        if (bagSizes.isEmpty()) { System.out.println("  No bag sizes found."); return; }

        System.out.println("\n  Bag sizes:");
        for (int i = 0; i < bagSizes.size(); i++) {
            double[] b = bagSizes.get(i);
            String bp = b[2] > 0 ? String.format(" | Bag: Rs.%.2f", b[2]) : "";
            System.out.printf("    [%d] %.0fkg%s%n", i + 1, b[1], bp);
        }

        // Sell by — must be 1 or 0, keep asking if invalid
        int sellBy;
        while (true) {
            System.out.print("  Sell by [1=Bag / 0=Kg]: ");
            String sb = sc.nextLine().trim();
            if (Main.isExit(sb)) return;
            if (sb.equals("1")) { sellBy = 1; break; }
            if (sb.equals("0")) { sellBy = 0; break; }
            System.out.println("  [!] Invalid. Enter 1 for Bag or 0 for Kg.");
        }

        // Choose bag size
        int choice = Main.chooseBagSize(bagSizes);
        if (choice < 0) return;

        double[] bag = bagSizes.get(choice);
        int bagSizeId = (int) bag[0];
        double sizeKg = bag[1];
        double bagPrice = bag[2]; // -1 if none
        int sealedCount = (int) bag[3];

        if (sellBy == 1) {
            // Sell full sealed bags
            System.out.print("  How many bags: ");
            String countInput = sc.nextLine().trim();
            if (Main.isExit(countInput)) return;
            int count;
            try { count = Integer.parseInt(countInput); }
            catch (NumberFormatException e) { System.out.println("  [!] Invalid."); return; }

            double pricePerBag = bagPrice > 0 ? bagPrice : sizeKg * pricePerKg;
            ProductManager.deductSealedBags(bagSizeId, count);
            items.add(new BillItem(brand, category,
                String.format("%.0fkg bag", sizeKg),
                count, "bag", pricePerBag));

        } else {
            // Sell by kg — AUTO SPLIT if qty > bag size
            System.out.print("  Quantity in kg (e.g. 5 or 0.25): ");
            String qtyInput = sc.nextLine().trim();
            if (Main.isExit(qtyInput)) return;
            double qty;
            try { qty = Double.parseDouble(qtyInput); }
            catch (NumberFormatException e) { System.out.println("  [!] Invalid."); return; }

            // AUTO SPLIT: if qty >= sizeKg, calculate full bags + loose
            if (qty >= sizeKg && sealedCount > 0) {
                int fullBagsNeeded = (int) (qty / sizeKg);
                double looseKg = qty % sizeKg;
                double pricePerBag = bagPrice > 0 ? bagPrice : sizeKg * pricePerKg;

                System.out.println("\n  Auto-split detected:");
                System.out.printf("  %d full bag(s) of %.0fkg + %.3fkg loose%n",
                    fullBagsNeeded, sizeKg, looseKg);

                // Add full bags line item
                if (fullBagsNeeded > 0) {
                    ProductManager.deductSealedBags(bagSizeId, fullBagsNeeded);
                    items.add(new BillItem(brand, category,
                        String.format("%.0fkg bag", sizeKg),
                        fullBagsNeeded, "bag", pricePerBag));
                }

                // Add loose kg line item
                if (looseKg > 0.001) {
                    ProductManager.deductOpenedKg(bagSizeId, looseKg);
                    items.add(new BillItem(brand, category,
                        String.format("%.0fkg (loose)", sizeKg),
                        looseKg, "kg", pricePerKg));
                }

            } else {
                // Simple kg sale from opened bag
                ProductManager.deductOpenedKg(bagSizeId, qty);
                items.add(new BillItem(brand, category,
                    String.format("%.0fkg (loose)", sizeKg),
                    qty, "kg", pricePerKg));
            }
        }
    }

    private static void addPacketItem(java.util.Scanner sc, List<BillItem> items,
                                       int productId, String brand, String category) {
        String sql = "SELECT * FROM packet_stock WHERE product_id=?";
        List<String[]> packetInfo = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            System.out.println("\n  Packet sizes:");
            int i = 1;
            while (rs.next()) {
                System.out.printf("    [%d] %s | Rs.%.2f | Stock: %d%n",
                    i++, rs.getString("pack_size"),
                    rs.getDouble("price_per_unit"),
                    rs.getInt("unit_count"));
                packetInfo.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("pack_size"),
                    String.valueOf(rs.getDouble("price_per_unit"))
                });
            }
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); return; }

        if (packetInfo.isEmpty()) { System.out.println("  No packet sizes."); return; }

        int choice;
        while (true) {
            System.out.print("  Which packet? Enter []: ");
            String v = sc.nextLine().trim();
            if (Main.isExit(v)) return;
            try {
                choice = Integer.parseInt(v) - 1;
                if (choice >= 0 && choice < packetInfo.size()) break;
                System.out.println("  [!] Invalid. Enter from the list.");
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }

        System.out.print("  How many units: ");
        String countInput = sc.nextLine().trim();
        if (Main.isExit(countInput)) return;
        int count;
        try { count = Integer.parseInt(countInput); }
        catch (NumberFormatException e) { System.out.println("  [!] Invalid."); return; }

        String[] p = packetInfo.get(choice);
        ProductManager.deductPacketUnits(Integer.parseInt(p[0]), count);
        items.add(new BillItem(brand, category, p[1], count, "unit", Double.parseDouble(p[2])));
    }

    private static void editItemPrice(java.util.Scanner sc, List<BillItem> items) {
        while (true) {
            System.out.print("Enter item number to edit: ");
            String v = sc.nextLine().trim();
            if (Main.isExit(v)) return;
            try {
                int idx = Integer.parseInt(v) - 1;
                if (idx < 0 || idx >= items.size()) { System.out.println("  [!] Invalid item."); continue; }
                BillItem item = items.get(idx);
                System.out.printf("  Current price: Rs.%.2f | New price: ", item.priceUsed);
                String np = sc.nextLine().trim();
                if (Main.isExit(np)) return;
                double newPrice = Double.parseDouble(np);
                item.priceUsed = newPrice;
                item.lineTotal = item.quantity * newPrice;
                System.out.println("  Price updated.");
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    static void printBillPreview(List<BillItem> items, String customer, String date) {
        System.out.println("\n============================================");
        System.out.println("            SHOP - BILL RECEIPT            ");
        System.out.println("============================================");
        System.out.println("Date     : " + date);
        System.out.println("Customer : " + customer);
        System.out.println("--------------------------------------------");
        double total = 0;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).printLine(i + 1);
            total += items.get(i).lineTotal;
        }
        System.out.println("--------------------------------------------");
        System.out.printf("TOTAL    : Rs. %.2f%n", total);
        System.out.println("============================================");
    }

    private static void saveBill(List<BillItem> items, String customer, String date) {
        double total = items.stream().mapToDouble(i -> i.lineTotal).sum();
        String billSql = "INSERT INTO bills (customer_name, total_amount, bill_date) VALUES (?,?,?)";
        String itemSql = """
            INSERT INTO bill_items (bill_id, brand, category, description,
            quantity, unit, price_used, line_total) VALUES (?,?,?,?,?,?,?,?)
        """;
        try (Connection conn = Database.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, customer);
            ps.setDouble(2, total);
            ps.setString(3, date);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) return;
            int billId = keys.getInt(1);

            for (BillItem item : items) {
                PreparedStatement ps2 = conn.prepareStatement(itemSql);
                ps2.setInt(1, billId);
                ps2.setString(2, item.brand);
                ps2.setString(3, item.category);
                ps2.setString(4, item.description);
                ps2.setDouble(5, item.quantity);
                ps2.setString(6, item.unit);
                ps2.setDouble(7, item.priceUsed);
                ps2.setDouble(8, item.lineTotal);
                ps2.executeUpdate();
            }
            System.out.println("\nBill saved. Bill ID: " + billId);
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    public static void viewBillHistory() {
        String sql = "SELECT * FROM bills ORDER BY bill_date DESC LIMIT 20";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n========== RECENT BILLS ==========");
            boolean any = false;
            while (rs.next()) {
                any = true;
                int billId = rs.getInt("id");
                System.out.printf("%nBill #%d | %s | %s | Total: Rs.%.2f%n",
                    billId, rs.getString("bill_date"),
                    rs.getString("customer_name"),
                    rs.getDouble("total_amount"));

                String itemSql = "SELECT * FROM bill_items WHERE bill_id=?";
                PreparedStatement ps = conn.prepareStatement(itemSql);
                ps.setInt(1, billId);
                ResultSet ir = ps.executeQuery();
                while (ir.next()) {
                    System.out.printf("  - %s %s | %.3f %s x Rs.%.2f = Rs.%.2f%n",
                        ir.getString("brand"),
                        ir.getString("description"),
                        ir.getDouble("quantity"),
                        ir.getString("unit"),
                        ir.getDouble("price_used"),
                        ir.getDouble("line_total"));
                }
            }
            if (!any) System.out.println("No bills yet.");
            System.out.println("===================================");
        } catch (SQLException e) { System.out.println("[ERROR] " + e.getMessage()); }
    }
}
