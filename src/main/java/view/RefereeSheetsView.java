package view;

import controller.RefereeSheetsController;
import model.Match;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * View for previewing and printing referee sheets (Schiedsrichterzettel).
 *
 * <p>Shown as a modeless dialog owned by the tournament window, so it is closed together with it.
 * Displays the tournament name and round as heading and one tab per match, each containing a
 * read-only table with player names, set rows, and a total-result row. Tabs are labelled with
 * table number and last names (e.g. {@code "T1: Müller – Meier"}), since tables can repeat
 * within a round. A print button delegates to {@link RefereeSheetsController#printRefereeSheets()}.</p>
 */
public class RefereeSheetsView extends JDialog {

    private final RefereeSheetsController controller;

    /**
     * Constructs the referee sheets view as a modeless dialog and immediately displays it.
     *
     * @param owner      the window owning this dialog (closing it also closes this dialog); may be {@code null}
     * @param controller the controller providing the table model and print logic
     * @param matches    the list of matches to display sheets for
     */
    public RefereeSheetsView(Window owner, RefereeSheetsController controller, List<Match> matches) {
        super(owner, "Schiedsrichterzettel", ModalityType.MODELESS);
        this.controller = controller;
        initializeUI(matches);
    }

    /**
     * Builds and displays the dialog with a heading and a tabbed pane containing one sheet per match.
     *
     * @param matches the list of matches to render
     */
    private void initializeUI(List<Match> matches) {
        setTitle("Schiedsrichterzettel – " + controller.getHeaderText());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        UITheme.sizeToScreen(this, 700, 560);
        setLocationRelativeTo(getOwner());
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        contentPane.setBackground(UITheme.BACKGROUND);
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contentPane);

        JButton printButton = UITheme.createPrimaryButton("Schiedsrichterzettel drucken");
        printButton.addActionListener(_ -> controller.printRefereeSheets());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        buttonPanel.setBackground(UITheme.BACKGROUND);
        buttonPanel.add(printButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        JLabel headerLabel = new JLabel(controller.getHeaderText());
        headerLabel.setFont(UITheme.FONT_SUBTITLE);
        headerLabel.setForeground(UITheme.PRIMARY);
        headerLabel.setBorder(new EmptyBorder(0, 2, 8, 2));
        contentPane.add(headerLabel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY);
        contentPane.add(tabbedPane, BorderLayout.CENTER);

        matches.forEach(match -> {
            String sheetLabel = controller.getSheetLabel(match);
            JPanel matchPanel = new JPanel(new BorderLayout(0, 0));
            matchPanel.setBackground(UITheme.BACKGROUND);
            matchPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel tableCard = new JPanel(new BorderLayout());
            tableCard.setBackground(UITheme.SURFACE);
            tableCard.setBorder(UITheme.cardBorder(sheetLabel));

            JTable table = new JTable(controller.createRefereeSheetTableModel(match));
            configureTable(table);

            tableCard.add(table.getTableHeader(), BorderLayout.NORTH);
            tableCard.add(UITheme.neutralViewport(new JScrollPane(table)), BorderLayout.CENTER);

            matchPanel.add(tableCard, BorderLayout.CENTER);
            tabbedPane.addTab(sheetLabel, matchPanel);
            tabbedPane.setToolTipTextAt(tabbedPane.getTabCount() - 1,
                    "Tisch " + match.getTableNumber() + ": " + match.getFirstPlayer().getFullName()
                            + " – " + match.getSecondPlayer().getFullName());
        });

        setVisible(true);
    }

    /**
     * Applies custom styling to a referee sheet table, including row-specific background colors
     * for the player header, alternating set rows, and the total-result row.
     *
     * @param table the table to configure
     */
    private void configureTable(JTable table) {
        UITheme.applyTableStyling(table);
        table.setRowHeight(34);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(UITheme.FONT_BODY);
                if (!isSelected) {
                    if (row == t.getRowCount() - 1) { // total row
                        setBackground(new Color(232, 245, 233));
                        setForeground(UITheme.SUCCESS);
                        setFont(UITheme.FONT_BODY.deriveFont(Font.BOLD));
                    } else {
                        setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.ROW_ALT);
                        setForeground(UITheme.TEXT_PRIMARY);
                    }
                }
                if (column == 0) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                    setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                }
                return this;
            }
        });
    }
}
