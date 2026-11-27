package com.shop.ui;

import com.shop.ShopService;
import com.shop.ShopService.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Make a bill: pick products on the left, see the bill on the right, press Save. */
class BillPanel extends Screen {

    // left side: products
    private List<Product> all = new ArrayList<>();
    private final List<Product> shown = new ArrayList<>();
    private final List<String> summaries = new ArrayList<>();
    private final DefaultTableModel productModel = Ui.model("Brand", "Category", "In stock");
    private final JTable productTable = Ui.table(productModel);
    private final JTextField search = Ui.field(16);

    // right side: the bill being made
    private final List<BillLine> lines = new ArrayList<>();
    private final DefaultTableModel billModel = Ui.model("Item", "Qty", "Unit", "Price", "Total");
    private final JTable billTable = Ui.table(billModel);
    private final JLabel totalLabel = new JLabel("Rs. 0.00");
    private final JTextField customer = Ui.field(12);
    private final JTextField date = Ui.field(9);

    BillPanel() {
        JPanel both = new JPanel(new GridLayout(1, 2, 16, 0));
        both.setOpaque(false);
        both.add(buildProductCard());
        both.add(buildBillCard());
        add(both, BorderLayout.CENTER);
    }

    // ───────────────────────── building the screen ─────────────────────────

