package controller;

import model.GroupKoState;
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
    /** Name of the tournament that came back from round 1: its file may be replaced without asking. */
    private String replaceableTournament;

    /**
     * Settings and participants of a tournament, to continue editing them in the setup screen
     * (back from round 1).
     *
     * @param name          tournament name
     * @param tables        number of tables
     * @param mode          tournament format
     * @param plannedRounds planned rounds (Swiss), 0 = open
     * @param doubles       doubles competition
     * @param groupKo       group settings, {@code null} for other formats
     * @param players       the participants
     */
    public record Setup(String name, int tables, TournamentMode mode, int plannedRounds, boolean doubles,
                        GroupKoState groupKo, List<Player> players) {
    }

    /**
     * Opens the setup screen filled with the given settings and participants. The tournament file
     * of that name is replaced without the "already saved" question.
     *
     * @param setup the settings
     */
    public static void openSetup(Setup setup) {
        TournamentView view = new TournamentView();
        TournamentController controller = new TournamentController(view);
        view.getTournamentNameField().setText(setup.name());
        view.getTableCountField().setText(String.valueOf(setup.tables()));
        view.setSelectedMode(setup.mode());
        view.getRoundCountField().setText(setup.plannedRounds() > 0 ? String.valueOf(setup.plannedRounds()) : "");
        if (setup.doubles() != view.getDoublesBox().isSelected()) view.getDoublesBox().doClick();
        if (setup.groupKo() != null) {
            view.getGroupSizeField().setText(String.valueOf(setup.groupKo().groupSize()));
            view.getQualifiersField().setText(String.valueOf(setup.groupKo().qualifiersPerGroup()));
            view.getBestNextField().setText(String.valueOf(setup.groupKo().bestNextPlaced()));
            view.getThirdPlaceBox().setSelected(setup.groupKo().thirdPlaceMatch());
        }
        controller.playerList.addAll(setup.players());
        controller.updatePlayerList();
        controller.replaceableTournament = setup.name();
        view.setVisible(true);
    }

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
        view.getPlayerTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
                        && view.getPlayerTable().rowAtPoint(e.getPoint()) >= 0) {
                    editPlayer();
                }
            }
        });
        view.getBeginTournamentButton().addActionListener(_ -> beginTournament());
        view.getDoublesBox().addActionListener(_ -> {
            // singles and pairs cannot be mixed
            if (!playerList.isEmpty()) {
                view.getDoublesBox().setSelected(!view.getDoublesBox().isSelected());
                JOptionPane.showMessageDialog(view, "Einzel und Doppel lassen sich nicht mischen.\n"
                        + "Bitte zuerst alle Teilnehmer entfernen, um den Modus zu wechseln.");
            }
            view.getAddPlayerButton().setText(doubles() ? "+ Doppel hinzufügen" : "+ Spieler hinzufügen");
            view.getEditPlayerButton().setText(doubles() ? "Doppel bearbeiten" : "Spieler bearbeiten");
            view.getRemovePlayerButton().setText(doubles() ? "Doppel entfernen" : "Spieler entfernen");
            updatePlayerList();
        });
        view.getLoadMenuItem().addActionListener(_ -> loadTournamentFromFile());
        view.getImportMenuItem().addActionListener(_ -> importParticipants());
        view.getExportMenuItem().addActionListener(_ -> exportParticipants());

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
        Player newPlayer = PlayerDialog.show(view, doubles() ? "Doppel hinzufügen" : "Spieler hinzufügen",
                null, playerList, doubles());
        if (newPlayer != null) {
            playerList.add(newPlayer);
            updatePlayerList();
        }
    }

    /**
     * Edits the selected player (also by double-click on the list).
     */
    private void editPlayer() {
        int selectedIndex = view.getPlayerTable().getSelectedRow();
        if (selectedIndex == -1) {
            JOptionPane.showMessageDialog(view, doubles() ? "Bitte wählen Sie ein Doppel aus der Liste aus."
                    : "Bitte wählen Sie einen Spieler aus der Liste aus.");
            return;
        }
        Player existing = playerList.get(selectedIndex);
        List<Player> others = new ArrayList<>(playerList);
        others.remove(selectedIndex);
        Player edited = PlayerDialog.show(view, doubles() ? "Doppel bearbeiten" : "Spieler bearbeiten",
                existing, others, doubles());
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
        int selectedIndex = view.getPlayerTable().getSelectedRow();
        if (selectedIndex != -1) {
            Player player = playerList.get(selectedIndex);
            int answer = JOptionPane.showConfirmDialog(view,
                    player.getFullName() + " (" + player.getClub() + ") wirklich entfernen?",
                    doubles() ? "Doppel entfernen" : "Spieler entfernen", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                playerList.remove(selectedIndex);
                updatePlayerList();
            }
        } else {
            JOptionPane.showMessageDialog(view, doubles() ? "Bitte wählen Sie ein Doppel aus der Liste aus."
                    : "Bitte wählen Sie einen Spieler aus der Liste aus.");
        }
    }

    /**
     * Updates the player list displayed in the view.
     * Sorts players by their TTR value in descending order.
     */
    private void updatePlayerList() {
        playerList.sort((p1, p2) -> Integer.compare(p2.getTtr(), p1.getTtr()));

        List<Object[]> rows = new ArrayList<>();
        for (Player player : playerList) {
            rows.add(new Object[]{player.getFullName(), player.getClub(), player.isDoubles()
                    ? player.getOwnTtr() + " + " + player.getPartnerTtr() + " = " + player.getTtr() : String.valueOf(player.getTtr())});
        }
        view.showParticipants(rows, doubles());
    }

    private boolean doubles() {
        return view.getDoublesBox().isSelected();
    }

    /**
     * Validates the group/knockout settings.
     *
     * @param participants number of participants
     * @param groupSize    maximum group size
     * @param perGroup     qualifiers per group
     * @param bestNext     best participants on the next place
     * @return an error message, or {@code null} if valid
     */
    static String validateGroupSettings(int participants, int groupSize, int perGroup, int bestNext) {
        if (groupSize < 2) return "Die Gruppengröße muss mindestens 2 sein.";
        if (perGroup < 1) return "Aus jeder Gruppe muss mindestens ein Teilnehmer weiterkommen.";
        if (bestNext < 0) return "Die Zahl der besten Nächstplatzierten darf nicht negativ sein.";
        List<List<Player>> groups = GroupKnockout.assignGroups(dummies(participants), groupSize);
        int smallest = groups.stream().mapToInt(List::size).min().orElse(0);
        if (smallest < 2) {
            return "Mit Gruppengröße " + groupSize + " entsteht eine Gruppe mit nur einem Teilnehmer.\n"
                    + "Bitte die Gruppengröße ändern.";
        }
        if (perGroup >= smallest) {
            return "In der kleinsten Gruppe sind nur " + smallest + " Teilnehmer.\n"
                    + "Es müssen weniger als " + smallest + " je Gruppe weiterkommen.";
        }
        if (bestNext > 0 && groups.size() == 1) {
            return "Bei nur einer Gruppe gibt es keine besten Nächstplatzierten. Bitte 0 eintragen.";
        }
        if (bestNext >= groups.size()) {
            return "Bei " + groups.size() + " Gruppen " + (groups.size() == 2 ? "kann höchstens ein Nächstplatzierter"
                    : "können höchstens " + (groups.size() - 1) + " Nächstplatzierte") + " weiterkommen."
                    + (perGroup + 1 < smallest ? "\nSollen alle weiterkommen, bitte stattdessen „weiter je Gruppe“ erhöhen." : "");
        }
        if (groups.size() * perGroup + bestNext < 2) {
            return "Für die KO-Runde müssen mindestens zwei Teilnehmer weiterkommen.";
        }
        return null;
    }

    private static List<Player> dummies(int n) {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Player("P" + i, "P" + i, "-", 0));
        return list;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
            JOptionPane.showMessageDialog(view, "Bitte fügen Sie mindestens 2 Teilnehmer hinzu.");
            return;
        }

        TournamentMode mode = view.getSelectedMode();
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
        GroupKoState groupKo = null;
        if (mode == TournamentMode.GROUPS_KO) {
            int groupSize = parseInt(view.getGroupSizeField().getText(), -1);
            int perGroup = parseInt(view.getQualifiersField().getText(), -1);
            String bestText = view.getBestNextField().getText().trim();
            int bestNext = bestText.isEmpty() ? 0 : parseInt(bestText, -1);
            String error = validateGroupSettings(playerList.size(), groupSize, perGroup, bestNext);
            if (error != null) {
                JOptionPane.showMessageDialog(view, error, "Gruppen-Einstellungen", JOptionPane.WARNING_MESSAGE);
                return;
            }
            groupKo = new GroupKoState(groupSize, perGroup, bestNext, view.getThirdPlaceBox().isSelected());
        }
        File existing = TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), tournamentName);
        boolean sameTournament = replaceableTournament != null && TournamentStorage.isSameFile(existing,
                TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), replaceableTournament));
        if (existing.exists() && !sameTournament && JOptionPane.showConfirmDialog(view,
                "Unter dem Namen „" + tournamentName + "“ ist bereits ein Turnier gespeichert:\n"
                        + existing.getAbsolutePath() + "\n\n"
                        + "Ein neues Turnier ersetzt diesen Stand, auch die alten Rundensicherungen\n"
                        + "(„…_Runde_N.ser“) werden nach und nach überschrieben. Um das gespeicherte Turnier fortzusetzen,\n"
                        + "bitte „Datei → Turnier laden“ wählen.\n\nTrotzdem ein neues Turnier beginnen?",
                "Turnier schon vorhanden", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        TournamentRound round = new TournamentRound(playerList, tournamentName, tableCount, mode, plannedRounds, doubles(), groupKo);
        round.saveTournamentState(); // the first draw is saved right away
        round.setVisible(true);
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
    public static void main(String[] args) {
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
        if (!playerList.isEmpty() && JOptionPane.showConfirmDialog(view,
                "Die eingetragenen Teilnehmer werden verworfen, wenn ein gespeichertes Turnier geladen wird.\n\nTrotzdem laden?",
                "Turnier laden", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser fileChooser = new JFileChooser(TournamentStorage.defaultSaveDirectory());
        fileChooser.setFileFilter(new FileNameExtensionFilter("Turnier-Dateien (*.ser)", "ser"));
        fileChooser.setDialogTitle("Turnierstand laden");

        int result = fileChooser.showOpenDialog(view);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            try {
                TournamentState state = TournamentStorage.load(file);
                if (!confirmReplacingCurrentState(file, state)) return;

                TournamentRound round = TournamentRound.fromSavedState(state);
                round.showLoadedFrom(file);
                round.setVisible(true);
                view.dispose();

            } catch (IOException | ClassNotFoundException e) {
                JOptionPane.showMessageDialog(view,
                        "Fehler beim Laden der Datei.\n\nDetails: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Imports participants from a CSV file and adds the valid ones to the list.
     */
    private void importParticipants() {
        JFileChooser chooser = new JFileChooser(TournamentStorage.defaultSaveDirectory());
        chooser.setFileFilter(new FileNameExtensionFilter("CSV-Dateien (*.csv, *.txt)", "csv", "txt"));
        chooser.setDialogTitle(doubles() ? "Doppel importieren" : "Teilnehmer importieren");
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) return;
        try {
            String text = ParticipantCsv.decode(java.nio.file.Files.readAllBytes(chooser.getSelectedFile().toPath()));
            ParticipantCsv.ImportResult result = ParticipantCsv.parse(text, doubles(), playerList);
            playerList.addAll(result.players());
            updatePlayerList();
            StringBuilder message = new StringBuilder(result.players().size() + (doubles() ? " Doppel" : " Teilnehmer")
                    + " übernommen.");
            if (!result.errors().isEmpty()) {
                message.append("\n\nNicht übernommen:\n")
                        .append(String.join("\n", result.errors().subList(0, Math.min(15, result.errors().size()))));
                if (result.errors().size() > 15) message.append("\n… und ").append(result.errors().size() - 15).append(" weitere");
            }
            JOptionPane.showMessageDialog(view, message.toString(), "Import",
                    result.errors().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(view, "Die Datei konnte nicht gelesen werden:\n" + e.getMessage(),
                    "Import", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the participant list as CSV, e.g. to reuse it for the next tournament.
     */
    private void exportParticipants() {
        if (playerList.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Die Teilnehmerliste ist leer.");
            return;
        }
        String name = view.getTournamentNameField().getText().isBlank() ? "Teilnehmer"
                : TournamentStorage.sanitizeFileName(view.getTournamentNameField().getText()) + "_Teilnehmer";
        File target = CsvFiles.chooseTarget(view, "Teilnehmer exportieren", name);
        if (target != null) CsvFiles.write(view, target, ParticipantCsv.format(playerList));
    }

    /**
     * A loaded tournament is continued in "&lt;name&gt;.ser" in the save directory. If another file
     * was loaded (a round backup or a file from elsewhere) and that file already exists, the user
     * is asked first, because the next save replaces it.
     *
     * @param loaded the loaded file
     * @param state  the loaded state
     * @return {@code true} if loading may continue
     */
    private boolean confirmReplacingCurrentState(File loaded, TournamentState state) {
        File target = TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), state.tournamentName());
        if (!target.exists() || TournamentStorage.isSameFile(loaded, target)) return true;
        String saved = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(target.lastModified()),
                        java.time.ZoneId.systemDefault()));
        return JOptionPane.showConfirmDialog(view,
                "Geladen wird: " + loaded.getName() + "\n"
                        + "Weitergespielt und gespeichert wird in:\n" + target.getAbsolutePath() + "\n\n"
                        + "Dort liegt bereits ein Turnierstand (gespeichert am " + saved + ").\n"
                        + "Er wird beim nächsten Speichern durch den geladenen Stand ersetzt.\n\nTrotzdem laden?",
                "Turnierstand ersetzen?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
