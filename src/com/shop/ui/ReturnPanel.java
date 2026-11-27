package com.shop.ui;

import com.shop.ShopService;
import com.shop.ShopService.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** A customer brings goods back: the stock goes up again and the return is written down. */
class ReturnPanel extends Screen {

    private final JComboBox<Product> productBox = new JComboBox<>();
    private final JComboBox<Object> sizeBox = new JComboBox<>();       // BagSize or Packet
    private final JRadioButton sealedRadio = new JRadioButton("Sealed bag(s)", true);
    private final JRadioButton openedRadio = new JRadioButton("Opened / partly used (measured kg)");
    private final JLabel quantityLabel = new JLabel("How many bags");
    private final JTextField quantity = Ui.field(10);
    private final DefaultTableModel model = Ui.model("Date", "Product", "Type", "Quantity");
    private final JTable table = Ui.table(model);
    private boolean loading = false;

    ReturnPanel() {
        JPanel left = Ui.card();
        left.add(Ui.title("Take goods back"), BorderLayout.NORTH);

        sealedRadio.setOpaque(false);
        openedRadio.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(sealedRadio);
        group.add(openedRadio);

        Ui.Form form = new Ui.Form();
        form.add("Product", productBox);
        form.add("Size / pack", sizeBox);
        form.addWide(sealedRadio);
        form.addWide(openedRadio);
        quantityLabel.setFont(Ui.BASE);
        form.add("", quantityLabel);
        form.add("Quantity", quantity);
        JPanel formWrap = new JPanel(new BorderLayout());
        formWrap.setOpaque(false);
        formWrap.add(form, BorderLayout.NORTH);
        left.add(formWrap, BorderLayout.CENTER);

        JButton save = Ui.button("Take back", Ui.BRICK);
        save.addActionListener(e -> Ui.run(this, this::processReturn));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(save);
        left.add(south, BorderLayout.SOUTH);

        JPanel right = Ui.card();
        right.add(Ui.title("Recent returns"), BorderLayout.NORTH);
        Ui.widths(table, 90, 170, 110, 110);
        Ui.alignRight(table, 3);
        right.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel both = new JPanel(new GridLayout(1, 2, 16, 0));
        both.setOpaque(false);
        both.add(left);
        both.add(right);
        add(both, BorderLayout.CENTER);

        productBox.addActionListener(e -> { if (!loading) loadSizes(); });
        sealedRadio.addActionListener(e -> updateLabel());
        openedRadio.addActionListener(e -> updateLabel());
    }

    @Override void refresh() {
        loading = true;
        productBox.removeAllItems();
        for (Product p : ShopService.listProducts()) productBox.addItem(p);
        loading = false;
        loadSizes();
        quantity.setText("");

        model.setRowCount(0);
        for (ReturnRow r : ShopService.listReturns(100)) {
            String type = switch (r.type) {
                case "sealed" -> "Sealed " + ShopService.fmt(r.sizeKg) + " kg bag";
                case "opened" -> "Opened " + ShopService.fmt(r.sizeKg) + " kg bag";
                default -> "Sealed packet";
            };
            model.addRow(new Object[]{r.date, r.brand, type, ShopService.fmt(r.quantity) + " " + r.unit});
        }
    }

    private void loadSizes() {
        sizeBox.removeAllItems();
        Product p = (Product) productBox.getSelectedItem();
        if (p != null) {
            if (p.isWeight()) for (BagSize b : ShopService.getBagSizes(p.id)) sizeBox.addItem(b);
            else for (Packet pk : ShopService.getPackets(p.id)) sizeBox.addItem(pk);
        }
        updateLabel();
    }

    private void updateLabel() {
        Product p = (Product) productBox.getSelectedItem();
        boolean weight = p != null && p.isWeight();
        sealedRadio.setEnabled(weight);
        openedRadio.setEnabled(weight);
        if (!weight) {
            sealedRadio.setSelected(true);
            quantityLabel.setText("Only unopened packets can be returned. How many units?");
        } else if (sealedRadio.isSelected()) {
            quantityLabel.setText("How many sealed bags came back?");
        } else {
            quantityLabel.setText("Measure the goods: how many kg came back?");
        }
    }

    private void processReturn() {
        Product p = (Product) productBox.getSelectedItem();
        Object size = sizeBox.getSelectedItem();
        if (p == null || size == null) throw new ShopException("Choose a product and a size first.");

        if (size instanceof BagSize b) {
            if (sealedRadio.isSelected()) {
                ShopService.returnSealedBags(b.id, Ui.whole(quantity.getText(), "Number of bags"));
            } else {
                ShopService.returnOpenedKg(b.id, Ui.num(quantity.getText(), "Kg"));
            }
        } else {
            ShopService.returnPackets(((Packet) size).id, Ui.whole(quantity.getText(), "Number of units"));
        }
        Ui.info(this, "Return saved. The stock has been updated.");
        refresh();
    }
}
