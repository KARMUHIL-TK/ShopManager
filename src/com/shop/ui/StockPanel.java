package com.shop.ui;

import com.shop.ShopService;
import com.shop.ShopService.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** See all stock, add products, edit numbers, open bags, remove things. */
class StockPanel extends Screen {

    /** One line in the table: a product with one bag size or one pack size (or neither yet). */
    private static class Row {
        final Product p;
        final BagSize b;      // set for weight products
        final Packet pk;      // set for packet products
        Row(Product p, BagSize b, Packet pk) { this.p = p; this.b = b; this.pk = pk; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final DefaultTableModel model = Ui.model(
            "Brand", "Category", "Size / pack", "Price", "Sealed / units", "Opened bags", "Loose kg");
    private final JTable table = Ui.table(model);

    StockPanel() {
        JPanel card = Ui.card();

        JButton addProduct = Ui.button("Add product", Ui.GREEN);
        JButton edit = Ui.button("Edit", Ui.BLUE);
        JButton addSize = Ui.button("Add size / pack", Ui.GREEN);
        JButton open = Ui.button("Open a bag", Ui.AMBER);
        JButton removeSize = Ui.button("Remove size", Ui.BRICK);
        JButton removeProduct = Ui.button("Remove product", Ui.BRICK);
        addProduct.addActionListener(e -> Ui.run(this, this::addProduct));
        edit.addActionListener(e -> Ui.run(this, this::editSelected));
        addSize.addActionListener(e -> Ui.run(this, this::addSizeToSelected));
        open.addActionListener(e -> Ui.run(this, this::openBag));
        removeSize.addActionListener(e -> Ui.run(this, this::removeSize));
        removeProduct.addActionListener(e -> Ui.run(this, this::removeProduct));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        for (JButton b : new JButton[]{addProduct, edit, addSize, open, removeSize, removeProduct}) bar.add(b);
        card.add(bar, BorderLayout.NORTH);

        Ui.widths(table, 130, 90, 110, 200, 110, 100, 90);
        Ui.alignRight(table, 5, 6);
        // Show "0" in the Sealed / units column in red, so empty shelves stand out.
        Ui.PaddedRenderer stockCell = new Ui.PaddedRenderer(SwingConstants.RIGHT) {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                    boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                comp.setForeground("0".equals(String.valueOf(v)) ? Ui.BRICK : Ui.TEXT);
                comp.setFont("0".equals(String.valueOf(v)) ? Ui.BOLD : Ui.BASE);
                return comp;
            }
        };
        table.getColumnModel().getColumn(4).setCellRenderer(stockCell);
        card.add(Ui.scroll(table), BorderLayout.CENTER);

        card.add(Ui.muted("Red 0 = out of stock.  \"Loose kg\" = kg in opened bags, used for selling by kg."),
                BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    // ───────────────────────── showing the table ─────────────────────────

    @Override void refresh() {
        rows.clear();
        model.setRowCount(0);
        for (Product p : ShopService.listProducts()) {
            if (p.isWeight()) {
                List<BagSize> bags = ShopService.getBagSizes(p.id);
                if (bags.isEmpty()) addRow(new Row(p, null, null));
                for (BagSize b : bags) addRow(new Row(p, b, null));
            } else {
                List<Packet> packs = ShopService.getPackets(p.id);
                if (packs.isEmpty()) addRow(new Row(p, null, null));
                for (Packet pk : packs) addRow(new Row(p, null, pk));
            }
        }
    }

    private void addRow(Row r) {
        rows.add(r);
        String size = "-", price = "", stock = "", opened = "", loose = "";
        if (r.b != null) {
            size = ShopService.fmt(r.b.sizeKg) + " kg bag";
            price = rupees(r.p.pricePerKg) + "/kg"
                    + (r.b.bagPrice != null ? "  |  bag " + rupees(r.b.bagPrice) : "");
            stock = String.valueOf(r.b.sealed);
            opened = String.valueOf(r.b.openedCount);
            loose = ShopService.fmt(r.b.openedKg);
        } else if (r.pk != null) {
            size = r.pk.packSize;
            price = rupees(r.pk.price) + " each";
            stock = String.valueOf(r.pk.units);
        } else if (r.p.isWeight()) {
            price = rupees(r.p.pricePerKg) + "/kg";
            size = "(no bag size yet)";
        } else {
            size = "(no pack size yet)";
        }
        model.addRow(new Object[]{r.p.brand, r.p.category, size, price, stock, opened, loose});
    }

    /** Keeps a form at the top of its space, so a short form does not float in the middle. */
    private static JPanel alignTop(JComponent form) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(form, BorderLayout.NORTH);
        return p;
    }

    /** 1420 -> "Rs.1,420"   and   56.5 -> "Rs.56.50" */
    private static String rupees(double v) {
        return v == Math.rint(v) ? String.format("Rs.%,.0f", v) : String.format("Rs.%,.2f", v);
    }

    private Row selected() {
        int i = table.getSelectedRow();
        if (i < 0) throw new ShopException("Click a row in the table first.");
        return rows.get(i);
    }

    private void reload() {
        refresh();
    }

    // ───────────────────────── add a product ─────────────────────────

    private void addProduct() {
        JComboBox<String> category = new JComboBox<>(ShopService.CATEGORIES);
        JTextField brand = Ui.field(14);
        JTextField date = Ui.field(ShopService.today(), 14);

        // weight form (rice, millets, pulses)
        JTextField pricePerKg = Ui.field(14), bagSize = Ui.field(14), bagPrice = Ui.field(14);
        JTextField sealed = Ui.field("0", 14), openedCount = Ui.field("0", 14), openedKg = Ui.field("0", 14);
        Ui.Form weight = new Ui.Form();
        weight.add("Price per kg (Rs.)", pricePerKg);
        weight.add("Bag size (kg)", bagSize);
        weight.add("Bag price (Rs., empty = none)", bagPrice);
        weight.add("Sealed bags in stock", sealed);
        weight.add("Opened bags", openedCount);
        weight.add("Loose kg in opened bags", openedKg);

        // packet form (oil, spices, health mix)
        JTextField packSize = Ui.field(14), packPrice = Ui.field(14), packUnits = Ui.field("0", 14);
        Ui.Form packet = new Ui.Form();
        packet.add("Pack size (e.g. 1L bottle)", packSize);
        packet.add("Price per unit (Rs.)", packPrice);
        packet.add("Units in stock", packUnits);

        CardLayout swap = new CardLayout();
        JPanel details = new JPanel(swap);
        details.setOpaque(false);
        details.add(alignTop(weight), "weight");
        details.add(alignTop(packet), "packet");
        category.addActionListener(e ->
                swap.show(details, ShopService.isWeightCategory((String) category.getSelectedItem()) ? "weight" : "packet"));
        swap.show(details, "weight");

        Ui.Form top = new Ui.Form();
        top.add("Category", category);
        top.add("Brand name", brand);
        top.add("Date", date);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(top, BorderLayout.NORTH);
        content.add(details, BorderLayout.CENTER);
        Ui.focusWhenShown(brand);

        Integer id = Ui.ask(this, "Add a new product", content, () -> {
            String cat = (String) category.getSelectedItem();
            if (ShopService.isWeightCategory(cat)) {
                return ShopService.createWeightProduct(brand.getText(), cat,
                        Ui.num(pricePerKg.getText(), "Price per kg"), date.getText(),
                        Ui.num(bagSize.getText(), "Bag size"),
                        Ui.optionalNum(bagPrice.getText(), "Bag price"),
                        Ui.wholeOrZero(sealed.getText(), "Sealed bags"),
                        Ui.wholeOrZero(openedCount.getText(), "Opened bags"),
                        Ui.numOrZero(openedKg.getText(), "Loose kg"));
            }
            return ShopService.createPacketProduct(brand.getText(), cat, date.getText(),
                    packSize.getText(), Ui.num(packPrice.getText(), "Price"),
                    Ui.wholeOrZero(packUnits.getText(), "Units"));
        });
        if (id != null) reload();
    }

    // ───────────────────────── edit ─────────────────────────

    private void editSelected() {
        Row r = selected();
        boolean weightProduct = r.p.isWeight();

        JTextField brand = Ui.field(r.p.brand, 14);
        JComboBox<String> category = new JComboBox<>();
        for (String c : ShopService.CATEGORIES) {
            if (ShopService.isWeightCategory(c) == weightProduct) category.addItem(c);   // keep the same kind
        }
        category.setSelectedItem(r.p.category);

        Ui.Form form = new Ui.Form();
        form.add("Brand name", brand);
        form.add("Category", category);

        JTextField pricePerKg = Ui.field(ShopService.fmt(r.p.pricePerKg), 14);
        JTextField bagPrice = null, sealed = null, openedCount = null, openedKg = null;
        JTextField packPrice = null, packUnits = null;
        if (weightProduct) {
            form.add("Price per kg (Rs.)", pricePerKg);
            if (r.b != null) {
                bagPrice = Ui.field(r.b.bagPrice == null ? "" : ShopService.fmt(r.b.bagPrice), 14);
                sealed = Ui.field(String.valueOf(r.b.sealed), 14);
                openedCount = Ui.field(String.valueOf(r.b.openedCount), 14);
                openedKg = Ui.field(ShopService.fmt(r.b.openedKg), 14);
                form.addWide(Ui.muted("Numbers for the " + ShopService.fmt(r.b.sizeKg) + " kg bag:"));
                form.add("Bag price (Rs., empty = none)", bagPrice);
                form.add("Sealed bags in stock", sealed);
                form.add("Opened bags", openedCount);
                form.add("Loose kg in opened bags", openedKg);
            }
        } else if (r.pk != null) {
            packPrice = Ui.field(ShopService.fmt(r.pk.price), 14);
            packUnits = Ui.field(String.valueOf(r.pk.units), 14);
            form.addWide(Ui.muted("Numbers for \"" + r.pk.packSize + "\":"));
            form.add("Price per unit (Rs.)", packPrice);
            form.add("Units in stock", packUnits);
        }

        final JTextField fBagPrice = bagPrice, fSealed = sealed, fOpenedCount = openedCount,
                fOpenedKg = openedKg, fPackPrice = packPrice, fPackUnits = packUnits;
        Boolean done = Ui.ask(this, "Edit " + r.p.brand, form, () -> {
            double ppk = weightProduct ? Ui.num(pricePerKg.getText(), "Price per kg") : 0;
            ShopService.updateProduct(r.p.id, brand.getText(), (String) category.getSelectedItem(), ppk);
            if (r.b != null) {
                ShopService.updateBagSize(r.b.id, Ui.optionalNum(fBagPrice.getText(), "Bag price"),
                        Ui.whole(fSealed.getText(), "Sealed bags"),
                        Ui.whole(fOpenedCount.getText(), "Opened bags"),
                        Ui.num(fOpenedKg.getText(), "Loose kg"));
            }
            if (r.pk != null) {
                ShopService.updatePacket(r.pk.id, Ui.num(fPackPrice.getText(), "Price"),
                        Ui.whole(fPackUnits.getText(), "Units"));
            }
            return true;
        });
        if (done != null) reload();
    }

    // ───────────────────────── add a size or pack to an existing product ─────────────────────────

    private void addSizeToSelected() {
        Row r = selected();
        Ui.Form form = new Ui.Form();
        Boolean done;
        if (r.p.isWeight()) {
            JTextField size = Ui.field(14), bagPrice = Ui.field(14);
            JTextField sealed = Ui.field("0", 14), openedCount = Ui.field("0", 14), openedKg = Ui.field("0", 14);
            form.add("Bag size (kg)", size);
            form.add("Bag price (Rs., empty = none)", bagPrice);
            form.add("Sealed bags in stock", sealed);
            form.add("Opened bags", openedCount);
            form.add("Loose kg in opened bags", openedKg);
            Ui.focusWhenShown(size);
            done = Ui.ask(this, "New bag size for " + r.p.brand, form, () -> {
                ShopService.addBagSize(r.p.id, Ui.num(size.getText(), "Bag size"),
                        Ui.optionalNum(bagPrice.getText(), "Bag price"),
                        Ui.wholeOrZero(sealed.getText(), "Sealed bags"),
                        Ui.wholeOrZero(openedCount.getText(), "Opened bags"),
                        Ui.numOrZero(openedKg.getText(), "Loose kg"));
                return true;
            });
        } else {
            JTextField pack = Ui.field(14), price = Ui.field(14), units = Ui.field("0", 14);
            form.add("Pack size (e.g. 500ml)", pack);
            form.add("Price per unit (Rs.)", price);
            form.add("Units in stock", units);
            Ui.focusWhenShown(pack);
            done = Ui.ask(this, "New pack size for " + r.p.brand, form, () -> {
                ShopService.addPacket(r.p.id, pack.getText(), Ui.num(price.getText(), "Price"),
                        Ui.wholeOrZero(units.getText(), "Units"));
                return true;
            });
        }
        if (done != null) reload();
    }

    // ───────────────────────── open a bag ─────────────────────────

    private void openBag() {
        Row r = selected();
        if (r.b == null) throw new ShopException("Pick a row of a rice / millets / pulses bag size to open a bag.");

        JTextField remaining = Ui.field(ShopService.fmt(r.b.sizeKg), 14);
        JCheckBox deduct = new JCheckBox("Take 1 bag out of the sealed count (" + r.b.sealed + " sealed now)", true);
        JRadioButton add = new JRadioButton("Add to the loose kg already there (" + ShopService.fmt(r.b.openedKg) + " kg)", true);
        JRadioButton fresh = new JRadioButton("The old opened bags are finished - start fresh with this one");
        ButtonGroup g = new ButtonGroup();
        g.add(add);
        g.add(fresh);

        Ui.Form form = new Ui.Form();
        form.add("Kg in the opened bag", remaining);
        form.addWide(deduct);
        if (r.b.openedCount > 0 && r.b.openedKg > 0) {
            form.addWide(add);
            form.addWide(fresh);
        }
        Ui.focusWhenShown(remaining);

        Boolean done = Ui.ask(this, "Open a " + ShopService.fmt(r.b.sizeKg) + " kg bag of " + r.p.brand, form, () -> {
            ShopService.openBag(r.b.id, Ui.num(remaining.getText(), "Kg in the opened bag"),
                    deduct.isSelected(), fresh.isSelected());
            return true;
        });
        if (done != null) reload();
    }

    // ───────────────────────── remove ─────────────────────────

    private void removeSize() {
        Row r = selected();
        if (r.b == null && r.pk == null) throw new ShopException("This product has no size to remove.");
        String what = r.b != null ? ShopService.fmt(r.b.sizeKg) + " kg bag" : "\"" + r.pk.packSize + "\"";
        if (!Ui.confirm(this, "Remove " + what + " of " + r.p.brand + "?")) return;
        if (r.b != null) ShopService.deleteBagSize(r.b.id); else ShopService.deletePacket(r.pk.id);
        reload();
    }

    private void removeProduct() {
        Row r = selected();
        if (!Ui.confirm(this, "Remove " + r.p.brand + " (" + r.p.category + ") and all its sizes?\n"
                + "Old bills are kept.")) return;
        ShopService.deleteProduct(r.p.id);
        reload();
    }
}
