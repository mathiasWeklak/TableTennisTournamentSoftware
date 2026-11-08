package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import view.BracketView;

import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Controller for the knockout bracket (Turnierbaum) of a group/knockout tournament: turns the
 * bracket and the knockout matches into boxes for the view.
 */
public class BracketController {

    /**
     * One match box of the bracket.
     *
     * @param top    upper participant ("" if not known yet)
     * @param bottom lower participant ("" if not known yet, "Freilos" for a bye)
     * @param topSets    sets of the upper participant, or "" if not decided
     * @param bottomSets sets of the lower participant, or ""
     * @param winner 1 = upper, 2 = lower, 0 = not decided
     */
    public record Box(String top, String bottom, String topSets, String bottomSets, int winner) {
    }

    /**
     * The whole bracket for display.
     *
     * @param roundNames  name of every knockout round
     * @param rounds      boxes of every round
     * @param thirdPlace  the match for third place, or {@code null}
     * @param placeholder text shown while there is no bracket yet, otherwise {@code null}
     */
    public record Model(List<String> roundNames, List<List<Box>> rounds, Box thirdPlace, String placeholder) {
    }

    private final BracketView view;

    /**
     * Creates the controller and shows the bracket dialog. Must be called on the EDT.
     *
     * @param owner          the tournament window
     * @param tournamentName the tournament name
     * @param groupKo        groups and bracket
     * @param allMatches     all matches
     */
    public BracketController(Window owner, String tournamentName, GroupKoState groupKo, List<Match> allMatches) {
        this.view = new BracketView(owner, tournamentName, buildModel(groupKo, allMatches));
        this.view.setVisible(true);
    }

    /**
     * Updates the bracket, if the dialog is open.
     *
     * @param groupKo    groups and bracket
     * @param allMatches all matches
     */
    public void refresh(GroupKoState groupKo, List<Match> allMatches) {
        if (isOpen()) view.setModel(buildModel(groupKo, allMatches));
    }

    /**
     * Returns {@code true} while the dialog is open.
     *
     * @return {@code true} if open
     */
    public boolean isOpen() {
        return view.isDisplayable();
    }

    /**
     * Brings the dialog to the front.
     */
    public void toFront() {
        view.toFront();
    }

    /**
     * Builds the display model of the bracket.
     *
     * @param groupKo    groups and bracket
     * @param allMatches all matches
     * @return the model
     */
    /** "Zweite", "Dritte", ... for a group place (used after "Gruppen-"). */
    static String placedName(int place) {
        return switch (place) {
            case 2 -> "Zweite";
            case 3 -> "Dritte";
            case 4 -> "Vierte";
            case 5 -> "Fünfte";
            default -> place + ".";
        };
    }

    /** Number of qualifiers if nobody withdraws (a third place needs two semi-finals with a loser). */
    private static int expectedQualifiers(GroupKoState groupKo) {
        if (groupKo.groups().isEmpty()) return Integer.MAX_VALUE; // groups not known yet
        int expected = 0;
        int nextPlaced = 0;
        for (List<Player> g : groupKo.groups()) {
            expected += Math.min(groupKo.qualifiersPerGroup(), g.size());
            if (g.size() > groupKo.qualifiersPerGroup()) nextPlaced++;
        }
        return expected + Math.min(groupKo.bestNextPlaced(), nextPlaced);
    }

