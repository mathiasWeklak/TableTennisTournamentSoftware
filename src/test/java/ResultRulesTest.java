import model.ResultRules;
import model.ResultRules.SetStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResultRulesTest {

    private static String[][] sets(String... values) {
        String[][] sets = new String[5][2];
        for (int i = 0; i < 5; i++) {
            sets[i][0] = 2 * i < values.length ? values[2 * i] : "";
            sets[i][1] = 2 * i + 1 < values.length ? values[2 * i + 1] : "";
        }
        return sets;
    }

    @ParameterizedTest
    @CsvSource({
            "11,0", "11,9", "9,11", "12,10", "10,12", "15,13", "25,23", "0,11"
    })
    public void checkSet_validScores(String a, String b) {
        assertEquals(SetStatus.VALID, ResultRules.checkSet(a, b));
    }

    @ParameterizedTest
    @CsvSource({
            "11,10", "10,10", "5,3", "12,9", "13,10", "11,11", "14,10", "10,11", "0,0", "abc,11", "-1,11"
    })
    public void checkSet_invalidScores(String a, String b) {
        assertEquals(SetStatus.INVALID, ResultRules.checkSet(a, b));
    }

    @Test
    public void checkSet_emptyAndIncomplete() {
        assertEquals(SetStatus.EMPTY, ResultRules.checkSet("", ""));
        assertEquals(SetStatus.EMPTY, ResultRules.checkSet(null, null));
        assertEquals(SetStatus.EMPTY, ResultRules.checkSet(" ", ""));
        assertEquals(SetStatus.INCOMPLETE, ResultRules.checkSet("11", ""));
        assertEquals(SetStatus.INCOMPLETE, ResultRules.checkSet(null, "7"));
    }

    @Test
    public void countSets_onlyValidSetsAreCounted() {
        int[] won = ResultRules.countSets(sets("11", "5", "11", "10", "8", "11"));
        assertArrayEquals(new int[]{1, 1}, won);
    }

    @Test
    public void countSets_nullSafe() {
        assertArrayEquals(new int[]{0, 0}, ResultRules.countSets(null));
        assertArrayEquals(new int[]{0, 0}, ResultRules.countSets(new String[5][]));
    }

    @Test
    public void overallResult_emptyWhenNothingEntered() {
        assertEquals("", ResultRules.overallResult(sets()));
        assertEquals("", ResultRules.overallResult(new String[5][2]));
        assertEquals("", ResultRules.overallResult(null));
    }

    @Test
    public void overallResult_countsSets() {
        assertEquals("3:1", ResultRules.overallResult(sets("11", "5", "9", "11", "11", "7", "12", "10")));
        assertEquals("0:1", ResultRules.overallResult(sets("3", "11")));
    }

    @Test
    public void overallResult_incompleteEntryIsNotEmpty() {
        // typing and deleting must not produce an empty result, but also not a decided one
        assertEquals("0:0", ResultRules.overallResult(sets("11", "")));
        assertFalse(ResultRules.isDecidedOverallResult("0:0"));
    }

    @Test
    public void isComplete_threeSetsWon() {
        assertTrue(ResultRules.isComplete(sets("11", "5", "11", "7", "11", "9")));
        assertTrue(ResultRules.isComplete(sets("5", "11", "11", "7", "9", "11", "11", "3", "10", "12")));
    }

    @Test
    public void isComplete_falseForUndecidedOrEmpty() {
        assertFalse(ResultRules.isComplete(sets()));
        assertFalse(ResultRules.isComplete(sets("11", "5")));
        assertFalse(ResultRules.isComplete(sets("11", "5", "5", "11", "11", "5", "5", "11")));
        assertFalse(ResultRules.isComplete(new String[5][2]));
    }

    @Test
    public void isComplete_falseWithInvalidSet() {
        assertFalse(ResultRules.isComplete(sets("11", "5", "11", "10", "11", "9", "11", "4")));
    }

    @Test
    public void validateSets_reportsGap() {
        String[][] s = sets("11", "5", "", "", "11", "7", "11", "9");
        List<String> problems = ResultRules.validateSets(s);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("Satz 3"));
        assertFalse(ResultRules.isComplete(s));
    }

    @Test
    public void validateSets_reportsSetAfterDecision() {
        String[][] s = sets("11", "5", "11", "7", "11", "9", "11", "4");
        List<String> problems = ResultRules.validateSets(s);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("bereits entschieden"));
        assertFalse(ResultRules.isComplete(s));
    }

    @Test
    public void validateSets_reportsIncompleteAndInvalid() {
        List<String> problems = ResultRules.validateSets(sets("11", "", "11", "10"));
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).contains("beide Werte"));
        assertTrue(problems.get(1).contains("11:10"));
    }

    @Test
    public void validateSets_noProblemsForPartialButConsistentEntry() {
        assertTrue(ResultRules.validateSets(sets("11", "5")).isEmpty());
        assertTrue(ResultRules.validateSets(null).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"3:0,true", "3:2,true", "1:3,true", "2:2,false", "1:0,false", "0:0,false", "3:3,false",
            "4:1,false", "':',false", "'',false", "abc,false", "3-1,false"})
    public void isDecidedOverallResult(String result, boolean expected) {
        assertEquals(expected, ResultRules.isDecidedOverallResult(result));
    }

    @Test
    public void parseOverallResult() {
        assertArrayEquals(new int[]{3, 1}, ResultRules.parseOverallResult("3:1"));
        assertNull(ResultRules.parseOverallResult(null));
        assertNull(ResultRules.parseOverallResult(":"));
        assertNull(ResultRules.parseOverallResult("x:1"));
    }
}
