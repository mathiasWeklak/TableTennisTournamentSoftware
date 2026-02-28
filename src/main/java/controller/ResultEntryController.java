package controller;

import static model.Match.MAX_SETS;

import model.Match;
import view.ResultEntryView;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This class represents the controller for managing the result entry window.
 */
public class ResultEntryController {

    public record MatchPanelData(JTextField[][] setResultsFields, JTextField wonSetsField,
                                 JTextField lostSetsField, Match match) {}

    private final ResultEntryView view;
    private final TournamentRound tournamentRound;

    /**
     * Constructs a new ResultEntryController and immediately initializes the UI.
     * Must be called on the Event Dispatch Thread.
     *
     * @param matches         List of matches to display and manage results for
     * @param tournamentRound The tournament round associated with the matches
     */
    public ResultEntryController(List<Match> matches, TournamentRound tournamentRound) {
        this.tournamentRound = tournamentRound;
        this.view = new ResultEntryView(this, matches);
    }

    /**
     * Saves the entered results for all matches and updates the UI accordingly.
     *
     * @param showConfirmation Flag indicating whether to show a confirmation message
     */
    public void saveResults(boolean showConfirmation) {
        Arrays.stream(view.getTabbedPane().getComponents())
                .filter(c -> c instanceof JPanel)
                .map(c -> (JPanel) c)
                .forEach(matchPanel -> {
                    MatchPanelData data = (MatchPanelData) matchPanel.getClientProperty("matchData");
                    if (data == null) return;

                    IntStream.range(0, MAX_SETS).forEach(i -> {
                        String[] result = new String[2];
                        result[0] = data.setResultsFields()[i][0].getText();
                        result[1] = data.setResultsFields()[i][1].getText();
                        data.match().setResults(i, result);
                    });

                    String overallResult = data.wonSetsField().getText() + ":" + data.lostSetsField().getText();
                    data.match().setOverallResult(overallResult);
                });

        tournamentRound.updateResultsTable();
        if (showConfirmation) {
            JOptionPane.showMessageDialog(view, "Ergebnisse gespeichert.");
        }
    }

    /**
     * Recalculates the overall result (sets won/lost) for a match panel based on the
     * currently entered individual set scores. Updates the read-only summary fields
     * in the panel accordingly.
     *
     * @param matchPanel the match panel whose score fields should be read and whose
     *                   summary fields should be updated
     */
    public void updateOverallResult(JPanel matchPanel) {
        MatchPanelData data = (MatchPanelData) matchPanel.getClientProperty("matchData");
        if (data == null) return;

        JTextField[][] setResultsFields = data.setResultsFields();
        JTextField wonSetsField = data.wonSetsField();
        JTextField lostSetsField = data.lostSetsField();

        int wonSets = (int) IntStream.range(0, MAX_SETS)
                .filter(i -> {
                    try {
                        return Integer.parseInt(setResultsFields[i][0].getText())
                                > Integer.parseInt(setResultsFields[i][1].getText());
                    } catch (NumberFormatException _) {
                        return false;
                    }
                }).count();

        int lostSets = (int) IntStream.range(0, MAX_SETS)
                .filter(i -> {
                    try {
                        return Integer.parseInt(setResultsFields[i][1].getText())
                                > Integer.parseInt(setResultsFields[i][0].getText());
                    } catch (NumberFormatException _) {
                        return false;
                    }
                }).count();

        wonSetsField.setText(String.valueOf(wonSets));
        lostSetsField.setText(String.valueOf(lostSets));
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
                    .filter(Character::isDigit)
                    .forEach(c -> sb.append((char) c));
            String filtered = sb.toString();
            if (filtered.isEmpty()) return;
            String current = getText(0, getLength());
            String result = current.substring(0, offs) + filtered + current.substring(offs);
            if (result.length() > 1 && result.startsWith("0")) return;
            super.insertString(offs, filtered, a);
        }
    }
}
