package view;

import controller.RefereeSheetsController;
import model.Match;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class RefereeSheetsView extends JFrame {

    private final RefereeSheetsController controller;

    public RefereeSheetsView(RefereeSheetsController controller, List<Match> matches) {
        this.controller = controller;
        initializeUI(matches);
    }

    private void initializeUI(List<Match> matches) {
        setTitle("Schiedsrichterzettel");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 520);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        contentPane.setBackground(UITheme.BACKGROUND);
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(contentPane);

        JButton printButton = UITheme.createPrimaryButton("Schiedsrichterzettel drucken");
        printButton.addActionListener(e -> controller.printRefereeSheets());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        buttonPanel.setBackground(UITheme.BACKGROUND);
        buttonPanel.add(printButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY);
        contentPane.add(tabbedPane, BorderLayout.CENTER);

        matches.forEach(match -> {
            JPanel matchPanel = new JPanel(new BorderLayout(0, 0));
            matchPanel.setBackground(UITheme.BACKGROUND);
            matchPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel tableCard = new JPanel(new BorderLayout());
            tableCard.setBackground(UITheme.SURFACE);
            tableCard.setBorder(UITheme.cardBorder("Tisch " + match.getTableNumber()));

            JTable table = new JTable(controller.createRefereeSheetTableModel(match));
            configureTable(table);

            tableCard.add(table.getTableHeader(), BorderLayout.NORTH);
            tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

            matchPanel.add(tableCard, BorderLayout.CENTER);
            tabbedPane.addTab("Tisch " + match.getTableNumber(), matchPanel);
        });

        setVisible(true);
    }

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
                    if (row == 0) {
                        setBackground(new Color(232, 240, 254));
                        setForeground(UITheme.PRIMARY);
                        setFont(UITheme.FONT_SUBTITLE);
                    } else if (row == 6) {
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
