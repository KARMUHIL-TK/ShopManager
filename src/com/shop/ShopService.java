package com.shop;

import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All shop logic in one place, with no printing and no Scanner, so the
 * Swing screens (and the tests) can use it directly.
 *
 * Rules that this class guarantees:
 *  - Stock only changes when a bill is SAVED (never while it is being built).
 *  - A bill is saved in ONE database transaction: either everything is
 *    saved, or nothing is.
 *  - You cannot sell more than the stock you have.
 */
public class ShopService {

    /** Any problem the user should be told about (shown in a dialog box). */
    public static class ShopException extends RuntimeException {
        public ShopException(String message) { super(message); }
    }

    // ───────────────────────── data classes ─────────────────────────

    public static class Product {
        public final int id;
        public final String brand, category, type;   // type = "weight" or "packet"
        public final double pricePerKg;

        public Product(int id, String brand, String category, String type, double pricePerKg) {
            this.id = id; this.brand = brand; this.category = category;
            this.type = type; this.pricePerKg = pricePerKg;
        }
        public boolean isWeight() { return type.equals("weight"); }
        @Override public String toString() { return brand + " (" + category + ")"; }
    }

    public static class BagSize {
        public final int id, productId;
        public final double sizeKg;
        public final Double bagPrice;        // null = no separate bag price
        public final int sealed, openedCount;
        public final double openedKg;

        public BagSize(int id, int productId, double sizeKg, Double bagPrice,
                       int sealed, int openedCount, double openedKg) {
            this.id = id; this.productId = productId; this.sizeKg = sizeKg;
            this.bagPrice = bagPrice; this.sealed = sealed;
            this.openedCount = openedCount; this.openedKg = openedKg;
        }
        /** Price of one sealed bag: the bag price if set, otherwise size x price per kg. */
        public double priceOfBag(double pricePerKg) {
            return bagPrice != null ? bagPrice : sizeKg * pricePerKg;
        }
        @Override public String toString() { return fmt(sizeKg) + " kg bag"; }
    }

    public static class Packet {
        public final int id, productId, units;
        public final String packSize;
        public final double price;

        public Packet(int id, int productId, String packSize, double price, int units) {
            this.id = id; this.productId = productId; this.packSize = packSize;
            this.price = price; this.units = units;
        }
        @Override public String toString() { return packSize; }
    }

    /** Where a bill line takes its stock from. */
    public enum Source { SEALED_BAG, OPENED_KG, PACKET, NONE }

    public static class BillLine {
        public final String brand, category, description, unit;
        public final double quantity;
        public double price;                 // can be edited before saving
        public final Source source;
        public final int targetId;           // bag_sizes.id or packet_stock.id

        public BillLine(String brand, String category, String description, double quantity,
                        String unit, double price, Source source, int targetId) {
            this.brand = brand; this.category = category; this.description = description;
            this.quantity = quantity; this.unit = unit; this.price = price;
            this.source = source; this.targetId = targetId;
        }
        public double total() { return quantity * price; }
    }

    public static class BillRow {
        public final int id;
        public final String customer, date;
        public final double total;
        public BillRow(int id, String customer, String date, double total) {
            this.id = id; this.customer = customer; this.date = date; this.total = total;
        }
    }

    public static class ReturnRow {
        public final String date, brand, type, unit;
        public final double sizeKg, quantity;
        public ReturnRow(String date, String brand, String type, double sizeKg,
                         double quantity, String unit) {
            this.date = date; this.brand = brand; this.type = type;
            this.sizeKg = sizeKg; this.quantity = quantity; this.unit = unit;
        }
    }

    // ───────────────────────── small helpers ─────────────────────────

    private static final DecimalFormat KG = new DecimalFormat("0.###");

    /** 26.0 -> "26", 0.5 -> "0.5" */
    public static String fmt(double number) { return KG.format(number); }

