import controller.ScoreCalculator;
import controller.Standings;
import model.Match;
import model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unplayed games in Buchholz and Fein-Buchholz, following the direction of the FIDE tie-break
 * revision 2026: a bye counts at most "Mr. 50%", a walkover counts the lower score of both players.
 */
public class UnplayedTieBreakTest {

    private static Player player(String name) {
        return new Player(name, "X", "C", 1500);
    }

    private static Match played(Player a, Player b, int round, String overall) {
        Match m = new Match(a, b, 1);
        m.setPairingInfo(round, 0, 0);
        m.setOverallResult(overall);
        return m;
    }

    private static Match walkover(Player a, Player b, int round, boolean firstWins) {
        Match m = new Match(a, b, 1);
        m.setPairingInfo(round, 0, 0);
        for (int i = 0; i < 3; i++) {
            m.setResults(i, firstWins ? new String[]{"11", "0"} : new String[]{"0", "11"});
        }
        return m;
    }

    private static Match bye(Player a, int round) {
        Match m = new Match(a, null, -1);
        m.setPairingInfo(round, 0, 0);
        return m;
    }

    // --- walkover detection ---------------------------------------------------------------

    @Test
    public void walkover_isDetectedOnlyForThreeSetsElevenZero() {
        Player a = player("A");
        Player b = player("B");
        assertTrue(walkover(a, b, 1, true).isWalkover());
        assertTrue(walkover(a, b, 1, false).isWalkover());

        Match close = new Match(a, b, 1);
        close.setResults(0, new String[]{"11", "0"});
        close.setResults(1, new String[]{"11", "0"});
        close.setResults(2, new String[]{"11", "1"});
        assertTrue(close.isDecided());
        assertFalse(close.isWalkover());

        assertFalse(played(a, b, 1, "3:0").isWalkover(), "nur Gesamtergebnis ist kein Kampflos");
        assertFalse(new Match(a, b, 1).isWalkover());
        assertFalse(new Match(a, null, 1).isWalkover());
    }

    @Test
    public void walkover_scoresPointsAndSetsLikeAWin() {
        Player a = player("A");
        Player b = player("B");
        new ScoreCalculator(new ArrayList<>(List.of(walkover(a, b, 1, true)))).calculate(List.of(a, b));
        assertEquals(1, a.getPoints());
        assertEquals(1, a.getWins());
        assertEquals(3, a.getSetsWon());
        assertEquals(1, b.getLosses());
    }

    // --- walkover in Buchholz -------------------------------------------------------------

    @Test
    public void walkover_countsLowerScoreOfBothPlayers() {
        // Round 1: A beats B kampflos, C beats D. Round 2: A beats C, B beats D.
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        Player d = player("D");
        List<Match> matches = new ArrayList<>(List.of(
                walkover(a, b, 1, true), played(c, d, 1, "3:0"),
                played(a, c, 2, "3:1"), played(b, d, 2, "3:2")));
        new ScoreCalculator(matches).calculate(List.of(a, b, c, d));

        assertEquals(2, a.getPoints());
        assertEquals(1, b.getPoints());
        assertEquals(1, c.getPoints());
        assertEquals(0, d.getPoints());
        // A: walkover against B -> min(2, 1) = 1, plus C (1)
        assertEquals(2, a.getBuchholz());
        // B: walkover against A -> min(1, 2) = 1, plus D (0)
        assertEquals(1, b.getBuchholz());
        // players who did not take part in the walkover count A and B at face value
        assertEquals(2, c.getBuchholz(), "C: D (0) + A (2)");
        assertEquals(2, d.getBuchholz(), "D: C (1) + B (1)");
        // Fein-Buchholz: walkover -> lower Buchholz of A (2) and B (1)
        assertEquals(1 + c.getBuchholz(), a.getFeinBuchholz());
        assertEquals(1 + d.getBuchholz(), b.getFeinBuchholz());
    }

