package controller;

import static model.Match.MAX_SETS;

import model.Match;
import model.Player;
import model.ResultRules;
import view.ResultEntryView;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Controller for the modal result entry dialog.
 *
 * <p>Shows the current round by default; earlier rounds can be selected to correct results.
 * Unsaved changes are detected when the dialog is closed or another round is selected, and the
 * user can save, discard or cancel.</p>
 */
public class ResultEntryController {

    /**
     * The input fields of one match tab.
     *
     * @param setResultsFields the score fields, {@code [set][player]}
     * @param wonSetsField     read-only sets won by the first player
     * @param lostSetsField    read-only sets won by the second player
     * @param match            the match
     */
    public record MatchPanelData(JTextField[][] setResultsFields, JTextField wonSetsField,
                                 JTextField lostSetsField, Match match) {}

    private final ResultEntryView view;
    private final TournamentRound tournamentRound;
    private int shownRound;

    /**
     * Constructs a new ResultEntryController and shows the modal result entry dialog for the
     * current round. Blocks until the dialog is closed. Must be called on the EDT.
     *
     * @param owner           the window that owns the modal dialog
     * @param tournamentRound the tournament
     */
    public ResultEntryController(Window owner, TournamentRound tournamentRound) {
        this(owner, tournamentRound, null);
    }

    /**
     * Shows the result entry for the current round with the given match selected (e.g. after a
     * double-click in the pairings). Blocks until the dialog is closed. Must be called on the EDT.
     *
     * @param owner           the window that owns the modal dialog
     * @param tournamentRound the tournament
     * @param selected        the match to show first, or {@code null}
     */
    public ResultEntryController(Window owner, TournamentRound tournamentRound, Match selected) {
        this.tournamentRound = tournamentRound;
        this.shownRound = tournamentRound.getCurrentRound();
        this.view = new ResultEntryView(owner, this, tournamentRound.correctableRounds(), shownRound);
        this.view.showMatches(tournamentRound.matchesOfRound(shownRound), false);
        if (selected != null) this.view.selectMatch(selected);
        this.view.setVisible(true);
    }

