package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixes from the third review: struck matches in the pairing text, group settings messages and
 * the bracket placeholder.
 */
public class ReviewRound3Test {

    private static List<Player> players(int n) {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) ps.add(new Player("V" + i, "N" + i, "C", 2000 - i));
        return ps;
    }

    @Test
    public void pairingText_struckMatchIsNotOpen() {
        List<Player> ps = players(4);
        Match struck = new Match(ps.get(0), ps.get(1), 1);
        Match played = new Match(ps.get(2), ps.get(3), 2);
        played.setOverallResult("3:1");
        String text = TournamentRound.formatPairingsWithStatus(List.of(struck, played), TournamentMode.ROUND_ROBIN, 0,
                Set.of(ps.get(1)));
        assertTrue(text.contains("gestrichen"), text);
        assertTrue(text.contains("Alle Ergebnisse erfasst."), text);

        String withoutStruck = TournamentRound.formatPairingsWithStatus(List.of(struck, played), TournamentMode.ROUND_ROBIN, 0);
        assertTrue(withoutStruck.contains("1 Begegnung(en) offen."));
    }

    @Test
    public void groupSettings_bestNextPlacedMessagesAreAchievable() {
        String twoGroups = TournamentController.validateGroupSettings(8, 4, 2, 2);
        assertTrue(twoGroups.contains("höchstens 1 Nächstplatzierte"), twoGroups);
        assertNull(TournamentController.validateGroupSettings(8, 4, 2, 1));

        String oneGroup = TournamentController.validateGroupSettings(4, 4, 2, 1);
        assertTrue(oneGroup.contains("nur einer Gruppe"), oneGroup);

        // groups 4 and 3, 2 qualify: raising "weiter je Gruppe" would not be possible
        String noAdvice = TournamentController.validateGroupSettings(7, 4, 2, 2);
        assertFalse(noAdvice.contains("erhöhen"), noAdvice);
        // groups of 4, 1 qualifies: raising is possible
        assertTrue(TournamentController.validateGroupSettings(8, 4, 1, 2).contains("erhöhen"));
    }

    @Test
    public void bracketPlaceholder_mentionsThirdPlaceOnlyWithSemiFinals() {
        List<Player> ps = players(8);
        GroupKoState twoQualifiers = new GroupKoState(4, 1, 0, true)
                .withGroupsAndBracket(GroupKnockout.assignGroups(ps, 4), new ArrayList<>());
        assertFalse(BracketController.buildModel(twoQualifiers, List.of()).placeholder().contains("Platz 3"));

        GroupKoState fourQualifiers = new GroupKoState(4, 2, 0, true)
                .withGroupsAndBracket(GroupKnockout.assignGroups(ps, 4), new ArrayList<>());
        assertTrue(BracketController.buildModel(fourQualifiers, List.of()).placeholder().contains("Spiel um Platz 3"));
    }
}
