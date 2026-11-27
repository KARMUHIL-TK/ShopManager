package com.shop.ui;

import com.shop.Database;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

/** Start the desktop app from here. */
public class App {

    public static void main(String[] args) {
        Database.initialize();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            // Use the same readable font everywhere (also in message boxes).
            for (Object key : Collections.list(UIManager.getDefaults().keys())) {
                if (key.toString().endsWith(".font")) UIManager.put(key, Ui.BASE);
            }

            JFrame frame = new JFrame("Shop Manager");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new MainPanel());
            frame.setSize(1180, 740);
            frame.setMinimumSize(new Dimension(1000, 640));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
