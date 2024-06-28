package controller;

import model.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

/**
 * A GUI application for managing a table tennis tournament.
 */
public class TournamentSoftware extends JFrame {
    private final ArrayList<Player> playerList;
    private final JList<String> playerJList;
    private final DefaultListModel<String> playerListModel;
    private final JTextField tournamentNameField;
    private final JTextField tableCountField;
    private final JCheckBox modusField;

    /**
     * Constructs the tournament software GUI.
     */
    public TournamentSoftware() {
        setTitle("Tischtennis Turniersoftware");
        setSize(600, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmAction()) {
                    dispose();
                }
            }
        });

        playerList = new ArrayList<>();
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
        JButton addPlayerButton = new JButton("Spieler hinzufügen");
        JButton removePlayerButton = new JButton("Spieler entfernen");
        addPlayerButton.addActionListener(e -> addPlayer());
        removePlayerButton.addActionListener(e -> removePlayer());
        buttonPanel.add(addPlayerButton);
        buttonPanel.add(removePlayerButton);
        playerPanel.add(buttonPanel, BorderLayout.SOUTH);

        centerPanel.add(playerPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton beginTournamentButton = new JButton("Turnier beginnen und erste Runde auslosen");
        beginTournamentButton.addActionListener(e -> beginTournament());
        bottomPanel.add(beginTournamentButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Adds a new player to the tournament player list.
     */
    private void addPlayer() {
        JTextField firstNameField = new JTextField(10);
        JTextField lastNameField = new JTextField(10);
        JTextField clubField = new JTextField(10);
        JTextField ttrField = new JTextField(10);

        JPanel myPanel = new JPanel();
        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
        myPanel.add(new JLabel("Vorname:"));
        myPanel.add(firstNameField);
        myPanel.add(new JLabel("Name:"));
        myPanel.add(lastNameField);
        myPanel.add(new JLabel("Verein:"));
        myPanel.add(clubField);
        myPanel.add(new JLabel("TTR-Wert (optional):"));
        myPanel.add(ttrField);

        int result;
        do {
            result = JOptionPane.showConfirmDialog(null, myPanel,
                    "Spieler hinzufügen", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String firstName = firstNameField.getText().trim();
                String lastName = lastNameField.getText().trim();
                String club = clubField.getText().trim();
                if (firstName.isEmpty() || lastName.isEmpty() || club.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Bitte füllen Sie alle Pflichtfelder aus.");
                    continue;
                }
                int ttr = 0;
                try {
                    String ttrValue = ttrField.getText().trim();
                    if (!ttrValue.isEmpty()) {
                        ttr = Integer.parseInt(ttrValue);
                    }
                    if (ttr < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Bitte geben Sie einen ganzzahligen positiven TTR-Wert ein oder lassen Sie das Feld leer.");
                    continue;
                }
                Player newPlayer = new Player(firstName, lastName, club, ttr);
                playerList.add(newPlayer);
                updatePlayerList();
                break;
            }
        } while (result == JOptionPane.OK_OPTION);
    }

    /**
     * Removes the selected player from the tournament player list.
     */
    private void removePlayer() {
        int selectedIndex = playerJList.getSelectedIndex();
        if (selectedIndex != -1) {
            playerList.remove(selectedIndex);
            updatePlayerList();
        } else {
            JOptionPane.showMessageDialog(null, "Bitte wählen Sie einen Spieler aus der Liste aus.");
        }
    }

    /**
     * Updates the player list in the GUI.
     */
    private void updatePlayerList() {
        playerList.sort((p1, p2) -> Integer.compare(p2.getTtr(), p1.getTtr()));

        playerListModel.clear();
        playerList.forEach(player -> playerListModel.addElement(player.getFullName() + " - " + player.getClub() + " - TTR: " + player.getTtr()));
    }

    /**
     * Begins the tournament with the entered tournament name and table count.
     */
    private void beginTournament() {
        String tournamentName = tournamentNameField.getText().trim();
        if (tournamentName.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Bitte geben Sie einen Turniernamen ein.");
            return;
        }

        int tableCount;
        try {
            tableCount = Integer.parseInt(tableCountField.getText().trim());
            if (tableCount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Bitte geben Sie eine positive ganze Zahl für die Anzahl der Tische ein.");
            return;
        }

        new TournamentRound(playerList, tournamentName, tableCount, modusField.isSelected()).setVisible(true);
        dispose();
    }

    /**
     * Asks for confirmation before closing the application window.
     *
     * @return true if user confirms to exit, false otherwise
     */
    private boolean confirmAction() {
        int result = JOptionPane.showConfirmDialog(null, "Wollen Sie das Programm wirklich beenden?", "Bestätigung", JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Main method to start the application.
     *
     * @param args Command-line arguments (unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TournamentSoftware().setVisible(true));
    }
}
