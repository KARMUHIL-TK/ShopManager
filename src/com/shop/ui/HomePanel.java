package com.shop.ui;

import com.shop.ShopService;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** The first screen: four big tiles. */
class HomePanel extends Screen {

    private final JLabel dateLabel = Ui.muted("");

    HomePanel(Consumer<String> go) {
        JPanel grid = new JPanel(new GridLayout(2, 2, 24, 24));
        grid.setOpaque(false);
        grid.add(tile("New Bill", "Make a bill for a customer", Ui.GREEN, () -> go.accept("bill")));
        grid.add(tile("Stock", "See, add and edit products", Ui.AMBER, () -> go.accept("stock")));
        grid.add(tile("Bill History", "Look at earlier bills", Ui.BLUE, () -> go.accept("history")));
        grid.add(tile("Returns", "Take stock back from a customer", Ui.BRICK, () -> go.accept("returns")));
        grid.setPreferredSize(new Dimension(820, 420));

        JPanel center = new JPanel(new GridBagLayout());   // keeps the tiles in the middle
        center.setOpaque(false);
        center.add(grid);
        add(center, BorderLayout.CENTER);

        dateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(dateLabel, BorderLayout.SOUTH);
    }

    private static JButton tile(String name, String hint, Color color, Runnable action) {
        JButton b = Ui.button("<html><div style='text-align:center'>"
                + "<span style='font-size:28px'>" + name + "</span><br><br>"
                + "<span style='font-size:14px'>" + hint + "</span></div></html>", color);
        b.addActionListener(e -> action.run());
        return b;
    }

    @Override void refresh() {
        dateLabel.setText("Today: " + ShopService.today());
    }
}