    private JPanel buildProductCard() {
        JPanel card = Ui.card();

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(Ui.title("1. Pick a product"), BorderLayout.NORTH);
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(Ui.muted("Search:"), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        top.add(searchRow, BorderLayout.SOUTH);
        card.add(top, BorderLayout.NORTH);

        Ui.widths(productTable, 130, 90, 170);
        card.add(Ui.scroll(productTable), BorderLayout.CENTER);

        JButton add = Ui.button("Add to bill  >", Ui.GREEN);
        add.addActionListener(e -> Ui.run(this, this::addSelectedProduct));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.setOpaque(false);
        bottom.add(add);
        card.add(bottom, BorderLayout.SOUTH);

        Ui.onChange(search, this::showProducts);
        productTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) Ui.run(BillPanel.this, BillPanel.this::addSelectedProduct);
            }
        });
        return card;
    }

    private JPanel buildBillCard() {
        JPanel card = Ui.card();
        card.add(Ui.title("2. The bill"), BorderLayout.NORTH);

        Ui.widths(billTable, 190, 55, 50, 80, 90);
        Ui.alignRight(billTable, 1, 3, 4);
        card.add(Ui.scroll(billTable), BorderLayout.CENTER);

        JButton edit = Ui.button("Change price", Ui.BLUE);
        JButton remove = Ui.button("Remove item", Ui.BRICK);
        JButton clear = Ui.button("Clear bill", new Color(0x777777));
        edit.addActionListener(e -> Ui.run(this, this::editPrice));
        remove.addActionListener(e -> Ui.run(this, this::removeItem));
        clear.addActionListener(e -> Ui.run(this, this::clearBill));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(edit);
        buttons.add(remove);
        buttons.add(clear);

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(Ui.muted("Customer:"));
        who.add(customer);
        who.add(Ui.muted("Date:"));
        who.add(date);

        totalLabel.setFont(Ui.BIG);
        totalLabel.setForeground(Ui.GREEN);
        JButton save = Ui.button("Save bill", Ui.GREEN);
        save.setFont(Ui.BOLD.deriveFont(18f));
        save.addActionListener(e -> Ui.run(this, this::saveBill));
        JPanel totalRow = new JPanel(new BorderLayout(10, 0));
        totalRow.setOpaque(false);
        totalRow.add(totalLabel, BorderLayout.WEST);
        totalRow.add(save, BorderLayout.EAST);

        JPanel south = new JPanel(new GridLayout(3, 1, 0, 10));
        south.setOpaque(false);
        south.add(buttons);
        south.add(who);
        south.add(totalRow);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    // ───────────────────────── loading data ─────────────────────────

    @Override void refresh() {
        all = ShopService.listProducts();
        summaries.clear();
        for (Product p : all) summaries.add(stockSummary(p));
        if (date.getText().trim().isEmpty()) date.setText(ShopService.today());
        showProducts();
        showBill();
    }

    private static String stockSummary(Product p) {
        if (p.isWeight()) {
            int sealed = 0;
            double loose = 0;
            for (BagSize b : ShopService.getBagSizes(p.id)) { sealed += b.sealed; loose += b.openedKg; }
            return sealed + " sealed, " + ShopService.fmt(loose) + " kg loose";
        }
        int units = 0;
        for (Packet pk : ShopService.getPackets(p.id)) units += pk.units;
        return units + " units";
    }

    /** Fills the left table, keeping only products that match the search box. */
    private void showProducts() {
        String q = search.getText().trim().toLowerCase();
        productModel.setRowCount(0);
        shown.clear();
        for (int i = 0; i < all.size(); i++) {
            Product p = all.get(i);
            if (!q.isEmpty() && !(p.brand + " " + p.category).toLowerCase().contains(q)) continue;
            shown.add(p);
            productModel.addRow(new Object[]{p.brand, p.category, summaries.get(i)});
        }
    }

    private void showBill() {
        billModel.setRowCount(0);
        double total = 0;
        for (BillLine l : lines) {
            billModel.addRow(new Object[]{l.brand + " " + l.description, ShopService.fmt(l.quantity),
                    l.unit, String.format("%,.2f", l.price), String.format("%,.2f", l.total())});
            total += l.total();
        }
        totalLabel.setText(Ui.rs(total));
    }

    // ───────────────────────── adding an item ─────────────────────────

    private void addSelectedProduct() {
        int row = productTable.getSelectedRow();
        if (row < 0) throw new ShopException("Click a product in the list first.");
        Product p = shown.get(row);

        List<BillLine> added = p.isWeight() ? askWeightItem(p) : askPacketItem(p);
        if (added == null) return;                       // user pressed Cancel

        // Warn now (not only at Save) if the whole bill would need more than the stock has.
        List<BillLine> together = new ArrayList<>(lines);
        together.addAll(added);
        ShopService.checkStock(together);

        lines.addAll(added);
        showBill();
        billTable.scrollRectToVisible(billTable.getCellRect(billModel.getRowCount() - 1, 0, true));
    }

    private List<BillLine> askWeightItem(Product p) {
        List<BagSize> bags = ShopService.getBagSizes(p.id);
        if (bags.isEmpty()) {
            throw new ShopException("This product has no bag sizes yet. Add one in the Stock screen.");
        }
        JComboBox<String> size = new JComboBox<>();
        for (BagSize b : bags) {
            size.addItem(ShopService.fmt(b.sizeKg) + " kg bag  -  " + b.sealed + " sealed, "
                    + ShopService.fmt(b.openedKg) + " kg loose");
        }
        JRadioButton byKg = new JRadioButton("By kg  (loose kg, or auto-split into bags + loose)", true);
        JRadioButton byBag = new JRadioButton("By whole sealed bags");
        ButtonGroup group = new ButtonGroup();
        group.add(byKg);
        group.add(byBag);
        JTextField qty = Ui.field(8);
        JLabel preview = new JLabel(" ");
        preview.setFont(Ui.BASE);
        preview.setForeground(Ui.GREEN);
        preview.setVerticalAlignment(SwingConstants.TOP);
        preview.setPreferredSize(new Dimension(460, 70));

        Runnable update = () -> {
            try {
                preview.setText(describe(plan(p, bags.get(size.getSelectedIndex()), byBag.isSelected(), qty)));
            } catch (ShopException e) {
                preview.setText("<html><span style='color:#777777'>" + e.getMessage() + "</span></html>");
            }
        };
        size.addActionListener(e -> update.run());
        byKg.addActionListener(e -> update.run());
        byBag.addActionListener(e -> update.run());
        Ui.onChange(qty, update);
        update.run();

        Ui.Form form = new Ui.Form();
        form.add("Bag size", size);
        form.addWide(byKg);
        form.addWide(byBag);
        form.add("Quantity", qty);
        form.addWide(preview);
        Ui.focusWhenShown(qty);

        return Ui.ask(this, "Add " + p.brand + " (" + p.category + ")", form,
                () -> plan(p, bags.get(size.getSelectedIndex()), byBag.isSelected(), qty));
    }

    private static List<BillLine> plan(Product p, BagSize b, boolean byBag, JTextField qty) {
        return byBag
                ? ShopService.planBagSale(p, b, Ui.whole(qty.getText(), "Number of bags"))
                : ShopService.planKgSale(p, b, Ui.num(qty.getText(), "Quantity in kg"));
    }

    private List<BillLine> askPacketItem(Product p) {
        List<Packet> packets = ShopService.getPackets(p.id);
        if (packets.isEmpty()) {
            throw new ShopException("This product has no pack sizes yet. Add one in the Stock screen.");
        }
        JComboBox<String> pack = new JComboBox<>();
        for (Packet pk : packets) {
            pack.addItem(pk.packSize + "  -  " + Ui.rs(pk.price) + "  -  " + pk.units + " in stock");
        }
        JTextField units = Ui.field(8);
        JLabel preview = new JLabel(" ");
        preview.setFont(Ui.BASE);
        preview.setForeground(Ui.GREEN);
        preview.setVerticalAlignment(SwingConstants.TOP);
        preview.setPreferredSize(new Dimension(460, 40));

        Runnable update = () -> {
            try {
                preview.setText(describe(ShopService.planPacketSale(p, packets.get(pack.getSelectedIndex()),
                        Ui.whole(units.getText(), "Number of units"))));
            } catch (ShopException e) {
                preview.setText("<html><span style='color:#777777'>" + e.getMessage() + "</span></html>");
            }
        };
        pack.addActionListener(e -> update.run());
        Ui.onChange(units, update);
        update.run();

        Ui.Form form = new Ui.Form();
        form.add("Pack size", pack);
        form.add("How many", units);
        form.addWide(preview);
        Ui.focusWhenShown(units);

        return Ui.ask(this, "Add " + p.brand + " (" + p.category + ")", form,
                () -> ShopService.planPacketSale(p, packets.get(pack.getSelectedIndex()),
                        Ui.whole(units.getText(), "Number of units")));
    }

    /** "1 bag x Rs. 1,420.00 = Rs. 1,420.00" (one line per bill line). */
    private static String describe(List<BillLine> ls) {
        StringBuilder sb = new StringBuilder("<html>");
        for (BillLine l : ls) {
            sb.append(ShopService.fmt(l.quantity)).append(' ').append(l.unit)
              .append("  x  ").append(Ui.rs(l.price)).append("  =  ").append(Ui.rs(l.total())).append("<br>");
        }
        return sb.append("</html>").toString();
    }

    // ───────────────────────── changing the bill ─────────────────────────

    private BillLine selectedLine() {
        int row = billTable.getSelectedRow();
        if (row < 0) throw new ShopException("Click an item on the bill first.");
        return lines.get(row);
    }

    private void editPrice() {
        BillLine l = selectedLine();
        JTextField price = Ui.field(ShopService.fmt(l.price), 10);
        Ui.focusWhenShown(price);
        Ui.Form form = new Ui.Form();
        form.add("Price per " + l.unit + " (Rs.)", price);
        Double newPrice = Ui.ask(this, l.brand + " " + l.description, form, () -> {
            double v = Ui.num(price.getText(), "Price");
            if (v < 0) throw new ShopException("Price cannot be negative.");
            return v;
        });
        if (newPrice == null) return;
        l.price = newPrice;
        showBill();
    }

    private void removeItem() {
        lines.remove(selectedLine());
        showBill();
    }

    private void clearBill() {
        if (lines.isEmpty()) return;
        if (Ui.confirm(this, "Remove all items from this bill?")) {
            lines.clear();
            showBill();
        }
    }

    // ───────────────────────── saving ─────────────────────────

    private void saveBill() {
        if (lines.isEmpty()) throw new ShopException("Add at least one item to the bill first.");
        List<BillLine> snapshot = new ArrayList<>(lines);
        String who = customer.getText().trim().isEmpty() ? "Walk-in" : customer.getText().trim();
        String day = date.getText().trim();

        int id = ShopService.saveBill(who, day, snapshot);   // checks stock, then saves, all-or-nothing

        lines.clear();
        customer.setText("");
        date.setText(ShopService.today());
        refresh();                                           // stock changed, so reload the product list
        showReceipt(id, who, day, snapshot);
    }

    private void showReceipt(int id, String who, String day, List<BillLine> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================\n");
        sb.append("                 SHOP - BILL\n");
        sb.append("==============================================\n");
        sb.append(String.format("Bill No  : %d%nDate     : %s%nCustomer : %s%n", id, day, who));
        sb.append("----------------------------------------------\n");
        double total = 0;
        int n = 1;
        for (BillLine l : items) {
            sb.append(String.format("%d. %s %s%n     %s %s x %,.2f = %,.2f%n", n++, l.brand, l.description,
                    ShopService.fmt(l.quantity), l.unit, l.price, l.total()));
            total += l.total();
        }
        sb.append("----------------------------------------------\n");
        sb.append(String.format("TOTAL    : Rs. %,.2f%n", total));
        sb.append("==============================================\n");

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Bill #" + id + " saved",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
