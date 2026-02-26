package controller;

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

    /**
     * Constructs a new ResultEntryController and immediately initializes the UI.
     * Must be called on the Event Dispatch Thread.
     *
     * @param matches         List of matches to display and manage results for
     * @param tournamentRound The tournament round associated with the matches
     */
    public ResultEntryController(List<Match> matches, TournamentRound tournamentRound) {
        this.view = new ResultEntryView(this, matches, tournamentRound);
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

                    IntStream.range(0, 5).forEach(i -> {
                        String[] result = new String[2];
                        result[0] = data.setResultsFields()[i][0].getText();
                        result[1] = data.setResultsFields()[i][1].getText();
                        data.match().setResults(i, result);
                    });

                    String overallResult = data.wonSetsField().getText() + ":" + data.lostSetsField().getText();
                    data.match().setOverallResult(overallResult);
                });

        view.getTournamentRound().updateResultsTable();
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

        int wonSets = (int) IntStream.range(0, 5)
                .filter(i -> {
                    try {
                        return Integer.parseInt(setResultsFields[i][0].getText())
                                > Integer.parseInt(setResultsFields[i][1].getText());
                    } catch (NumberFormatException _) {
                        return false;
                    }
                }).count();

        int lostSets = (int) IntStream.range(0, 5)
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
     * Creates a text field that only accepts positive integers.
     *
     * @return JTextField configured to accept only positive integers
     */
    public JTextField createIntegerField() {
        JTextField textField = new JTextField();
        textField.setDocument(new PositiveIntegerDocument());
        return textField;
    }

    /**
     * Document that allows only positive integers to be entered into a text field.
     */
    public static class PositiveIntegerDocument extends PlainDocument {
        /**
         * {@inheritDoc}
         * Filters out any non-digit characters before inserting, ensuring only
         * non-negative integers can be typed into the associated text field.
         */
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            StringBuilder sb = new StringBuilder();
            str.chars()
                    .filter(Character::isDigit)
                    .forEach(c -> sb.append((char) c));
            super.insertString(offs, sb.toString(), a);
        }
    }
}
