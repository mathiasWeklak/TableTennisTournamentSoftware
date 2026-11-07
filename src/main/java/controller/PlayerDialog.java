package controller;

import model.Player;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * Dialog for entering or editing a player (first name, last name, club, optional TTR).
 * Used on the setup screen and for late registration during a Swiss tournament.
 */
public final class PlayerDialog {

    private PlayerDialog() {
    }

    /**
     * Shows the dialog until the input is valid or the user cancels.
     *
     * @param parent   parent component
     * @param title    dialog title
     * @param existing player to edit, or {@code null} for a new player
     * @param others   the other players (for the duplicate name check; must not contain {@code existing})
     * @return the new player, or {@code null} if cancelled
     */
    public static Player show(Component parent, String title, Player existing, List<Player> others) {
        JTextField firstNameField = new JTextField(existing == null ? "" : existing.getFirstName(), 15);
        JTextField lastNameField = new JTextField(existing == null ? "" : existing.getLastName(), 15);
        JTextField clubField = new JTextField(existing == null ? "" : existing.getClub(), 15);
        JTextField ttrField = new JTextField(existing == null || existing.getTtr() == 0 ? "" : String.valueOf(existing.getTtr()), 15);

        // put the cursor into the first name field as soon as the dialog is shown
        firstNameField.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(firstNameField::requestFocusInWindow);
                firstNameField.removeAncestorListener(this);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Vorname:"));
        panel.add(firstNameField);
        panel.add(new JLabel("Name:"));
        panel.add(lastNameField);
        panel.add(new JLabel("Verein:"));
        panel.add(clubField);
        panel.add(new JLabel("TTR-Wert (optional):"));
        panel.add(ttrField);

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String error = validate(firstNameField.getText(), lastNameField.getText(), clubField.getText(),
                    ttrField.getText(), others);
            if (error != null) {
                JOptionPane.showMessageDialog(parent, error);
                continue;
            }
            String ttr = ttrField.getText().trim();
            return new Player(firstNameField.getText().trim(), lastNameField.getText().trim(),
                    clubField.getText().trim(), ttr.isEmpty() ? 0 : Integer.parseInt(ttr));
        }
    }

    /**
     * Shows the dialog for a single player or, in a doubles competition, for a pair.
     *
     * @param parent   parent component
     * @param title    dialog title
     * @param existing participant to edit, or {@code null}
     * @param others   the other participants
     * @param doubles  {@code true} for a doubles competition
     * @return the new participant, or {@code null} if cancelled
     */
    public static Player show(Component parent, String title, Player existing, List<Player> others, boolean doubles) {
        return doubles ? showPair(parent, title, existing, others) : show(parent, title, existing, others);
    }

    /**
     * Shows the dialog for a doubles pair until the input is valid or the user cancels.
     *
     * @param parent   parent component
     * @param title    dialog title
     * @param existing pair to edit, or {@code null}
     * @param others   the other pairs (a person may only play in one pair)
     * @return the new pair, or {@code null} if cancelled
     */
    public static Player showPair(Component parent, String title, Player existing, List<Player> others) {
        boolean edit = existing != null && existing.isDoubles();
        JTextField[] first = {
                new JTextField(edit ? existing.getFirstName() : "", 12), new JTextField(edit ? existing.getPartnerFirstName() : "", 12)};
        JTextField[] last = {
                new JTextField(edit ? existing.getLastName() : "", 12), new JTextField(edit ? existing.getPartnerLastName() : "", 12)};
        JTextField[] club = {
                new JTextField(edit ? existing.getOwnClub() : "", 12), new JTextField(edit ? existing.getPartnerClub() : "", 12)};
        JTextField[] ttr = {
                new JTextField(edit && existing.getOwnTtr() > 0 ? String.valueOf(existing.getOwnTtr()) : "", 12),
                new JTextField(edit && existing.getPartnerTtr() > 0 ? String.valueOf(existing.getPartnerTtr()) : "", 12)};
        first[0].addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(first[0]::requestFocusInWindow);
                first[0].removeAncestorListener(this);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 3, 8, 4));
        panel.add(new JLabel(""));
        panel.add(new JLabel("Spieler 1"));
        panel.add(new JLabel("Spieler 2"));
        String[] labels = {"Vorname:", "Name:", "Verein:", "TTR-Wert (optional):"};
        JTextField[][] rows = {first, last, club, ttr};
        for (int r = 0; r < rows.length; r++) {
            panel.add(new JLabel(labels[r]));
            panel.add(rows[r][0]);
            panel.add(rows[r][1]);
        }

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String error = validatePair(
                    new String[]{first[0].getText(), last[0].getText(), club[0].getText(), ttr[0].getText()},
                    new String[]{first[1].getText(), last[1].getText(), club[1].getText(), ttr[1].getText()},
                    others);
            if (error != null) {
                JOptionPane.showMessageDialog(parent, error);
                continue;
            }
            Player a = new Player(first[0].getText().trim(), last[0].getText().trim(), club[0].getText().trim(),
                    ttr[0].getText().isBlank() ? 0 : Integer.parseInt(ttr[0].getText().trim()));
            Player b = new Player(first[1].getText().trim(), last[1].getText().trim(), club[1].getText().trim(),
                    ttr[1].getText().isBlank() ? 0 : Integer.parseInt(ttr[1].getText().trim()));
            return Player.pair(a, b);
        }
    }

    /**
     * Validates the input for a doubles pair.
     *
     * @param a      first person: first name, last name, club, TTR
     * @param b      second person: first name, last name, club, TTR
     * @param others the other pairs
     * @return an error message for the user, or {@code null} if the input is valid
     */
    static String validatePair(String[] a, String[] b, List<Player> others) {
        for (String[] person : new String[][]{a, b}) {
            String error = validate(person[0], person[1], person[2], person[3], List.of());
            if (error != null) return error;
        }
        if (personKey(a[0], a[1]).equals(personKey(b[0], b[1]))) {
            return "Ein Doppel braucht zwei verschiedene Spieler.";
        }
        Set<String> taken = new HashSet<>();
        for (Player p : others) {
            taken.add(personKey(p.getFirstName(), p.getLastName()));
            if (p.isDoubles()) taken.add(personKey(p.getPartnerFirstName(), p.getPartnerLastName()));
        }
        for (String[] person : new String[][]{a, b}) {
            if (taken.contains(personKey(person[0], person[1]))) {
                return person[0].strip() + " " + person[1].strip() + " spielt bereits in einem anderen Doppel.";
            }
        }
        return null;
    }

    private static String personKey(String firstName, String lastName) {
        return normalizeName(firstName) + "|" + normalizeName(lastName);
    }

    /**
     * Validates the input.
     *
     * @return an error message for the user, or {@code null} if the input is valid
     */
    static String validate(String firstName, String lastName, String club, String ttr, List<Player> others) {
        if (firstName.isBlank() || lastName.isBlank() || club.isBlank()) {
            return "Bitte füllen Sie alle Pflichtfelder aus.";
        }
        String t = ttr.trim();
        if (!t.isEmpty()) {
            try {
                if (Integer.parseInt(t) < 0) {
                    return "Bitte geben Sie einen ganzzahligen positiven TTR-Wert ein oder lassen Sie das Feld leer.";
                }
            } catch (NumberFormatException e) {
                return "Bitte geben Sie einen ganzzahligen positiven TTR-Wert ein oder lassen Sie das Feld leer.";
            }
        }
        if (isDuplicateName(others, firstName, lastName)) {
            return "Ein Spieler mit diesem Vor- und Nachnamen ist bereits in der Liste.\n"
                    + "Bitte zur Unterscheidung z. B. einen Namenszusatz ergänzen.";
        }
        return null;
    }

    /**
     * Returns {@code true} if a player with the same first and last name already exists.
     * The comparison ignores case and surrounding/multiple whitespace. Club and TTR are not
     * considered: two players with the same name could not be told apart in pairings, result
     * entry and printouts.
     *
     * @param players   the existing players
     * @param firstName the new player's first name
     * @param lastName  the new player's last name
     * @return {@code true} if the name is already taken
     */
    static boolean isDuplicateName(List<Player> players, String firstName, String lastName) {
        String key = normalizeName(firstName) + "|" + normalizeName(lastName);
        return players.stream()
                .anyMatch(p -> (normalizeName(p.getFirstName()) + "|" + normalizeName(p.getLastName())).equals(key));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
