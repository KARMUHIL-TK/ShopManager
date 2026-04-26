package com.shop;

import java.util.Scanner;
import java.util.List;
import java.sql.*;

public class Main {

    static Scanner sc = new Scanner(System.in);
    static final String EXIT = "exit";

    public static void main(String[] args) {
        Database.initialize();
        System.out.println("\n=== SHOP MANAGER ===");
        System.out.println("(Type 'exit' at any prompt to go back to main menu)");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> addNewProduct();
                case "2" -> ProductManager.viewAllStock();
                case "3" -> editStock();
                case "4" -> markOpened();
                case "5" -> BillingManager.startBilling(sc);
                case "6" -> BillingManager.viewBillHistory();
                case "7" -> ReturnManager.processReturn(sc);
                case "8" -> removeProduct();
                case "0" -> { running = false; System.out.println("Goodbye!"); }
                default  -> System.out.println("  [!] Invalid option. Try again.");
            }
        }
    }

    static void printMenu() {
        System.out.println("\n--- MAIN MENU ---");
        System.out.println("1. Add New Product");
        System.out.println("2. View All Stock");
        System.out.println("3. Edit Stock");
        System.out.println("4. Mark Bag as Opened");
        System.out.println("5. Generate Bill");
        System.out.println("6. View Bill History");
        System.out.println("7. Return Stock");
        System.out.println("8. Remove Product");
        System.out.println("0. Exit");
        System.out.print("Enter choice: ");
    }

    // ─── ADD NEW PRODUCT ──────────────────────────────────────────────────────

    static void addNewProduct() {
        while (true) {
            System.out.println("\n--- ADD NEW PRODUCT ---");
            System.out.println("Categories:");
            System.out.println("  1. Rice    2. Millets   3. Pulses");
            System.out.println("  4. Oil     5. Spices    6. Health Mix");

            String category;
            String productType;
            while (true) {
                System.out.print("Choose category [1-6]: ");
                String c = sc.nextLine().trim();
                if (isExit(c)) return;
                switch (c) {
                    case "1" -> { category = "Rice";       productType = "weight"; }
                    case "2" -> { category = "Millets";    productType = "weight"; }
                    case "3" -> { category = "Pulses";     productType = "weight"; }
                    case "4" -> { category = "Oil";        productType = "packet"; }
                    case "5" -> { category = "Spices";     productType = "packet"; }
                    case "6" -> { category = "Health Mix"; productType = "packet"; }
                    default  -> { System.out.println("  [!] Invalid. Enter 1-6."); continue; }
                }
                break;
            }

            String brand;
            while (true) {
                System.out.print("Brand name: ");
                brand = sc.nextLine().trim();
                if (isExit(brand)) return;
                if (!brand.isEmpty()) break;
                System.out.println("  [!] Brand name cannot be empty.");
            }

            String date = readDate();
            if (date == null) return;

            boolean saved;
            if (productType.equals("weight")) {
                saved = addWeightProduct(brand, category, date);
            } else {
                saved = addPacketProduct(brand, category, date);
            }

            if (!saved) return;

            // Add another product loop
            System.out.print("\nAdd another product? [1=Yes / 0=No]: ");
            String again = sc.nextLine().trim();
            if (!again.equals("1")) break;
        }
    }

    // ─── ADD WEIGHT PRODUCT ───────────────────────────────────────────────────

    static boolean addWeightProduct(String brand, String category, String date) {
        double pricePerKg = readPositiveDouble("Price per kg (Rs.): ");
        if (pricePerKg < 0) return false;

        List<Object[]> bagEntries = new java.util.ArrayList<>();

        while (true) {
            String prompt = bagEntries.isEmpty() ? "\nBag size in kg: " : "\nNext bag size in kg (0 to finish): ";
            System.out.print(prompt);
            String sizeInput = sc.nextLine().trim();
            if (isExit(sizeInput)) return false;

            double sizeKg;
            try { sizeKg = Double.parseDouble(sizeInput); }
            catch (NumberFormatException e) { System.out.println("  [!] Invalid. Enter a number."); continue; }

            if (sizeKg == 0) {
                if (bagEntries.isEmpty()) { System.out.println("  [!] Must enter at least one bag size."); continue; }
                break;
            }
            if (sizeKg < 0.1) { System.out.println("  [!] Must be at least 0.1 kg."); continue; }

            // Duplicate check
            boolean dup = false;
            for (Object[] ex : bagEntries) {
                if (Math.abs((double) ex[0] - sizeKg) < 0.01) { dup = true; break; }
            }
            if (dup) { System.out.printf("  [!] %.0fkg already entered.%n", sizeKg); continue; }

            // Bag price
            Double bagPrice = readBagPrice(pricePerKg);
            if (bagPrice != null && bagPrice == -1.0) return false;

            // Sealed count
            int sealed = readNonNegativeInt("  How many SEALED bags: ");
            if (sealed == Integer.MIN_VALUE) return false;

            // Opened count
            int openedCount = readOpenedBagCount();
            if (openedCount == Integer.MIN_VALUE) return false;

            if (sealed == 0 && openedCount == 0) {
                System.out.println("  [!] Both sealed and opened are 0. Try again.");
                continue;
            }

            // Opened kg — mandatory if opened > 0
            double openedKg = 0;
            if (openedCount > 0) {
                openedKg = readPositiveDouble("  Total kg in opened bags: ");
                if (openedKg < 0) return false;
            }

            bagEntries.add(new Object[]{sizeKg, bagPrice, sealed, openedCount, openedKg});
            System.out.printf("  [OK] %.0fkg: %d sealed%s%n", sizeKg, sealed,
                openedCount > 0 ? ", " + openedCount + " opened (" + openedKg + "kg)" : "");
        }

        int productId = ProductManager.addProduct(brand, category, "weight", pricePerKg, date);
        if (productId == -1) { System.out.println("  Failed to save."); return false; }

        for (Object[] e : bagEntries) {
            ProductManager.addBagSize(productId,
                (double) e[0], (Double) e[1], (int) e[2], (int) e[3], (double) e[4]);
        }
        System.out.println("\nProduct saved: " + brand + " | " + category);
        return true;
    }

    // ─── ADD PACKET PRODUCT ───────────────────────────────────────────────────

    static boolean addPacketProduct(String brand, String category, String date) {
        int productId = ProductManager.addProduct(brand, category, "packet", 0, date);
        if (productId == -1) { System.out.println("  Failed."); return false; }

        System.out.println("\nNow add packet sizes. (0 to finish)");
        while (true) {
            System.out.print("\nPack size (e.g. '1L bottle', '250g packet') or 0 to finish: ");
            String packSize = sc.nextLine().trim();
            if (isExit(packSize)) return false;
            if (packSize.equals("0")) break;
            if (packSize.isEmpty()) { System.out.println("  [!] Cannot be empty."); continue; }

            double price = readPositiveDouble("  Price per unit (Rs.): ");
            if (price < 0) return false;

            int units = readNonNegativeInt("  Units in stock: ");
            if (units == Integer.MIN_VALUE) return false;
            if (units == 0) { System.out.println("  [!] Units must be > 0."); continue; }

            ProductManager.addPacketStock(productId, packSize, price, units);
            System.out.println("  [OK] " + packSize + " x " + units + " @ Rs." + price);
        }
        System.out.println("\nProduct saved: " + brand + " | " + category);
        return true;
    }

    // ─── EDIT STOCK ───────────────────────────────────────────────────────────

    static void editStock() {
        ProductManager.viewAllStock();
        System.out.print("\nEnter Product ID to edit: ");
        String idInput = sc.nextLine().trim();
        if (isExit(idInput)) return;

        int productId;
        try { productId = Integer.parseInt(idInput); }
        catch (NumberFormatException e) { System.out.println("  [!] Invalid ID."); return; }

        String[] product = ProductManager.findProduct(productId);
        if (product == null) { System.out.println("  [!] Not found."); return; }

        String type = product[3];
        double pricePerKg = Double.parseDouble(product[4]);

        while (true) {
            System.out.println("\nEditing: [" + product[0] + "] " + product[1] + " | " + product[2]);
            System.out.println("  1. Brand name      2. Category");
            System.out.println("  3. Date            4. Price per kg");
            if (type.equals("weight")) {
                System.out.println("  5. Bag price       6. Sealed count");
                System.out.println("  7. Opened count    8. Opened total kg");
                System.out.println("  9. Add opened bag  10. Add new bag size");
                System.out.println("  11. Remove bag size");
            }
            System.out.print("Choice (or 'exit'): ");
            String ch = sc.nextLine().trim();
            if (isExit(ch)) return;

            switch (ch) {
                case "1" -> {
                    System.out.print("New brand name: ");
                    String v = sc.nextLine().trim();
                    if (isExit(v)) return;
                    if (v.isEmpty()) { System.out.println("  [!] Cannot be empty."); break; }
                    ProductManager.updateBrand(productId, v);
                    product[1] = v;
                }
                case "2" -> {
                    System.out.println("Categories: 1.Rice 2.Millets 3.Pulses 4.Oil 5.Spices 6.Health Mix");
                    while (true) {
                        System.out.print("Choose [1-6]: ");
                        String cv = sc.nextLine().trim();
                        if (isExit(cv)) return;
                        String newCat = switch (cv) {
                            case "1" -> "Rice"; case "2" -> "Millets"; case "3" -> "Pulses";
                            case "4" -> "Oil";  case "5" -> "Spices";  case "6" -> "Health Mix";
                            default -> null;
                        };
                        if (newCat == null) { System.out.println("  [!] Invalid."); continue; }
                        ProductManager.updateCategory(productId, newCat);
                        product[2] = newCat;
                        break;
                    }
                }
                case "3" -> {
                    String nd = readDate();
                    if (nd == null) return;
                    ProductManager.updateDate(productId, nd);
                }
                case "4" -> {
                    double np = readPositiveDouble("New price per kg (Rs.): ");
                    if (np < 0) return;
                    ProductManager.updatePrice(productId, np);
                    pricePerKg = np;
                    product[4] = String.valueOf(np);
                }
                case "5" -> { if (type.equals("weight")) editBagPrice(productId, pricePerKg); }
                case "6" -> { if (type.equals("weight")) editSealedCount(productId); }
                case "7" -> { if (type.equals("weight")) editOpenedCount(productId); }
                case "8" -> { if (type.equals("weight")) editOpenedKg(productId); }
                case "9" -> { if (type.equals("weight")) addOpenedBag(productId); }
                case "10" -> { if (type.equals("weight")) addNewBagSize(productId, pricePerKg); }
                case "11" -> { if (type.equals("weight")) removeBagSize(productId); }
                default  -> { System.out.println("  [!] Invalid. Try again."); continue; }
            }

            System.out.print("\nEdit another field? [1=Yes / 0=No]: ");
            if (!sc.nextLine().trim().equals("1")) break;
        }
    }

    static void editBagPrice(int productId, double pricePerKg) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;
        int bagSizeId = (int) bags.get(choice)[0];

        while (true) {
            System.out.print("  New bag price (0 to remove): Rs.");
            String v = sc.nextLine().trim();
            if (isExit(v)) return;
            try {
                double bp = Double.parseDouble(v);
                if (bp != 0 && bp < pricePerKg) {
                    System.out.printf("  [!] Cannot be less than Rs.%.2f.%n", pricePerKg);
                    continue;
                }
                ProductManager.updateBagPrice(bagSizeId, bp);
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    static void editSealedCount(int productId) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;
        int bagSizeId = (int) bags.get(choice)[0];
        double current = bags.get(choice)[3];

        while (true) {
            System.out.printf("  Current: %.0f | New value or +/- (e.g. 10, +5, -3): ", current);
            String v = sc.nextLine().trim();
            if (isExit(v)) return;
            try {
                if (v.startsWith("+")) ProductManager.addSealedBags(bagSizeId, Integer.parseInt(v.substring(1)));
                else if (v.startsWith("-")) ProductManager.addSealedBags(bagSizeId, -Integer.parseInt(v.substring(1)));
                else {
                    int nv = Integer.parseInt(v);
                    if (nv < 0) { System.out.println("  [!] Cannot be negative."); continue; }
                    ProductManager.setSealedCount(bagSizeId, nv);
                    System.out.println("  Sealed count updated.");
                }
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    static void editOpenedCount(int productId) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;
        int bagSizeId = (int) bags.get(choice)[0];

        while (true) {
            System.out.print("  New opened count (0 or more): ");
            String v = sc.nextLine().trim();
            if (isExit(v)) return;
            try {
                int nv = Integer.parseInt(v);
                if (nv < 0) { System.out.println("  [!] Cannot be negative."); continue; }
                ProductManager.setOpenedCount(bagSizeId, nv);
                System.out.println("  Opened count updated.");
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    static void editOpenedKg(int productId) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;
        int bagSizeId = (int) bags.get(choice)[0];

        while (true) {
            System.out.printf("  Current: %.1fkg | New total opened kg: ", bags.get(choice)[5]);
            String v = sc.nextLine().trim();
            if (isExit(v)) return;
            try {
                double nv = Double.parseDouble(v);
                if (nv < 0) { System.out.println("  [!] Cannot be negative."); continue; }
                ProductManager.setOpenedKg(bagSizeId, nv);
                System.out.println("  Opened kg updated.");
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    static void addOpenedBag(int productId) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;

        double[] bag = bags.get(choice);
        int bagSizeId = (int) bag[0];
        int currentOpened = (int) bag[4];
        double currentKg = bag[5];

        // Check previous opened bag
        boolean previousSoldOut = true;
        if (currentOpened > 0) {
            System.out.printf("  There are already %d opened bag(s) with %.1fkg remaining.%n",
                currentOpened, currentKg);
            System.out.print("  Have the previous opened bags been sold out? [1=Yes / 0=No]: ");
            String ans = sc.nextLine().trim();
            previousSoldOut = ans.equals("1");
        }

        // Auto deduct sealed
        System.out.print("  Auto deduct 1 from sealed count? [1=Yes / 0=No]: ");
        boolean autoDeduct = sc.nextLine().trim().equals("1");

        // Remaining kg — mandatory
        double remainingKg = readPositiveDouble("  Remaining kg in this opened bag: ");
        if (remainingKg < 0) return;

        ProductManager.openBag(bagSizeId, remainingKg, autoDeduct, previousSoldOut, currentOpened);
        System.out.println("  Opened bag added.");
    }

    static void addNewBagSize(int productId, double pricePerKg) {
        List<double[]> existing = ProductManager.getBagSizes(productId);
        double sizeKg;
        while (true) {
            System.out.print("  New bag size in kg: ");
            String v = sc.nextLine().trim();
            if (isExit(v)) return;
            try {
                sizeKg = Double.parseDouble(v);
                if (sizeKg < 0.1) { System.out.println("  [!] Must be at least 0.1 kg."); continue; }
                boolean dup = false;
                for (double[] ex : existing) {
                    if (Math.abs(ex[1] - sizeKg) < 0.01) { dup = true; break; }
                }
                if (dup) { System.out.printf("  [!] %.0fkg already exists.%n", sizeKg); continue; }
                break;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }

        Double bagPrice = readBagPrice(pricePerKg);
        if (bagPrice != null && bagPrice == -1.0) return;

        int sealed = readNonNegativeInt("  Sealed bags: ");
        if (sealed == Integer.MIN_VALUE) return;

        int openedCount = readOpenedBagCount();
        if (openedCount == Integer.MIN_VALUE) return;

        double openedKg = 0;
        if (openedCount > 0) {
            openedKg = readPositiveDouble("  Total kg in opened bags: ");
            if (openedKg < 0) return;
        }

        ProductManager.addBagSize(productId, sizeKg, bagPrice, sealed, openedCount, openedKg);
        System.out.printf("  [OK] %.0fkg bag size added.%n", sizeKg);
    }

    static void removeBagSize(int productId) {
        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }
        int choice = chooseBagSize(bags);
        if (choice < 0) return;
        int bagSizeId = (int) bags.get(choice)[0];
        double sizeKg = bags.get(choice)[1];
        System.out.printf("  Remove %.0fkg bag entry? [1=Yes / 0=No]: ", sizeKg);
        if (sc.nextLine().trim().equals("1")) ProductManager.deleteBagSize(bagSizeId);
        else System.out.println("  Cancelled.");
    }

    // ─── MARK BAG AS OPENED ───────────────────────────────────────────────────

    static void markOpened() {
        ProductManager.viewAllStock();
        System.out.print("\nEnter Product ID: ");
        String idInput = sc.nextLine().trim();
        if (isExit(idInput)) return;

        int productId;
        try { productId = Integer.parseInt(idInput); }
        catch (NumberFormatException e) { System.out.println("  [!] Invalid ID."); return; }

        List<double[]> bags = ProductManager.getBagSizes(productId);
        if (bags.isEmpty()) { System.out.println("  [!] No bag sizes."); return; }

        int choice = chooseBagSize(bags);
        if (choice < 0) return;

        double[] bag = bags.get(choice);
        int bagSizeId = (int) bag[0];
        int currentOpened = (int) bag[4];
        double currentKg = bag[5];

        // Check previous opened bag
        boolean previousSoldOut = true;
        if (currentOpened > 0) {
            System.out.printf("  There are already %d opened bag(s) with %.1fkg remaining.%n",
                currentOpened, currentKg);
            System.out.print("  Have the previous opened bags been sold out? [1=Yes / 0=No]: ");
            previousSoldOut = sc.nextLine().trim().equals("1");
        }

        // Auto deduct
        System.out.print("  Auto deduct 1 from sealed count? [1=Yes / 0=No]: ");
        boolean autoDeduct = sc.nextLine().trim().equals("1");

        double remainingKg = readPositiveDouble("  Remaining kg in this bag: ");
        if (remainingKg < 0) return;

        ProductManager.openBag(bagSizeId, remainingKg, autoDeduct, previousSoldOut, currentOpened);
        System.out.println("  Bag marked as opened.");
    }

    // ─── REMOVE PRODUCT ───────────────────────────────────────────────────────

    static void removeProduct() {
        ProductManager.viewAllStock();
        System.out.print("\nEnter Product ID to remove: ");
        String idInput = sc.nextLine().trim();
        if (isExit(idInput)) return;

        int productId;
        try { productId = Integer.parseInt(idInput); }
        catch (NumberFormatException e) { System.out.println("  [!] Invalid ID."); return; }

        String[] product = ProductManager.findProduct(productId);
        if (product == null) { System.out.println("  [!] Not found."); return; }

        System.out.printf("  Remove [%s | %s]? Bill history kept. [1=Yes / 0=No]: ",
            product[1], product[2]);
        if (sc.nextLine().trim().equals("1")) {
            ProductManager.deleteItem(productId);
            System.out.println("  Product removed.");
        } else {
            System.out.println("  Cancelled.");
        }
    }

    // ─── SHARED BAG SIZE CHOOSER ──────────────────────────────────────────────

    static int chooseBagSize(List<double[]> bags) {
        System.out.println("  Bag sizes:");
        for (int i = 0; i < bags.size(); i++) {
            double[] b = bags.get(i);
            System.out.printf("    [%d] %.0fkg | Sealed: %.0f | Opened: %s%n",
                i + 1, b[1], b[3],
                b[4] > 0 ? (int)b[4] + " bags (" + b[5] + "kg)" : "none");
        }
        while (true) {
            System.out.print("  Enter []: ");
            String input = sc.nextLine().trim();
            if (isExit(input)) return -1;
            try {
                int choice = Integer.parseInt(input) - 1;
                if (choice >= 0 && choice < bags.size()) return choice;
                System.out.println("  [!] Invalid. Enter a number from the list.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid. Enter a number.");
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    static boolean isExit(String input) {
        if (input.trim().equalsIgnoreCase(EXIT)) {
            System.out.println("  Cancelled. Back to main menu.");
            return true;
        }
        return false;
    }

    // Returns -1.0 as exit signal
    static double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (isExit(input)) return -1.0;
            try {
                double val = Double.parseDouble(input);
                if (val > 0) return val;
                System.out.println("  [!] Must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid. Enter a number.");
            }
        }
    }

    // Returns Integer.MIN_VALUE as exit signal
    static int readNonNegativeInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (isExit(input)) return Integer.MIN_VALUE;
            try {
                int val = Integer.parseInt(input);
                if (val >= 0) return val;
                System.out.println("  [!] Cannot be negative.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Invalid. Enter a whole number.");
            }
        }
    }

    // Returns null=no price, -1.0=exit signal
    static Double readBagPrice(double pricePerKg) {
        while (true) {
            System.out.print("  Separate bag price (0 if none): Rs.");
            String v = sc.nextLine().trim();
            if (isExit(v)) return -1.0;
            try {
                double bp = Double.parseDouble(v);
                if (bp == 0) return null;
                if (bp < pricePerKg) {
                    System.out.printf("  [!] Cannot be less than Rs.%.2f (price per kg).%n", pricePerKg);
                    continue;
                }
                return bp;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    // Returns Integer.MIN_VALUE as exit signal, handles >5 reconfirmation
    static int readOpenedBagCount() {
        while (true) {
            System.out.print("  How many OPENED bags (0 if none): ");
            String input = sc.nextLine().trim();
            if (isExit(input)) return Integer.MIN_VALUE;
            try {
                int count = Integer.parseInt(input);
                if (count < 0) { System.out.println("  [!] Cannot be negative."); continue; }
                if (count > 5) {
                    System.out.printf("  You entered %d opened bags. Confirm? [1=Yes / 0=No]: ", count);
                    if (!sc.nextLine().trim().equals("1")) { System.out.println("  Re-enter."); continue; }
                }
                return count;
            } catch (NumberFormatException e) { System.out.println("  [!] Invalid."); }
        }
    }

    // Returns null if exit
    static String readDate() {
        while (true) {
            System.out.print("Date (DD-MM-YYYY): ");
            String date = sc.nextLine().trim();
            if (isExit(date)) return null;
            String[] parts = date.split("-");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    if (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 2000 && y <= 2100)
                        return date;
                } catch (NumberFormatException ignored) {}
            }
            System.out.println("  [!] Invalid. Use DD-MM-YYYY (e.g. 25-04-2026).");
        }
    }

    static boolean readYesNo(String question) {
        while (true) {
            System.out.print(question + " [1=Yes / 0=No]: ");
            String input = sc.nextLine().trim();
            if (input.equals("1")) return true;
            if (input.equals("0")) return false;
            System.out.println("  [!] Enter 1 or 0.");
        }
    }

    static int readInt() {
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (NumberFormatException e) {
            System.out.print("Invalid. Enter again: ");
            return readInt();
        }
    }
}
