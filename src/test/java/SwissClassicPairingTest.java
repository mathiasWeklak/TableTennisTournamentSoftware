import controller.PairingEngine;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classic (FIDE Dutch) Swiss pairing rules: upper half against lower half inside a score group,
 * the last player of an odd group floats down, and a floater plays the strongest player of the
 * next group according to the ranking (not the TTR).
 *
 * <p>Points and Buchholz are set directly, so the ranking is fully controlled by the test.</p>
 */
public class SwissClassicPairingTest {

    private static Player player(String name, int ttr, int points, int buchholz) {
        Player p = new Player(name, "X", "C", ttr);
        p.setPoints(points);
        p.setBuchholz(buchholz);
        return p;
    }

    private static Player opponentOf(List<Match> matches, Player p) {
        Match m = matches.stream()
                .filter(x -> p.equals(x.getFirstPlayer()) || p.equals(x.getSecondPlayer()))
                .findFirst()
                .orElseThrow();
        return p.equals(m.getFirstPlayer()) ? m.getSecondPlayer() : m.getFirstPlayer();
    }

    private static List<Match> pairRound2(List<Player> players, List<Match> history) {
        PairingEngine engine = new PairingEngine(players, 10, TournamentMode.SWISS);
        engine.restoreState(history, List.of());
        engine.generatePairings(2);
        return engine.getMatches();
    }

    @Test
    public void scoreGroupOfFour_firstPlaysFirstOfLowerHalf() {
        Player a = player("A", 1600, 1, 0);
        Player b = player("B", 1500, 1, 0);
        Player c = player("C", 1400, 1, 0);
        Player d = player("D", 1300, 1, 0);
        List<Match> m = pairRound2(List.of(a, b, c, d), List.of());
        assertEquals(c, opponentOf(m, a));
        assertEquals(d, opponentOf(m, b));
    }

    @Test
    public void scoreGroupUsesRankingNotTtr() {
        // B has the higher TTR, but C is ranked higher because of the Buchholz
        Player a = player("A", 1600, 1, 3);
        Player b = player("B", 1550, 1, 0);
        Player c = player("C", 1400, 1, 2);
        Player d = player("D", 1300, 1, 1);
        // ranking: A, C, D, B  ->  A-D, C-B
        List<Match> m = pairRound2(List.of(a, b, c, d), List.of());
        assertEquals(d, opponentOf(m, a));
        assertEquals(b, opponentOf(m, c));
    }

    @Test
    public void rematchInLowerHalf_triesNextPlayerOfLowerHalfFirst() {
        Player a = player("A", 1600, 1, 0);
        Player b = player("B", 1500, 1, 0);
        Player c = player("C", 1400, 1, 0);
        Player d = player("D", 1300, 1, 0);
        Player e = player("E", 1200, 0, 0);
        Player f = player("F", 1100, 0, 0);
        Player g = player("G", 1000, 0, 0);
        Player h = player("H", 900, 0, 0);
        List<Match> history = new ArrayList<>(List.of(new Match(a, c, 1)));
        List<Match> m = pairRound2(List.of(a, b, c, d, e, f, g, h), history);
        assertEquals(d, opponentOf(m, a), "A-C schon gespielt: zuerst D aus der unteren Hälfte");
        assertEquals(c, opponentOf(m, b));
        assertEquals(g, opponentOf(m, e));
        assertEquals(h, opponentOf(m, f));
    }

    @Test
    public void oddScoreGroup_lastFloatsDownAndPlaysStrongestOfNextGroup() {
        Player a = player("A", 1600, 1, 0);
        Player b = player("B", 1500, 1, 0);
        Player c = player("C", 1400, 1, 0);
        Player d = player("D", 1300, 1, 0);
        Player e = player("E", 1200, 1, 0);
        Player f = player("F", 1100, 0, 0);
        Player g = player("G", 1000, 0, 0);
        Player h = player("H", 900, 0, 0);
        // group of five: A-C, B-D, E floats down to the strongest 0-point player F; then G-H
        List<Match> m = pairRound2(List.of(a, b, c, d, e, f, g, h), List.of());
        assertEquals(c, opponentOf(m, a));
        assertEquals(d, opponentOf(m, b));
        assertEquals(f, opponentOf(m, e));
        assertEquals(h, opponentOf(m, g));
    }

