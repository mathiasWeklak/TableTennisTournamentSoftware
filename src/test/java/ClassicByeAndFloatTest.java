import controller.PairingEngine;
import controller.ScoreCalculator;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classic bye scoring (one point, no sets/balls, virtual opponent for Buchholz) and the float
 * history (no identical float in two consecutive rounds where avoidable).
 */
public class ClassicByeAndFloatTest {

    private static Player player(String name, int ttr, int points) {
        Player p = new Player(name, "X", "C", ttr);
        p.setPoints(points);
        return p;
    }

    private static Match decided(Player a, Player b, String overall) {
        Match m = new Match(a, b, 1);
        m.setOverallResult(overall);
        return m;
    }

    private static Match paired(Player a, Player b, int round, int pointsA, int pointsB) {
        Match m = new Match(a, b, 1);
        m.setPairingInfo(round, pointsA, pointsB);
        return m;
    }

    private static Player opponentOf(List<Match> matches, Player p) {
        Match m = matches.stream()
                .filter(x -> p.equals(x.getFirstPlayer()) || p.equals(x.getSecondPlayer()))
                .findFirst()
                .orElseThrow();
        return p.equals(m.getFirstPlayer()) ? m.getSecondPlayer() : m.getFirstPlayer();
    }

    // --- bye scoring -------------------------------------------------------------------------

    @Test
    public void bye_givesPointButNoSetsBallsOrWin() {
        Player a = player("A", 1500, 0);
        Player b = player("B", 1400, 0);
        Match win = decided(a, b, "3:1");
        new ScoreCalculator(new ArrayList<>(List.of(new Match(a, null, -1), win))).calculate(List.of(a, b));

        assertEquals(2, a.getPoints());
        assertEquals(1, a.getWins(), "nur das gespielte Spiel zählt als Sieg");
        assertEquals(3, a.getSetsWon());
        assertEquals(1, a.getSetsLost());
        assertEquals(0, a.getBallsWon());
    }

    @Test
    public void bye_buchholzAndFeinBuchholzUseVirtualOpponent() {
        Player a = player("A", 1500, 0);
        Player b = player("B", 1400, 0);
        Player c = player("C", 1300, 0);
        List<Match> matches = new ArrayList<>(List.of(
                new Match(a, null, -1),
                decided(a, b, "3:0"),
                decided(c, b, "3:0")));
        new ScoreCalculator(matches).calculate(List.of(a, b, c));

        assertEquals(2, a.getPoints());
        assertEquals(0, b.getPoints());
        assertEquals(1, c.getPoints());
        // A: B (0) + virtual opponent with A's own points (2)
        assertEquals(2, a.getBuchholz());
        // B: A (2) + C (1)
        assertEquals(3, b.getBuchholz());
        assertEquals(0, c.getBuchholz());
        // A: Buchholz of B (3) + virtual opponent with A's own Buchholz (2)
        assertEquals(5, a.getFeinBuchholz());
        assertEquals(2, b.getFeinBuchholz());
    }

    @Test
    public void bye_noLongerBenefitsSetAndBallDifference() {
        // A had a bye and lost one match 0:3; B won one match and lost one, equal sets.
        Player a = player("A", 1500, 0);
        Player b = player("B", 1400, 0);
        Player c = player("C", 1300, 0);
        new ScoreCalculator(new ArrayList<>(List.of(
                new Match(a, null, -1),
                decided(c, a, "3:0")))).calculate(List.of(a, b, c));
        assertEquals(-3, a.getSetsWon() - a.getSetsLost());
    }

    // --- float information on matches ----------------------------------------------------------

    @Test
    public void match_floatDirections() {
        Player a = player("A", 1500, 0);
        Player b = player("B", 1400, 0);
        Match m = paired(a, b, 2, 2, 1);
        assertTrue(m.isDownfloatFor(a));
        assertTrue(m.isUpfloatFor(b));
        assertFalse(m.isUpfloatFor(a));
        assertFalse(m.isDownfloatFor(b));

        Match equal = paired(a, b, 2, 1, 1);
        assertFalse(equal.isDownfloatFor(a) || equal.isUpfloatFor(a) || equal.isDownfloatFor(b) || equal.isUpfloatFor(b));

        Match bye = new Match(a, null, -1);
        assertTrue(bye.isDownfloatFor(a), "Freilos zählt als Downfloat");
        assertFalse(bye.isUpfloatFor(a));

        assertFalse(m.isDownfloatFor(player("Z", 1000, 0)));
        assertEquals(0, new Match(a, b, 1).getRound(), "unbekannte Runde (alte Speicherdateien)");
    }

