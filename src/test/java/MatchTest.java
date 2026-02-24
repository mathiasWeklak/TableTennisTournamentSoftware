import static org.junit.jupiter.api.Assertions.*;

import model.Match;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MatchTest {
    private Player player1;
    private Player player2;
    private Match match;

    @BeforeEach
    public void setUp() {
        player1 = new Player("John", "Doe", "ClubA", 1500);
        player2 = new Player("Jane", "Smith", "ClubB", 1600);
        match = new Match(player1, player2, 1);
    }

    @Test
    public void testConstructorInitialization() {
        assertEquals(player1, match.getFirstPlayer());
        assertEquals(player2, match.getSecondPlayer());
        assertEquals(1, match.getTableNumber());
        assertEquals("", match.getOverallResult());
        assertEquals(5, match.getResults().length);
    }

    @Test
    public void testSetAndGetTableNumber() {
        match.setTable(2);
        assertEquals(2, match.getTableNumber());
    }

    @Test
    public void testSetAndGetOverallResult() {
        match.setOverallResult("3-2");
        assertEquals("3-2", match.getOverallResult());
    }

    @Test
    public void testSetAndGetResults() {
        String[] setResult1 = {"11-9", "9-11"};
        match.setResults(0, setResult1);
        assertEquals(setResult1, match.getResults()[0]);
    }

    @Test
    public void testSetResultsInvalidIndex() {
        String[] setResult = {"11-9", "9-11"};
        assertThrows(IllegalArgumentException.class, () -> match.setResults(-1, setResult));
        assertThrows(IllegalArgumentException.class, () -> match.setResults(5, setResult));
    }

    @Test
    public void testSetResultsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> match.setResults(0, null));
        assertThrows(IllegalArgumentException.class, () -> match.setResults(0, new String[]{"11-9"}));
    }

    @Test
    public void testResultConstructorInitialization() {
        Match.Result result = new Match.Result();
        assertEquals(5, result.getResults().length);
    }

    @Test
    public void testResultSetAndGetResults() {
        Match.Result result = new Match.Result();
        String[] setResult1 = {"11-9", "9-11"};
        result.setResults(0, setResult1);
        assertEquals(setResult1, result.getResults()[0]);
    }

    @Test
    public void testResultSetResultsInvalidIndex() {
        Match.Result result = new Match.Result();
        String[] setResult = {"11-9", "9-11"};
        assertThrows(IllegalArgumentException.class, () -> result.setResults(-1, setResult));
        assertThrows(IllegalArgumentException.class, () -> result.setResults(5, setResult));
    }

    @Test
    public void testResultSetResultsInvalidData() {
        Match.Result result = new Match.Result();
        assertThrows(IllegalArgumentException.class, () -> result.setResults(0, null));
        assertThrows(IllegalArgumentException.class, () -> result.setResults(0, new String[]{"11-9"}));
    }

    @Test
    public void testIsEvaluated_defaultFalse() {
        assertFalse(match.isEvaluated());
    }

    @Test
    public void testSetEvaluated_toTrue() {
        match.setEvaluated(true);
        assertTrue(match.isEvaluated());
    }

    @Test
    public void testSetEvaluated_backToFalse() {
        match.setEvaluated(true);
        match.setEvaluated(false);
        assertFalse(match.isEvaluated());
    }

    @Test
    public void testByeMatch_secondPlayerIsNull() {
        Match bye = new Match(player1, null, 3);
        assertEquals(player1, bye.getFirstPlayer());
        assertNull(bye.getSecondPlayer());
        assertEquals(3, bye.getTableNumber());
    }

    @Test
    public void testEquals_sameOrder() {
        Match other = new Match(player1, player2, 99);
        assertEquals(match, other);
    }

    @Test
    public void testEquals_reverseOrder_isSymmetric() {
        Match reversed = new Match(player2, player1, 1);
        assertEquals(match, reversed);
    }

    @Test
    public void testEquals_differentOpponent_notEqual() {
        Player player3 = new Player("Jim", "Bean", "ClubC", 1700);
        Match other = new Match(player1, player3, 1);
        assertNotEquals(match, other);
    }

    @Test
    public void testEquals_sameReference() {
        assertEquals(match, match);
    }

    @Test
    public void testEquals_null_notEqual() {
        assertNotEquals(null, match);
    }

    @Test
    public void testEquals_differentType_notEqual() {
        assertNotEquals("not a match", match);
    }

    @Test
    public void testHashCode_symmetricMatches_haveSameHashCode() {
        Match reversed = new Match(player2, player1, 99);
        assertEquals(match.hashCode(), reversed.hashCode());
    }

    @Test
    public void testHashCode_differentMatches_haveDifferentHashCode() {
        Player player3 = new Player("Jim", "Bean", "ClubC", 1700);
        Match other = new Match(player1, player3, 1);
        assertNotEquals(match.hashCode(), other.hashCode());
    }
}
