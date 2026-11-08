package controller;

import model.TournamentState;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saves and loads tournament states as serialized {@code .ser} files.
 *
 * <p>Writes are atomic: the state is written to a temporary file in the target directory first
 * and then moved over the target, so a crash during autosave never leaves a corrupt file.
 * Loading only accepts the classes a tournament state consists of.</p>
 */
public final class TournamentStorage {

    /** Deserialization filter: only JDK base/collection classes and the model are accepted. */
    static final String LOAD_FILTER = "java.lang.**;java.util.**;model.**;!*";

    private TournamentStorage() {
    }

    /**
     * Returns the directory tournament files are saved to: the user's documents folder as
     * reported by the operating system (this respects a redirected Documents folder, e.g.
     * OneDrive), falling back to the user's home directory.
     *
     * @return an existing directory
     */
    public static File defaultSaveDirectory() {
        try {
            File documents = FileSystemView.getFileSystemView().getDefaultDirectory();
            if (documents != null && documents.isDirectory()) {
                return documents;
            }
        } catch (RuntimeException _) {
            // fall through to the home directory
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * Turns a tournament name into a safe file name part. Letters of any language (including
     * umlauts), digits, dot, dash and underscore are kept; everything else becomes "_".
     *
     * @param name the tournament name
     * @return a file-system-safe name, never empty
     */
    public static String sanitizeFileName(String name) {
        String sanitized = name == null ? "" : name.strip().replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return sanitized.isEmpty() ? "Turnier" : sanitized;
    }

    /**
     * Returns the file holding the current state of a tournament (autosave after every change).
     *
     * @param directory      the target directory
     * @param tournamentName the tournament name
     * @return the file, e.g. {@code Vereinsmeisterschaft.ser}
     */
    public static File currentFile(File directory, String tournamentName) {
        return new File(directory, sanitizeFileName(tournamentName) + ".ser");
    }

    /**
     * Returns {@code true} if both files denote the same file on disk.
     *
     * @param a a file
     * @param b another file
     * @return {@code true} for the same file
     */
    public static boolean isSameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /**
     * Returns the backup file for the state after a completed round.
     *
     * @param directory      the target directory
     * @param tournamentName the tournament name
     * @param round          the round number
     * @return the file, e.g. {@code Vereinsmeisterschaft_Runde_3.ser}
     */
    public static File saveFile(File directory, String tournamentName, int round) {
        return new File(directory, sanitizeFileName(tournamentName) + "_Runde_" + round + ".ser");
    }

    /**
     * Writes the state atomically to {@code target}, creating missing parent directories.
     *
     * @param state  the state to write
     * @param target the target file
     * @throws IOException if the file cannot be written
     */
    public static void save(TournamentState state, File target) throws IOException {
        Path targetPath = target.toPath().toAbsolutePath();
        Path dir = targetPath.getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, targetPath.getFileName().toString(), ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmp);
                 ObjectOutputStream out = new ObjectOutputStream(os)) {
                out.writeObject(state);
            }
            try {
                Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Reads a tournament state, accepting only the classes allowed by {@link #LOAD_FILTER}.
     *
     * @param source the file to read
     * @return the state
     * @throws IOException            if the file cannot be read or contains disallowed classes
     * @throws ClassNotFoundException if a class in the file is unknown
     */
    public static TournamentState load(File source) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(source))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(LOAD_FILTER));
            Object obj = in.readObject();
            if (!(obj instanceof TournamentState state)) {
                throw new InvalidObjectException("Keine Turnierdatei.");
            }
            return state;
        }
    }
}
