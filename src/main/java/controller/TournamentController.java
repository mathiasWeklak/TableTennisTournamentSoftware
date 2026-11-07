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
import java.io.IOException;
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
        view.getEditPlayerButton().addActionListener(_ -> editPlayer());
        view.getPlayerJList().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
                        && view.getPlayerJList().locationToIndex(e.getPoint()) >= 0) {
                    editPlayer();
                }
            }
        });
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
        Player newPlayer = PlayerDialog.show(view, "Spieler hinzufügen", null, playerList);
        if (newPlayer != null) {
            playerList.add(newPlayer);
            updatePlayerList();
        }
    }

    /**
     * Edits the selected player (also by double-click on the list).
     */
    private void editPlayer() {
        int selectedIndex = view.getPlayerJList().getSelectedIndex();
        if (selectedIndex == -1) {
            JOptionPane.showMessageDialog(view, "Bitte wählen Sie einen Spieler aus der Liste aus.");
            return;
        }
        Player existing = playerList.get(selectedIndex);
        List<Player> others = new ArrayList<>(playerList);
        others.remove(selectedIndex);
        Player edited = PlayerDialog.show(view, "Spieler bearbeiten", existing, others);
        if (edited != null) {
            playerList.set(selectedIndex, edited);
            updatePlayerList();
        }
    }

    /**
     * Returns {@code true} if a player with the same first and last name already exists.
     * The comparison ignores case and surrounding/multiple whitespace, so "Max  Müller" and
     * "max müller" count as the same name. Club and TTR are not considered: two players with
     * the same name could not be told apart in pairings, result entry and printouts.
     *
     * @param players   the existing players
     * @param firstName the new player's first name
     * @param lastName  the new player's last name
     * @return {@code true} if the name is already taken
     */
    static boolean isDuplicateName(List<Player> players, String firstName, String lastName) {
        return PlayerDialog.isDuplicateName(players, firstName, lastName);
    }

    /**
     * Removes the selected player from the player list.
     * Prompts the user to select a player to remove.
     */
    private void removePlayer() {
        int selectedIndex = view.getPlayerJList().getSelectedIndex();
        if (selectedIndex != -1) {
            Player player = playerList.get(selectedIndex);
            int answer = JOptionPane.showConfirmDialog(view,
                    player.getFullName() + " (" + player.getClub() + ") wirklich entfernen?",
                    "Spieler entfernen", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                playerList.remove(selectedIndex);
                updatePlayerList();
            }
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
        int plannedRounds = 0;
        String roundsText = view.getRoundCountField().getText().trim();
        if (mode == TournamentMode.SWISS && !roundsText.isEmpty()) {
            try {
                plannedRounds = Integer.parseInt(roundsText);
                if (plannedRounds <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(view, "Bitte geben Sie für die Rundenzahl eine positive ganze Zahl ein oder lassen Sie das Feld leer.");
                return;
            }
        }
        new TournamentRound(playerList, tournamentName, tableCount, mode, plannedRounds).setVisible(true);
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
        ErrorHandler.install();
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

            try {
                TournamentState state = TournamentStorage.load(file);

                TournamentRound round = TournamentRound.fromSavedState(state);
                round.setVisible(true);
                view.dispose();

            } catch (IOException | ClassNotFoundException e) {
                JOptionPane.showMessageDialog(view,
                        "Fehler beim Laden der Datei.\n\nDetails: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
