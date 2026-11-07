package view;

import controller.CrossTableController;
import model.ResultRules;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * View for displaying and printing the cross table (Kreuztabelle).
 *
 * <p>A modeless dialog owned by the tournament window, so it closes together with it. Rows and
 * columns represent ranked players; each cell shows the result of the row player against the
 * column player. Decided matches are green (won) or red (lost), entries that are not complete
 * yet are shown neutral.</p>
 */
public class CrossTableView extends JDialog {

    private static final Color WON_BG = new Color(200, 230, 201);
    private static final Color LOST_BG = new Color(255, 205, 210);
    private static final Color LOST_FG = new Color(183, 28, 28);
    private static final Color OPEN_BG = new Color(255, 243, 205);
    private static final Color DIAGONAL_BG = new Color(220, 220, 220);

    private final CrossTableController controller;
    private final List<JTable> tables = new ArrayList<>();
    private final JPanel tableArea = new JPanel(new BorderLayout());
    private JTabbedPane tabs;

    /**
     * Constructs the cross table dialog.
     *
     * @param owner          the tournament window
     * @param controller     the controller providing the table models and print logic
     * @param tournamentName the name of the tournament, shown in the title
     */
    public CrossTableView(Window owner, CrossTableController controller, String tournamentName) {
        super(owner, "Kreuztabelle – " + tournamentName, ModalityType.MODELESS);
        this.controller = controller;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        UITheme.sizeToScreen(this, 900, 500);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        contentPane.setBackground(UITheme.BACKGROUND);
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contentPane);

        tableArea.setBackground(UITheme.BACKGROUND);
        contentPane.add(tableArea, BorderLayout.CENTER);
        rebuild();

        JButton printButton = UITheme.createPrimaryButton("Kreuztabelle drucken");
        printButton.addActionListener(_ -> controller.printCrossTable(getSelectedSection()));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        buttonPanel.setBackground(UITheme.BACKGROUND);
        buttonPanel.add(printButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Rebuilds the tables from the controller, e.g. after new results were entered. With more
     * than one section (groups) every section gets its own tab; the selected tab is kept.
     */
    public void rebuild() {
        int selected = getSelectedSection();
        tables.clear();
        tableArea.removeAll();
        int count = controller.getSectionCount();
        tabs = count > 1 ? new JTabbedPane() : null;
        for (int i = 0; i < count; i++) {
            JTable table = new JTable();
            TableModel model = controller.buildTableModel(i, false);
            table.setModel(model);
            configureTable(table, model.getColumnCount() - 1, false);
            tables.add(table);

            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(UITheme.SURFACE);
            card.setBorder(UITheme.cardBorder(controller.getSectionTitle(i)));
            card.add(UITheme.neutralViewport(new JScrollPane(table)), BorderLayout.CENTER);
            if (tabs != null) tabs.addTab(controller.getSectionTitle(i), card); else tableArea.add(card, BorderLayout.CENTER);
        }
        if (tabs != null) {
            tableArea.add(tabs, BorderLayout.CENTER);
            if (selected < count) tabs.setSelectedIndex(selected);
        }
        tableArea.revalidate();
        tableArea.repaint();
    }

    /**
     * Returns the index of the shown section (group).
     *
     * @return the selected section, 0 without tabs
     */
    public int getSelectedSection() {
        return tabs == null ? 0 : Math.max(0, tabs.getSelectedIndex());
    }

    /**
     * Returns the cross table of the shown section.
     *
     * @return the table
     */
    public JTable getCrossTable() {
        return tables.get(getSelectedSection());
    }

    /**
     * Creates a compact, unrealized table for printing: small font and narrow columns, so that
     * larger fields still fit on a landscape page in a readable size.
     *
     * @param model a model built with short names
     * @return a table sized to its preferred size, ready for {@link JTable#getPrintable}
     */
    public static JTable createPrintTable(TableModel model) {
        JTable table = new JTable(model);
        configureTable(table, model.getColumnCount() - 1, true);
        table.setSize(table.getPreferredSize());
        table.getTableHeader().setSize(table.getTableHeader().getPreferredSize());
        table.doLayout();
        return table;
    }

    /**
     * Applies styling to the cross table.
     *
     * @param table       the table to configure
     * @param playerCount the number of players (used to identify diagonal cells)
     * @param compact     {@code true} for the print layout
     */
    private static void configureTable(JTable table, int playerCount, boolean compact) {
        UITheme.applyTableStyling(table);
        Font font = compact ? UITheme.FONT_BODY.deriveFont(9f) : UITheme.FONT_BODY;
        table.setRowHeight(compact ? 16 : 26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        if (compact) {
            // the screen spacing (10 px) would leave no room for "3:1" in the narrow print columns
            table.setIntercellSpacing(new Dimension(1, 1));
        }
        table.getTableHeader().setFont(compact ? font.deriveFont(Font.BOLD) : table.getTableHeader().getFont());

        // the name column is as wide as the longest name (doubles names are long)
        FontMetrics fm = table.getFontMetrics(font);
        int longest = 0;
        for (int row = 0; row < table.getRowCount(); row++) {
            longest = Math.max(longest, fm.stringWidth(String.valueOf(table.getValueAt(row, 0))));
        }
        int nameWidth = Math.max(compact ? 100 : 200, longest + (compact ? 14 : 30));
        table.getColumnModel().getColumn(0).setPreferredWidth(nameWidth);
        table.getColumnModel().getColumn(0).setMinWidth(nameWidth);
        for (int col = 1; col <= playerCount; col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(compact ? 32 : 50);
            table.getColumnModel().getColumn(col).setMinWidth(compact ? 32 : 40);
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(font);
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
                if (column == 0) {
                    setBorder(BorderFactory.createEmptyBorder(0, compact ? 3 : 8, 0, compact ? 3 : 8));
                }
                if (isSelected) {
                    return this;
                }
                Color stripe = row % 2 == 0 ? UITheme.SURFACE : UITheme.ROW_ALT;
                String text = value == null ? "" : value.toString();
                if (column == 0) {
                    setBackground(stripe);
                    setForeground(UITheme.TEXT_PRIMARY);
                } else if (column - 1 == row) {
                    setBackground(DIAGONAL_BG);
                    setForeground(UITheme.TEXT_MUTED);
                } else if (text.isEmpty()) {
                    setBackground(stripe);
                    setForeground(UITheme.TEXT_MUTED);
                } else if (ResultRules.isDecidedOverallResult(text)) {
                    int[] sets = ResultRules.parseOverallResult(text);
                    boolean won = sets[0] > sets[1];
                    setBackground(won ? WON_BG : LOST_BG);
                    setForeground(won ? UITheme.SUCCESS : LOST_FG);
                } else {
                    // entered, but not complete yet: neutral, no win/loss colour
                    setBackground(OPEN_BG);
                    setForeground(UITheme.TEXT_MUTED);
                }
                return this;
            }
        });
    }

}
