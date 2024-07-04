package view;

import javax.swing.*;
import java.awt.*;

/**
 * View class for managing a table tennis tournament GUI.
 * Provides fields and methods to interact with tournament data and UI elements.
 */
public class TournamentView extends JFrame {
    private final JList<String> playerJList;
    private final DefaultListModel<String> playerListModel;
    private final JTextField tournamentNameField;
    private final JTextField tableCountField;
    private final JCheckBox modusField;
    private final JButton addPlayerButton;
    private final JButton removePlayerButton;
    private final JButton beginTournamentButton;

    /**
     * Constructs a TournamentView GUI.
     */
    public TournamentView() {
        setTitle("Tischtennis Turniersoftware");
        setSize(600, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        playerListModel = new DefaultListModel<>();
        playerJList = new JList<>(playerListModel);

        JPanel topPanel = new JPanel();
        JLabel nameLabel = new JLabel("Turniername:");
        tournamentNameField = new JTextField(15);
        topPanel.add(nameLabel);
        topPanel.add(tournamentNameField);

        JLabel tableLabel = new JLabel("Anzahl der Tische:");
        tableCountField = new JTextField(5);
        topPanel.add(tableLabel);
        topPanel.add(tableCountField);

        JLabel modusCheckboxLabel = new JLabel("Jeder gegen Jeden?");
        modusField = new JCheckBox();
        topPanel.add(modusCheckboxLabel);
        topPanel.add(modusField);

        JPanel centerPanel = new JPanel(new BorderLayout());
        JPanel playerPanel = new JPanel(new BorderLayout());
        playerPanel.setBorder(BorderFactory.createTitledBorder("Spielerliste"));
        JScrollPane playerScrollPane = new JScrollPane(playerJList);
        playerPanel.add(playerScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        addPlayerButton = new JButton("Spieler hinzufügen");
        removePlayerButton = new JButton("Spieler entfernen");
        buttonPanel.add(addPlayerButton);
        buttonPanel.add(removePlayerButton);
        playerPanel.add(buttonPanel, BorderLayout.SOUTH);

        centerPanel.add(playerPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        beginTournamentButton = new JButton("Turnier beginnen und erste Runde auslosen");
        bottomPanel.add(beginTournamentButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Gets the JList component displaying the list of players.
     *
     * @return The JList component for players.
     */
    public JList<String> getPlayerJList() {
        return playerJList;
    }

    /**
     * Gets the model associated with the player JList.
     *
     * @return The DefaultListModel containing player names.
     */
    public DefaultListModel<String> getPlayerListModel() {
        return playerListModel;
    }

    /**
     * Gets the JTextField for entering the tournament name.
     *
     * @return The JTextField for tournament name input.
     */
    public JTextField getTournamentNameField() {
        return tournamentNameField;
    }

    /**
     * Gets the JTextField for entering the number of tables.
     *
     * @return The JTextField for table count input.
     */
    public JTextField getTableCountField() {
        return tableCountField;
    }

    /**
     * Gets the JCheckBox indicating the tournament mode.
     *
     * @return The JCheckBox indicating if it's a round-robin tournament.
     */
    public JCheckBox getModusField() {
        return modusField;
    }

    /**
     * Gets the JButton for adding a new player to the tournament.
     *
     * @return The JButton for adding a player.
     */
    public JButton getAddPlayerButton() {
        return addPlayerButton;
    }

    /**
     * Gets the JButton for removing a selected player from the tournament.
     *
     * @return The JButton for removing a player.
     */
    public JButton getRemovePlayerButton() {
        return removePlayerButton;
    }

    /**
     * Gets the JButton for starting the tournament and drawing the first round.
     *
     * @return The JButton for beginning the tournament.
     */
    public JButton getBeginTournamentButton() {
        return beginTournamentButton;
    }
}
