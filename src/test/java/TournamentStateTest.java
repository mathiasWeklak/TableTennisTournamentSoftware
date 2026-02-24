import model.Match;
import model.Player;
import model.TournamentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TournamentStateTest {

    private Player p1 = new Player("Alice", "A", "ClubA", 1500);
    private Player p2 = new Player("Bob", "B", "ClubB", 1600);

    @Test
    public void testConstruction_allFieldsStoredCorrectly() {
        List<Player> players = List.of(p1, p2);
        List<Match> allMatches = List.of(new Match(p1, p2, 1));
        List<Match> currentMatches = List.of(new Match(p1, p2, 1));

        TournamentState state = new TournamentState(
                players, allMatches, currentMatches, 3, false, "TestTurnier", 4, true
        );

        assertSame(players, state.playerList());
        assertSame(allMatches, state.allMatches());
        assertSame(currentMatches, state.matches());
        assertEquals(3, state.currentRound());
        assertFalse(state.finished());
        assertEquals("TestTurnier", state.tournamentName());
        assertEquals(4, state.tableCount());
        assertTrue(state.modus());
    }

    @Test
    public void testFinished_trueStoredCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 1, true, "T", 1, false
        );
        assertTrue(state.finished());
    }

    @Test
    public void testModus_falseStoredCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 1, false, "T", 1, false
        );
        assertFalse(state.modus());
    }

    @Test
    public void testCurrentRound_storedCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 7, false, "T", 2, false
        );
        assertEquals(7, state.currentRound());
    }

    @Test
    public void testTableCount_storedCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 1, false, "T", 10, false
        );
        assertEquals(10, state.tableCount());
    }

    @Test
    public void testTournamentName_storedCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 1, false, "Mein Turnier", 1, false
        );
        assertEquals("Mein Turnier", state.tournamentName());
    }

    @Test
    public void testEmptyLists_storedCorrectly() {
        TournamentState state = new TournamentState(
                List.of(), List.of(), List.of(), 1, false, "T", 1, false
        );
        assertTrue(state.playerList().isEmpty());
        assertTrue(state.allMatches().isEmpty());
        assertTrue(state.matches().isEmpty());
    }

    @Test
    public void testEquality_twoIdenticalStates_areEqual() {
        List<Player> players = List.of(p1);
        List<Match> matches = List.of();

        TournamentState state1 = new TournamentState(players, matches, matches, 2, false, "T", 3, true);
        TournamentState state2 = new TournamentState(players, matches, matches, 2, false, "T", 3, true);

        assertEquals(state1, state2);
    }

    @Test
    public void testEquality_differentRound_notEqual() {
        TournamentState state1 = new TournamentState(List.of(), List.of(), List.of(), 1, false, "T", 1, false);
        TournamentState state2 = new TournamentState(List.of(), List.of(), List.of(), 2, false, "T", 1, false);
        assertNotEquals(state1, state2);
    }
}
