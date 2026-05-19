import model.Match;
import model.Player;
import model.TournamentMode;
import model.TournamentState;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TournamentStateRoundtripTest {

    @Test
    public void testRoundtripWithLoadFilter() throws Exception {
        Player p1 = new Player("Alice", "A", "ClubA", 1500);
        Player p2 = new Player("Bob", "B", "ClubB", 1600);
        Match m = new Match(p1, p2, 1);
        m.setResults(0, new String[]{"11", "9"});
        m.setOverallResult("1:0");

        TournamentState state = new TournamentState(
                new ArrayList<>(List.of(p1, p2)),
                new ArrayList<>(List.of(m)),
                new ArrayList<>(List.of(m)),
                2,
                false,
                "TestTurnier",
                4,
                TournamentMode.SWISS
        );

        File tmp = File.createTempFile("state", ".ser");
        tmp.deleteOnExit();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmp))) {
            out.writeObject(state);
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(tmp))) {
            in.setObjectInputFilter(java.io.ObjectInputFilter.Config.createFilter(
                    "java.lang.Object;java.lang.String;java.lang.Number;"
                            + "java.lang.Integer;java.lang.Boolean;java.lang.Enum;"
                            + "java.util.ArrayList;model.**;!*"));
            TournamentState restored = (TournamentState) in.readObject();
            assertEquals("TestTurnier", restored.tournamentName());
            assertEquals(2, restored.playerList().size());
            assertEquals(1, restored.matches().size());
            assertEquals("1:0", restored.matches().get(0).getOverallResult());
            assertArrayEquals(new String[]{"11", "9"}, restored.matches().get(0).getResults()[0]);
        }
    }
}
