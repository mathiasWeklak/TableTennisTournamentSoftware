package controller;

import model.Match;
import view.ResultEntryView;
import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * This class represents the controller for managing the result entry window.
 */
public class ResultEntryController {

    private ResultEntryView view;

    /**
     * Constructs a new ResultEntryController.
     *
     * @param matches         List of matches to display and manage results for
     * @param tournamentRound The tournament round associated with the matches
     */
    public ResultEntryController(List<Match> matches, TournamentRound tournamentRound) {
        SwingUtilities.invokeLater(() -> initializeUI(matches, tournamentRound));
    }

    /**
     * Initializes the user interface.
     */
    private void initializeUI(List<Match> matches, TournamentRound tournamentRound) {
        view = new ResultEntryView(this, matches, tournamentRound);
    }

    /**
     * Saves the entered results for all matches and updates the UI accordingly.
     *
     * @param showConfirmation Flag indicating whether to show a confirmation message
     */
    public void saveResults(boolean showConfirmation) {
        Arrays.stream(view.getTabbedPane().getComponents())
                .filter(component -> component instanceof JPanel)
                .forEach(component -> {
                    JPanel matchPanel = (JPanel) component;
                    JTextField[][] setResultsFields = (JTextField[][]) matchPanel.getClientProperty("setResultsFields");
                    JTextField wonSetsField = (JTextField) matchPanel.getClientProperty("wonSetsField");
                    JTextField lostSetsField = (JTextField) matchPanel.getClientProperty("lostSetsField");
                    Match match = (Match) matchPanel.getClientProperty("match");

                    IntStream.range(0, 5).forEach(i -> {
                        String[] result = new String[2];
                        result[0] = setResultsFields[i][0].getText();
                        result[1] = setResultsFields[i][1].getText();
                        match.setResults(i, result);
                    });

                    String overallResult = wonSetsField.getText() + ":" + lostSetsField.getText();
                    match.setOverallResult(overallResult);
                });

        view.getTournamentRound().updateResultsTable();
        if (showConfirmation) {
            JOptionPane.showMessageDialog(view, "Ergebnisse gespeichert.");
        }
    }

    /**
     * Document listener that updates overall results based on set results changes.
     */
    public void updateOverallResult(JPanel matchPanel) {
        JTextField[][] setResultsFields = (JTextField[][]) matchPanel.getClientProperty("setResultsFields");
        JTextField wonSetsField = (JTextField) matchPanel.getClientProperty("wonSetsField");
        JTextField lostSetsField = (JTextField) matchPanel.getClientProperty("lostSetsField");

        if (setResultsFields == null || wonSetsField == null || lostSetsField == null) {
            return;
        }

        AtomicInteger wonSets = new AtomicInteger();
        AtomicInteger lostSets = new AtomicInteger();

        IntStream.range(0, 5)
                .forEach(i -> {
                    try {
                        int firstPlayerScore = Integer.parseInt(setResultsFields[i][0].getText());
                        int secondPlayerScore = Integer.parseInt(setResultsFields[i][1].getText());
                        if (firstPlayerScore > secondPlayerScore) {
                            wonSets.getAndIncrement();
                        } else if (secondPlayerScore > firstPlayerScore) {
                            lostSets.getAndIncrement();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                });

        wonSetsField.setText(String.valueOf(wonSets.get()));
        lostSetsField.setText(String.valueOf(lostSets.get()));
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
