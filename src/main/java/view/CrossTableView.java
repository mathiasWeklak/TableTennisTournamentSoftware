package view;

import controller.CrossTableController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * View for displaying and printing the cross table (Kreuztabelle).
 *
 * <p>Shows a matrix where rows and columns represent ranked players. Each cell displays
 * the set result of the row player against the column player, the diagonal is marked "-".</p>
 */
public class CrossTableView extends JFrame {

    private final JTable crossTable;

    /**
     * Constructs the cross table view.
     *
     * @param controller     the controller providing the table model and print logic
     * @param tournamentName the name of the tournament, shown in the title
     */
    public CrossTableView(CrossTableController controller, String tournamentName) {
        setTitle("Kreuztabelle – " + tournamentName);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 500);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        contentPane.setBackground(UITheme.BACKGROUND);
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contentPane);

        crossTable = new JTable(controller.buildTableModel());
        configureTable(crossTable, controller.getPlayerCount());

        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(UITheme.SURFACE);
        tableCard.setBorder(UITheme.cardBorder("Kreuztabelle"));
        tableCard.add(crossTable.getTableHeader(), BorderLayout.NORTH);
        tableCard.add(new JScrollPane(crossTable), BorderLayout.CENTER);
        contentPane.add(tableCard, BorderLayout.CENTER);

        JButton printButton = UITheme.createPrimaryButton("Kreuztabelle drucken");
        printButton.addActionListener(_ -> controller.printCrossTable());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        buttonPanel.setBackground(UITheme.BACKGROUND);
        buttonPanel.add(printButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Applies styling to the cross table. The diagonal cells and result cells are
     * colored to distinguish wins, losses, and unplayed matches.
     *
     * @param table       the table to configure
     * @param playerCount the number of players (used to identify diagonal cells)
     */
    private void configureTable(JTable table, int playerCount) {
        UITheme.applyTableStyling(table);
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(0).setMinWidth(160);
        for (int col = 1; col <= playerCount; col++) {
            table.getColumnModel().getColumn(col).setPreferredWidth(50);
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(UITheme.FONT_BODY);
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
                if (column == 0) {
                    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                }
                if (!isSelected) {
                    if (column == 0) {
                        setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.ROW_ALT);
                        setForeground(UITheme.TEXT_PRIMARY);
                    } else if (column - 1 == row) {
                        setBackground(new Color(220, 220, 220));
                        setForeground(UITheme.TEXT_MUTED);
                    } else {
                        String text = value == null ? "" : value.toString();
                        if (text.isEmpty()) {
                            setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.ROW_ALT);
                            setForeground(UITheme.TEXT_MUTED);
                        } else {
                            String[] parts = text.split(":");
                            boolean won = parts.length == 2
                                    && !parts[0].isBlank() && !parts[1].isBlank()
                                    && Integer.parseInt(parts[0].trim()) > Integer.parseInt(parts[1].trim());
                            if (won) {
                                setBackground(new Color(200, 230, 201));
                                setForeground(UITheme.SUCCESS);
                            } else {
                                setBackground(new Color(255, 205, 210));
                                setForeground(new Color(183, 28, 28));
                            }
                        }
                    }
                }
                return this;
            }
        });
    }

    /**
     * Returns the cross table component for printing.
     *
     * @return the {@link JTable} backing the cross table
     */
    public JTable getCrossTable() {
        return crossTable;
    }
}
