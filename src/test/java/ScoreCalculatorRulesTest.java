import controller.ScoreCalculator;
import model.Match;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that only decided, valid results are counted, and that match data is copied defensively.
 */
public class ScoreCalculatorRulesTest {

    private Player p1;
    private Player p2;

    @BeforeEach
    public void setUp() {
        p1 = new Player("Anna", "Alpha", "TTC A", 1500);
        p2 = new Player("Ben", "Beta", "TTC B", 1400);
    }

    private void calculate(Match... matches) {
        new ScoreCalculator(new ArrayList<>(List.of(matches))).calculate(List.of(p1, p2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0:0", "1:0", "2:2", "2:1", "3:3", "4:0", ":", ""})
    public void undecidedResultsAreNotCounted(String overall) {
        Match m = new Match(p1, p2, 1);
        m.setResults(0, new String[]{"11", "5"});
        m.setOverallResult(overall);
        calculate(m);

        assertEquals(0, p1.getPoints());
        assertEquals(0, p1.getWins() + p1.getLosses() + p2.getWins() + p2.getLosses());
        assertEquals(0, p1.getSetsWon() + p1.getSetsLost());
        assertEquals(0, p1.getBallsWon() + p2.getBallsWon());
    }

    @Test
    public void decidedResultCountsWinnerSetsAndBalls() {
        Match m = new Match(p1, p2, 1);
        m.setResults(0, new String[]{"11", "5"});
        m.setResults(1, new String[]{"9", "11"});
        m.setResults(2, new String[]{"12", "10"});
        m.setResults(3, new String[]{"11", "7"});
        m.setOverallResult("3:1");
        calculate(m);

        assertEquals(1, p1.getPoints());
        assertEquals(1, p1.getWins());
        assertEquals(1, p2.getLosses());
        assertEquals(3, p1.getSetsWon());
        assertEquals(1, p1.getSetsLost());
        assertEquals(11 + 9 + 12 + 11, p1.getBallsWon());
        assertEquals(5 + 11 + 10 + 7, p1.getBallsLost());
        assertEquals(p1.getBallsLost(), p2.getBallsWon());
    }

    @Test
    public void secondPlayerWinning() {
        Match m = new Match(p1, p2, 1);
        m.setOverallResult("2:3");
        calculate(m);
        assertEquals(0, p1.getPoints());
        assertEquals(1, p2.getPoints());
        assertEquals(1, p1.getLosses());
    }

    @Test
    public void invalidSetMakesMatchNotCount() {
        Match m = new Match(p1, p2, 1);
        m.setResults(0, new String[]{"11", "5"});
        m.setResults(1, new String[]{"11", "10"}); // invalid, ignored
        m.setResults(2, new String[]{"11", "3"});
        m.setResults(3, new String[]{"11", "4"});
        m.setOverallResult("3:0");
        calculate(m);
        // one set is invalid: the match is not decided, so nothing counts (single rule, Match#isDecided)
        assertEquals(0, p1.getPoints());
        assertEquals(0, p1.getBallsWon());
    }

    @Test
    public void match_setResultsStoresCopy() {
        Match m = new Match(p1, p2, 1);
        String[] set = {"11", "5"};
        m.setResults(0, set);
        set[0] = "0";
        assertEquals("11", m.getResults()[0][0]);
    }

    @Test
    public void match_getResultsReturnsCopy() {
        Match m = new Match(p1, p2, 1);
        m.setResults(0, new String[]{"11", "5"});
        m.getResults()[0][0] = "0";
        assertEquals("11", m.getResults()[0][0]);
    }

    @Test
    public void result_setResultsStoresCopy() {
        Match.Result r = new Match.Result();
        String[] set = {"11", "5"};
        r.setResults(0, set);
        set[1] = "9";
        assertEquals("5", r.getResultsCopy()[0][1]);
    }
}
