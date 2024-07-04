package view;

import controller.RefereeSheetsController;
import model.Match;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * This class represents the window for displaying referee sheets for matches.
 */
public class RefereeSheetsView extends JFrame {

    private final RefereeSheetsController controller;

    /**
     * Constructs a new RefereeSheetsView.
     *
     * @param controller the controller handling the logic for this view
     * @param matches the list of matches to display
     */
    public RefereeSheetsView(RefereeSheetsController controller, List<Match> matches) {
        this.controller = controller;
        initializeUI(matches);
    }

    /**
     * Initializes the user interface.
     */
    private void initializeUI(List<Match> matches) {
        setTitle("Schiedsrichterzettel");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton printButton = new JButton("Schiedsrichterzettel drucken");
        printButton.addActionListener(e -> controller.printRefereeSheets());
        buttonPanel.add(printButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        contentPane.add(tabbedPane, BorderLayout.CENTER);

        matches.forEach(match -> {
            JPanel matchPanel = new JPanel(new BorderLayout());
            matchPanel.setBorder(BorderFactory.createTitledBorder("Tisch " + match.getTableNumber()));

            JTable table = new JTable(controller.createRefereeSheetTableModel(match));
            configureTable(table);

            JScrollPane scrollPane = new JScrollPane(table);
            matchPanel.add(scrollPane, BorderLayout.CENTER);

            tabbedPane.addTab("Tisch " + match.getTableNumber(), matchPanel);
        });

        setVisible(true);
    }

    /**
     * Configures the appearance and behavior of a JTable.
     *
     * @param table the table to configure
     */
    private void configureTable(JTable table) {
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column == 0) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                }
                return c;
            }
        });
    }
}
