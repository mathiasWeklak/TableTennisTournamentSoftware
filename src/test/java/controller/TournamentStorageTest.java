package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import model.TournamentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TournamentStorageTest {

    @TempDir
    Path tempDir;

    private static TournamentState sampleState(String name, int round) {
        Player a = new Player("Anna", "Alpha", "TTC Test", 1500);
        Player b = new Player("Ben", "Beta", "TTC Test", 1400);
        Player c = new Player("Cleo", "Gamma", "TTC Test", 1300);
        Match m = new Match(a, b, 1);
        m.setResults(0, new String[]{"11", "9"});
        m.setResults(1, new String[]{"11", "7"});
        m.setResults(2, new String[]{"11", "5"});
        m.setOverallResult("3:0");
        Match bye = new Match(c, null, -1);
        return new TournamentState(
                new ArrayList<>(List.of(a, b, c)),
                new ArrayList<>(List.of(m, bye)),
                new ArrayList<>(List.of(m, bye)),
                round, false, name, 4, TournamentMode.SWISS);
    }

    @Test
    public void sanitizeFileName_keepsUmlautsAndReplacesSpecialCharacters() {
        assertEquals("Vereinsmeisterschaft_Jöhstadt_2026", TournamentStorage.sanitizeFileName("Vereinsmeisterschaft Jöhstadt 2026"));
        assertEquals("a_b_c_d", TournamentStorage.sanitizeFileName("a/b\\c:d"));
        assertEquals("Turnier.v2-final_x", TournamentStorage.sanitizeFileName("Turnier.v2-final_x"));
    }

    @Test
    public void sanitizeFileName_distinguishesUmlautNames() {
        assertNotEquals(TournamentStorage.sanitizeFileName("Turnier Ä"), TournamentStorage.sanitizeFileName("Turnier Ö"));
    }

    @Test
    public void sanitizeFileName_neverEmpty() {
        assertEquals("Turnier", TournamentStorage.sanitizeFileName(""));
        assertEquals("Turnier", TournamentStorage.sanitizeFileName("   "));
        assertEquals("Turnier", TournamentStorage.sanitizeFileName(null));
    }

    @Test
    public void saveFile_containsNameAndRound() {
        File f = TournamentStorage.saveFile(tempDir.toFile(), "Herbst Cup", 3);
        assertEquals("Herbst_Cup_Runde_3.ser", f.getName());
        assertEquals(tempDir.toFile(), f.getParentFile());
    }

    @Test
    public void defaultSaveDirectory_existsAndIsDirectory() {
        File dir = TournamentStorage.defaultSaveDirectory();
        assertNotNull(dir);
        assertTrue(dir.isDirectory());
    }

    @Test
    public void saveAndLoad_roundtripKeepsStateAndSharedReferences() throws Exception {
        File target = tempDir.resolve("t.ser").toFile();
        TournamentStorage.save(sampleState("Roundtrip", 2), target);

        TournamentState loaded = TournamentStorage.load(target);
        assertEquals("Roundtrip", loaded.tournamentName());
        assertEquals(2, loaded.currentRound());
        assertEquals(3, loaded.playerList().size());
        assertEquals(2, loaded.allMatches().size());
        assertEquals("3:0", loaded.allMatches().getFirst().getOverallResult());
        assertArrayEquals(new String[]{"11", "9"}, loaded.allMatches().getFirst().getResults()[0]);
        // the player objects in matches are the same instances as in the player list
        assertSame(loaded.playerList().getFirst(), loaded.allMatches().getFirst().getFirstPlayer());
        assertSame(loaded.allMatches().getFirst(), loaded.matches().getFirst());
    }

    @Test
    public void save_createsMissingDirectories() throws Exception {
        File target = tempDir.resolve("a").resolve("b").resolve("t.ser").toFile();
        TournamentStorage.save(sampleState("Nested", 1), target);
        assertTrue(target.isFile());
    }

    @Test
    public void save_overwritesExistingFileAndLeavesNoTempFiles() throws Exception {
        File target = tempDir.resolve("t.ser").toFile();
        TournamentStorage.save(sampleState("Erst", 1), target);
        TournamentStorage.save(sampleState("Zweit", 5), target);

        TournamentState loaded = TournamentStorage.load(target);
        assertEquals("Zweit", loaded.tournamentName());
        assertEquals(5, loaded.currentRound());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of("t.ser"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    public void load_rejectsObjectThatIsNoTournamentState() throws Exception {
        File file = tempDir.resolve("string.ser").toFile();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject("kein Turnier");
        }
        assertThrows(InvalidObjectException.class, () -> TournamentStorage.load(file));
    }

    /** A serializable class outside the allowed packages. */
    static class Foreign implements Serializable {
        @Override
        public boolean equals(Object o) {
            return o instanceof Foreign;
        }

        @Override
        public int hashCode() {
            return Objects.hash(1);
        }
    }

    @Test
    public void load_rejectsClassesOutsideTheFilter() throws Exception {
        File file = tempDir.resolve("foreign.ser").toFile();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new Foreign());
        }
        assertThrows(InvalidClassException.class, () -> TournamentStorage.load(file));
    }

    @Test
    public void load_missingFileThrows() {
        assertThrows(IOException.class, () -> TournamentStorage.load(tempDir.resolve("fehlt.ser").toFile()));
    }
}
