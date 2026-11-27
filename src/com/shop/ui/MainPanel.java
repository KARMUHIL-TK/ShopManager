package com.shop.ui;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** The whole window: a green top bar, and below it one screen at a time (CardLayout). */
public class MainPanel extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, String> headings = new LinkedHashMap<>();
    private final JLabel heading = new JLabel();
    private final JButton homeButton = Ui.button("<  Home", new Color(0x14503C));

    public MainPanel() {
        super(new BorderLayout());
        setBackground(Ui.BG);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Ui.GREEN);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        heading.setFont(Ui.TITLE.deriveFont(24f));
        heading.setForeground(Color.WHITE);
        homeButton.addActionListener(e -> show("home"));
        bar.add(heading, BorderLayout.WEST);
        bar.add(homeButton, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        register("home", "Shop Manager", new HomePanel(this::show));
        register("bill", "New Bill", new BillPanel());
        register("stock", "Stock", new StockPanel());
        register("history", "Bill History", new HistoryPanel());
        register("returns", "Returns", new ReturnPanel());
        add(body, BorderLayout.CENTER);

        show("home");
    }

    private void register(String name, String headingText, Screen screen) {
        screens.put(name, screen);
        headings.put(name, headingText);
        body.add(screen, name);
    }

    /** Shows one screen (and reloads its data first). */
    public void show(String name) {
        screens.get(name).refresh();
        heading.setText(headings.get(name));
        homeButton.setVisible(!name.equals("home"));
        cards.show(body, name);
    }
}
