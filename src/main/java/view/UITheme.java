package view;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Central UI theme provider for the application.
 *
 * <p>Defines the shared color palette, typography, and factory methods for creating
 * consistently styled Swing components. All fields and methods are static; this class
 * cannot be instantiated.</p>
 */
public final class UITheme {

    private UITheme() {}

    /** Primary brand color used for buttons and header backgrounds. */
    public static final Color PRIMARY      = new Color(25, 118, 210);
    /** Success/positive accent color used for total-result rows. */
    public static final Color SUCCESS      = new Color(46, 125, 50);
    /** Application window background color. */
    public static final Color BACKGROUND   = new Color(245, 247, 250);
    /** Card and table surface color. */
    public static final Color SURFACE      = Color.WHITE;
    /** Alternating row highlight color for tables and lists. */
    public static final Color ROW_ALT      = new Color(232, 240, 254);
    /** Default foreground color for body text. */
    public static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    /** Muted foreground color for secondary labels. */
    public static final Color TEXT_MUTED   = new Color(97, 97, 97);
    /** Color used for borders and dividers. */
    public static final Color BORDER_COLOR = new Color(210, 215, 220);
    /** Background color for table header rows. */
    public static final Color HEADER_BG    = new Color(30, 80, 160);
    /** Foreground color for table header text. */
    public static final Color HEADER_FG    = Color.WHITE;

    /** Large bold font for window and section titles. */
    public static final Font FONT_TITLE    = new Font("Segoe UI", Font.BOLD, 22);
    /** Medium bold font for sub-headings and table headers. */
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    /** Standard body font used throughout the application. */
    public static final Font FONT_BODY     = new Font("Segoe UI", Font.PLAIN, 13);
    /** Monospaced font used in the pairings text area. */
    public static final Font FONT_MONO     = new Font("Consolas", Font.PLAIN, 13);

    /**
     * Sets the application icon (table tennis bat and ball) for a window. Dialogs owned by the
     * window show the same icon.
     *
     * @param window the window
     */
    public static void applyAppIcon(Window window) {
        java.util.List<Image> icons = new java.util.ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            java.net.URL url = UITheme.class.getResource("/icons/app-" + size + ".png");
            if (url != null) icons.add(new ImageIcon(url).getImage());
        }
        if (!icons.isEmpty()) window.setIconImages(icons);
    }

    /**
     * Sets the window size, limited to the usable screen area (without taskbar), so windows also
     * fit on small laptop screens such as 1366x768.
     *
     * @param window the window
     * @param width  the desired width
     * @param height the desired height
     */
    public static void sizeToScreen(Window window, int width, int height) {
        Rectangle usable;
        try {
            usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        } catch (HeadlessException e) {
            usable = new Rectangle(0, 0, width, height);
        }
        window.setSize(Math.min(width, usable.width - 20), Math.min(height, usable.height - 20));
    }

    /**
     * Applies the Nimbus Look and Feel to the application.
     * Silently ignores any exceptions if Nimbus is unavailable.
     */
    public static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception _) {}
    }

    /**
     * Creates a styled primary action button with the application's brand color.
     *
     * @param text the button label
     * @return the configured {@link JButton}
     */
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

    /**
     * Creates a styled secondary action button with a neutral outlined appearance,
     * visually distinct from the primary button.
     *
     * @param text the button label
     * @return the configured {@link JButton}
     */
    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY);
        btn.setBackground(SURFACE);
        btn.setForeground(PRIMARY);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY, 1, true),
            BorderFactory.createEmptyBorder(8, 17, 8, 17)
        ));
        return btn;
    }

    /**
     * Creates a compound border suitable for named card panels.
     * Combines a titled line border with inner padding.
     *
     * @param title the title to display in the border
     * @return the compound {@link Border}
     */
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

    /**
     * Applies the application's standard styling to a {@link JTable}, including font,
     * row height, grid appearance, selection colors, and an alternating row renderer.
     *
     * @param table the table to style
     */
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
        // the area right of the last column stays neutral; only the header cells are blue
        header.setBackground(SURFACE);
        header.setForeground(HEADER_FG);
        header.setPreferredSize(new Dimension(header.getWidth(), 34));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());

        table.setFillsViewportHeight(true); // no grey area below the last row
        table.setDefaultRenderer(Object.class, new AlternatingRowRenderer());
    }

    /**
     * Sets a neutral background for the visible area of a table's scroll pane, so the space next
     * to or below a narrow table is not grey.
     *
     * @param scrollPane the scroll pane containing a table
     * @return the same scroll pane
     */
    public static JScrollPane neutralViewport(JScrollPane scrollPane) {
        scrollPane.getViewport().setBackground(SURFACE);
        return scrollPane;
    }

    /**
     * Header renderer that paints every header cell in the header colour with white bold text,
     * independent of the look and feel (Nimbus would paint grey cells).
     */
    public static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(true);
            setBackground(HEADER_BG);
            setForeground(HEADER_FG);
            setFont(table.getTableHeader().getFont());
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(70, 110, 180)),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)));
            return this;
        }
    }

    /**
     * Sizes all columns in the table to fit their content, leaving the name column
     * to absorb any remaining width via auto-resize.
     *
     * @param table           the table whose columns should be resized
     * @param nameColumnIndex the zero-based column index of the name column (will not be resized)
     */
    public static void setNameColumnWide(JTable table, int nameColumnIndex) {
        // all other columns have a fixed width, so the name column takes up the remaining width
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        int nameContentWidth = 0;
        for (int col = 0; col < table.getColumnCount(); col++) {
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
            if (col == nameColumnIndex) {
                nameContentWidth = width + 12;
            } else {
                int finalWidth = width + 12;
                table.getColumnModel().getColumn(col).setMinWidth(finalWidth);
                table.getColumnModel().getColumn(col).setMaxWidth(finalWidth);
                table.getColumnModel().getColumn(col).setPreferredWidth(finalWidth);
            }
        }
        if (nameContentWidth > 0) {
            // the name may be shortened ("…") in a narrow window; the number columns stay visible
            table.getColumnModel().getColumn(nameColumnIndex).setMinWidth(Math.min(nameContentWidth, 180));
            table.getColumnModel().getColumn(nameColumnIndex).setPreferredWidth(nameContentWidth);
        }
    }

    /**
     * Table cell renderer that applies alternating row background colors and consistent
     * padding and font settings across all cells.
     */
    public static class AlternatingRowRenderer extends DefaultTableCellRenderer {
        /**
         * {@inheritDoc}
         * Applies alternating row colors ({@link #SURFACE} and {@link #ROW_ALT}) to unselected
         * cells and adds left/right padding.
         */
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
