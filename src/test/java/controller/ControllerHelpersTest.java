package controller;

import model.Match;
import model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.MessageFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Swing-free helper logic of the controllers: duplicate names, round completion,
 * tab titles and print header escaping.
 */
public class ControllerHelpersTest {

    private final Player anna = new Player("Anna", "Alpha", "TTC A", 1500);
    private final Player ben = new Player("Ben", "Beta", "TTC B", 1400);
    private final Player cleo = new Player("Cleo", "Gamma", "TTC C", 1300);
    private final Player dora = new Player("Dora", "Delta", "TTC D", 1200);

    // --- duplicate names -------------------------------------------------------------------

    @Test
    public void isDuplicateName_sameNameDifferentClubAndTtr() {
        assertTrue(TournamentController.isDuplicateName(List.of(anna), "Anna", "Alpha"));
    }

    @Test
    public void isDuplicateName_ignoresCaseAndWhitespace() {
        assertTrue(TournamentController.isDuplicateName(List.of(anna), "  anna ", "ALPHA"));
        Player doubleName = new Player("Anna Lena", "Alpha", "TTC A", 1500);
        assertTrue(TournamentController.isDuplicateName(List.of(doubleName), "Anna   Lena", "alpha"));
    }

    @Test
    public void isDuplicateName_differentNamesAreAllowed() {
        assertFalse(TournamentController.isDuplicateName(List.of(anna), "Anna", "Beta"));
        assertFalse(TournamentController.isDuplicateName(List.of(anna), "Anne", "Alpha"));
        assertFalse(TournamentController.isDuplicateName(List.of(), "Anna", "Alpha"));
    }

    @Test
    public void isDuplicateName_firstAndLastNameAreNotMixedUp() {
        Player p = new Player("Ann", "Aalpha", "TTC A", 1500);
        assertFalse(TournamentController.isDuplicateName(List.of(p), "Anna", "alpha"));
    }

    // --- round completion ------------------------------------------------------------------

    private static Match matchWithSets(Player a, Player b, String... values) {
        Match m = new Match(a, b, 1);
        for (int i = 0; i < 5; i++) {
            String x = 2 * i < values.length ? values[2 * i] : "";
            String y = 2 * i + 1 < values.length ? values[2 * i + 1] : "";
            m.setResults(i, new String[]{x, y});
        }
        return m;
    }

    @Test
    public void hasUnfinishedMatches_falseWhenAllDecided() {
        List<Match> round = List.of(
                matchWithSets(anna, ben, "11", "3", "11", "4", "11", "5"),
                matchWithSets(cleo, dora, "9", "11", "11", "9", "8", "11", "11", "13"));
        assertFalse(TournamentRound.hasUnfinishedMatches(round));
    }

    @Test
    public void hasUnfinishedMatches_trueForNeverEnteredMatch() {
        assertTrue(TournamentRound.hasUnfinishedMatches(List.of(new Match(anna, ben, 1))));
    }

    @Test
    public void hasUnfinishedMatches_trueForUndecidedOrInvalidResult() {
        assertTrue(TournamentRound.hasUnfinishedMatches(List.of(matchWithSets(anna, ben, "11", "3", "11", "4"))));
        assertTrue(TournamentRound.hasUnfinishedMatches(List.of(matchWithSets(anna, ben, "11", "3", "11", "4", "11", "10"))));
        // overall result alone ("0:0" after typing and deleting) is not enough
        Match m = matchWithSets(anna, ben);
        m.setOverallResult("0:0");
        assertTrue(TournamentRound.hasUnfinishedMatches(List.of(m)));
    }

    @Test
    public void hasUnfinishedMatches_ignoresBye() {
        assertFalse(TournamentRound.hasUnfinishedMatches(List.of(new Match(anna, null, -1))));
    }

    // --- tab titles ------------------------------------------------------------------------

    @Test
    public void tabTitle_containsTableAndLastNames() {
        assertEquals("T3: Alpha – Beta", ResultEntryController.tabTitle(new Match(anna, ben, 3)));
    }

    @Test
    public void tabTitle_uniqueWhenTablesRepeat() {
        String first = ResultEntryController.tabTitle(new Match(anna, ben, 1));
        String second = ResultEntryController.tabTitle(new Match(cleo, dora, 1));
        assertNotEquals(first, second);
    }

    // --- print header escaping -------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Herbst Cup", "Kreis's Cup", "Cup {2026}", "a'{b}'c", "Seite {0}", "''", "}{", "'Cup'", "'{x}'", "{'}", "x}'", "'"})
    public void escapeForMessageFormat_printsTextLiterally(String text) {
        String pattern = PrintUtil.escapeForMessageFormat(text);
        assertEquals(text, new MessageFormat(pattern).format(new Object[]{42}));
    }

    @Test
    public void escapeForMessageFormat_nullIsEmpty() {
        assertEquals("", PrintUtil.escapeForMessageFormat(null));
    }
}
