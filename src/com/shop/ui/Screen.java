package com.shop.ui;

import javax.swing.*;
import java.awt.*;

/** One full screen of the app (Home, Bill, Stock ...). refresh() reloads its data from the database. */
abstract class Screen extends JPanel {

    Screen() {
        super(new BorderLayout(0, 12));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
    }

    /** Called every time the screen is shown, so it always displays the latest data. */
    abstract void refresh();
}