    @Test
    public void engine_storesRoundAndPointsBeforeRound() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 5; i++) ps.add(player("P" + i, 1500 - 50 * i, 0));
        PairingEngine engine = new PairingEngine(ps, 3, TournamentMode.SWISS);
        engine.generatePairings(1);
        engine.getMatches().forEach(m -> assertEquals(1, m.getRound()));

        Player bye = engine.getMatches().stream().filter(m -> m.getSecondPlayer() == null)
                .findFirst().orElseThrow().getFirstPlayer();
        assertEquals(Set.of(bye), engine.floatersOfRound(1, true), "in Runde 1 floatet nur der Freilos-Spieler");
        assertTrue(engine.floatersOfRound(1, false).isEmpty());
        assertTrue(engine.floatersOfRound(0, true).isEmpty());

        // round 2: winners have 1 point, the pairing records that
        for (Match m : engine.getMatches()) {
            if (m.getSecondPlayer() != null) m.setOverallResult("3:0");
        }
        new ScoreCalculator(engine.getAllMatches()).calculate(ps);
        engine.clearCurrentRound();
        engine.generatePairings(2);
        for (Match m : engine.getMatches()) {
            assertEquals(2, m.getRound());
            if (m.getSecondPlayer() != null && m.getFirstPlayer().getPoints() != m.getSecondPlayer().getPoints()) {
                Player higher = m.getFirstPlayer().getPoints() > m.getSecondPlayer().getPoints()
                        ? m.getFirstPlayer() : m.getSecondPlayer();
                assertTrue(m.isDownfloatFor(higher));
            }
        }
    }

    @Test
    public void manualPairingAndRestoreKeepRoundNumber() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 4; i++) ps.add(player("P" + i, 1500 - 50 * i, 0));
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.generatePairings(1);
        engine.clearCurrentRound();
        engine.generatePairings(2);
        engine.setNewMatches(List.of(new Match(ps.get(0), ps.get(3), -1), new Match(ps.get(1), ps.get(2), -1)));
        engine.getMatches().forEach(m -> assertEquals(2, m.getRound()));

        PairingEngine restored = new PairingEngine(ps, 2, TournamentMode.SWISS);
        restored.restoreState(new ArrayList<>(engine.getAllMatches()), new ArrayList<>(engine.getMatches()));
        restored.setNewMatches(List.of(new Match(ps.get(0), ps.get(3), -1), new Match(ps.get(1), ps.get(2), -1)));
        restored.getMatches().forEach(m -> assertEquals(2, m.getRound()));
    }

    // --- float history in the pairing ----------------------------------------------------------

    @Test
    public void oddGroup_withoutHistory_lastPlayerFloats() {
        Player a = player("A", 1600, 1), b = player("B", 1500, 1), c = player("C", 1400, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        PairingEngine engine = new PairingEngine(List.of(a, b, c, d, e, f), 3, TournamentMode.SWISS);
        engine.generatePairings(2);
        List<Match> m = engine.getMatches();
        assertEquals(b, opponentOf(m, a));
        assertEquals(d, opponentOf(m, c));
        assertEquals(f, opponentOf(m, e));
    }

    @Test
    public void oddGroup_lastPlayerFloatedDownLastRound_nextOneFloatsInstead() {
        Player a = player("A", 1600, 1), b = player("B", 1500, 1), c = player("C", 1400, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        PairingEngine engine = new PairingEngine(List.of(a, b, c, d, e, f), 3, TournamentMode.SWISS);
        // round 1: C floated down against F
        engine.restoreState(new ArrayList<>(List.of(paired(c, f, 1, 1, 0))), List.of());
        engine.generatePairings(2);
        List<Match> m = engine.getMatches();
        assertEquals(c, opponentOf(m, a), "C bleibt in der Gruppe");
        assertEquals(d, opponentOf(m, b), "B floatet statt C und spielt den Stärksten der nächsten Gruppe");
        assertEquals(f, opponentOf(m, e));
    }

    @Test
    public void floater_avoidsOpponentWhoFloatedUpLastRound() {
        Player a = player("A", 1600, 1), b = player("B", 1500, 1), c = player("C", 1400, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        PairingEngine engine = new PairingEngine(List.of(a, b, c, d, e, f), 3, TournamentMode.SWISS);
        // round 1: D floated up against A
        engine.restoreState(new ArrayList<>(List.of(paired(a, d, 1, 1, 0))), List.of());
        engine.generatePairings(2);
        List<Match> m = engine.getMatches();
        assertEquals(b, opponentOf(m, a));
        assertEquals(e, opponentOf(m, c), "D floatete letzte Runde hoch, daher E");
        assertEquals(f, opponentOf(m, d));
    }

    @Test
    public void floatHistory_twoRoundsAgoAlsoCounts() {
        Player a = player("A", 1600, 1), b = player("B", 1500, 1), c = player("C", 1400, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        PairingEngine engine = new PairingEngine(List.of(a, b, c, d, e, f), 3, TournamentMode.SWISS);
        // C floated down in round 1, the pairing is for round 3: B floats instead of C
        engine.restoreState(new ArrayList<>(List.of(paired(c, f, 1, 1, 0))), List.of());
        engine.generatePairings(3);
        assertEquals(c, opponentOf(engine.getMatches(), a));
        assertEquals(d, opponentOf(engine.getMatches(), b));
    }

    @Test
    public void floatHistory_previousRoundWeighsMoreThanTwoRoundsAgo() {
        Player a = player("A", 1600, 1), b = player("B", 1500, 1), c = player("C", 1400, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        Player g = player("G", 1000, 0), h = player("H", 900, 0);
        PairingEngine engine = new PairingEngine(List.of(a, b, c, d, e, f, g, h), 4, TournamentMode.SWISS);
        // round 1: C floated down; round 2: A and B floated down
        engine.restoreState(new ArrayList<>(List.of(
                paired(c, h, 1, 1, 0),
                paired(a, g, 2, 1, 0),
                paired(b, f, 2, 1, 0))), List.of());
        engine.generatePairings(3);
        List<Match> m = engine.getMatches();
        assertEquals(b, opponentOf(m, a));
        assertEquals(d, opponentOf(m, c), "C floatete vor zwei Runden, A und B in der Vorrunde: C floatet");
    }

    @Test
    public void floatHistory_isOnlyPreferenceNotHardRule() {
        // group A, C, X: C and X floated down last round, so the top player A floats (FIDE: any
        // player may float to avoid an identical float); if everyone floated, someone floats again
        Player a = player("A", 1600, 1), c = player("C", 1400, 1), x = player("X", 1350, 1);
        Player d = player("D", 1300, 0), e = player("E", 1200, 0), f = player("F", 1100, 0);
        PairingEngine engine = new PairingEngine(List.of(a, c, x, d, e, f), 3, TournamentMode.SWISS);
        engine.restoreState(new ArrayList<>(List.of(paired(c, f, 1, 1, 0), paired(x, e, 1, 1, 0))), List.of());
        engine.generatePairings(2);
        List<Match> m = engine.getMatches();
        assertEquals(x, opponentOf(m, c));
        assertEquals(d, opponentOf(m, a), "A floatet und spielt D (E und F floateten hoch)");

        // everyone in the group floated down last round: still a valid pairing, lowest floats
        PairingEngine engine2 = new PairingEngine(List.of(a, c, x, d, e, f), 3, TournamentMode.SWISS);
        engine2.restoreState(new ArrayList<>(List.of(paired(a, d, 1, 1, 0),
                paired(c, f, 1, 1, 0), paired(x, e, 1, 1, 0))), List.of());
        engine2.generatePairings(2);
        List<Match> m2 = engine2.getMatches();
        assertEquals(c, opponentOf(m2, a));
        assertNotNull(opponentOf(m2, x));
    }
}
