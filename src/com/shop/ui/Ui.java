package com.shop.ui;

import com.shop.ShopService.ShopException;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.function.Supplier;

/** Colors, fonts and small helpers shared by all the screens. */
public class Ui {

    public static final Color GREEN = new Color(0x1F6F54);
    public static final Color AMBER = new Color(0xB86E0F);
    public static final Color BLUE  = new Color(0x2F5D8A);
    public static final Color BRICK = new Color(0xA8483A);
    public static final Color BG    = new Color(0xF6F3EC);
    public static final Color LINE  = new Color(0xDDD6C8);
    public static final Color TEXT  = new Color(0x222222);
    public static final Color MUTED = new Color(0x6B6B6B);

    public static final Font BASE  = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font BOLD  = BASE.deriveFont(Font.BOLD);
    public static final Font TITLE = BASE.deriveFont(Font.BOLD, 20f);
    public static final Font BIG   = BASE.deriveFont(Font.BOLD, 28f);

    // ───────────────────────── buttons ─────────────────────────

    /** A button that paints its own colored background, so it looks the same on every computer. */
    public static class FlatButton extends JButton {
        private final Color base;

        public FlatButton(String text, Color base) {
            super(text);
            this.base = base;
            setFont(BOLD);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (!isEnabled()) c = new Color(0xBDBDBD);
            else if (getModel().isPressed()) c = base.darker().darker();
            else if (getModel().isRollover()) c = base.darker();
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static JButton button(String text, Color color) { return new FlatButton(text, color); }

    // ───────────────────────── labels, fields, panels ─────────────────────────

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(TITLE);
        l.setForeground(TEXT);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(BASE);
        l.setForeground(MUTED);
        return l;
    }

    public static JTextField field(int columns) { return field("", columns); }

    public static JTextField field(String text, int columns) {
        JTextField f = new JTextField(text, columns);
        f.setFont(BASE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return f;
    }

    /** Runs 'action' every time the text in the field changes (used for live previews). */
    public static void onChange(javax.swing.text.JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        });
    }

    /** Puts the cursor in this field as soon as its dialog opens. */
    public static void focusWhenShown(JComponent field) {
        field.addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorAdded(javax.swing.event.AncestorEvent e) {
                SwingUtilities.invokeLater(field::requestFocusInWindow);
            }
            public void ancestorRemoved(javax.swing.event.AncestorEvent e) { }
            public void ancestorMoved(javax.swing.event.AncestorEvent e) { }
        });
    }

    /** A white rounded-looking box with a thin border, used to group things on a screen. */
    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        return p;
    }

    /** A simple two-column "label : field" form. */
    public static class Form extends JPanel {
        private int row = 0;

        public Form() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        public void add(String label, JComponent field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.insets = new Insets(5, 5, 5, 5);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0;
            JLabel l = new JLabel(label);
            l.setFont(BASE);
            add(l, c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            field.setFont(BASE);
            add(field, c);
            row++;
        }

        public void addWide(JComponent comp) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.gridwidth = 2;
            c.insets = new Insets(5, 5, 5, 5);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            add(comp, c);
        }
    }

    // ───────────────────────── tables ─────────────────────────

    /** A table model the user cannot type into. */
    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setFont(BASE);
        t.setRowHeight(30);
        t.setGridColor(LINE);
        t.setShowVerticalLines(false);
        t.setSelectionBackground(new Color(0xCFE6DC));
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setDefaultRenderer(Object.class, new PaddedRenderer(SwingConstants.LEFT));
        JTableHeader h = t.getTableHeader();
        h.setFont(BOLD);
        h.setBackground(new Color(0xEFE9DC));
        h.setForeground(TEXT);
        h.setReorderingAllowed(false);
        return t;
    }

    public static JScrollPane scroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    /** Table cell with some space on the left and right, so text does not touch the cell edge. */
    public static class PaddedRenderer extends DefaultTableCellRenderer {
        public PaddedRenderer(int alignment) { setHorizontalAlignment(alignment); }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                boolean focus, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));   // after super: it resets the border
            return comp;
        }
    }

    public static void alignRight(JTable table, int... columns) {
        PaddedRenderer r = new PaddedRenderer(SwingConstants.RIGHT);
        for (int c : columns) table.getColumnModel().getColumn(c).setCellRenderer(r);
    }

    public static void widths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ───────────────────────── reading what the user typed ─────────────────────────

    public static double num(String text, String name) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new ShopException(name + " is required.");
        try { return Double.parseDouble(t); }
        catch (NumberFormatException e) { throw new ShopException(name + " must be a number."); }
    }

    public static int whole(String text, String name) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new ShopException(name + " is required.");
        try { return Integer.parseInt(t); }
        catch (NumberFormatException e) { throw new ShopException(name + " must be a whole number."); }
    }

    /** Empty or 0 means "none" (returns null). */
    public static Double optionalNum(String text, String name) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return null;
        double v = num(t, name);
        return v == 0 ? null : v;
    }

    /** Empty means 0. */
    public static int wholeOrZero(String text, String name) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? 0 : whole(t, name);
    }

    public static double numOrZero(String text, String name) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? 0 : num(t, name);
    }

    public static String rs(double amount) { return String.format("Rs. %,.2f", amount); }

    // ───────────────────────── dialogs ─────────────────────────

    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Shows a form in a dialog. When the user presses OK, 'read' turns the fields into a result.
     * If 'read' throws a ShopException (for example "Brand name cannot be empty."), the message is
     * shown and the same form stays open with everything the user typed. Returns null on Cancel.
     */
    public static <T> T ask(Component parent, String title, JComponent content, Supplier<T> read) {
        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, content, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            try {
                return read.get();
            } catch (ShopException e) {
                warn(parent, e.getMessage());
            }
        }
    }

    /** Runs an action; any problem is shown to the user in a message box instead of crashing. */
    public static void run(Component parent, Runnable action) {
        try {
            action.run();
        } catch (ShopException e) {
            warn(parent, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            warn(parent, "Something went wrong: " + e.getMessage());
        }
    }
}