    static Model buildModel(GroupKoState groupKo, List<Match> allMatches) {
        if (groupKo == null || groupKo.bracket().isEmpty()) {
            String text = "Der Turnierbaum wird nach der Vorrunde ausgelost.";
            if (groupKo != null) {
                text += (groupKo.qualifiersPerGroup() == 1
                        ? (groupKo.bestNextPlaced() > 0 ? " Weiter kommen der Erste" : " Weiter kommt der Erste")
                        : " Weiter kommen die ersten " + groupKo.qualifiersPerGroup()) + " jeder Gruppe"
                        + (groupKo.bestNextPlaced() > 0
                        ? " und " + (groupKo.bestNextPlaced() == 1 ? "der beste " : "die " + groupKo.bestNextPlaced() + " besten ")
                        + "Gruppen-" + placedName(groupKo.qualifiersPerGroup() + 1) + (groupKo.bestNextPlaced() == 1 ? "" : "n")
                        : "") + "."
                        + (expectedQualifiers(groupKo) < 4 ? ""
                        : groupKo.thirdPlaceMatch() ? " Es gibt ein Spiel um Platz 3."
                        : " Ohne Spiel um Platz 3: beide Halbfinal-Verlierer werden Dritte.");
            }
            return new Model(List.of(), List.of(), null, text);
        }
        List<Player> bracket = groupKo.bracket();
        List<Match> ko = allMatches.stream().filter(m -> m.getKoRound() > 0).toList();
        int total = GroupKnockout.knockoutRounds(bracket.size());
        List<String> names = new ArrayList<>();
        List<List<Box>> rounds = new ArrayList<>();
        for (int r = 1; r <= total; r++) {
            names.add(GroupKnockout.roundName(r, total));
            List<Player> entrants = GroupKnockout.entrants(bracket, ko, r, Set.of());
            List<Box> boxes = new ArrayList<>();
            for (int slot = 0; slot < entrants.size() / 2; slot++) {
                Player a = entrants.get(2 * slot);
                Player b = entrants.get(2 * slot + 1);
                Match m = GroupKnockout.find(ko, r, slot, false);
                boolean byeKnown = (r == 1 && (a == null) != (b == null)) || (m != null && m.getSecondPlayer() == null);
                if (m != null && m.getSecondPlayer() == null) {
                    a = m.getFirstPlayer();
                    b = null;
                } else if (r == 1 && a == null && b != null) {
                    a = b;
                    b = null;
                }
                Player walkoverLoser = m == null ? null : GroupKnockout.loserOf(ko, m);
                if (m != null && m.getSecondPlayer() == null && walkoverLoser != null) {
                    // the opponent withdrew after his previous match: walkover
                    boxes.add(new Box(m.getFirstPlayer().getFullName(), walkoverLoser.getFullName() + " (zurückgezogen)",
                            "", "", 1));
                    continue;
                }
                boxes.add(box(a, b, m, byeKnown));
            }
            rounds.add(boxes);
        }
        Match third = GroupKnockout.find(ko, total, 0, true);
        Box thirdBox = third == null ? null
                : box(third.getFirstPlayer(), third.getSecondPlayer(), third, third.getSecondPlayer() == null);
        if (third != null && third.getSecondPlayer() == null) {
            // walkover: show the semi-final loser who withdrew
            for (Match semi : ko) {
                Player loser = semi.getKoRound() == total - 1 && !semi.isThirdPlace() ? GroupKnockout.loserOf(ko, semi) : null;
                if (loser != null && !loser.equals(third.getFirstPlayer())) {
                    thirdBox = new Box(third.getFirstPlayer().getFullName(), loser.getFullName() + " (zurückgezogen)", "", "", 1);
                }
            }
        }
        return new Model(names, rounds, thirdBox, null);
    }

    private static Box box(Player a, Player b, Match m, boolean bye) {
        String top = a == null ? "" : a.getFullName();
        String bottom = b != null ? b.getFullName() : bye ? "Freilos" : "";
        String topSets = "";
        String bottomSets = "";
        int winner = 0;
        if (m != null) {
            int[] sets = m.decidedSets();
            if (sets != null) {
                boolean swapped = a != null && a.equals(m.getSecondPlayer());
                topSets = String.valueOf(swapped ? sets[1] : sets[0]);
                bottomSets = String.valueOf(swapped ? sets[0] : sets[1]);
            }
            Player w = m.getWinner();
            if (w != null && (sets != null || m.getSecondPlayer() == null)) {
                winner = w.equals(a) ? 1 : 2;
            }
        }
        return new Box(top, bottom, topSets, bottomSets, winner);
    }
}