    @Test
    public void floaterPlaysStrongestOfNextGroupByRankingNotByTtr() {
        Player a = player("A", 1600, 2, 0);
        Player b = player("B", 1500, 2, 0);
        Player c = player("C", 1400, 2, 0);
        // 1-point group: ranking D, E, F (Buchholz), although E has the highest TTR
        Player d = player("D", 1350, 1, 5);
        Player e = player("E", 1550, 1, 2);
        Player f = player("F", 1300, 1, 1);
        Player g = player("G", 1000, 0, 0);
        Player h = player("H", 900, 0, 0);
        List<Match> m = pairRound2(List.of(a, b, c, d, e, f, g, h), List.of());
        assertEquals(b, opponentOf(m, a), "Gruppe mit 3: A-B, C ist Floater");
        assertEquals(d, opponentOf(m, c), "Floater C spielt den Bestplatzierten der 1-Punkt-Gruppe");
        assertEquals(f, opponentOf(m, e));
        assertEquals(h, opponentOf(m, g));
    }

    @Test
    public void wholeRoundIsOptimised_noUnnecessaryFloaters() {
        // B and D have already played. Pairing from the top (A-C first) would leave B and D,
        // who both had to float down. Optimised: A-D, B-C, no floater at all.
        Player a = player("A", 1600, 1, 0);
        Player b = player("B", 1500, 1, 0);
        Player c = player("C", 1400, 1, 0);
        Player d = player("D", 1300, 1, 0);
        Player e = player("E", 1200, 0, 0);
        Player f = player("F", 1100, 0, 0);
        Player g = player("G", 1000, 0, 0);
        Player h = player("H", 900, 0, 0);
        List<Match> m = pairRound2(List.of(a, b, c, d, e, f, g, h), new ArrayList<>(List.of(new Match(b, d, 1))));
        assertEquals(d, opponentOf(m, a));
        assertEquals(c, opponentOf(m, b));
        assertEquals(g, opponentOf(m, e));
        assertEquals(h, opponentOf(m, f));
    }

    @Test
    public void wholeRoundIsOptimised_smallerScoreDifferenceWins() {
        // A (2 points) can only play D (1 point) or H (0 points) without rematch.
        // A-H would be a jump of two score groups; A-D keeps the differences small.
        Player a = player("A", 1600, 2, 0);
        Player b = player("B", 1500, 1, 0);
        Player c = player("C", 1400, 1, 0);
        Player d = player("D", 1300, 1, 0);
        Player e = player("E", 1200, 0, 0);
        Player f = player("F", 1100, 0, 0);
        Player g = player("G", 1000, 0, 0);
        Player h = player("H", 900, 0, 0);
        List<Match> history = new ArrayList<>(List.of(
                new Match(a, b, 1), new Match(a, c, 1), new Match(a, e, 1), new Match(a, f, 1), new Match(a, g, 1)));
        List<Match> m = pairRound2(List.of(a, b, c, d, e, f, g, h), history);
        assertEquals(d, opponentOf(m, a));
    }

    @Test
    public void round1_upperHalfAgainstLowerHalfByTtr() {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            players.add(new Player("P" + i, "X", "C", 2000 - 50 * i));
        }
        PairingEngine engine = new PairingEngine(new ArrayList<>(players), 5, TournamentMode.SWISS);
        engine.generatePairings(1);
        for (int i = 0; i < 5; i++) {
            assertEquals(players.get(i + 5), opponentOf(engine.getMatches(), players.get(i)));
        }
    }
}
