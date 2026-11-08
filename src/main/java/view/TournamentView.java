package view;

import model.TournamentMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main setup view for the tournament. Allows the user to enter a tournament name,
 * the number of tables, select the tournament mode (Swiss or Round Robin), manage
 * the player list, and start the tournament.
 *
 * <p>The view is purely presentational; all logic is delegated to
 * {@link controller.TournamentController}.</p>
 */
public class TournamentView extends JFrame {
    private final JTable playerTable;
    private final javax.swing.table.DefaultTableModel playerTableModel;
    private final JLabel playerCountLabel;
    private final JLabel roundCountLabel;
    private final JLabel groupSizeLabel;
    private final JPanel groupRow;
    private final JTextField tournamentNameField;
    private final JTextField tableCountField;
    private final JComboBox<String> modeBox;
    private final JCheckBox doublesBox;
    private final JTextField roundCountField;
    private final JTextField groupSizeField;
    private final JTextField qualifiersField;
    private final JTextField bestNextField;
    private final JCheckBox thirdPlaceBox;
    private final JButton addPlayerButton;
    private final JButton editPlayerButton;
    private final JButton removePlayerButton;
    private final JButton beginTournamentButton;
    private final JMenuItem loadMenuItem;
    private final JMenuItem importMenuItem;
    private final JMenuItem exportMenuItem;

