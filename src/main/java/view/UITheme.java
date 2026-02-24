package view;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

public final class UITheme {

    private UITheme() {}

    public static final Color PRIMARY      = new Color(25, 118, 210);
    public static final Color PRIMARY_DARK = new Color(13, 71, 161);
    public static final Color SUCCESS      = new Color(46, 125, 50);
    public static final Color DANGER       = new Color(198, 40, 40);
    public static final Color BACKGROUND   = new Color(245, 247, 250);
    public static final Color SURFACE      = Color.WHITE;
    public static final Color ROW_ALT      = new Color(232, 240, 254);
    public static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    public static final Color TEXT_MUTED   = new Color(97, 97, 97);
    public static final Color BORDER_COLOR = new Color(210, 215, 220);
    public static final Color HEADER_BG    = new Color(30, 80, 160);
    public static final Color HEADER_FG    = Color.WHITE;

    public static final Font FONT_TITLE    = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY     = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_MONO     = new Font("Consolas", Font.PLAIN, 13);

    public static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {}
    }

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY.deriveFont(Font.BOLD));
        btn.setBackground(PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        return createPrimaryButton(text);
    }

    public static JButton createDangerButton(String text) {
        return createPrimaryButton(text);
    }

    public static Border cardBorder(String title) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                title,
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                FONT_SUBTITLE,
                TEXT_PRIMARY
            ),
            BorderFactory.createEmptyBorder(4, 8, 8, 8)
        );
    }

    public static void applyTableStyling(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(28);
        table.setIntercellSpacing(new Dimension(10, 2));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER_COLOR);
        table.setBackground(SURFACE);
        table.setSelectionBackground(new Color(187, 222, 251));
        table.setSelectionForeground(TEXT_PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_SUBTITLE);
        header.setBackground(HEADER_BG);
        header.setForeground(HEADER_FG);
        header.setPreferredSize(new Dimension(header.getWidth(), 34));
        header.setReorderingAllowed(false);

        table.setDefaultRenderer(Object.class, new AlternatingRowRenderer());
    }

    public static void setNameColumnWide(JTable table, int nameColumnIndex) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        for (int col = 0; col < table.getColumnCount(); col++) {
            if (col == nameColumnIndex) continue;
            int width = 0;
            for (int row = -1; row < table.getRowCount(); row++) {
                java.awt.Component c;
                if (row == -1) {
                    c = table.getTableHeader().getDefaultRenderer()
                            .getTableCellRendererComponent(table,
                                    table.getColumnModel().getColumn(col).getHeaderValue(),
                                    false, false, -1, col);
                } else {
                    c = table.getCellRenderer(row, col)
                            .getTableCellRendererComponent(table, table.getValueAt(row, col),
                                    false, false, row, col);
                }
                width = Math.max(width, c.getPreferredSize().width);
            }
            int finalWidth = width + 12;
            table.getColumnModel().getColumn(col).setPreferredWidth(finalWidth);
            table.getColumnModel().getColumn(col).setMaxWidth(finalWidth + 20);
        }
    }

    public static class AlternatingRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(FONT_BODY);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? SURFACE : ROW_ALT);
                setForeground(TEXT_PRIMARY);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }
}
