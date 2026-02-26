import controller.ScoreCalculator;
import model.Match;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreCalculatorTest {

    private Player p1;
    private Player p2;
    private Player p3;

    @BeforeEach
    public void setUp() {
        p1 = new Player("Alice", "A", "ClubA", 1500);
        p2 = new Player("Bob", "B", "ClubB", 1600);
        p3 = new Player("Carol", "C", "ClubC", 1400);
    }

    private Match matchWithResult(Player first, Player second, String overallResult, String[][] setBalls) {
        Match match = new Match(first, second, 1);
        match.setOverallResult(overallResult);
        if (setBalls != null) {
            for (int i = 0; i < setBalls.length; i++) {
                if (setBalls[i] != null) {
                    match.setResults(i, setBalls[i]);
                }
            }
        }
        return match;
    }

    @Test
    public void testCalculate_resetsAllPlayerStats() {
        p1.setPoints(5);
        p1.setWins(3);
        p1.setLosses(2);
        p1.setSetsWon(9);
        p1.setSetsLost(6);
        p1.setBallsWon(100);
        p1.setBallsLost(80);
        p1.setBuchholz(10);
        p1.setFeinBuchholz(7);

        List<Match> matches = new ArrayList<>();
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1));

        assertEquals(0, p1.getPoints());
        assertEquals(0, p1.getWins());
        assertEquals(0, p1.getLosses());
        assertEquals(0, p1.getSetsWon());
        assertEquals(0, p1.getSetsLost());
        assertEquals(0, p1.getBallsWon());
        assertEquals(0, p1.getBallsLost());
        assertEquals(0, p1.getBuchholz());
        assertEquals(0, p1.getFeinBuchholz());
    }

    @Test
    public void testCalculate_byeMatch_givesWinPointSetsAndBalls() {
        Match bye = new Match(p1, null, 1);
        List<Match> matches = new ArrayList<>(List.of(bye));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1));

        assertEquals(1, p1.getPoints());
        assertEquals(1, p1.getWins());
        assertEquals(3, p1.getSetsWon());
        assertEquals(33, p1.getBallsWon());
    }

    @Test
    public void testCalculate_player1Wins_updatesWinAndLoss() {
        Match match = matchWithResult(p1, p2, "3:1", new String[][]{
                {"11", "9"}, {"11", "7"}, {"9", "11"}, {"11", "8"}, null
        });
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(1, p1.getPoints());
        assertEquals(1, p1.getWins());
        assertEquals(0, p1.getLosses());
        assertEquals(0, p2.getPoints());
        assertEquals(0, p2.getWins());
        assertEquals(1, p2.getLosses());
    }

    @Test
    public void testCalculate_player2Wins_updatesWinAndLoss() {
        Match match = matchWithResult(p1, p2, "1:3", new String[][]{
                {"9", "11"}, {"7", "11"}, {"11", "9"}, {"8", "11"}, null
        });
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getPoints());
        assertEquals(1, p1.getLosses());
        assertEquals(1, p2.getPoints());
        assertEquals(1, p2.getWins());
        assertEquals(0, p2.getLosses());
    }

    @Test
    public void testCalculate_draw_noWinOrLossAdded() {
        Match match = matchWithResult(p1, p2, "2:2", null);
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getPoints());
        assertEquals(0, p1.getWins());
        assertEquals(0, p1.getLosses());
        assertEquals(0, p2.getPoints());
        assertEquals(0, p2.getWins());
        assertEquals(0, p2.getLosses());
    }

    @Test
    public void testCalculate_setsAccumulatedCorrectly() {
        Match match = matchWithResult(p1, p2, "3:1", null);
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(3, p1.getSetsWon());
        assertEquals(1, p1.getSetsLost());
        assertEquals(1, p2.getSetsWon());
        assertEquals(3, p2.getSetsLost());
    }

    @Test
    public void testCalculate_ballsAccumulatedCorrectly() {
        Match match = matchWithResult(p1, p2, "3:0", new String[][]{
                {"11", "9"}, {"11", "7"}, {"11", "5"}, null, null
        });
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(33, p1.getBallsWon());
        assertEquals(21, p1.getBallsLost());
        assertEquals(21, p2.getBallsWon());
        assertEquals(33, p2.getBallsLost());
    }

    @Test
    public void testCalculate_emptySetEntriesAreSkipped() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("3:0");
        match.setResults(0, new String[]{"11", "9"});
        match.setResults(1, new String[]{"", ""});
        match.setResults(2, new String[]{"11", "5"});

        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(22, p1.getBallsWon());
        assertEquals(14, p1.getBallsLost());
    }

    @Test
    public void testCalculate_duplicateByeMatchesOnlyCountedOnce() {
        Match bye1 = new Match(p1, null, 1);
        Match bye2 = new Match(p1, null, 2);
        List<Match> matches = new ArrayList<>(List.of(bye1, bye2));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1));

        assertEquals(1, p1.getPoints());
        assertEquals(1, p1.getWins());
    }

    @Test
    public void testCalculate_alreadyEvaluatedMatchIsSkipped() {
        Match match = matchWithResult(p1, p2, "3:0", null);
        List<Match> matches = new ArrayList<>();
        matches.add(match);
        matches.add(match);

        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(1, p1.getPoints());
        assertEquals(1, p1.getWins());
    }

    @Test
    public void testCalculate_buchholzIsSumOfOpponentsPoints() {
        Match m1 = matchWithResult(p1, p2, "3:0", null);
        Match m2 = matchWithResult(p1, p3, "3:0", null);
        Match m3 = matchWithResult(p2, p3, "3:0", null);

        List<Match> matches = new ArrayList<>(List.of(m1, m2, m3));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2, p3));

        assertEquals(p2.getPoints() + p3.getPoints(), p1.getBuchholz());
        assertEquals(p1.getPoints() + p3.getPoints(), p2.getBuchholz());
        assertEquals(p1.getPoints() + p2.getPoints(), p3.getBuchholz());
    }

    @Test
    public void testCalculate_feinBuchholzIsSumOfOpponentsBuchholz() {
        Match m1 = matchWithResult(p1, p2, "3:0", null);
        Match m2 = matchWithResult(p1, p3, "3:0", null);
        Match m3 = matchWithResult(p2, p3, "3:0", null);

        List<Match> matches = new ArrayList<>(List.of(m1, m2, m3));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2, p3));

        assertEquals(p2.getBuchholz() + p3.getBuchholz(), p1.getFeinBuchholz());
    }

    @Test
    public void testCalculate_byeMatchNotCountedInBuchholz() {
        Match bye = new Match(p1, null, 1);
        Match m1 = matchWithResult(p1, p2, "3:0", null);

        List<Match> matches = new ArrayList<>(List.of(bye, m1));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(p2.getPoints(), p1.getBuchholz());
    }

    @Test
    public void testCalculate_invalidOverallResultFormat_doesNotUpdateSets() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("invalid");
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getSetsWon());
        assertEquals(0, p2.getSetsWon());
        assertEquals(0, p1.getPoints());
        assertEquals(0, p2.getPoints());
    }

    @Test
    public void testCalculate_multipleMatchesAccumulateStats() {
        Match m1 = matchWithResult(p1, p2, "3:0", null);
        Match m2 = matchWithResult(p1, p3, "3:2", null);

        List<Match> matches = new ArrayList<>(List.of(m1, m2));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2, p3));

        assertEquals(2, p1.getPoints());
        assertEquals(2, p1.getWins());
        assertEquals(6, p1.getSetsWon());
        assertEquals(2, p1.getSetsLost());
    }

    @Test
    public void testCalculate_setWithNullSecondEntry_isSkipped() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("1:0");
        match.setResults(0, new String[]{"11", null});

        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getBallsWon());
        assertEquals(0, p2.getBallsWon());
    }

    @Test
    public void testCalculate_setWithEmptySecondEntry_isSkipped() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("1:0");
        match.setResults(0, new String[]{"11", ""});

        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getBallsWon());
        assertEquals(0, p2.getBallsWon());
    }

    @Test
    public void testCalculate_nonNumericOverallResultParts_doesNotUpdateStats() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("abc:def");
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getPoints());
        assertEquals(0, p2.getPoints());
        assertEquals(0, p1.getSetsWon());
        assertEquals(0, p2.getSetsWon());
    }

    @Test
    public void testCalculate_nonNumericSetScore_isSkipped() {
        Match match = new Match(p1, p2, 1);
        match.setOverallResult("3:0");
        match.setResults(0, new String[]{"abc", "def"});
        match.setResults(1, new String[]{"11", "7"});
        match.setResults(2, new String[]{"11", "5"});
        List<Match> matches = new ArrayList<>(List.of(match));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(22, p1.getBallsWon());
        assertEquals(12, p1.getBallsLost());
    }

    @Test
    public void testCalculate_duplicateByeMatchesDoNotMutateOriginalList() {
        Match bye1 = new Match(p1, null, 1);
        Match bye2 = new Match(p1, null, 2);
        List<Match> matches = new ArrayList<>(List.of(bye1, bye2));
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1));

        assertEquals(2, matches.size());
    }

    @Test
    public void testCalculate_emptyMatchList_allStatsRemainZero() {
        List<Match> matches = new ArrayList<>();
        ScoreCalculator calc = new ScoreCalculator(matches);
        calc.calculate(List.of(p1, p2));

        assertEquals(0, p1.getPoints());
        assertEquals(0, p2.getPoints());
        assertEquals(0, p1.getBuchholz());
        assertEquals(0, p2.getFeinBuchholz());
    }
}
