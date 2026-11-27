package com.shop;

import com.shop.ShopService.*;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Simple tests for the shop logic. No extra libraries needed.
 *
 * Each check() line says WHAT should be true. If something is wrong the
 * test prints FAIL and the program ends with an error.
 *
 * It uses its own throw-away database file (test-shop.db), so your real
 * shop.db is never touched.
 *
 * Run (Windows):
 *   javac -cp "lib/sqlite-jdbc-3.53.0.0.jar" -d out src/com/shop/*.java test/com/shop/*.java
 *   java -Dshop.db=test-shop.db -cp "out;lib/sqlite-jdbc-3.53.0.0.jar" com.shop.ShopServiceTest
 */
public class ShopServiceTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean condition) {
        if (condition) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    /** Passes only if the code throws a ShopException. Returns its message. */
    static String expectError(String name, Runnable action) {
        try {
            action.run();
        } catch (ShopException e) {
            check(name, true);
            return e.getMessage();
        }
        check(name + " (expected an error, but none happened)", false);
        return "";
    }

    static BagSize bag(int productId) { return ShopService.getBagSizes(productId).get(0); }
    static Packet packet(int productId) { return ShopService.getPackets(productId).get(0); }

    public static void main(String[] args) {
        String file = System.getProperty("shop.db", "test-shop.db");
        if (file.equals("shop.db")) {
            System.out.println("Refusing to run on the real shop.db. Use -Dshop.db=test-shop.db");
            System.exit(2);
        }
        new File(file).delete();
        Database.initialize();

        // Rice: Rs.50/kg, one 26kg bag size, bag price Rs.1200, 5 sealed, 1 opened bag with 10kg loose
        int rice = ShopService.createWeightProduct("TestRice", "Rice", 50, "01-01-2026",
                26, 1200.0, 5, 1, 10);
        // Oil: 1L bottle at Rs.150, 10 units
        int oil = ShopService.createPacketProduct("TestOil", "Oil", "01-01-2026", "1L bottle", 150, 10);

        Product riceP = ShopService.getProduct(rice);
        Product oilP = ShopService.getProduct(oil);

        System.out.println("\n== Planning a sale (must NOT change stock) ==");
        List<BillLine> split = ShopService.planKgSale(riceP, bag(rice), 30);
        check("30 kg from 26 kg bags auto-splits into 2 lines", split.size() == 2);
        check("first line is 1 full bag at the bag price Rs.1200",
                split.get(0).quantity == 1 && split.get(0).unit.equals("bag") && split.get(0).price == 1200);
        check("second line is 4 kg loose at Rs.50/kg",
                Math.abs(split.get(1).quantity - 4) < 0.0001 && split.get(1).unit.equals("kg") && split.get(1).price == 50);
        check("planning did not change sealed stock", bag(rice).sealed == 5);
        check("planning did not change loose kg", bag(rice).openedKg == 10);

        List<BillLine> small = ShopService.planKgSale(riceP, bag(rice), 2.5);
        check("2.5 kg (less than a bag) is one loose line", small.size() == 1 && small.get(0).total() == 125);

        System.out.println("\n== Saving a bill ==");
        int billId = ShopService.saveBill("Ravi", "25-11-2026", split);
        check("bill saved with an id", billId > 0);
        check("sealed bags went from 5 to 4", bag(rice).sealed == 4);
        check("loose kg went from 10 to 6", Math.abs(bag(rice).openedKg - 6) < 0.0001);
        List<BillLine> saved = ShopService.getBillItems(billId);
        check("bill has 2 items saved", saved.size() == 2);
        check("bill total is 1200 + 4 x 50 = 1400", ShopService.listBills(10).get(0).total == 1400);

        System.out.println("\n== Cannot sell more than the stock ==");
        List<BillLine> tooMany = ShopService.planBagSale(riceP, bag(rice), 9);
        String msg = expectError("selling 9 bags when only 4 exist is refused",
                () -> ShopService.saveBill("X", "25-11-2026", tooMany));
        check("the message says how many are in stock", msg.contains("4"));
        check("stock is unchanged after the refused sale", bag(rice).sealed == 4);
        check("no bill was created by the refused sale", ShopService.listBills(10).size() == 1);

        expectError("selling 8 kg loose when only 6 kg is opened is refused",
                () -> ShopService.saveBill("X", "25-11-2026", ShopService.planKgSale(riceP, bag(rice), 8)));

        System.out.println("\n== A bill is all-or-nothing ==");
        List<BillLine> mixed = new java.util.ArrayList<>(ShopService.planBagSale(riceP, bag(rice), 1));
        mixed.addAll(ShopService.planPacketSale(oilP, packet(oil), 50));   // oil: only 10 exist
        expectError("a bill with one good item and one impossible item is refused",
                () -> ShopService.saveBill("X", "25-11-2026", mixed));
        check("the good item was NOT deducted either (rollback)", bag(rice).sealed == 4);

        System.out.println("\n== Same product added twice on one bill ==");
        List<BillLine> twice = new java.util.ArrayList<>(ShopService.planBagSale(riceP, bag(rice), 3));
        twice.addAll(ShopService.planBagSale(riceP, bag(rice), 3));   // 6 in total, only 4 exist
        expectError("3 + 3 bags is refused because the total (6) is more than 4",
                () -> ShopService.saveBill("X", "25-11-2026", twice));
        check("stock still 4", bag(rice).sealed == 4);

        System.out.println("\n== Packets ==");
        ShopService.saveBill("", "26-11-2026", ShopService.planPacketSale(oilP, packet(oil), 4));
        check("oil went from 10 to 6", packet(oil).units == 6);
        check("empty customer name becomes Walk-in", ShopService.listBills(1).get(0).customer.equals("Walk-in"));

        System.out.println("\n== Bill history order (newest first) ==");
        ShopService.saveBill("Old date", "05-12-2025", ShopService.planPacketSale(oilP, packet(oil), 1));
        check("the bill saved last is shown first, even with an older date",
                ShopService.listBills(1).get(0).customer.equals("Old date"));

        System.out.println("\n== Loose stock running out ==");
        // 6 kg loose left: sell all of it
        ShopService.saveBill("Last kg", "27-11-2026", ShopService.planKgSale(riceP, bag(rice), 6));
        check("loose kg is now 0", bag(rice).openedKg < 0.0005);
        check("opened bag count goes back to 0", bag(rice).openedCount == 0);
        String noLoose = expectError("selling loose kg with no opened bag is refused",
                () -> ShopService.saveBill("X", "27-11-2026", ShopService.planKgSale(riceP, bag(rice), 1)));
        check("the message tells the user to open a bag", noLoose.toLowerCase().contains("open a bag"));

        System.out.println("\n== Opening a bag ==");
        ShopService.openBag(bag(rice).id, 26, true, false);
        check("sealed went from 4 to 3", bag(rice).sealed == 3);
        check("opened kg is now 26", bag(rice).openedKg == 26);
        check("opened count is 1", bag(rice).openedCount == 1);
        expectError("an opened bag cannot hold more than the bag size",
                () -> ShopService.openBag(bag(rice).id, 40, false, false));

        System.out.println("\n== Returns ==");
        ShopService.returnSealedBags(bag(rice).id, 2);
        check("2 sealed bags returned: 3 -> 5", bag(rice).sealed == 5);
        ShopService.returnOpenedKg(bag(rice).id, 3.5);
        check("3.5 kg returned: 26 -> 29.5", Math.abs(bag(rice).openedKg - 29.5) < 0.0001);
        ShopService.returnPackets(packet(oil).id, 2);
        check("2 oil bottles returned: 5 -> 7", packet(oil).units == 7);
        check("3 returns were logged", ShopService.listReturns(10).size() == 3);
        expectError("returning 0 bags is refused", () -> ShopService.returnSealedBags(bag(rice).id, 0));

        System.out.println("\n== Checking inputs ==");
        expectError("bag price lower than the price of 1 kg is refused",
                () -> ShopService.createWeightProduct("Bad", "Rice", 60, "01-01-2026", 10, 30.0, 1, 0, 0));
        // A bag price below size x price per kg IS allowed (a discount for buying a whole bag).
        int discounted = ShopService.createWeightProduct("Disc", "Rice", 60, "01-01-2026", 10, 550.0, 1, 0, 0);
        check("a discounted bag price (550 for 10 kg at 60/kg) is accepted", discounted > 0);
        expectError("empty brand name is refused",
                () -> ShopService.createPacketProduct("  ", "Oil", "01-01-2026", "1L", 100, 1));
        expectError("a wrong date is refused",
                () -> ShopService.saveBill("X", "2026-11-25", ShopService.planPacketSale(oilP, packet(oil), 1)));
        expectError("an empty bill is refused",
                () -> ShopService.saveBill("X", "25-11-2026", new java.util.ArrayList<>()));
        expectError("a second 26 kg size for the same product is refused",
                () -> ShopService.addBagSize(rice, 26, null, 1, 0, 0));

        System.out.println("\n== Editing and removing ==");
        ShopService.updateProduct(rice, "TestRice2", "Rice", 55);
        check("brand and price per kg updated",
                ShopService.getProduct(rice).brand.equals("TestRice2") && ShopService.getProduct(rice).pricePerKg == 55);
        ShopService.updateBagSize(bag(rice).id, null, 7, 0, 0);
        check("bag price removed and sealed count set to 7",
                bag(rice).bagPrice == null && bag(rice).sealed == 7);
        check("with no bag price, a bag costs 26 x 55 = 1430",
                bag(rice).priceOfBag(55) == 1430);
        int billsBefore = ShopService.listBills(100).size();
        ShopService.deleteProduct(oil);
        check("product removed", ShopService.getProduct(oil) == null);
        check("old bills are kept after removing a product", ShopService.listBills(100).size() == billsBefore);

        System.out.println("\nResult: " + passed + " passed, " + failed + " failed");
        new File(file).delete();
        if (failed > 0) System.exit(1);
    }
}
