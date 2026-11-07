package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.ResultRules;
import model.TournamentMode;
import model.TournamentState;
import view.TournamentRoundView;
import view.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Controller for a running tournament. Delegates pairing logic to {@link PairingEngine},
 * score calculation to {@link ScoreCalculator}, the ranking to {@link Standings} and all
 * rendering to {@link TournamentRoundView}.
 */
public class TournamentRound {

    private final List<Player> playerList;
    private final int tableNumber;
    private final TournamentMode mode;
    private final String tournamentName;
    private final int plannedRounds;
    private final boolean doubles;
    private BracketController bracketView;

    private final PairingEngine pairingEngine;
    private final ScoreCalculator scoreCalculator;
    private final TournamentRoundView view;
    private CrossTableController crossTable;

    private int currentRound;

    /**
     * Constructs a new tournament without planned round count and draws the first round.
     *
     * @param playerList     the list of players participating in the tournament
     * @param tournamentName the name of the tournament
     * @param tableNumber    the number of available tables
     * @param mode           the tournament mode (SWISS or ROUND_ROBIN)
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode) {
        this(playerList, tournamentName, tableNumber, mode, 0);
    }

    /**
     * Constructs a new tournament and draws the first round.
     *
     * @param playerList     the list of players participating in the tournament
     * @param tournamentName the name of the tournament
     * @param tableNumber    the number of available tables
     * @param mode           the tournament mode (SWISS or ROUND_ROBIN)
     * @param plannedRounds  planned number of rounds (Swiss), 0 = until no pairing is possible
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode,
                           int plannedRounds) {
        this(playerList, tournamentName, tableNumber, mode, plannedRounds, false, null, false);
    }

    /**
     * Constructs a new tournament and draws the first round.
     *
     * @param playerList     the participants (single players or pairs)
     * @param tournamentName the name of the tournament
     * @param tableNumber    the number of available tables
     * @param mode           the tournament format
     * @param plannedRounds  planned number of rounds (Swiss), 0 = until no pairing is possible
     * @param doubles        {@code true} for a doubles competition
     * @param groupKo        group settings for {@link TournamentMode#GROUPS_KO}, otherwise {@code null}
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode,
                           int plannedRounds, boolean doubles, GroupKoState groupKo) {
        this(playerList, tournamentName, tableNumber, mode, plannedRounds, doubles, groupKo, false);
    }

    private TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode,
                            int plannedRounds, boolean doubles, GroupKoState groupKo, boolean skipInitialPairing) {
        this.doubles = doubles;
        this.playerList = skipInitialPairing ? new ArrayList<>(playerList) : seed(playerList, new Random());
        this.tableNumber = tableNumber;
        this.mode = mode;
        this.tournamentName = tournamentName;
        this.plannedRounds = mode == TournamentMode.SWISS ? Math.max(0, plannedRounds) : 0;
        this.currentRound = 1;

        // the engine shares playerList, so late registrations are paired from the next round on
        this.pairingEngine = new PairingEngine(this.playerList, tableNumber, mode);
        this.scoreCalculator = new ScoreCalculator(pairingEngine.getAllMatches(), mode);
        // Round Robin: all matches of a withdrawn player are struck (live view of the withdrawn set)
        this.scoreCalculator.setAnnulledPlayers(pairingEngine.getWithdrawn());
        if (mode == TournamentMode.GROUPS_KO && groupKo != null) {
            // new tournament: snake distribution in seeding order; loaded tournament: saved groups
            pairingEngine.setGroupKo(skipInitialPairing ? groupKo
                    : new GroupKoState(groupKo.groupSize(), groupKo.qualifiersPerGroup(), groupKo.bestNextPlaced(),
                    GroupKnockout.assignGroups(this.playerList, groupKo.groupSize()), new ArrayList<>()));
        }

        this.view = new TournamentRoundView(tournamentName, mode);
        wireActions();

        if (!skipInitialPairing) {
            if (pairingEngine.generatePairings(currentRound) == null) {
                pairingEngine.setFinished(true);
                JOptionPane.showMessageDialog(view, "Es konnte keine Runde ausgelost werden.",
                        "Keine Paarungen möglich", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        updateResultsTable();
    }

    /**
     * Returns the seeding order for a new tournament: TTR descending; players with equal TTR
     * (e.g. all without TTR) are ordered by lot. The order is kept for the whole tournament and is
     * the last tie-break for pairing and standings.
     *
     * @param players the registered players
     * @param random  source for the lot
     * @return a new list in seeding order
     */
    static List<Player> seed(List<Player> players, Random random) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, random);
        shuffled.sort(Comparator.comparingInt(Player::getTtr).reversed()); // stable: lot decides ties
        return shuffled;
    }

    /**
     * Returns the total number of rounds, or 0 if open (Swiss without planned round count).
     *
     * @return the planned rounds (Swiss) or the Round Robin round count
     */
    int totalRounds() {
        return switch (mode) {
            case ROUND_ROBIN -> pairingEngine.roundRobinRounds();
            case GROUPS_KO -> pairingEngine.groupRounds() + pairingEngine.knockoutRounds();
            case SWISS -> plannedRounds;
        };
    }

    /**
     * Returns the text for the current round, e.g. "Runde 3 von 5", "Vorrunde – Runde 2 von 3" or
     * "Halbfinale".
     *
     * @return the round label
     */
    String roundLabel() {
        int total = totalRounds();
        if (mode == TournamentMode.GROUPS_KO) {
            int groupRounds = pairingEngine.groupRounds();
            if (currentRound <= groupRounds) return "Vorrunde – Runde " + currentRound + " von " + groupRounds;
            return "KO-Runde: " + GroupKnockout.roundName(currentRound - groupRounds, pairingEngine.knockoutRounds());
        }
        return "Runde " + currentRound + (total > 0 ? " von " + total : "");
    }

    private void wireActions() {
        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (JOptionPane.showConfirmDialog(view,
                        "Programm schließen? Der Turnierstand wird gespeichert.", "Schließen",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    saveTournamentState();
                    view.dispose(); // owned dialogs (cross table, referee sheets) close with it
                }
            }
        });

        view.getPreviewRefereeSheetsButton().addActionListener(_ -> previewRefereeSheets());
        view.getResultEntryButton().addActionListener(_ -> {
            new ResultEntryController(view, this);
            updateResultsTable();
        });
        view.getPrintTableButton().addActionListener(_ -> printPlacementTable());
        view.getCrossTableButton().addActionListener(_ -> showCrossTable());
        if (view.getBracketButton() != null) {
            view.getBracketButton().addActionListener(_ -> showBracket());
        }
        view.getNextRoundButton().addActionListener(_ -> startNextRound());
        view.getUndoRoundButton().addActionListener(_ -> undoLastRound());
        view.getWithdrawPlayerButton().addActionListener(_ -> withdrawPlayer());
        if (view.getAddPlayerButton() != null) {
            view.getAddPlayerButton().addActionListener(_ -> addLatePlayer());
        }
        if (view.getManipulateButton() != null) {
            view.getManipulateButton().addActionListener(_ -> manipulatePairings());
        }
    }

    /**
     * Makes the tournament round window visible or invisible.
     *
     * @param visible {@code true} to show the window
     */
    public void setVisible(boolean visible) {
        view.setVisible(visible);
    }

    // ---------------------------------------------------------------------------------------
    // Display
    // ---------------------------------------------------------------------------------------

    /**
     * Recalculates all statistics and refreshes the standings, the pairings with their result
     * status, an open cross table and the enabled state of the buttons.
     */
    public void updateResultsTable() {
        scoreCalculator.calculate(playerList);
        List<Standings.Entry> standings = currentStandings();

        boolean groups = mode == TournamentMode.GROUPS_KO;
        DefaultTableModel tableModel = groups ? getGroupTableModel() : getDefaultTableModel(standings);
        view.getResultsTable().setModel(tableModel);
        UITheme.applyTableStyling(view.getResultsTable());
        UITheme.setNameColumnWide(view.getResultsTable(), groups ? 2 : 1);

        String text = formatPairingsWithStatus(pairingEngine.getMatches(), mode, pairingEngine.knockoutRounds());
        if (groups && pairingEngine.isFinished()) text += finalPlacingText();
        view.getPairingsTextArea().setText(text);
        view.getPairingsTextArea().setCaretPosition(0);
        if (crossTable != null && crossTable.isOpen()) {
            crossTable.refresh(crossTableSections(standings), countingMatches());
        }
        if (bracketView != null && bracketView.isOpen()) {
            bracketView.refresh(pairingEngine.getGroupKo(), pairingEngine.getAllMatches());
        }
        updateControls();
        view.validate();
        view.repaint();
    }

    private List<Standings.Entry> currentStandings() {
        return Standings.compute(playerList, mode, countingMatches());
    }

    /**
     * Returns the matches that count: in Round Robin without the struck matches of withdrawn
     * players, otherwise all matches.
     *
     * @return the counting matches
     */
    private List<Match> countingMatches() {
        return ScoreCalculator.countingMatches(pairingEngine.getAllMatches(), mode, pairingEngine.getWithdrawn());
    }

    private void updateControls() {
        boolean finished = pairingEngine.isFinished();
        view.showRound(roundLabel(), finished);
        view.getNextRoundButton().setEnabled(!finished);
        view.getUndoRoundButton().setEnabled(pairingEngine.canUndoLastRound());
        view.getWithdrawPlayerButton().setEnabled(!finished);
        if (view.getAddPlayerButton() != null) view.getAddPlayerButton().setEnabled(!finished);
        if (view.getManipulateButton() != null) view.getManipulateButton().setEnabled(!finished);
        boolean hasMatches = pairingEngine.getMatches().stream().anyMatch(m -> m.getSecondPlayer() != null);
        view.getPreviewRefereeSheetsButton().setEnabled(hasMatches && !finished);
        view.getResultEntryButton().setEnabled(hasMatches || currentRound > 1);
    }

    /**
     * Formats the current round's pairings with their result status, e.g.
     * "Tisch 1: Anna Alpha (TTC A) – Ben Beta (TTC B)   3:1" or "... offen".
     *
     * @param matches the current round's matches
     * @param mode    the tournament mode (wording of the bye)
     * @return the text for the pairings area
     */
    static String formatPairingsWithStatus(List<Match> matches, TournamentMode mode) {
        return formatPairingsWithStatus(matches, mode, 0);
    }

    /**
     * Formats the current round's pairings with their result status; in a group/knockout tournament
     * every line also names the group or the knockout round.
     *
     * @param matches   the current round's matches
     * @param mode      the tournament mode
     * @param koRounds  number of knockout rounds (for the round names), 0 if not relevant
     * @return the text for the pairings area
     */
    static String formatPairingsWithStatus(List<Match> matches, TournamentMode mode, int koRounds) {
        StringBuilder sb = new StringBuilder();
        long open = matches.stream()
                .filter(m -> m.getSecondPlayer() != null && !m.isDecided())
                .count();
        for (Match m : matches) {
            Player a = m.getFirstPlayer();
            String stage = stageLabel(m, koRounds);
            if (m.getSecondPlayer() == null) {
                String bye = m.getKoRound() > 0 ? "Freilos, kommt weiter"
                        : mode == TournamentMode.SWISS ? "Freilos" : "spielfrei";
                sb.append("           ").append(stage).append(a.getFullName()).append(" (").append(a.getClub()).append(") – ")
                        .append(bye).append('\n');
                continue;
            }
            Player b = m.getSecondPlayer();
            int[] sets = m.decidedSets();
            String status = sets != null ? sets[0] + ":" + sets[1] : "offen";
            sb.append(String.format("Tisch %2d: ", m.getTableNumber()))
                    .append(stage)
                    .append(a.getFullName()).append(" (").append(a.getClub()).append(") – ")
                    .append(b.getFullName()).append(" (").append(b.getClub()).append(")   ")
                    .append(status).append('\n');
        }
        if (!matches.isEmpty()) {
            sb.append('\n').append(open == 0 ? "Alle Ergebnisse erfasst." : open + " Begegnung(en) offen.");
        }
        return sb.toString();
    }

    private static String stageLabel(Match m, int koRounds) {
        if (m.getGroupNo() > 0) return "Gruppe " + GroupKoState.groupName(m.getGroupNo()) + "  ";
        if (m.isThirdPlace()) return "Spiel um Platz 3  ";
        if (m.getKoRound() > 0) {
            return (koRounds > 0 ? GroupKnockout.roundName(m.getKoRound(), koRounds) : m.getKoRound() + ". KO-Runde") + "  ";
        }
        return "";
    }

    private String finalPlacingText() {
        List<Match> ko = pairingEngine.getAllMatches().stream().filter(m -> m.getKoRound() > 0).toList();
        List<Map.Entry<Player, Integer>> placing = GroupKnockout.finalPlacing(ko, pairingEngine.knockoutRounds());
        if (placing.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\nEndplatzierung:\n");
        for (Map.Entry<Player, Integer> e : placing) {
            sb.append(String.format("%3d. ", e.getValue())).append(e.getKey().getFullName())
                    .append(" (").append(e.getKey().getClub()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Builds the table of a group/knockout tournament: one block per group with the group rank;
     * withdrawn participants are struck and listed at the end of their group.
     */
    private DefaultTableModel getGroupTableModel() {
        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String c : new String[]{"Gruppe", "Rang", "Name", "Punkte", "Spiele", "Sätze", "Bälle"}) {
            tableModel.addColumn(c);
        }
        GroupKoState groupKo = pairingEngine.getGroupKo();
        if (groupKo == null) return tableModel;
        Set<Player> withdrawn = pairingEngine.getWithdrawn();
        List<Match> counting = countingMatches();
        for (int g = 0; g < groupKo.groups().size(); g++) {
            List<Standings.Entry> entries = new ArrayList<>(
                    Standings.compute(groupKo.groups().get(g), TournamentMode.ROUND_ROBIN, counting));
            entries.sort(Comparator.comparing((Standings.Entry e) -> withdrawn.contains(e.player())));
            for (Standings.Entry e : entries) {
                Player p = e.player();
                boolean out = withdrawn.contains(p);
                tableModel.addRow(new Object[]{GroupKoState.groupName(g + 1), out ? "–" : e.rank(),
                        p.getFullName() + " (" + p.getClub() + ")" + (out ? " – zurückgezogen" : ""),
                        p.getPoints(), p.getWins() + ":" + p.getLosses(),
                        p.getSetsWon() + ":" + p.getSetsLost(), p.getBallsWon() + ":" + p.getBallsLost()});
            }
        }
        return tableModel;
    }

    /**
     * Builds the standings table model. Columns differ between Swiss System (with BHZ and fBHZ)
     * and Round Robin. Players equal in all criteria share a rank; withdrawn players are marked.
     */
    private DefaultTableModel getDefaultTableModel(List<Standings.Entry> standings) {
        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableModel.addColumn("Rang");
        tableModel.addColumn("Name");
        tableModel.addColumn("Punkte");
        tableModel.addColumn("Spiele");
        if (mode == TournamentMode.SWISS) {
            tableModel.addColumn("BHZ");
            tableModel.addColumn("fBHZ");
        }
        tableModel.addColumn("Sätze");
        tableModel.addColumn("Bälle");

        Set<Player> withdrawn = pairingEngine.getWithdrawn();
        boolean struck = mode == TournamentMode.ROUND_ROBIN;
        List<Standings.Entry> ordered = new ArrayList<>(standings);
        if (struck) {
            // Round Robin: withdrawn players are struck from the ranking and listed at the end
            ordered.sort(Comparator.comparing((Standings.Entry e) -> withdrawn.contains(e.player())));
        }
        for (Standings.Entry entry : ordered) {
            Player player = entry.player();
            boolean out = withdrawn.contains(player);
            String name = player.getFullName() + " (" + player.getClub() + ")"
                    + (out ? (struck ? " – zurückgezogen, Spiele gestrichen" : " – zurückgezogen") : "");
            Object rank = out && struck ? "–" : entry.rank();
            List<Object> row = new ArrayList<>(List.of(rank, name, player.getPoints(),
                    player.getWins() + ":" + player.getLosses()));
            if (mode == TournamentMode.SWISS) {
                row.add(player.getBuchholz());
                row.add(player.getFeinBuchholz());
            }
            row.add(player.getSetsWon() + ":" + player.getSetsLost());
            row.add(player.getBallsWon() + ":" + player.getBallsLost());
            tableModel.addRow(row.toArray());
        }
        return tableModel;
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private void previewRefereeSheets() {
        List<Match> matchesWithoutBye = pairingEngine.getMatches().stream()
                .filter(match -> match.getSecondPlayer() != null)
                .toList();
        new RefereeSheetsController(view, matchesWithoutBye, tournamentName, currentRound);
    }

    private void manipulatePairings() {
        boolean resultsEntered = pairingEngine.getMatches().stream()
                .anyMatch(match -> match.getSecondPlayer() != null && !match.getOverallResult().isEmpty());
        if (resultsEntered) {
            JOptionPane.showMessageDialog(view,
                    "Setzung kann nicht manipuliert werden, da bereits Ergebnisse eingetragen wurden.",
                    "Ergebnisse vorhanden", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Match> allPossibleOpenMatches = pairingEngine.calculateAllPossibleOpenMatches();
        allPossibleOpenMatches.sort(Comparator.comparing(match -> match.getFirstPlayer().getFullName()));
        Set<Match> uniqueMatches = new LinkedHashSet<>(allPossibleOpenMatches);
        new MatchManagerController(view, uniqueMatches, pairingEngine.activePlayers().size(), this)
                .getView().setVisible(true);
    }

    /**
     * Closes the current round and draws the next one, after a confirmation. If the planned
     * number of rounds is reached or no further round can be drawn, the tournament is finished;
     * the last round stays visible and editable.
     */
    public void startNextRound() {
        if (pairingEngine.isFinished()) return;
        List<String> open = unfinishedMatchDescriptions(countingMatches());
        if (!open.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Es gibt noch unbeendete oder ungültig erfasste Begegnungen. Bitte alle Ergebnisse vollständig erfassen\n"
                            + "(ein Spieler muss 3 Sätze gewonnen haben), bevor die nächste Runde gestartet werden kann:\n\n"
                            + String.join("\n", open.subList(0, Math.min(15, open.size())))
                            + (open.size() > 15 ? "\n…" : ""),
                    "Unbeendete Begegnungen", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int total = totalRounds();
        if (total > 0 && currentRound >= total) {
            String text = mode == TournamentMode.GROUPS_KO
                    ? "Das Finale ist gespielt.\nTurnier jetzt abschließen?"
                    : mode == TournamentMode.ROUND_ROBIN
                    ? "Alle " + total + " Runden sind gespielt.\nTurnier jetzt abschließen?"
                    : "Runde " + currentRound + " war die letzte geplante Runde.\nTurnier jetzt abschließen?";
            if (confirm(text, "Turnier abschließen")) {
                finishTournament("Das Turnier ist nach " + currentRound + " Runden beendet.");
            }
            return;
        }

        String question = mode == TournamentMode.GROUPS_KO && currentRound == pairingEngine.groupRounds()
                ? "Vorrunde abschließen und die KO-Runde auslosen?"
                : "Runde " + currentRound + " abschließen und Runde " + (currentRound + 1) + " auslosen?";
        if (!confirm(question, "Nächste Runde")) {
            return;
        }

        saveRoundBackup(); // state after the completed round; never invalidated by an undo
        String pairingsText = pairingEngine.generateNextRound(currentRound + 1);
        if (pairingsText == null) {
            String reason = pairingEngine.activePlayers().size() < 2
                    ? "Es sind weniger als zwei Spieler aktiv."
                    : mode == TournamentMode.ROUND_ROBIN
                    ? "Alle Runden sind gespielt."
                    : mode == TournamentMode.GROUPS_KO
                    ? "Die KO-Runde ist beendet."
                    : "Es ist keine weitere Runde ohne Wiederholungsbegegnung möglich.";
            finishTournament(reason + "\nDas Turnier ist nach " + currentRound + " Runden beendet.");
            return;
        }
        currentRound++;
        updateResultsTable();
        saveTournamentState();
    }

    private void finishTournament(String message) {
        pairingEngine.setFinished(true);
        updateResultsTable();
        saveTournamentState();
        JOptionPane.showMessageDialog(view, message, "Turnier beendet", JOptionPane.INFORMATION_MESSAGE);
    }

    private void undoLastRound() {
        if (!pairingEngine.canUndoLastRound()) return;
        if (!confirm("Die Auslosung von Runde " + currentRound + " zurücknehmen?\n"
                + "Runde " + (currentRound - 1) + " wird wieder die aktuelle Runde.", "Auslosung zurücknehmen")) {
            return;
        }
        pairingEngine.undoLastRound();
        currentRound--;
        updateResultsTable();
        saveTournamentState();
    }

    private void withdrawPlayer() {
        List<Player> active = pairingEngine.activePlayers();
        if (active.size() <= 2) {
            JOptionPane.showMessageDialog(view, "Es müssen mindestens zwei Spieler im Turnier bleiben.",
                    "Spieler zurückziehen", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<Player> box = new JComboBox<>(active.toArray(new Player[0]));
        int choice = JOptionPane.showConfirmDialog(view,
                new Object[]{"Welcher Spieler zieht sich zurück?", box,
                        switch (mode) {
                            case ROUND_ROBIN -> "Alle seine Spiele werden gestrichen, auch die bereits gespielten.\n"
                                    + "In den restlichen Runden sind seine Gegner spielfrei.";
                            case GROUPS_KO -> "Vorrunde: alle seine Gruppenspiele werden gestrichen.\n"
                                    + "KO-Runde: sein nächster Gegner kommt kampflos weiter.";
                            case SWISS -> "Bisherige Ergebnisse bleiben bestehen. Ab der nächsten Auslosung wird er nicht mehr gepaart.";
                        }},
                "Spieler zurückziehen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        Player player = (Player) box.getSelectedItem();
        if (choice != JOptionPane.OK_OPTION || player == null) return;
        pairingEngine.withdraw(player);
        updateResultsTable();
        saveTournamentState();
        boolean openMatch = mode != TournamentMode.ROUND_ROBIN && pairingEngine.getMatches().stream()
                .filter(m -> mode == TournamentMode.SWISS || m.getKoRound() > 0)
                .anyMatch(m -> m.getSecondPlayer() != null
                        && (m.getFirstPlayer().equals(player) || m.getSecondPlayer().equals(player))
                        && !m.isDecided());
        if (openMatch) {
            JOptionPane.showMessageDialog(view, player.getFullName() + " hat in dieser Runde noch ein offenes Spiel.\n"
                    + "Bitte das Ergebnis erfassen (bei Nichtantritt z. B. 3:0 mit 11:0-Sätzen für den Gegner).");
        }
    }

    private void addLatePlayer() {
        Player newPlayer = PlayerDialog.show(view, doubles ? "Doppel nachmelden" : "Spieler nachmelden", null, playerList, doubles);
        if (newPlayer == null) return;
        playerList.add(newPlayer);
        updateResultsTable();
        saveTournamentState();
        JOptionPane.showMessageDialog(view, newPlayer.getFullName() + " wird ab der nächsten Runde mit 0 Punkten gepaart.");
    }

    private boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(view, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Returns {@code true} if any non-bye match has no decided result (see {@link Match#isDecided()}).
     *
     * @param matches the matches to check
     * @return {@code true} if the round cannot be closed yet
     */
    static boolean hasUnfinishedMatches(List<Match> matches) {
        return matches.stream().anyMatch(match -> match.getSecondPlayer() != null && !match.isDecided());
    }

    /**
     * Describes all non-bye matches without decided result, e.g. "Runde 2, Tisch 3: A – B".
     *
     * @param matches the matches to check (all rounds)
     * @return one line per open match
     */
    static List<String> unfinishedMatchDescriptions(List<Match> matches) {
        return matches.stream()
                .filter(m -> m.getSecondPlayer() != null && !m.isDecided())
                .map(m -> (m.getRound() > 0 ? "Runde " + m.getRound() + ", " : "") + "Tisch " + m.getTableNumber() + ": "
                        + m.getFirstPlayer().getFullName() + " – " + m.getSecondPlayer().getFullName())
                .toList();
    }

    /**
     * Replaces the current round's pairings with the provided match selection and updates the UI.
     *
     * @param selectedMatches the manually selected matches to use for the current round
     */
    public void setNewMatches(List<Match> selectedMatches) {
        pairingEngine.setNewMatches(selectedMatches);
        updateResultsTable();
        saveTournamentState();
    }

    private void showCrossTable() {
        List<CrossTableController.Section> sections = crossTableSections(currentStandings());
        if (crossTable != null && crossTable.isOpen()) {
            crossTable.refresh(sections, countingMatches());
            crossTable.toFront();
        } else {
            crossTable = new CrossTableController(view, sections, countingMatches(), tournamentName);
        }
    }

    /**
     * Returns the sections of the cross table: one per group in a group/knockout tournament,
     * otherwise one with all players in ranking order.
     */
    private List<CrossTableController.Section> crossTableSections(List<Standings.Entry> standings) {
        GroupKoState groupKo = pairingEngine.getGroupKo();
        if (mode != TournamentMode.GROUPS_KO || groupKo == null) {
            return List.of(new CrossTableController.Section("Kreuztabelle",
                    standings.stream().map(Standings.Entry::player).toList()));
        }
        List<Match> counting = countingMatches();
        List<CrossTableController.Section> sections = new ArrayList<>();
        for (int g = 0; g < groupKo.groups().size(); g++) {
            sections.add(new CrossTableController.Section("Gruppe " + GroupKoState.groupName(g + 1),
                    Standings.compute(groupKo.groups().get(g), TournamentMode.ROUND_ROBIN, counting).stream()
                            .map(Standings.Entry::player).toList()));
        }
        return sections;
    }

    private void showBracket() {
        if (bracketView != null && bracketView.isOpen()) {
            bracketView.refresh(pairingEngine.getGroupKo(), pairingEngine.getAllMatches());
            bracketView.toFront();
        } else {
            bracketView = new BracketController(view, tournamentName, pairingEngine.getGroupKo(), pairingEngine.getAllMatches());
        }
    }

    private void printPlacementTable() {
        PrintUtil.printTable(view, view.getResultsTable(), "Rangliste - " + tournamentName,
                "Turnier: " + tournamentName + "  —  " + roundLabel()
                        + (pairingEngine.isFinished() ? " (Endstand)" : ""));
    }

    // ---------------------------------------------------------------------------------------
    // Rounds for result entry
    // ---------------------------------------------------------------------------------------

    /**
     * Returns the current round number.
     *
     * @return the current round
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * Returns the rounds whose results can be entered or corrected: the current round and every
     * earlier round whose matches are known (files from older versions have no round numbers).
     *
     * @return ascending round numbers
     */
    public List<Integer> correctableRounds() {
        List<Integer> rounds = new ArrayList<>();
        for (int r = 1; r < currentRound; r++) {
            if (!pairingEngine.getMatchesOfRound(r).isEmpty()) rounds.add(r);
        }
        rounds.add(currentRound);
        return rounds;
    }

    /**
     * Returns the matches of a round; for the current round these are the current matches.
     *
     * @param round the round number
     * @return the matches
     */
    public List<Match> matchesOfRound(int round) {
        return round == currentRound ? pairingEngine.getMatches() : pairingEngine.getMatchesOfRound(round);
    }

    // ---------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------

    /**
     * Saves the current tournament state to "&lt;Turniername&gt;.ser" in the default save directory
     * (see {@link TournamentStorage#defaultSaveDirectory()}). Called after every result entry,
     * pairing change, round change and when the window is closed, so no entered result is lost.
     * Shows an error dialog with the target path if the file cannot be written.
     */
    void saveTournamentState() {
        writeState(TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), tournamentName));
    }

    /**
     * Saves a backup of the state after a completed round ("…_Runde_N.ser"), right before the
     * next round is drawn.
     */
    private void saveRoundBackup() {
        writeState(TournamentStorage.saveFile(TournamentStorage.defaultSaveDirectory(), tournamentName, currentRound));
    }

    private void writeState(File file) {
        try {
            TournamentStorage.save(toState(), file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(view,
                    "Fehler beim Speichern nach:\n" + file.getAbsolutePath() + "\n\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates a snapshot of the current tournament state.
     *
     * @return the state
     */
    TournamentState toState() {
        return new TournamentState(
                new ArrayList<>(playerList),
                new ArrayList<>(pairingEngine.getAllMatches()),
                new ArrayList<>(pairingEngine.getMatches()),
                currentRound,
                pairingEngine.isFinished(),
                tournamentName,
                tableNumber,
                mode,
                new ArrayList<>(pairingEngine.getWithdrawn()),
                plannedRounds,
                doubles,
                pairingEngine.getGroupKo()
        );
    }

    /**
     * Reconstructs a {@link TournamentRound} from a previously saved {@link TournamentState}.
     *
     * @param state the saved state
     * @return the restored tournament
     */
    public static TournamentRound fromSavedState(TournamentState state) {
        TournamentRound round = new TournamentRound(
                new ArrayList<>(state.playerList()),
                state.tournamentName(),
                state.tableCount(),
                state.mode(),
                state.plannedRounds(),
                state.doubles(),
                state.groupKo(),
                true
        );
        round.currentRound = state.currentRound();
        round.pairingEngine.restoreState(state.allMatches(), state.matches());
        round.pairingEngine.setWithdrawn(state.withdrawnPlayers());
        round.pairingEngine.setFinished(state.finished());
        round.updateResultsTable();
        return round;
    }

    /**
     * Returns the matches scheduled for the current round.
     *
     * @return the current round's match list
     */
    public List<Match> getMatches() {
        return pairingEngine.getMatches();
    }
}