    @Test
    public void walkover_absentStrongerOpponentNoLongerInflatesBuchholz() {
        // A wins round 1 kampflos against B, who then wins two games: B's 2 points do not
        // count for A, only A's own lower score (1).
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        Player d = player("D");
        List<Match> matches = new ArrayList<>(List.of(
                walkover(a, b, 1, true), played(c, d, 1, "3:0"),
                played(b, c, 2, "3:0"), played(d, a, 2, "3:0"),
                played(b, d, 3, "3:0"), played(c, a, 3, "3:0")));
        new ScoreCalculator(matches).calculate(List.of(a, b, c, d));
        assertEquals(1, a.getPoints());
        assertEquals(2, b.getPoints());
        assertEquals(2, c.getPoints());
        assertEquals(1, d.getPoints());
        // A: walkover min(1, 2) = 1, D (1), C (2); formerly B's 2 points counted (5)
        assertEquals(4, a.getBuchholz());
    }

    // --- bye capped at half the number of rounds ------------------------------------------

    @Test
    public void bye_virtualOpponentIsCappedAtHalfTheRounds() {
        // Four rounds; A has the bye in round 1 and wins everything -> 4 points, but the
        // virtual opponent counts at most 4 / 2 = 2.
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        Player d = player("D");
        Player e = player("E");
        List<Match> matches = new ArrayList<>(List.of(
                bye(a, 1), played(b, c, 1, "3:0"), played(d, e, 1, "3:0"),
                played(a, b, 2, "3:0"), bye(c, 2), played(d, e, 2, "3:0"),
                played(a, d, 3, "3:0"), bye(e, 3), played(b, c, 3, "3:0"),
                played(a, c, 4, "3:0"), bye(b, 4), played(d, e, 4, "3:0")));
        new ScoreCalculator(matches).calculate(List.of(a, b, c, d, e));
        assertEquals(4, a.getPoints());
        assertEquals(3, b.getPoints());
        assertEquals(1, c.getPoints());
        assertEquals(3, d.getPoints());
        // B (3), D (3), C (1) + virtual opponent min(4, 2) = 2
        assertEquals(3 + 3 + 1 + 2, a.getBuchholz());
    }

    @Test
    public void bye_withOddRoundsCountsHalfPoint() {
        // Three rounds, A has 3 points: the virtual opponent counts 1.5
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        List<Match> matches = new ArrayList<>(List.of(
                bye(a, 1), played(b, c, 1, "3:0"),
                played(a, b, 2, "3:0"), bye(c, 2),
                played(a, c, 3, "3:0"), bye(b, 3)));
        new ScoreCalculator(matches).calculate(List.of(a, b, c));
        assertEquals(3, a.getPoints());
        assertEquals(2, b.getPoints());
        assertEquals(1, c.getPoints());
        assertEquals(2 + 1 + 1.5, a.getBuchholz(), 1e-9);
        assertEquals("4,5", Standings.formatScore(a.getBuchholz()));
    }

    @Test
    public void bye_weakPlayerKeepsOwnScore() {
        // the cap only limits upward: a player below 50 % keeps his own score
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        List<Match> matches = new ArrayList<>(List.of(
                bye(c, 1), played(a, b, 1, "3:0"),
                played(a, c, 2, "3:0"), bye(b, 2),
                played(b, c, 3, "3:0"), bye(a, 3)));
        new ScoreCalculator(matches).calculate(List.of(a, b, c));
        assertEquals(1, c.getPoints());
        // C: A (3) + B (2) + virtual opponent min(1, 1.5) = 1
        assertEquals(6, c.getBuchholz(), 1e-9);
    }

    @Test
    public void bye_interimCapUsesRoundsSoFar() {
        // after round 1 only: A's bye counts min(1, 0.5) = 0.5
        Player a = player("A");
        Player b = player("B");
        Player c = player("C");
        new ScoreCalculator(new ArrayList<>(List.of(bye(a, 1), played(b, c, 1, "3:0"))))
                .calculate(List.of(a, b, c));
        assertEquals(0.5, a.getBuchholz(), 1e-9);
    }

    // --- formatting -----------------------------------------------------------------------

    @Test
    public void formatScore_wholeAndHalfNumbers() {
        assertEquals("12", Standings.formatScore(12));
        assertEquals("0", Standings.formatScore(0));
        assertEquals("12,5", Standings.formatScore(12.5));
    }
}
