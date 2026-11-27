package com.shop.ui;

import com.shop.ShopService;
import com.shop.ShopService.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Earlier bills (newest first). Click a bill to see what was on it. */
class HistoryPanel extends Screen {

    private final List<BillRow> bills = new ArrayList<>();
    private final DefaultTableModel billModel = Ui.model("Bill no.", "Date", "Customer", "Total");
    private final JTable billTable = Ui.table(billModel);
    private final DefaultTableModel itemModel = Ui.model("Item", "Qty", "Unit", "Price", "Total");
    private final JTable itemTable = Ui.table(itemModel);
    private final JLabel summary = Ui.muted(" ");
    private final JLabel itemsTitle = Ui.title("Items on the bill");

    HistoryPanel() {
        JPanel left = Ui.card();
        left.add(Ui.title("Recent bills"), BorderLayout.NORTH);
        Ui.widths(billTable, 70, 100, 140, 100);
        Ui.alignRight(billTable, 0, 3);
        left.add(Ui.scroll(billTable), BorderLayout.CENTER);
        left.add(summary, BorderLayout.SOUTH);

        JPanel right = Ui.card();
        right.add(itemsTitle, BorderLayout.NORTH);
        Ui.widths(itemTable, 220, 60, 55, 90, 100);
        Ui.alignRight(itemTable, 1, 3, 4);
        right.add(Ui.scroll(itemTable), BorderLayout.CENTER);

        JPanel both = new JPanel(new GridLayout(1, 2, 16, 0));
        both.setOpaque(false);
        both.add(left);
        both.add(right);
        add(both, BorderLayout.CENTER);

        billTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) Ui.run(this, this::showItems);
        });
    }

    @Override void refresh() {
        bills.clear();
        bills.addAll(ShopService.listBills(300));
        billModel.setRowCount(0);
        double sum = 0;
        for (BillRow b : bills) {
            billModel.addRow(new Object[]{b.id, b.date, b.customer, String.format("%,.2f", b.total)});
            sum += b.total;
        }
        summary.setText(bills.isEmpty() ? "No bills yet."
                : bills.size() + " bill(s) shown, total " + Ui.rs(sum));
        itemModel.setRowCount(0);
        itemsTitle.setText("Items on the bill");
        if (!bills.isEmpty()) billTable.setRowSelectionInterval(0, 0);   // show the newest bill
    }

    private void showItems() {
        int row = billTable.getSelectedRow();
        itemModel.setRowCount(0);
        if (row < 0) return;
        BillRow bill = bills.get(row);
        itemsTitle.setText("Items on bill no. " + bill.id);
        for (BillLine l : ShopService.getBillItems(bill.id)) {
            itemModel.addRow(new Object[]{l.brand + " " + l.description, ShopService.fmt(l.quantity),
                    l.unit, String.format("%,.2f", l.price), String.format("%,.2f", l.total())});
        }
    }
}