    /**
     * Constructs and lays out the tournament setup window.
     */
    public TournamentView() {
        setTitle(AppInfo.NAME + " " + AppInfo.version());
        UITheme.sizeToScreen(this, 820, 620);
        UITheme.applyAppIcon(this);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel headerLabel = new JLabel("Tischtennis Turniersoftware");
        headerLabel.setFont(UITheme.FONT_TITLE);
        headerLabel.setForeground(Color.WHITE);
        headerPanel.add(headerLabel, BorderLayout.WEST);

        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setBackground(UITheme.SURFACE);
        formCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
            new EmptyBorder(12, 20, 12, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formCard.add(makeLabel("Turniername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tournamentNameField = new JTextField(20);
        tournamentNameField.setFont(UITheme.FONT_BODY);
        formCard.add(tournamentNameField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formCard.add(makeLabel("Anzahl Tische:"), gbc);
        gbc.gridx = 3;
        tableCountField = new JTextField(5);
        tableCountField.setFont(UITheme.FONT_BODY);
        formCard.add(tableCountField, gbc);

        // row 1: format and doubles
        gbc.gridx = 0; gbc.gridy = 1;
        formCard.add(makeLabel("Turnierform:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        modeBox = new JComboBox<>(new String[]{MODE_SWISS, MODE_ROUND_ROBIN, MODE_GROUPS_KO});
        modeBox.setFont(UITheme.FONT_BODY);
        formCard.add(modeBox, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 2;
        formCard.add(makeLabel("Doppel:"), gbc);
        gbc.gridx = 3;
        doublesBox = new JCheckBox();
        doublesBox.setOpaque(false);
        doublesBox.setToolTipText("Doppelkonkurrenz: Teilnehmer sind Paare aus zwei Spielern.");
        formCard.add(doublesBox, gbc);

        // row 2: Swiss System
        gbc.gridx = 0; gbc.gridy = 2;
        roundCountLabel = makeLabel("Rundenzahl:");
        formCard.add(roundCountLabel, gbc);
        gbc.gridx = 1;
        roundCountField = new JTextField(5);
        roundCountField.setFont(UITheme.FONT_BODY);
        roundCountField.setToolTipText("Schweizer System: nach dieser Runde ist das Turnier beendet. Leer lassen, um ohne feste Rundenzahl zu spielen.");
        formCard.add(roundCountField, gbc);

        // row 3: groups + knockout
        gbc.gridx = 0; gbc.gridy = 3;
        groupSizeLabel = makeLabel("Gruppengröße:");
        formCard.add(groupSizeLabel, gbc);
        gbc.gridx = 1;
        groupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        groupRow.setOpaque(false);
        groupSizeField = new JTextField("4", 3);
        qualifiersField = new JTextField("2", 3);
        bestNextField = new JTextField("0", 3);
        for (JTextField f : new JTextField[]{groupSizeField, qualifiersField, bestNextField}) f.setFont(UITheme.FONT_BODY);
        groupSizeField.setToolTipText("Maximale Teilnehmerzahl je Gruppe. Geht es nicht auf, haben einige Gruppen einen Teilnehmer weniger.");
        qualifiersField.setToolTipText("So viele Teilnehmer je Gruppe kommen in die KO-Runde (z. B. 2 = Erster und Zweiter).");
        bestNextField.setToolTipText("Zusätzlich die besten Nächstplatzierten aller Gruppen (z. B. die besten Gruppendritten).");
        groupRow.add(groupSizeField);
        groupRow.add(makeLabel("  weiter je Gruppe:"));
        groupRow.add(qualifiersField);
        groupRow.add(makeLabel("  + beste Nächstplatzierte:"));
        groupRow.add(bestNextField);
        thirdPlaceBox = new JCheckBox("Spiel um Platz 3", true);
        thirdPlaceBox.setOpaque(false);
        thirdPlaceBox.setFont(UITheme.FONT_BODY);
        thirdPlaceBox.setToolTipText("Ohne Spiel um Platz 3 teilen sich beide Halbfinal-Verlierer den dritten Platz.");
        groupRow.add(Box.createHorizontalStrut(10));
        groupRow.add(thirdPlaceBox);
        gbc.gridwidth = 5;
        formCard.add(groupRow, gbc);
        gbc.gridwidth = 1;

        modeBox.addActionListener(_ -> updateModeFields());
        updateModeFields();

        JPanel playerCard = new JPanel(new BorderLayout(0, 6));
        playerCard.setBackground(UITheme.BACKGROUND);
        playerCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel playerInner = new JPanel(new BorderLayout(0, 6));
        playerInner.setBackground(UITheme.SURFACE);
        playerInner.setBorder(UITheme.cardBorder("Teilnehmer"));

        playerTableModel = new javax.swing.table.DefaultTableModel(new String[]{"Name", "Verein", "TTR"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        playerTable = new JTable(playerTableModel);
        UITheme.applyTableStyling(playerTable);
        playerTable.setIntercellSpacing(new Dimension(0, 1));
        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playerTable.setToolTipText("Doppelklick: bearbeiten");

        playerCountLabel = new JLabel("Noch keine Teilnehmer – „+ Spieler hinzufügen“ oder Datei → Teilnehmer importieren");
        playerCountLabel.setFont(UITheme.FONT_BODY);
        playerCountLabel.setForeground(UITheme.TEXT_MUTED);
        playerInner.add(playerCountLabel, BorderLayout.NORTH);
        JScrollPane playerScrollPane = UITheme.neutralViewport(new JScrollPane(playerTable));
        playerInner.add(playerScrollPane, BorderLayout.CENTER);

        JPanel playerBtns = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 6));
        playerBtns.setBackground(UITheme.SURFACE);
        addPlayerButton = UITheme.createPrimaryButton("+ Spieler hinzufügen");
        editPlayerButton = UITheme.createSecondaryButton("Spieler bearbeiten");
        removePlayerButton = UITheme.createSecondaryButton("Spieler entfernen");
        playerBtns.add(addPlayerButton);
        playerBtns.add(editPlayerButton);
        playerBtns.add(removePlayerButton);
        playerInner.add(playerBtns, BorderLayout.SOUTH);
        playerCard.add(playerInner, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UITheme.BACKGROUND);
        mainPanel.add(formCard, BorderLayout.NORTH);
        mainPanel.add(playerCard, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        bottomPanel.setBackground(UITheme.BACKGROUND);
        beginTournamentButton = UITheme.createPrimaryButton("Turnier beginnen und erste Runde auslosen");
        bottomPanel.add(beginTournamentButton);

        add(headerPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Datei");
        fileMenu.setFont(UITheme.FONT_BODY);
        JMenuItem loadTournamentItem = new JMenuItem("Turnier laden");
        loadTournamentItem.setFont(UITheme.FONT_BODY);
        fileMenu.add(loadTournamentItem);
        fileMenu.addSeparator();
        importMenuItem = new JMenuItem("Teilnehmer importieren (CSV) …");
        importMenuItem.setFont(UITheme.FONT_BODY);
        fileMenu.add(importMenuItem);
        exportMenuItem = new JMenuItem("Teilnehmer exportieren (CSV) …");
        exportMenuItem.setFont(UITheme.FONT_BODY);
        fileMenu.add(exportMenuItem);
        menuBar.add(fileMenu);
        JMenu helpMenu = new JMenu("Hilfe");
        helpMenu.setFont(UITheme.FONT_BODY);
        JMenuItem aboutItem = new JMenuItem("Über …");
        aboutItem.setFont(UITheme.FONT_BODY);
        aboutItem.addActionListener(_ -> JOptionPane.showMessageDialog(this,
                AppInfo.NAME + "\nVersion " + AppInfo.version()
                        + "\n\nSchweizer System, Jeder gegen Jeden, Vorrunde in Gruppen + KO-Runde; Einzel und Doppel."
                        + "\nFür nicht-kommerzielle Vereins-, Hobby- und Freizeitturniere (Lizenz CC BY-NC-SA 4.0)."
                        + "\n\ngithub.com/mathiasWeklak/TableTennisTournamentSoftware",
                "Über", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
        this.loadMenuItem = loadTournamentItem;
    }

    /**
     * Creates a {@link JLabel} styled with the standard body font.
     *
     * @param text the label text
     * @return the styled label
     */
    private static JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UITheme.FONT_BODY);
        return lbl;
    }

    /**
     * Returns the participant table (name, club, TTR).
     *
     * @return the table
     */
    public JTable getPlayerTable() {
        return playerTable;
    }

    /**
     * Shows the participants: one row {name, club, TTR} per participant, and their number.
     *
     * @param rows    the rows
     * @param doubles {@code true} for pairs
     */
    public void showParticipants(java.util.List<Object[]> rows, boolean doubles) {
        playerTableModel.setRowCount(0);
        rows.forEach(playerTableModel::addRow);
        playerCountLabel.setText(rows.isEmpty()
                ? "Noch keine Teilnehmer – „" + (doubles ? "+ Doppel" : "+ Spieler") + " hinzufügen“ oder Datei → Teilnehmer importieren"
                : rows.size() + (doubles ? " Doppel" : " Teilnehmer") + ", nach TTR sortiert");
        UITheme.setNameColumnWide(playerTable, 0);
    }

    /**
     * Returns the tournament name input field.
     *
     * @return the tournament name text field
     */
    public JTextField getTournamentNameField() {
        return tournamentNameField;
    }

    /**
     * Returns the table count input field.
     *
     * @return the table count text field
     */
    public JTextField getTableCountField() {
        return tableCountField;
    }

    /**
     * Returns the "add player" button.
     *
     * @return the add player button
     */
    public JButton getAddPlayerButton() {
        return addPlayerButton;
    }

    /** Display names of the tournament formats. */
    public static final String MODE_SWISS = "Schweizer System";
    /** Display name of the Round Robin format. */
    public static final String MODE_ROUND_ROBIN = "Jeder gegen Jeden";
    /** Display name of the group/knockout format. */
    public static final String MODE_GROUPS_KO = "Vorrunde in Gruppen + KO-Runde";

    private void updateModeFields() {
        Object mode = modeBox.getSelectedItem();
        // only the fields of the chosen format are shown
        boolean swiss = MODE_SWISS.equals(mode);
        roundCountLabel.setVisible(swiss);
        roundCountField.setVisible(swiss);
        roundCountField.setEnabled(swiss);
        boolean groups = MODE_GROUPS_KO.equals(mode);
        groupSizeLabel.setVisible(groups);
        groupRow.setVisible(groups);
        groupSizeField.setEnabled(groups);
        qualifiersField.setEnabled(groups);
        bestNextField.setEnabled(groups);
        thirdPlaceBox.setEnabled(groups);
        revalidate();
        repaint();
    }

    /**
     * Returns the selected tournament format.
     *
     * @return the format
     */
    public TournamentMode getSelectedMode() {
        Object mode = modeBox.getSelectedItem();
        if (MODE_ROUND_ROBIN.equals(mode)) return TournamentMode.ROUND_ROBIN;
        if (MODE_GROUPS_KO.equals(mode)) return TournamentMode.GROUPS_KO;
        return TournamentMode.SWISS;
    }

    /**
     * Returns the format selection.
     *
     * @return the combo box
     */
    public JComboBox<String> getModeBox() {
        return modeBox;
    }

    /**
     * Returns the doubles check box.
     *
     * @return the check box
     */
    public JCheckBox getDoublesBox() {
        return doublesBox;
    }

    /**
     * Returns the group size field.
     *
     * @return the field
     */
    public JTextField getGroupSizeField() {
        return groupSizeField;
    }

    /**
     * Returns the field for the number of qualifiers per group.
     *
     * @return the field
     */
    public JTextField getQualifiersField() {
        return qualifiersField;
    }

    /**
     * Returns the field for the number of best participants on the next place.
     *
     * @return the field
     */
    public JTextField getBestNextField() {
        return bestNextField;
    }

    /**
     * Returns the check box "Spiel um Platz 3".
     *
     * @return the check box
     */
    public JCheckBox getThirdPlaceBox() {
        return thirdPlaceBox;
    }

    /**
     * Returns the "edit player" button.
     *
     * @return the edit player button
     */
    public JButton getEditPlayerButton() {
        return editPlayerButton;
    }

    /**
     * Returns the field for the planned number of rounds (Swiss System only, optional).
     *
     * @return the round count field
     */
    public JTextField getRoundCountField() {
        return roundCountField;
    }

    /**
     * Returns the "remove player" button.
     *
     * @return the remove player button
     */
    public JButton getRemovePlayerButton() {
        return removePlayerButton;
    }

    /**
     * Returns the "begin tournament" button.
     *
     * @return the begin tournament button
     */
    public JButton getBeginTournamentButton() {
        return beginTournamentButton;
    }

    /**
     * Returns the "load tournament" menu item.
     *
     * @return the load menu item
     */
    public JMenuItem getLoadMenuItem() {
        return loadMenuItem;
    }

    /**
     * Returns the "import participants" menu item.
     *
     * @return the menu item
     */
    public JMenuItem getImportMenuItem() {
        return importMenuItem;
    }

    /**
     * Returns the "export participants" menu item.
     *
     * @return the menu item
     */
    public JMenuItem getExportMenuItem() {
        return exportMenuItem;
    }

    /**
     * Selects a tournament format.
     *
     * @param mode the format
     */
    public void setSelectedMode(TournamentMode mode) {
        modeBox.setSelectedItem(switch (mode) {
            case SWISS -> MODE_SWISS;
            case ROUND_ROBIN -> MODE_ROUND_ROBIN;
            case GROUPS_KO -> MODE_GROUPS_KO;
        });
    }
}
