import static org.junit.jupiter.api.Assertions.assertEquals;

import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PlayerTest {
    private Player player;

    @BeforeEach
    public void setUp() {
        player = new Player("John", "Doe", "ClubA", 1500);
    }

    @Test
    public void testConstructorInitialization() {
        assertEquals("John", player.getFirstName());
        assertEquals("Doe", player.getLastName());
        assertEquals("ClubA", player.getClub());
        assertEquals(1500, player.getTtr());
        assertEquals(0, player.getPoints());
        assertEquals(0, player.getBuchholz());
        assertEquals(0, player.getFeinBuchholz());
        assertEquals(0, player.getWins());
        assertEquals(0, player.getLosses());
        assertEquals(0, player.getSetsWon());
        assertEquals(0, player.getSetsLost());
        assertEquals(0, player.getBallsWon());
        assertEquals(0, player.getBallsLost());
    }

    @Test
    public void testGetFullName() {
        assertEquals("John Doe", player.getFullName());
    }

    @Test
    public void testGetAndSetPoints() {
        player.setPoints(10);
        assertEquals(10, player.getPoints());
    }

    @Test
    public void testGetAndSetBuchholz() {
        player.setBuchholz(5);
        assertEquals(5, player.getBuchholz());
    }

    @Test
    public void testGetAndSetFeinBuchholz() {
        player.setFeinBuchholz(3);
        assertEquals(3, player.getFeinBuchholz());
    }

    @Test
    public void testGetAndSetWins() {
        player.setWins(4);
        assertEquals(4, player.getWins());
    }

    @Test
    public void testGetAndSetLosses() {
        player.setLosses(2);
        assertEquals(2, player.getLosses());
    }

    @Test
    public void testGetAndSetSetsWon() {
        player.setSetsWon(7);
        assertEquals(7, player.getSetsWon());
    }

    @Test
    public void testGetAndSetSetsLost() {
        player.setSetsLost(3);
        assertEquals(3, player.getSetsLost());
    }

    @Test
    public void testGetAndSetBallsWon() {
        player.setBallsWon(100);
        assertEquals(100, player.getBallsWon());
    }

    @Test
    public void testGetAndSetBallsLost() {
        player.setBallsLost(80);
        assertEquals(80, player.getBallsLost());
    }
}
