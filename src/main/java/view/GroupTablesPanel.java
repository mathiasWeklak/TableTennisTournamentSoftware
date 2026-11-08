package view;

import controller.GroupTables;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the group tables of a group/knockout tournament: one box per group with the result
 * matrix; participants who reach the knockout stage are highlighted. The boxes wrap next to each
 * other when there is room. The same boxes are used for printing.
 */
public class GroupTablesPanel extends JPanel implements Scrollable {

    /** Background of participants who reach the knockout stage. */
    public static final Color QUALIFIED = new Color(220, 239, 221);
    private static final Color DIAGONAL = new Color(120, 126, 134);
    private static final Color WITHDRAWN = new Color(150, 150, 150);
    private static final int RESULT_WIDTH = 42;

    private final JLabel legend = new JLabel();
    private final JPanel boxes = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 10));

    /**
     * Creates an empty panel.
     */
    public GroupTablesPanel() {
        super(new BorderLayout());
        setBackground(UITheme.SURFACE);
        boxes.setBackground(UITheme.SURFACE);
        legend.setFont(UITheme.FONT_BODY);
        legend.setForeground(UITheme.TEXT_MUTED);
        legend.setBorder(BorderFactory.createEmptyBorder(4, 14, 6, 14));
        add(boxes, BorderLayout.CENTER);
        add(legend, BorderLayout.SOUTH);
    }

    /**
     * Shows the given tables.
     *
     * @param tables     the group tables
     * @param legendText explanation of the highlighting
     */
    public void setTables(List<GroupTables.Table> tables, String legendText) {
        boxes.removeAll();
        for (GroupTables.Table t : tables) {
            boxes.add(createBox(t));
        }
        legend.setText(legendText);
        revalidate();
        repaint();
    }

    /**
     * Returns the number of group boxes shown.
     *
     * @return the number of groups
     */
    public int getGroupCount() {
        return boxes.getComponentCount();
    }

    /**
     * Creates the box of one group: title, table header and table.
     *
     * @param t the group table
     * @return the box
     */
    static JPanel createBox(GroupTables.Table t) {
        JTable table = createTable(t);
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(UITheme.SURFACE);
        box.setBorder(UITheme.cardBorder(t.title()));
        JTableHeader header = table.getTableHeader();
        box.add(header, BorderLayout.NORTH);
        box.add(table, BorderLayout.CENTER);
        return box;
    }

    /**
     * Creates the styled table of one group.
     *
     * @param t the group table
     * @return the table
     */
    static JTable createTable(GroupTables.Table t) {
        DefaultTableModel model = new DefaultTableModel(t.columns(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        t.rows().forEach(model::addRow);
        JTable table = new JTable(model);
        UITheme.applyTableStyling(table);
        table.setShowVerticalLines(true);
        table.setFillsViewportHeight(false);
        table.setRowSelectionAllowed(false);
        table.setFocusable(false);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setDefaultRenderer(Object.class, new CellRenderer(t));
        table.getTableHeader().setDefaultRenderer(new CenteredHeaderRenderer());
        table.getTableHeader().setFont(UITheme.FONT_BODY.deriveFont(Font.BOLD));
        table.setRowHeight(26);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int width = 0;
        for (int c = 0; c < table.getColumnCount(); c++) {
            TableColumn column = table.getColumnModel().getColumn(c);
            int w = t.isResultColumn(c) ? RESULT_WIDTH : contentWidth(table, c) + 4;
            column.setPreferredWidth(w);
            column.setMinWidth(w);
            width += w;
        }
        table.setPreferredSize(new Dimension(width, table.getRowHeight() * table.getRowCount()));
        table.getTableHeader().setPreferredSize(new Dimension(width, 28));
        return table;
    }

    private static int contentWidth(JTable table, int column) {
        int max = 0;
        for (int row = -1; row < table.getRowCount(); row++) {
            Component c = row < 0
                    ? table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
                    table, table.getColumnName(column), false, false, -1, column)
                    : table.getCellRenderer(row, column).getTableCellRendererComponent(
                    table, table.getValueAt(row, column), false, false, row, column);
            max = Math.max(max, c.getPreferredSize().width);
        }
        return max;
    }

    /**
     * Returns a printable with all group boxes stacked, scaled to the page width (never enlarged);
     * a page break is only made between groups.
     *
     * @param tables the group tables (compact names recommended)
     * @param title  the heading on every page
     * @return the printable
     */
    public static Printable printable(List<GroupTables.Table> tables, String title) {
        List<JComponent> parts = new ArrayList<>();
        for (GroupTables.Table t : tables) {
            JPanel box = createBox(t);
            box.setSize(box.getPreferredSize());
            layoutTree(box);
            parts.add(box);
        }
        return new Printable() {
            private List<List<JComponent>> pages;
            private double scale;

            @Override
            public int print(Graphics graphics, PageFormat pf, int pageIndex) {
                double headerHeight = 28;
                if (pages == null) {
                    double maxWidth = parts.stream().mapToDouble(JComponent::getWidth).max().orElse(1);
                    scale = Math.min(1.0, pf.getImageableWidth() / maxWidth);
                    double available = (pf.getImageableHeight() - headerHeight) / scale;
                    pages = new ArrayList<>();
                    List<JComponent> page = new ArrayList<>();
                    double used = 0;
                    for (JComponent part : parts) {
                        double h = part.getHeight() + 10;
                        if (!page.isEmpty() && used + h > available) {
                            pages.add(page);
                            page = new ArrayList<>();
                            used = 0;
                        }
                        page.add(part);
                        used += h;
                    }
                    if (!page.isEmpty() || pages.isEmpty()) pages.add(page);
                }
                if (pageIndex >= pages.size()) return NO_SUCH_PAGE;
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(pf.getImageableX(), pf.getImageableY());
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                String heading = title + (pages.size() > 1 ? "  (Seite " + (pageIndex + 1) + "/" + pages.size() + ")" : "");
                g.drawString(heading, 0, 16);
                g.translate(0, headerHeight);
                g.scale(scale, scale);
                int y = 0;
                for (JComponent part : pages.get(pageIndex)) {
                    Graphics2D pg = (Graphics2D) g.create(0, y, part.getWidth(), part.getHeight());
                    part.printAll(pg);
                    pg.dispose();
                    y += part.getHeight() + 10;
                }
                g.dispose();
                return PAGE_EXISTS;
            }
        };
    }

    /** Lays out a component tree that is not displayed (for printing). */
    private static void layoutTree(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layoutTree(child);
        }
    }

    // --- Scrollable: follow the viewport width so the boxes wrap ---------------------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(16, visibleRect.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // follow the viewport (boxes wrap) as long as the widest group fits, otherwise scroll
        return getParent() == null || getParent().getWidth() >= minimumContentWidth();
    }

    private int minimumContentWidth() {
        int widest = 0;
        for (Component box : boxes.getComponents()) widest = Math.max(widest, box.getPreferredSize().width);
        return widest + 2 * 12 + getInsets().left + getInsets().right;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, minimumContentWidth()), d.height);
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }

    // --- rendering ---------------------------------------------------------------------------

    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final GroupTables.Table t;

        CellRenderer(GroupTables.Table t) {
            this.t = t;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            boolean last = column == table.getColumnCount() - 1;
            setFont(table.getFont().deriveFont(last || column == table.getColumnCount() - 2 ? Font.BOLD : Font.PLAIN));
            if (t.isDiagonal(row, column)) {
                setBackground(DIAGONAL);
            } else if (t.qualified()[row]) {
                setBackground(QUALIFIED);
            } else {
                setBackground(UITheme.SURFACE);
            }
            setForeground(t.withdrawn()[row] ? WITHDRAWN : UITheme.TEXT_PRIMARY);
            return this;
        }
    }

    private static final class CenteredHeaderRenderer extends UITheme.HeaderRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(70, 110, 180)),
                    BorderFactory.createEmptyBorder(0, 6, 0, 6)));
            return this;
        }
    }
}
