package controller;

import model.Player;
import model.TournamentMode;
import model.TournamentState;
import view.TournamentView;
import view.UITheme;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller class for managing the tournament setup and player management.
 * Handles interactions between the TournamentView and the underlying player data model.
 */
public class TournamentController {
    private final TournamentView view;
    private final List<Player> playerList;

    /**
     * Constructs a TournamentController with the specified view.
     *
     * @param view The view associated with this controller.
     */
    public TournamentController(TournamentView view) {
        this.view = view;
        this.playerList = new ArrayList<>();

        view.getAddPlayerButton().addActionListener(_ -> addPlayer());
        view.getRemovePlayerButton().addActionListener(_ -> removePlayer());
        view.getBeginTournamentButton().addActionListener(_ -> beginTournament());
        view.getLoadMenuItem().addActionListener(_ -> loadTournamentFromFile());

        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmAction()) {
                    view.dispose();
                }
            }
        });
    }

    /**
     * Adds a new player to the player list.
     * Prompts the user for player details and validates the input.
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
            result = JOptionPane.showConfirmDialog(view, myPanel,
                    "Spieler hinzufügen", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String firstName = firstNameField.getText().trim();
                String lastName = lastNameField.getText().trim();
                String club = clubField.getText().trim();
                if (firstName.isEmpty() || lastName.isEmpty() || club.isEmpty()) {
                    JOptionPane.showMessageDialog(view, "Bitte füllen Sie alle Pflichtfelder aus.");
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
                    JOptionPane.showMessageDialog(view, "Bitte geben Sie einen ganzzahligen positiven TTR-Wert ein oder lassen Sie das Feld leer.");
                    continue;
                }
                Player newPlayer = new Player(firstName, lastName, club, ttr);
                if (playerList.contains(newPlayer)) {
                    JOptionPane.showMessageDialog(view, "Ein Spieler mit diesem Namen und Verein ist bereits in der Liste.");
                    continue;
                }
                playerList.add(newPlayer);
                updatePlayerList();
                break;
            }
        } while (result == JOptionPane.OK_OPTION);
    }

    /**
     * Removes the selected player from the player list.
     * Prompts the user to select a player to remove.
     */
    private void removePlayer() {
        int selectedIndex = view.getPlayerJList().getSelectedIndex();
        if (selectedIndex != -1) {
            playerList.remove(selectedIndex);
            updatePlayerList();
        } else {
            JOptionPane.showMessageDialog(view, "Bitte wählen Sie einen Spieler aus der Liste aus.");
        }
    }

    /**
     * Updates the player list displayed in the view.
     * Sorts players by their TTR value in descending order.
     */
    private void updatePlayerList() {
        playerList.sort((p1, p2) -> Integer.compare(p2.getTtr(), p1.getTtr()));

        DefaultListModel<String> playerListModel = view.getPlayerListModel();
        playerListModel.clear();
        playerList.forEach(player -> playerListModel.addElement(player.getFullName() + " - " + player.getClub() + " - TTR: " + player.getTtr()));
    }

    /**
     * Begins the tournament with the specified settings.
     * Validates the tournament name and table count, and starts a new tournament round.
     */
    private void beginTournament() {
        String tournamentName = view.getTournamentNameField().getText().trim();
        if (tournamentName.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Bitte geben Sie einen Turniernamen ein.");
            return;
        }

        int tableCount;
        try {
            tableCount = Integer.parseInt(view.getTableCountField().getText().trim());
            if (tableCount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(view, "Bitte geben Sie eine positive ganze Zahl für die Anzahl der Tische ein.");
            return;
        }

        if (playerList.size() < 2) {
            JOptionPane.showMessageDialog(view, "Bitte fügen Sie mindestens 2 Spieler hinzu.");
            return;
        }

        TournamentMode mode = view.getModusField().isSelected() ? TournamentMode.ROUND_ROBIN : TournamentMode.SWISS;
        new TournamentRound(playerList, tournamentName, tableCount, mode).setVisible(true);
        view.dispose();
    }

    /**
     * Prompts the user for confirmation before closing the application.
     *
     * @return true if the user confirms to close the application, false otherwise.
     */
    private boolean confirmAction() {
        int result = JOptionPane.showConfirmDialog(view, "Wollen Sie das Programm wirklich beenden?", "Bestätigung", JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Main method to start the application.
     *
     */
    static void main() {
        UITheme.applyLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            TournamentView view = new TournamentView();
            new TournamentController(view);
            view.setVisible(true);
        });
    }

    /**
     * Opens a file chooser dialog to load a previously saved tournament state from a .ser file.
     *
     * <p>This method displays a {@link JFileChooser} restricted to files with the {@code .ser} extension
     * and prompts the user to select a tournament save file. If a valid file is selected, the method attempts
     * to deserialize it into a {@link TournamentState} object and restore the tournament via
     * {@link TournamentRound#fromSavedState(TournamentState)}.</p>
     *
     * <p>If the loading process is successful, the current tournament setup view is closed and the restored
     * tournament round is made visible. In case of errors during file reading or deserialization,
     * an error dialog is shown to the user.</p>
     */
    private void loadTournamentFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Turnier-Dateien (*.ser)", "ser"));
        fileChooser.setDialogTitle("Turnierstand laden");

        int result = fileChooser.showOpenDialog(view);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                TournamentState state = (TournamentState) in.readObject();

                TournamentRound round = TournamentRound.fromSavedState(state);
                round.setVisible(true);
                view.dispose();

            } catch (IOException | ClassNotFoundException _) {
                JOptionPane.showMessageDialog(view, "Fehler beim Laden der Datei.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
