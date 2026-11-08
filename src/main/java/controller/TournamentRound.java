package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.ResultRules;
import model.TournamentMode;
import model.TournamentState;
import view.GroupTablesPanel;
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
    private RefereeSheetsController refereeSheets;

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
        // Round Robin and group stage: the matches of a withdrawn player are struck
        this.scoreCalculator.setAnnulledPlayers(pairingEngine.annulledPlayers());
        if (mode == TournamentMode.GROUPS_KO && groupKo != null) {
            // new tournament: snake distribution in seeding order; loaded tournament: saved groups
            pairingEngine.setGroupKo(skipInitialPairing ? groupKo
                    : groupKo.withGroupsAndBracket(
                    GroupKnockout.assignGroups(this.playerList, groupKo.groupSize()), new ArrayList<>()));
        }

        this.view = new TournamentRoundView(tournamentName, mode);
        if (doubles) view.useDoublesLabels();
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
        return roundLabel(currentRound);
    }

    /**
     * Returns the short name of a round for dialogs: "Runde 3", "Runde 2 der Vorrunde" or the
     * knockout round name, e.g. "Halbfinale".
     *
     * @param round the round number
     * @return the name
     */
    public String roundName(int round) {
        if (mode == TournamentMode.GROUPS_KO) {
            int groupRounds = pairingEngine.groupRounds();
            if (round <= groupRounds) return "Runde " + round + " der Vorrunde";
            return GroupKnockout.roundName(round - groupRounds, pairingEngine.knockoutRounds());
        }
        return "Runde " + round;
    }

    private String roundLabel(int currentRound) {
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
        view.getPrintTableButton().addActionListener(_ -> showPrintMenu());
        view.getCrossTableButton().addActionListener(_ -> showCrossTable());
        if (view.getBracketButton() != null) {
            view.getBracketButton().addActionListener(_ -> showBracket());
        }
        view.getNextRoundButton().addActionListener(_ -> startNextRound());
        view.getUndoRoundButton().addActionListener(_ -> {
            if (currentRound == 1) backToSetup();
            else undoLastRound();
        });
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
        scoreCalculator.setAnnulledPlayers(pairingEngine.annulledPlayers());
        scoreCalculator.calculate(playerList);
        pairingEngine.assignFreeTables(); // waiting matches get the tables that became free
        List<Standings.Entry> standings = currentStandings();

        boolean groups = mode == TournamentMode.GROUPS_KO;
        if (groups && view.getGroupTablesPanel() != null) {
            view.getGroupTablesPanel().setTables(groupTables(false), groupLegend());
        } else {
            view.getResultsTable().setModel(getDefaultTableModel(standings));
            UITheme.applyTableStyling(view.getResultsTable());
            UITheme.setNameColumnWide(view.getResultsTable(), 1);
        }

        String text = formatPairingsWithStatus(pairingEngine.getMatches(), mode, pairingEngine.knockoutRounds(), struckPlayers());
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
        // Round Robin: withdrawn players are struck, the ranks are computed without them
        return Standings.computeWithout(playerList, mode, countingMatches(),
                mode == TournamentMode.ROUND_ROBIN ? pairingEngine.getWithdrawn() : Set.of());
    }

    /**
     * Returns the matches that count: in Round Robin without the struck matches of withdrawn
     * players, otherwise all matches.
     *
     * @return the counting matches
     */
    private List<Match> countingMatches() {
        return ScoreCalculator.countingMatches(pairingEngine.getAllMatches(), mode, pairingEngine.annulledPlayers());
    }

    private void updateControls() {
        boolean finished = pairingEngine.isFinished();
        view.showRound(roundLabel(), finished);
        view.getNextRoundButton().setEnabled(!finished);
        int total = totalRounds();
        view.getNextRoundButton().setText(total > 0 && currentRound >= total ? "Turnier abschließen"
                : mode == TournamentMode.GROUPS_KO && currentRound == pairingEngine.groupRounds() ? "KO-Runde auslosen"
                : "Nächste Runde auslosen");
        // round 1 without results: back to the setup (participants and settings stay), e.g. for latecomers
        boolean firstRound = currentRound == 1;
        view.getUndoRoundButton().setText(firstRound ? "Zurück zur Einrichtung" : "Auslosung zurücknehmen");
        view.getUndoRoundButton().setToolTipText(firstRound
                ? "Verwirft die Auslosung von Runde 1; Teilnehmer und Einstellungen bleiben erhalten (z. B. für Nachzügler)."
                : "Nimmt die Auslosung der aktuellen Runde zurück, solange noch kein Ergebnis erfasst ist.");
        view.getUndoRoundButton().setEnabled(firstRound
                ? !finished && !pairingEngine.hasResultsInCurrentRound()
                : pairingEngine.canUndoLastRound());
        view.getWithdrawPlayerButton().setEnabled(!finished);
        // a late registration is paired from the next round on: not in the last planned round
        if (view.getAddPlayerButton() != null) {
            view.getAddPlayerButton().setEnabled(!finished && (plannedRounds == 0 || currentRound < plannedRounds));
        }
        if (view.getManipulateButton() != null) view.getManipulateButton().setEnabled(!finished);
        Set<Player> struck = struckPlayers();
        boolean hasMatches = pairingEngine.getMatches().stream().anyMatch(m -> m.getSecondPlayer() != null && !isStruck(m, struck));
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
        return formatPairingsWithStatus(matches, mode, koRounds, Set.of());
    }

    /**
     * Formats the current round's pairings with their result status; matches of withdrawn players
     * whose matches are struck are shown as "gestrichen" and do not count as open.
     *
     * @param matches   the current round's matches
     * @param mode      the tournament mode
     * @param koRounds  number of knockout rounds (for the round names), 0 if not relevant
     * @param struck    players whose group or league matches are struck
     * @return the text for the pairings area
     */
    static String formatPairingsWithStatus(List<Match> matches, TournamentMode mode, int koRounds, Set<Player> struck) {
        StringBuilder sb = new StringBuilder();
        long open = matches.stream()
                .filter(m -> m.getSecondPlayer() != null && !m.isDecided() && !isStruck(m, struck))
                .count();
        for (Match m : matches) {
            Player a = m.getFirstPlayer();
            String stage = stageLabel(m, koRounds);
            if (m.getSecondPlayer() == null) {
                String bye = m.isThirdPlace() ? "kampflos Platz 3"
                        : m.getKoRound() > 0 && m.getKoRound() == koRounds ? "kampflos Turniersieg"
                        : m.getKoRound() > 0 ? "Freilos, kommt weiter"
                        : mode == TournamentMode.SWISS ? "Freilos" : "spielfrei";
                sb.append("           ").append(stage).append(a.getFullName()).append(" (").append(a.getClub()).append(") – ")
                        .append(bye).append('\n');
                continue;
            }
            Player b = m.getSecondPlayer();
            int[] sets = m.decidedSets();
            String status = isStruck(m, struck) ? "gestrichen (zurückgezogen)" : sets != null ? sets[0] + ":" + sets[1] : "offen";
            sb.append(m.getTableNumber() > 0 ? String.format("Tisch %2d: ", m.getTableNumber()) : "Wartet:   ")
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

    private static boolean isStruck(Match m, Set<Player> struck) {
        return m.getKoRound() == 0 && m.getSecondPlayer() != null
                && (struck.contains(m.getFirstPlayer()) || struck.contains(m.getSecondPlayer()));
    }

    /**
     * Group/knockout tournament with a drawn bracket: returns {@code true} if the participants of
     * the bracket are exactly those who qualify with the current group standings.
     *
     * @return {@code false} without bracket, in other formats or if they differ
     */
    boolean bracketMatchesQualifiers() {
        GroupKoState groupKo = pairingEngine.getGroupKo();
        if (mode != TournamentMode.GROUPS_KO || groupKo == null || groupKo.bracket().isEmpty()) return false;
        Set<Player> qualified = new HashSet<>();
        GroupKnockout.qualifiers(pairingEngine.rankedGroups(), groupKo.qualifiersPerGroup(), groupKo.bestNextPlaced())
                .forEach(qualified::addAll);
        Set<Player> inBracket = new HashSet<>();
        groupKo.bracket().stream().filter(p -> p != null && !pairingEngine.getWithdrawn().contains(p)).forEach(inBracket::add);
        return qualified.equals(inBracket);
    }

    /** Players whose group or league matches are struck (none in the Swiss System). */
    private Set<Player> struckPlayers() {
        return mode == TournamentMode.SWISS ? Set.of() : pairingEngine.annulledPlayers();
    }

    private static String stageLabel(Match m, int koRounds) {
        if (m.getGroupNo() > 0) return "Gruppe " + GroupKoState.groupName(m.getGroupNo()) + "  ";
        if (m.isThirdPlace()) return "Spiel um Platz 3  ";
        if (m.getKoRound() > 0) {
            return (koRounds > 0 ? GroupKnockout.roundName(m.getKoRound(), koRounds) : m.getKoRound() + ". KO-Runde") + "  ";
        }
        return "";
    }

    private List<Map.Entry<Player, Integer>> finalPlacing() {
        List<Match> ko = pairingEngine.getAllMatches().stream().filter(m -> m.getKoRound() > 0).toList();
        return GroupKnockout.finalPlacing(ko, pairingEngine.knockoutRounds());
    }

    private String finalPlacingText() {
        List<Map.Entry<Player, Integer>> placing = finalPlacing();
        if (placing.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\nEndplatzierung:\n");
        for (Map.Entry<Player, Integer> e : placing) {
            sb.append(String.format("%3d. ", e.getValue())).append(e.getKey().getFullName())
                    .append(" (").append(e.getKey().getClub()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Builds the group tables of a group/knockout tournament.
     *
     * @param compact {@code true}: short names for printing
     * @return one table per group, empty without groups
     */
    List<GroupTables.Table> groupTables(boolean compact) {
        GroupKoState groupKo = pairingEngine.getGroupKo();
        if (groupKo == null) return List.of();
        return GroupTables.build(groupKo, countingMatches(), pairingEngine.annulledPlayers(), pairingEngine.getWithdrawn(),
                knockoutParticipants(), compact);
    }

    /**
     * Returns the participants of the knockout stage: the drawn bracket, or, while the group
     * stage is running, those who would qualify with the current standings.
     */
    private Set<Player> knockoutParticipants() {
        GroupKoState groupKo = pairingEngine.getGroupKo();
        Set<Player> result = new HashSet<>();
        if (!groupKo.bracket().isEmpty()) {
            groupKo.bracket().stream().filter(Objects::nonNull).forEach(result::add);
        } else {
            GroupKnockout.qualifiers(pairingEngine.rankedGroups(), groupKo.qualifiersPerGroup(), groupKo.bestNextPlaced())
                    .forEach(result::addAll);
        }
        return result;
    }

    private String groupLegend() {
        GroupKoState groupKo = pairingEngine.getGroupKo();
        if (groupKo == null) return "";
        return groupKo.bracket().isEmpty()
                ? "Grün: nach aktuellem Stand in der KO-Runde (vorläufig, solange die Vorrunde läuft)."
                : "Grün: für die KO-Runde qualifiziert.";
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
                row.add(Standings.formatScore(player.getBuchholz()));
                row.add(Standings.formatScore(player.getFeinBuchholz()));
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
        closeRefereeSheets(); // one window, always with the current pairings
        Set<Player> struck = struckPlayers();
        List<Match> matchesWithoutBye = pairingEngine.getMatches().stream()
                .filter(match -> match.getSecondPlayer() != null && !isStruck(match, struck))
                .toList();
        // on the sheet "Runde 3" is enough (no "von 7"), so the tournament name fits next to it
        refereeSheets = new RefereeSheetsController(view, matchesWithoutBye, tournamentName,
                mode == TournamentMode.GROUPS_KO ? roundLabel() : "Runde " + currentRound, pairingEngine.knockoutRounds());
    }

    /** Closes an open referee sheets window, e.g. because the pairings changed. */
    private void closeRefereeSheets() {
        if (refereeSheets != null) {
            refereeSheets.close();
            refereeSheets = null;
        }
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
        new MatchManagerController(view, uniqueMatches, pairingEngine.currentRoundPlayers().size(), this)
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
                            + "(eine Seite muss 3 Sätze gewonnen haben), bevor die nächste Runde gestartet werden kann:\n\n"
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
                    ? (total == 1 ? "Die einzige Runde ist gespielt." : "Alle " + total + " Runden sind gespielt.")
                    + "\nTurnier jetzt abschließen?"
                    : "Runde " + currentRound + " war die letzte geplante Runde.\nTurnier jetzt abschließen?";
            if (confirm(text, "Turnier abschließen")) {
                finishTournament("Das Turnier ist nach " + rounds(currentRound) + " beendet.");
            }
            return;
        }

        String question = mode == TournamentMode.GROUPS_KO && currentRound == pairingEngine.groupRounds()
                ? "Vorrunde abschließen und die KO-Runde auslosen?"
                : roundName(currentRound) + " abschließen und " + roundName(currentRound + 1) + " auslosen?";
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
                    : allActiveHadBye()
                    ? "Alle verbleibenden Spieler hatten bereits ein Freilos; ein zweites Freilos ist nicht erlaubt."
                    : "Es ist keine weitere Runde ohne Wiederholungsbegegnung möglich.";
            finishTournament(reason + "\nDas Turnier ist nach " + rounds(currentRound) + " beendet.");
            return;
        }
        currentRound++;
        closeRefereeSheets();
        updateResultsTable();
        saveTournamentState();
    }

    /** Swiss System with an odd field in which every active player already had a bye. */
    private boolean allActiveHadBye() {
        List<Player> active = pairingEngine.activePlayers();
        return active.size() % 2 == 1
                && new HashSet<>(pairingEngine.getPlayersWithBye(pairingEngine.getAllMatches())).containsAll(active);
    }

    private static String rounds(int n) {
        return n == 1 ? "1 Runde" : n + " Runden";
    }

    private void finishTournament(String message) {
        pairingEngine.setFinished(true);
        updateResultsTable();
        saveTournamentState();
        JOptionPane.showMessageDialog(view, message, "Turnier beendet", JOptionPane.INFORMATION_MESSAGE);
    }

    private void undoLastRound() {
        if (!pairingEngine.canUndoLastRound()) return;
        if (!confirm("Die Auslosung zurücknehmen (" + roundName(currentRound) + ")?\n"
                + "Danach ist wieder " + roundName(currentRound - 1) + " die aktuelle Runde.", "Auslosung zurücknehmen")) {
            return;
        }
        pairingEngine.undoLastRound();
        currentRound--;
        closeRefereeSheets();
        updateResultsTable();
        saveTournamentState();
    }

    private void withdrawPlayer() {
        String title = doubles ? "Doppel zurückziehen" : "Spieler zurückziehen";
        List<Player> active = pairingEngine.activePlayers();
        if (active.size() <= 2) {
            JOptionPane.showMessageDialog(view, doubles ? "Es müssen mindestens zwei Doppel im Turnier bleiben."
                            : "Es müssen mindestens zwei Spieler im Turnier bleiben.", title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean knockoutStage = mode == TournamentMode.GROUPS_KO && currentRound > pairingEngine.groupRounds();
        // knockout stage: only participants of the running knockout round can withdraw
        List<Player> candidates = knockoutStage ? pairingEngine.currentRoundPlayers() : active;
        if (candidates.isEmpty()) return;
        JComboBox<Player> box = new JComboBox<>(candidates.toArray(new Player[0]));
        int choice = JOptionPane.showConfirmDialog(view,
                new Object[]{doubles ? "Welches Doppel zieht sich zurück?" : "Welcher Spieler zieht sich zurück?", box,
                        switch (mode) {
                            case ROUND_ROBIN -> "Alle Spiele werden gestrichen, auch die bereits gespielten.\n"
                                    + "In den restlichen Runden sind die Gegner spielfrei.";
                            case GROUPS_KO -> knockoutStage
                                    ? "Die Gruppenergebnisse bleiben bestehen.\nDer nächste Gegner kommt kampflos weiter."
                                    : "Alle Gruppenspiele werden gestrichen, auch die bereits gespielten.";
                            case SWISS -> "Bisherige Ergebnisse bleiben bestehen. Ab der nächsten Auslosung wird nicht mehr gepaart.";
                        }},
                title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        Player player = (Player) box.getSelectedItem();
        if (choice != JOptionPane.OK_OPTION || player == null) return;
        pairingEngine.withdraw(player);
        closeRefereeSheets(); // the sheets may contain matches that are struck now
        updateResultsTable();
        saveTournamentState();
        boolean openMatch = mode != TournamentMode.ROUND_ROBIN && pairingEngine.getMatches().stream()
                .filter(m -> mode == TournamentMode.SWISS || m.getKoRound() > 0)
                .anyMatch(m -> m.getSecondPlayer() != null
                        && (m.getFirstPlayer().equals(player) || m.getSecondPlayer().equals(player))
                        && !m.isDecided());
        if (openMatch) {
            JOptionPane.showMessageDialog(view, player.getFullName() + " hat in dieser Runde noch ein offenes Spiel.\n"
                    + "Bitte das Ergebnis mit der Schaltfläche „Kampflos“ in der Ergebniserfassung eintragen.");
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
                .map(m -> (m.getRound() > 0 ? "Runde " + m.getRound() + ", " : "") + "Tisch " + m.getTableLabel() + ": "
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
        closeRefereeSheets();
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
                    Standings.computeWithout(groupKo.groups().get(g), TournamentMode.ROUND_ROBIN, counting,
                            pairingEngine.annulledPlayers()).stream().map(Standings.Entry::player).toList()));
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

    /**
     * Discards the draw of round 1 and opens the setup screen with the same participants and
     * settings, e.g. to add latecomers. Only possible as long as no result is entered.
     */
    private void backToSetup() {
        if (currentRound != 1 || pairingEngine.hasResultsInCurrentRound()) return;
        if (!confirm("Zurück zur Einrichtung?\nDie Auslosung von Runde 1 wird verworfen; Teilnehmer und Einstellungen "
                + "bleiben erhalten.\nDanach Teilnehmer ergänzen und das Turnier neu beginnen.", "Zurück zur Einrichtung")) {
            return;
        }
        GroupKoState groupKo = pairingEngine.getGroupKo();
        GroupKoState settings = groupKo == null ? null : new GroupKoState(groupKo.groupSize(),
                groupKo.qualifiersPerGroup(), groupKo.bestNextPlaced(), groupKo.thirdPlaceMatch());
        List<Player> participants = new ArrayList<>(pairingEngine.activePlayers());
        closeRefereeSheets();
        view.dispose();
        TournamentController.openSetup(new TournamentController.Setup(tournamentName, tableNumber, mode, plannedRounds,
                doubles, settings, participants));
    }

    /** Opens the menu of the "Drucken / Export" button. */
    private void showPrintMenu() {
        JPopupMenu menu = new JPopupMenu();
        boolean groups = mode == TournamentMode.GROUPS_KO;
        menu.add(menuItem(groups ? "Gruppentabellen drucken" : "Rangliste drucken", this::printPlacementTable));
        menu.add(menuItem("Ergebnisliste drucken", this::printResultList));
        JMenuItem winners = menuItem(pairingEngine.isFinished() ? "Siegerliste drucken" : "Zwischenstand als Siegerliste drucken",
                this::printWinnerList);
        winners.setEnabled(!groups || pairingEngine.isFinished());
        if (groups && !pairingEngine.isFinished()) winners.setToolTipText("Bei Gruppen + KO erst nach dem Finale");
        menu.add(winners);
        menu.addSeparator();
        menu.add(menuItem(groups ? "Gruppentabellen als CSV speichern …" : "Rangliste als CSV speichern …", this::exportStandings));
        menu.add(menuItem("Ergebnisliste als CSV speichern …", this::exportResults));
        menu.addSeparator();
        menu.add(menuItem("Kopie des Turnierstands speichern (z. B. USB-Stick) …", this::saveCopy));
        JButton button = view.getPrintTableButton();
        menu.show(button, 0, -menu.getPreferredSize().height);
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(UITheme.FONT_BODY);
        item.addActionListener(_ -> action.run());
        return item;
    }

    /**
     * Rows of the result list: every decided match (struck matches excluded), by round.
     *
     * @return rows {round, table, first, second, result, sets}
     */
    List<String[]> resultListRows() {
        Set<Player> struck = struckPlayers();
        List<String[]> rows = new ArrayList<>();
        List<Match> matches = new ArrayList<>(pairingEngine.getAllMatches());
        matches.sort(Comparator.comparingInt(Match::getRound).thenComparing(Match::isThirdPlace)
                .thenComparingInt(Match::getTableNumber));
        for (Match m : matches) {
            if (m.getSecondPlayer() == null || isStruck(m, struck)) continue;
            int[] sets = m.decidedSets();
            if (sets == null) continue;
            String stage = m.isThirdPlace() ? "Spiel um Platz 3"
                    : m.getRound() > 0 ? roundName(m.getRound()) + (m.getGroupNo() > 0 ? ", Gruppe " + GroupKoState.groupName(m.getGroupNo()) : "")
                    : "–";
            StringBuilder setText = new StringBuilder();
            if (m.isWalkover()) {
                setText.append("kampflos");
            } else {
                for (String[] set : m.getResults()) {
                    if (set == null || ResultRules.checkSet(set[0], set[1]) != ResultRules.SetStatus.VALID) continue;
                    if (!setText.isEmpty()) setText.append(", ");
                    setText.append(set[0].trim()).append(':').append(set[1].trim());
                }
            }
            rows.add(new String[]{stage, m.getTableLabel(), m.getFirstPlayer().getFullName(), m.getSecondPlayer().getFullName(),
                    sets[0] + ":" + sets[1], setText.toString()});
        }
        return rows;
    }

    /**
     * Rows of the winner list: place, name, club. Swiss/Round Robin from the standings (struck
     * players without place, withdrawn players marked); group/knockout: the final placing,
     * followed by the participants eliminated in the group stage (without place).
     *
     * @return rows {place, name, club}
     */
    List<Object[]> winnerListRows() {
        List<Object[]> rows = new ArrayList<>();
        Set<Player> withdrawn = pairingEngine.getWithdrawn();
        if (mode == TournamentMode.GROUPS_KO) {
            Set<Player> placed = new HashSet<>();
            for (Map.Entry<Player, Integer> e : finalPlacing()) {
                placed.add(e.getKey());
                rows.add(new Object[]{e.getValue() + ".", e.getKey().getFullName()
                        + (withdrawn.contains(e.getKey()) ? " (zurückgezogen)" : ""), e.getKey().getClub()});
            }
            List<GroupTables.Table> tables = groupTables(false);
            List<List<Player>> groups = pairingEngine.getGroupKo().groups();
            for (int g = 0; g < tables.size(); g++) {
                GroupTables.Table t = tables.get(g); // tables are in group order
                List<Player> group = groups.get(g);
                for (int i = 0; i < group.size(); i++) {
                    Player p = group.get(i);
                    if (placed.contains(p)) continue;
                    Object place = t.rows().get(i)[t.columns().length - 1];
                    rows.add(new Object[]{"–", p.getFullName() + " (Vorrunde: " + t.title() + ", "
                            + (withdrawn.contains(p) ? "zurückgezogen" : place) + ")", p.getClub()});
                }
            }
            return rows;
        }
        Set<Player> struck = struckPlayers();
        for (Standings.Entry e : currentStandings()) {
            Player p = e.player();
            boolean out = struck.contains(p);
            rows.add(new Object[]{out ? "–" : e.rank() + ".", p.getFullName()
                    + (withdrawn.contains(p) ? " (zurückgezogen)" : ""), p.getClub()});
        }
        return rows;
    }

    private String printHeading(String what) {
        return what + ": " + tournamentName + "  —  " + roundLabel() + (pairingEngine.isFinished() ? " (Endstand)" : "");
    }

    private void printResultList() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"Runde", "Tisch", "Spieler", "Gegner", "Ergebnis", "Sätze"}, 0);
        resultListRows().forEach(model::addRow);
        if (model.getRowCount() == 0) {
            JOptionPane.showMessageDialog(view, "Es sind noch keine Ergebnisse erfasst.");
            return;
        }
        JTable table = new JTable(model);
        UITheme.applyTableStyling(table);
        UITheme.setNameColumnWide(table, 2);
        PrintUtil.printTable(view, table, "Ergebnisliste - " + tournamentName, printHeading("Ergebnisliste"));
    }

    private void printWinnerList() {
        String title = pairingEngine.isFinished() ? "Siegerliste" : "Zwischenstand";
        PrintUtil.print(view, GroupTablesPanel.printable(List.of(), winnerListRows(),
                pairingEngine.isFinished() ? "Endstand" : "Zwischenstand", printHeading(title)), title + " - " + tournamentName);
    }

    private void exportStandings() {
        List<String[]> rows = new ArrayList<>();
        if (mode == TournamentMode.GROUPS_KO) {
            rows.add(new String[]{"Gruppe", "Nr.", "Name", "Spiele", "Sätze", "Bälle", "Punkte", "Platz"});
            for (GroupTables.Table t : groupTables(false)) {
                int n = t.size();
                for (Object[] r : t.rows()) {
                    rows.add(new String[]{t.title(), String.valueOf(r[0]), String.valueOf(r[1]), String.valueOf(r[n + 2]),
                            String.valueOf(r[n + 3]), String.valueOf(r[n + 4]), String.valueOf(r[n + 5]), String.valueOf(r[n + 6])});
                }
            }
        } else {
            DefaultTableModel model = getDefaultTableModel(currentStandings());
            String[] header = new String[model.getColumnCount()];
            for (int c = 0; c < header.length; c++) header[c] = model.getColumnName(c);
            rows.add(header);
            for (int r = 0; r < model.getRowCount(); r++) {
                String[] row = new String[header.length];
                for (int c = 0; c < header.length; c++) row[c] = String.valueOf(model.getValueAt(r, c));
                rows.add(row);
            }
        }
        File target = CsvFiles.chooseTarget(view, mode == TournamentMode.GROUPS_KO ? "Gruppentabellen speichern" : "Rangliste speichern",
                TournamentStorage.sanitizeFileName(tournamentName) + (mode == TournamentMode.GROUPS_KO ? "_Gruppen" : "_Rangliste"));
        if (target != null) CsvFiles.write(view, target, ParticipantCsv.toCsv(rows));
    }

    private void exportResults() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Runde", "Tisch", "Spieler", "Gegner", "Ergebnis", "Sätze"});
        rows.addAll(resultListRows());
        File target = CsvFiles.chooseTarget(view, "Ergebnisliste speichern",
                TournamentStorage.sanitizeFileName(tournamentName) + "_Ergebnisse");
        if (target != null) CsvFiles.write(view, target, ParticipantCsv.toCsv(rows));
    }

    /** Saves a copy of the tournament state at a place of the user's choice (e.g. a USB stick). */
    private void saveCopy() {
        JFileChooser chooser = new JFileChooser(TournamentStorage.defaultSaveDirectory());
        chooser.setDialogTitle("Kopie des Turnierstands speichern");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Turnier-Dateien (*.ser)", "ser"));
        chooser.setSelectedFile(new File(TournamentStorage.sanitizeFileName(tournamentName) + "_Kopie.ser"));
        if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".ser")) file = new File(file.getParentFile(), file.getName() + ".ser");
        File current = TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), tournamentName);
        if (TournamentStorage.isSameFile(file, current)) {
            JOptionPane.showMessageDialog(view, "Das ist die Datei, in die ohnehin automatisch gespeichert wird.");
            return;
        }
        if (file.exists() && !confirm(file.getName() + " gibt es schon. Ersetzen?", "Kopie speichern")) return;
        if (writeState(file)) {
            JOptionPane.showMessageDialog(view, "Kopie gespeichert:\n" + file.getAbsolutePath()
                    + "\n\nWeiter automatisch gespeichert wird in:\n" + current.getAbsolutePath());
        }
    }

    private void printPlacementTable() {
        if (mode == TournamentMode.GROUPS_KO) {
            String heading = "Gruppen: " + tournamentName + "  —  " + roundLabel()
                    + (pairingEngine.isFinished() ? " (Endstand)" : "");
            List<Object[]> placing = new ArrayList<>();
            if (pairingEngine.isFinished()) {
                for (Map.Entry<Player, Integer> e : finalPlacing()) {
                    placing.add(new Object[]{e.getValue() + ".", e.getKey().getFullName(), e.getKey().getClub()});
                }
            }
            PrintUtil.print(view, GroupTablesPanel.printable(groupTables(true), placing, heading), "Gruppen - " + tournamentName);
            return;
        }
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
            // rounds whose matches are all struck have nothing to enter
            if (matchesOfRound(r).stream().anyMatch(m -> m.getSecondPlayer() != null)) rounds.add(r);
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
        List<Match> matches = round == currentRound ? pairingEngine.getMatches() : pairingEngine.getMatchesOfRound(round);
        Set<Player> struck = struckPlayers();
        return struck.isEmpty() ? matches : matches.stream().filter(m -> !isStruck(m, struck)).toList();
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
        File file = TournamentStorage.currentFile(TournamentStorage.defaultSaveDirectory(), tournamentName);
        if (writeState(file)) {
            view.showSaveInfo("Automatisch gespeichert " + java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " in " + file.getAbsolutePath());
        }
    }

    /**
     * Saves a backup of the state after a completed round ("…_Runde_N.ser"), right before the
     * next round is drawn.
     */
    private void saveRoundBackup() {
        writeState(TournamentStorage.saveFile(TournamentStorage.defaultSaveDirectory(), tournamentName, currentRound));
    }

    private boolean writeState(File file) {
        try {
            TournamentStorage.save(toState(), file);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(view,
                    "Fehler beim Speichern nach:\n" + file.getAbsolutePath() + "\n\n" + e.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return false;
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