    public static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private static boolean validDate(String date) {
        if (date == null) return false;
        String[] p = date.trim().split("-");
        if (p.length != 3) return false;
        try {
            int d = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]);
            return d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 2000 && y <= 2100;
        } catch (NumberFormatException e) { return false; }
    }

    private static ShopException dbError(SQLException e) {
        return new ShopException("Database error: " + e.getMessage());
    }

    // ───────────────────────── reading stock ─────────────────────────

    public static List<Product> listProducts() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY category, brand";
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(readProduct(rs));
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }

    public static Product getProduct(int id) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM products WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? readProduct(rs) : null;
        } catch (SQLException e) { throw dbError(e); }
    }

    private static Product readProduct(ResultSet rs) throws SQLException {
        return new Product(rs.getInt("id"), rs.getString("brand"), rs.getString("category"),
                rs.getString("product_type"), rs.getDouble("price_per_kg"));
    }

    public static List<BagSize> getBagSizes(int productId) {
        List<BagSize> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM bag_sizes WHERE product_id=? ORDER BY size_kg")) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Double bagPrice = rs.getObject("bag_price") != null ? rs.getDouble("bag_price") : null;
                list.add(new BagSize(rs.getInt("id"), productId, rs.getDouble("size_kg"), bagPrice,
                        rs.getInt("sealed_count"), rs.getInt("opened_count"),
                        rs.getDouble("opened_total_kg")));
            }
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }

    public static List<Packet> getPackets(int productId) {
        List<Packet> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM packet_stock WHERE product_id=? ORDER BY pack_size")) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Packet(rs.getInt("id"), productId, rs.getString("pack_size"),
                        rs.getDouble("price_per_unit"), rs.getInt("unit_count")));
            }
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }

    // ───────────────────────── building a bill (NO stock change) ─────────────────────────

    /** Sell whole sealed bags. */
    public static List<BillLine> planBagSale(Product p, BagSize b, int bags) {
        if (bags <= 0) throw new ShopException("Number of bags must be more than 0.");
        List<BillLine> lines = new ArrayList<>();
        lines.add(new BillLine(p.brand, p.category, fmt(b.sizeKg) + "kg bag", bags, "bag",
                b.priceOfBag(p.pricePerKg), Source.SEALED_BAG, b.id));
        return lines;
    }

    /**
     * Sell by kg. If the kg is a full bag or more and sealed bags exist, the sale is
     * AUTO-SPLIT: full bags at the bag price + the rest as loose kg.
     * Example: 30 kg from 26 kg bags = 1 bag + 4 kg loose.
     */
    public static List<BillLine> planKgSale(Product p, BagSize b, double kg) {
        if (kg <= 0) throw new ShopException("Quantity must be more than 0.");
        List<BillLine> lines = new ArrayList<>();
        if (kg >= b.sizeKg && b.sealed > 0) {
            int fullBags = (int) (kg / b.sizeKg + 1e-9);
            double loose = Math.round((kg - fullBags * b.sizeKg) * 1000.0) / 1000.0;
            if (fullBags > 0) {
                lines.add(new BillLine(p.brand, p.category, fmt(b.sizeKg) + "kg bag", fullBags,
                        "bag", b.priceOfBag(p.pricePerKg), Source.SEALED_BAG, b.id));
            }
            if (loose > 0.001) {
                lines.add(new BillLine(p.brand, p.category, fmt(b.sizeKg) + "kg (loose)", loose,
                        "kg", p.pricePerKg, Source.OPENED_KG, b.id));
            }
        } else {
            lines.add(new BillLine(p.brand, p.category, fmt(b.sizeKg) + "kg (loose)", kg,
                    "kg", p.pricePerKg, Source.OPENED_KG, b.id));
        }
        return lines;
    }

    public static List<BillLine> planPacketSale(Product p, Packet pk, int units) {
        if (units <= 0) throw new ShopException("Number of units must be more than 0.");
        List<BillLine> lines = new ArrayList<>();
        lines.add(new BillLine(p.brand, p.category, pk.packSize, units, "unit",
                pk.price, Source.PACKET, pk.id));
        return lines;
    }

    // ───────────────────────── saving a bill ─────────────────────────

    /**
     * Saves the bill and takes the stock out, all in one transaction.
     * Throws ShopException (and changes NOTHING) if any item is short of stock.
     * @return the new bill id
     */
    public static int saveBill(String customer, String date, List<BillLine> lines) {
        if (lines == null || lines.isEmpty()) throw new ShopException("The bill has no items.");
        if (!validDate(date)) throw new ShopException("Date must look like 25-04-2026.");
        if (customer == null || customer.trim().isEmpty()) customer = "Walk-in";
        for (BillLine l : lines) {
            if (l.price < 0) throw new ShopException("A price cannot be negative.");
        }

        Needs need = needsOf(lines);

        double total = 0;
        for (BillLine l : lines) total += l.total();

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                verifyStock(c, need);            // refuse BEFORE changing anything

                for (Map.Entry<Integer, Double> e : need.sealed.entrySet()) {
                    update(c, "UPDATE bag_sizes SET sealed_count = sealed_count - ? WHERE id=?",
                            e.getValue().intValue(), e.getKey());
                }
                for (Map.Entry<Integer, Double> e : need.opened.entrySet()) {
                    update(c, "UPDATE bag_sizes SET opened_total_kg = MAX(0, opened_total_kg - ?) WHERE id=?",
                            e.getValue(), e.getKey());
                    // When no loose kg are left, there is no opened bag any more.
                    update(c, "UPDATE bag_sizes SET opened_count = 0 WHERE id=? AND opened_total_kg < 0.0005",
                            e.getKey());
                }
                for (Map.Entry<Integer, Double> e : need.packets.entrySet()) {
                    update(c, "UPDATE packet_stock SET unit_count = unit_count - ? WHERE id=?",
                            e.getValue().intValue(), e.getKey());
                }

                int billId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bills (customer_name, total_amount, bill_date) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, customer.trim());
                    ps.setDouble(2, total);
                    ps.setString(3, date.trim());
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    if (!keys.next()) throw new ShopException("Could not save the bill.");
                    billId = keys.getInt(1);
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO bill_items (bill_id, brand, category, description,
                        quantity, unit, price_used, line_total) VALUES (?,?,?,?,?,?,?,?)""")) {
                    for (BillLine l : lines) {
                        ps.setInt(1, billId);
                        ps.setString(2, l.brand);
                        ps.setString(3, l.category);
                        ps.setString(4, l.description);
                        ps.setDouble(5, l.quantity);
                        ps.setString(6, l.unit);
                        ps.setDouble(7, l.price);
                        ps.setDouble(8, l.total());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return billId;
            } catch (RuntimeException | SQLException e) {
                c.rollback();            // undo everything, stock stays as it was
                throw e;
            }
        } catch (SQLException e) { throw dbError(e); }
    }

    /** How much of each stock row a bill needs (the same product may appear on several lines). */
    private static class Needs {
        final Map<Integer, Double> sealed = new LinkedHashMap<>();
        final Map<Integer, Double> opened = new LinkedHashMap<>();
        final Map<Integer, Double> packets = new LinkedHashMap<>();
        final Map<String, String> label = new LinkedHashMap<>();
    }

    private static Needs needsOf(List<BillLine> lines) {
        Needs n = new Needs();
        for (BillLine l : lines) {
            Map<Integer, Double> target = switch (l.source) {
                case SEALED_BAG -> n.sealed;
                case OPENED_KG -> n.opened;
                case PACKET -> n.packets;
                case NONE -> null;
            };
            if (target != null) {
                target.merge(l.targetId, l.quantity, Double::sum);
                n.label.put(l.source + ":" + l.targetId, l.brand + " " + l.description);
            }
        }
        return n;
    }

    /** Throws ShopException if the stock in the database is not enough. Changes nothing. */
    private static void verifyStock(Connection c, Needs n) throws SQLException {
        for (Map.Entry<Integer, Double> e : n.sealed.entrySet()) {
            double have = readNumber(c, "SELECT sealed_count FROM bag_sizes WHERE id=?", e.getKey());
            if (have < e.getValue() - 1e-9) {
                throw new ShopException("Not enough sealed bags of "
                        + n.label.get("SEALED_BAG:" + e.getKey()) + ". In stock: "
                        + fmt(have) + ", needed: " + fmt(e.getValue()) + ".");
            }
        }
        for (Map.Entry<Integer, Double> e : n.opened.entrySet()) {
            double have = readNumber(c, "SELECT opened_total_kg FROM bag_sizes WHERE id=?", e.getKey());
            if (have < e.getValue() - 1e-9) {
                throw new ShopException("Not enough opened (loose) stock of "
                        + n.label.get("OPENED_KG:" + e.getKey()) + ". Loose kg available: "
                        + fmt(have) + ", needed: " + fmt(e.getValue())
                        + ". Open a bag first (Stock screen).");
            }
        }
        for (Map.Entry<Integer, Double> e : n.packets.entrySet()) {
            double have = readNumber(c, "SELECT unit_count FROM packet_stock WHERE id=?", e.getKey());
            if (have < e.getValue() - 1e-9) {
                throw new ShopException("Not enough units of "
                        + n.label.get("PACKET:" + e.getKey()) + ". In stock: "
                        + fmt(have) + ", needed: " + fmt(e.getValue()) + ".");
            }
        }
    }

    /**
     * Checks the whole list of lines against the stock WITHOUT changing anything.
     * The bill screen uses this to warn you as soon as you add an item.
     */
    public static void checkStock(List<BillLine> lines) {
        try (Connection c = Database.getConnection()) {
            verifyStock(c, needsOf(lines));
        } catch (SQLException e) { throw dbError(e); }
    }

    private static double readNumber(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new ShopException("An item on this bill was removed from stock.");
            return rs.getDouble(1);
        }
    }

    private static void update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    // ───────────────────────── bill history ─────────────────────────

    /** Newest bill first. (Sorted by id, because dates are stored as DD-MM-YYYY text.) */
    public static List<BillRow> listBills(int limit) {
        List<BillRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, customer_name, total_amount, bill_date FROM bills ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new BillRow(rs.getInt(1), rs.getString(2), rs.getString(4), rs.getDouble(3)));
            }
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }

    public static List<BillLine> getBillItems(int billId) {
        List<BillLine> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM bill_items WHERE bill_id=? ORDER BY id")) {
            ps.setInt(1, billId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new BillLine(rs.getString("brand"), rs.getString("category"),
                        rs.getString("description"), rs.getDouble("quantity"), rs.getString("unit"),
                        rs.getDouble("price_used"), Source.NONE, 0));
            }
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }

    // ───────────────────────── adding / editing stock ─────────────────────────

    public static final String[] CATEGORIES =
            {"Rice", "Millets", "Pulses", "Oil", "Spices", "Health Mix"};

    /** Rice, Millets and Pulses are sold by weight; the rest are packets. */
    public static boolean isWeightCategory(String category) {
        return category.equals("Rice") || category.equals("Millets") || category.equals("Pulses");
    }

    private static String requireText(String text, String what) {
        if (text == null || text.trim().isEmpty()) throw new ShopException(what + " cannot be empty.");
        return text.trim();
    }

    private static void checkBagFields(double pricePerKg, double sizeKg, Double bagPrice,
                                       int sealed, int openedCount, double openedKg) {
        if (sizeKg < 0.1) throw new ShopException("Bag size must be at least 0.1 kg.");
        if (bagPrice != null && bagPrice < pricePerKg) {
            throw new ShopException("Bag price cannot be less than the price per kg (Rs. "
                    + fmt(pricePerKg) + ").");
        }
        if (sealed < 0 || openedCount < 0 || openedKg < 0) throw new ShopException("Numbers cannot be negative.");
        if (openedCount > 0 && openedKg <= 0) {
            throw new ShopException("Enter the loose kg that are in the opened bag(s).");
        }
    }

    /** Creates a weight product (rice, millets, pulses) with its first bag size. */
    public static int createWeightProduct(String brand, String category, double pricePerKg,
                                          String date, double sizeKg, Double bagPrice,
                                          int sealed, int openedCount, double openedKg) {
        brand = requireText(brand, "Brand name");
        if (!validDate(date)) throw new ShopException("Date must look like 25-04-2026.");
        if (pricePerKg <= 0) throw new ShopException("Price per kg must be more than 0.");
        checkBagFields(pricePerKg, sizeKg, bagPrice, sealed, openedCount, openedKg);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id = insertProduct(c, brand, category, "weight", pricePerKg, date);
                insertBagSize(c, id, sizeKg, bagPrice, sealed, openedCount, openedKg);
                c.commit();
                return id;
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    /** Creates a packet product (oil, spices, health mix) with its first pack size. */
    public static int createPacketProduct(String brand, String category, String date,
                                          String packSize, double price, int units) {
        brand = requireText(brand, "Brand name");
        packSize = requireText(packSize, "Pack size");
        if (!validDate(date)) throw new ShopException("Date must look like 25-04-2026.");
        if (price <= 0) throw new ShopException("Price must be more than 0.");
        if (units < 0) throw new ShopException("Units cannot be negative.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id = insertProduct(c, brand, category, "packet", 0, date);
                insertPacket(c, id, packSize, price, units);
                c.commit();
                return id;
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    private static int insertProduct(Connection c, String brand, String category, String type,
                                     double pricePerKg, String date) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO products (brand, category, product_type, price_per_kg, date_added) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, brand);
            ps.setString(2, category);
            ps.setString(3, type);
            ps.setDouble(4, pricePerKg);
            ps.setString(5, date.trim());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        }
    }

    private static void insertBagSize(Connection c, int productId, double sizeKg, Double bagPrice,
                                      int sealed, int openedCount, double openedKg) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO bag_sizes (product_id, size_kg, bag_price, sealed_count,
                opened_count, opened_total_kg) VALUES (?,?,?,?,?,?)""")) {
            ps.setInt(1, productId);
            ps.setDouble(2, sizeKg);
            if (bagPrice != null) ps.setDouble(3, bagPrice); else ps.setNull(3, Types.REAL);
            ps.setInt(4, sealed);
            ps.setInt(5, openedCount);
            ps.setDouble(6, openedKg);
            ps.executeUpdate();
        }
    }

    private static void insertPacket(Connection c, int productId, String packSize,
                                     double price, int units) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO packet_stock (product_id, pack_size, price_per_unit, unit_count) VALUES (?,?,?,?)")) {
            ps.setInt(1, productId);
            ps.setString(2, packSize);
            ps.setDouble(3, price);
            ps.setInt(4, units);
            ps.executeUpdate();
        }
    }

    public static void addBagSize(int productId, double sizeKg, Double bagPrice,
                                  int sealed, int openedCount, double openedKg) {
        Product p = getProduct(productId);
        if (p == null || !p.isWeight()) throw new ShopException("Bag sizes can only be added to weight products.");
        for (BagSize b : getBagSizes(productId)) {
            if (Math.abs(b.sizeKg - sizeKg) < 0.01) {
                throw new ShopException(fmt(sizeKg) + " kg already exists for this product.");
            }
        }
        checkBagFields(p.pricePerKg, sizeKg, bagPrice, sealed, openedCount, openedKg);
        try (Connection c = Database.getConnection()) {
            insertBagSize(c, productId, sizeKg, bagPrice, sealed, openedCount, openedKg);
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void addPacket(int productId, String packSize, double price, int units) {
        packSize = requireText(packSize, "Pack size");
        if (price <= 0) throw new ShopException("Price must be more than 0.");
        if (units < 0) throw new ShopException("Units cannot be negative.");
        for (Packet pk : getPackets(productId)) {
            if (pk.packSize.equalsIgnoreCase(packSize)) {
                throw new ShopException("\"" + packSize + "\" already exists for this product.");
            }
        }
        try (Connection c = Database.getConnection()) {
            insertPacket(c, productId, packSize, price, units);
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void updateProduct(int id, String brand, String category, double pricePerKg) {
        brand = requireText(brand, "Brand name");
        Product p = getProduct(id);
        if (p == null) throw new ShopException("Product not found.");
        if (p.isWeight()) {
            if (pricePerKg <= 0) throw new ShopException("Price per kg must be more than 0.");
            for (BagSize b : getBagSizes(id)) {
                if (b.bagPrice != null && b.bagPrice < pricePerKg) {
                    throw new ShopException("The " + fmt(b.sizeKg) + " kg bag price (Rs. " + fmt(b.bagPrice)
                            + ") would be lower than the new price per kg. Change the bag price first.");
                }
            }
        } else {
            pricePerKg = 0;
        }
        try (Connection c = Database.getConnection()) {
            update(c, "UPDATE products SET brand=?, category=?, price_per_kg=? WHERE id=?",
                    brand, category, pricePerKg, id);
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void updateBagSize(int bagSizeId, Double bagPrice, int sealed,
                                     int openedCount, double openedKg) {
        BagSize old = findBagSize(bagSizeId);
        Product p = getProduct(old.productId);
        checkBagFields(p.pricePerKg, old.sizeKg, bagPrice, sealed, openedCount, openedKg);
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                UPDATE bag_sizes SET bag_price=?, sealed_count=?, opened_count=?,
                opened_total_kg=? WHERE id=?""")) {
            if (bagPrice != null) ps.setDouble(1, bagPrice); else ps.setNull(1, Types.REAL);
            ps.setInt(2, sealed);
            ps.setInt(3, openedCount);
            ps.setDouble(4, openedKg);
            ps.setInt(5, bagSizeId);
            ps.executeUpdate();
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void updatePacket(int packetId, double price, int units) {
        if (price <= 0) throw new ShopException("Price must be more than 0.");
        if (units < 0) throw new ShopException("Units cannot be negative.");
        try (Connection c = Database.getConnection()) {
            update(c, "UPDATE packet_stock SET price_per_unit=?, unit_count=? WHERE id=?", price, units, packetId);
        } catch (SQLException e) { throw dbError(e); }
    }

    private static BagSize findBagSize(int bagSizeId) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM bag_sizes WHERE id=?")) {
            ps.setInt(1, bagSizeId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new ShopException("Bag size not found.");
            Double bagPrice = rs.getObject("bag_price") != null ? rs.getDouble("bag_price") : null;
            return new BagSize(bagSizeId, rs.getInt("product_id"), rs.getDouble("size_kg"), bagPrice,
                    rs.getInt("sealed_count"), rs.getInt("opened_count"), rs.getDouble("opened_total_kg"));
        } catch (SQLException e) { throw dbError(e); }
    }

    /**
     * Mark one sealed bag as opened.
     * @param replaceExisting true = the old opened bag(s) are finished, start fresh with this one;
     *                        false = add this bag on top of the opened stock already there.
     */
    public static void openBag(int bagSizeId, double remainingKg, boolean deductSealed,
                               boolean replaceExisting) {
        if (remainingKg <= 0) throw new ShopException("Enter the kg left in the opened bag.");
        BagSize b = findBagSize(bagSizeId);
        if (remainingKg > b.sizeKg + 1e-9) {
            throw new ShopException("An opened bag cannot hold more than " + fmt(b.sizeKg) + " kg.");
        }
        if (deductSealed && b.sealed < 1) throw new ShopException("There are no sealed bags left to open.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (replaceExisting || b.openedCount == 0) {
                    update(c, "UPDATE bag_sizes SET opened_count=1, opened_total_kg=? WHERE id=?",
                            remainingKg, bagSizeId);
                } else {
                    update(c, "UPDATE bag_sizes SET opened_count=opened_count+1, "
                            + "opened_total_kg=opened_total_kg+? WHERE id=?", remainingKg, bagSizeId);
                }
                if (deductSealed) {
                    update(c, "UPDATE bag_sizes SET sealed_count=sealed_count-1 WHERE id=?", bagSizeId);
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void deleteBagSize(int bagSizeId) {
        try (Connection c = Database.getConnection()) {
            update(c, "DELETE FROM bag_sizes WHERE id=?", bagSizeId);
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void deletePacket(int packetId) {
        try (Connection c = Database.getConnection()) {
            update(c, "DELETE FROM packet_stock WHERE id=?", packetId);
        } catch (SQLException e) { throw dbError(e); }
    }

    /** Removes the product and its stock. Old bills are kept. */
    public static void deleteProduct(int productId) {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                update(c, "DELETE FROM bag_sizes WHERE product_id=?", productId);
                update(c, "DELETE FROM packet_stock WHERE product_id=?", productId);
                update(c, "DELETE FROM products WHERE id=?", productId);
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    // ───────────────────────── returns ─────────────────────────

    public static void returnSealedBags(int bagSizeId, int count) {
        if (count <= 0) throw new ShopException("Number of bags must be more than 0.");
        BagSize b = findBagSize(bagSizeId);
        Product p = getProduct(b.productId);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                update(c, "UPDATE bag_sizes SET sealed_count=sealed_count+? WHERE id=?", count, bagSizeId);
                logReturn(c, p, "sealed", b.sizeKg, count, "bag");
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void returnOpenedKg(int bagSizeId, double kg) {
        if (kg <= 0) throw new ShopException("Measured kg must be more than 0.");
        BagSize b = findBagSize(bagSizeId);
        Product p = getProduct(b.productId);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                update(c, "UPDATE bag_sizes SET opened_count=opened_count+1, "
                        + "opened_total_kg=opened_total_kg+? WHERE id=?", kg, bagSizeId);
                logReturn(c, p, "opened", b.sizeKg, kg, "kg");
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    public static void returnPackets(int packetId, int count) {
        if (count <= 0) throw new ShopException("Number of units must be more than 0.");
        Product p = null;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT product_id FROM packet_stock WHERE id=?")) {
            ps.setInt(1, packetId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new ShopException("Pack size not found.");
            p = getProduct(rs.getInt(1));
        } catch (SQLException e) { throw dbError(e); }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                update(c, "UPDATE packet_stock SET unit_count=unit_count+? WHERE id=?", count, packetId);
                logReturn(c, p, "sealed_packet", 0, count, "unit");
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw dbError(e); }
    }

    private static void logReturn(Connection c, Product p, String type, double sizeKg,
                                  double quantity, String unit) throws SQLException {
        update(c, """
                INSERT INTO returns (product_id, brand, return_type, size_kg, quantity, unit, return_date)
                VALUES (?,?,?,?,?,?,?)""", p.id, p.brand, type, sizeKg, quantity, unit, today());
    }

    public static List<ReturnRow> listReturns(int limit) {
        List<ReturnRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM returns ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new ReturnRow(rs.getString("return_date"), rs.getString("brand"),
                        rs.getString("return_type"), rs.getDouble("size_kg"),
                        rs.getDouble("quantity"), rs.getString("unit")));
            }
        } catch (SQLException e) { throw dbError(e); }
        return list;
    }
}