    /**
     * Saves the entered results of the shown round, recalculates the standings and writes the
     * tournament state to disk. Shows a warning listing inconsistent set results, if any.
     *
     * @param showConfirmation Flag indicating whether to show a confirmation message
     */
    public void saveResults(boolean showConfirmation) {
        List<String> problems = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        // group/knockout: did the qualifiers match the drawn bracket before this change?
        boolean bracketMatchedQualifiers = tournamentRound.bracketMatchesQualifiers();
        List<Match> waiting = tournamentRound.getMatches().stream()
                .filter(m -> m.getSecondPlayer() != null && m.getTableNumber() <= 0).toList();
        List<Match> decidedBefore = view.getMatchPanelData().stream().map(MatchPanelData::match).filter(Match::isDecided).toList();
        for (MatchPanelData data : view.getMatchPanelData()) {
            String[][] sets = readSets(data.setResultsFields());
            if (changesDrawnWinner(data.match(), sets, tournamentRound.getCurrentRound())) {
                // the winner is already drawn into the next knockout round: keep the stored result
                blocked.add(data.match().getFirstPlayer().getFullName() + " – " + data.match().getSecondPlayer().getFullName());
                restoreFields(data);
                continue;
            }
            IntStream.range(0, MAX_SETS).forEach(i -> data.match().setResults(i, sets[i]));
            data.match().setOverallResult(ResultRules.overallResult(sets));

            ResultRules.validateSets(sets).forEach(problem -> problems.add(
                    "Tisch " + data.match().getTableLabel() + " ("
                            + data.match().getFirstPlayer().getFullName() + " – "
                            + data.match().getSecondPlayer().getFullName() + "): " + problem));
        }

        tournamentRound.updateResultsTable();
        tournamentRound.saveTournamentState();

        List<String> called = waiting.stream().filter(m -> m.getTableNumber() > 0)
                .map(m -> "Tisch " + m.getTableNumber() + ": " + m.getFirstPlayer().getFullName()
                        + " – " + m.getSecondPlayer().getFullName())
                .toList();
        if (!called.isEmpty()) view.refreshTabTitles();

        // first the feedback on the own input, then consequences, last the next call
        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Ergebnisse gespeichert, aber folgende Eingaben sind ungültig und werden nicht gewertet:\n\n"
                            + String.join("\n", problems),
                    "Ungültige Satzergebnisse", JOptionPane.WARNING_MESSAGE);
        } else if (showConfirmation && called.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Ergebnisse gespeichert.");
        }
        if (!blocked.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Diese Ergebnisse wurden nicht geändert, weil der Sieger schon in der nächsten KO-Runde steht:\n\n"
                            + String.join("\n", blocked)
                            + "\n\nUm den Sieger zu ändern, bitte zuerst die Auslosung der nächsten Runde zurücknehmen.",
                    "KO-Ergebnis gesperrt", JOptionPane.WARNING_MESSAGE);
        }
        if (bracketMatchedQualifiers && !tournamentRound.bracketMatchesQualifiers()) {
            JOptionPane.showMessageDialog(view,
                    "Durch die Korrektur hätten sich andere Teilnehmer für die KO-Runde qualifiziert.\n"
                            + "Die bereits ausgeloste KO-Runde bleibt unverändert.\n\n"
                            + "Um neu auszulosen, die Auslosung der ersten KO-Runde zurücknehmen\n"
                            + "(möglich, solange dort noch kein Ergebnis eingetragen ist).",
                    "Weiterkommende geändert", JOptionPane.WARNING_MESSAGE);
        }
        List<Match> reopened = decidedBefore.stream().filter(m -> !m.isDecided()).toList();
        List<String> doubleTables = reopened.isEmpty() ? List.of() : tournamentRound.tablesWithTwoOpenMatches(reopened);
        if (!doubleTables.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Ein Ergebnis wurde wieder geöffnet, sein Tisch ist inzwischen neu vergeben:\n\n"
                            + String.join("\n", doubleTables)
                            + "\n\nBitte das richtige Ergebnis erfassen; der Tisch bleibt dem laufenden Spiel.",
                    "Tisch doppelt", JOptionPane.INFORMATION_MESSAGE);
        }
        if (!called.isEmpty()) {
            JOptionPane.showMessageDialog(view, (showConfirmation && problems.isEmpty() ? "Ergebnisse gespeichert.\n\n" : "")
                    + "Jetzt aufrufen:\n\n" + String.join("\n", called), "Tisch frei", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Returns {@code true} if the entered sets would change the winner of a knockout match whose
     * winner has already been drawn into a later round.
     *
     * @param match        the match
     * @param sets         the entered sets
     * @param currentRound the current round of the tournament
     * @return {@code true} if the change must not be saved
     */
    static boolean changesDrawnWinner(Match match, String[][] sets, int currentRound) {
        if (match.getKoRound() <= 0 || match.getRound() <= 0 || match.getRound() >= currentRound) return false;
        Player newWinner = null;
        if (ResultRules.isComplete(sets)) {
            int[] won = ResultRules.countSets(sets);
            newWinner = won[0] > won[1] ? match.getFirstPlayer() : match.getSecondPlayer();
        }
        return !java.util.Objects.equals(match.getWinner(), newWinner);
    }

    private static void restoreFields(MatchPanelData data) {
        String[][] stored = data.match().getResults();
        for (int i = 0; i < MAX_SETS; i++) {
            for (int j = 0; j < 2; j++) {
                String v = stored[i] == null || stored[i][j] == null ? "" : stored[i][j];
                data.setResultsFields()[i][j].setText(v);
            }
        }
    }

    /**
     * Returns the name of a round for the round selector, e.g. "Halbfinale".
     *
     * @param round the round number
     * @return the name
     */
    public String roundName(int round) {
        return tournamentRound.roundName(round);
    }

    /**
     * Returns {@code true} if any field differs from the stored results of its match.
     *
     * @return {@code true} if there are unsaved changes
     */
    public boolean hasUnsavedChanges() {
        return view.getMatchPanelData().stream()
                .anyMatch(data -> differs(readSets(data.setResultsFields()), data.match().getResults()));
    }

    /**
     * Compares entered set results with stored ones; {@code null} and empty count as equal,
     * surrounding whitespace is ignored.
     *
     * @param entered the values from the input fields
     * @param stored  the values stored in the match
     * @return {@code true} if at least one value differs
     */
    static boolean differs(String[][] entered, String[][] stored) {
        for (int i = 0; i < MAX_SETS; i++) {
            for (int j = 0; j < 2; j++) {
                String a = value(entered, i, j);
                String b = value(stored, i, j);
                if (!a.equals(b)) return true;
            }
        }
        return false;
    }

    private static String value(String[][] sets, int i, int j) {
        if (sets == null || i >= sets.length || sets[i] == null || sets[i][j] == null) return "";
        return sets[i][j].trim();
    }

    /**
     * Asks what to do with unsaved changes. Without changes nothing is asked.
     *
     * @return {@code true} if the caller may continue (saved or discarded), {@code false} if the
     *         user cancelled
     */
    public boolean confirmPendingChanges() {
        if (!hasUnsavedChanges()) return true;
        Object[] options = {"Speichern", "Verwerfen", "Abbrechen"};
        int choice = JOptionPane.showOptionDialog(view,
                "Es gibt nicht gespeicherte Eingaben. Was soll damit passieren?",
                "Ungespeicherte Änderungen", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            saveResults(false);
            return true;
        }
        return choice == 1;
    }

    /**
     * Closes the dialog after asking about unsaved changes.
     */
    public void requestClose() {
        if (confirmPendingChanges()) {
            view.dispose();
        }
    }

    /**
     * Shows another round, after asking about unsaved changes of the current one.
     *
     * @param round the round to show
     * @return {@code true} if the round was switched, {@code false} if the user cancelled
     */
    public boolean switchRound(int round) {
        if (round == shownRound) return true;
        if (!confirmPendingChanges()) return false;
        shownRound = round;
        view.showMatches(tournamentRound.matchesOfRound(round), round != tournamentRound.getCurrentRound());
        return true;
    }

    /**
     * Recalculates the overall result (sets won/lost) of a match panel from the entered set
     * scores and updates the tab's completion mark. Only valid sets are counted.
     *
     * @param matchPanel the match panel
     */
    public void updateOverallResult(JPanel matchPanel) {
        MatchPanelData data = (MatchPanelData) matchPanel.getClientProperty("matchData");
        if (data == null) return;

        String[][] sets = readSets(data.setResultsFields());
        int[] won = ResultRules.countSets(sets);
        data.wonSetsField().setText(String.valueOf(won[0]));
        data.lostSetsField().setText(String.valueOf(won[1]));
        view.markTab(matchPanel, ResultRules.isComplete(sets));
    }

    /**
     * Builds a unique tab title for a match. Table numbers can repeat when there are more matches
     * than tables, so the players' last names are part of the title.
     *
     * @param match a non-bye match
     * @return the tab title, e.g. "T1: Müller – Meier"
     */
    public static String tabTitle(Match match) {
        return tabTitle(match, java.util.Map.of());
    }

    /**
     * Builds the tab title of a match with short names that are unique within the round
     * (see {@link Player#uniqueShortNames}).
     *
     * @param match      a non-bye match
     * @param shortNames unique short names; players missing in the map use their last name
     * @return the tab title, e.g. "T1: A. Müller – Meier"; "T–" for a match waiting for a table
     */
    public static String tabTitle(Match match, java.util.Map<Player, String> shortNames) {
        return "T" + match.getTableLabel() + ": "
                + shortNames.getOrDefault(match.getFirstPlayer(), match.getFirstPlayer().getShortName())
                + " – " + shortNames.getOrDefault(match.getSecondPlayer(), match.getSecondPlayer().getShortName());
    }

    private static String[][] readSets(JTextField[][] fields) {
        String[][] sets = new String[MAX_SETS][2];
        for (int i = 0; i < MAX_SETS; i++) {
            sets[i][0] = fields[i][0].getText();
            sets[i][1] = fields[i][1].getText();
        }
        return sets;
    }

    /**
     * Creates a text field that only accepts non-negative integers without leading zeros.
     *
     * @return JTextField configured to accept only non-negative integers
     */
    public JTextField createIntegerField() {
        JTextField textField = new JTextField();
        textField.setDocument(new DigitsOnlyDocument());
        return textField;
    }

    /**
     * Document that allows only non-negative integers without leading zeros to be entered
     * into a text field (e.g. "0", "11", "42" are valid; "007" is not).
     */
    public static class DigitsOnlyDocument extends PlainDocument {
        /**
         * {@inheritDoc}
         * Filters out non-digit characters and prevents a leading zero when the field
         * already contains content or when the inserted string would cause one.
         */
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            StringBuilder sb = new StringBuilder();
            str.chars()
                    .filter(c -> c >= '0' && c <= '9') // only ASCII digits (no full-width digits)
                    .forEach(c -> sb.append((char) c));
            String filtered = sb.toString();
            if (filtered.isEmpty()) return;
            String current = getText(0, getLength());
            String result = current.substring(0, offs) + filtered + current.substring(offs);
            if (result.length() > 1 && result.startsWith("0")) return;
            if (result.length() > 3) return; // no set ends beyond three digits
            super.insertString(offs, filtered, a);
        }
    }
}
