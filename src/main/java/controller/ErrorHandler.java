package controller;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Shows unexpected errors to the user instead of only printing them to an invisible console, and
 * appends them to a log file next to the tournament files.
 */
public final class ErrorHandler {

    /** Name of the log file in the save directory. */
    static final String LOG_FILE = "Turniersoftware-Fehler.log";

    private static volatile boolean dialogOpen;

    private ErrorHandler() {
    }

    /**
     * Installs the handler for all threads, including the Event Dispatch Thread.
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> handle(error));
    }

    private static void handle(Throwable error) {
        File log = new File(TournamentStorage.defaultSaveDirectory(), LOG_FILE);
        String logInfo;
        try {
            Files.writeString(log.toPath(), format(error), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logInfo = "Details stehen in:\n" + log.getAbsolutePath();
        } catch (IOException e) {
            logInfo = "Die Details konnten nicht protokolliert werden.";
        }
        error.printStackTrace();
        String message = "Ein unerwarteter Fehler ist aufgetreten:\n" + error
                + "\n\nDer Turnierstand wird nach jeder Eingabe automatisch gespeichert.\n" + logInfo;
        Runnable show = () -> {
            if (dialogOpen) return; // avoid a cascade of dialogs for repeated errors
            dialogOpen = true;
            try {
                JOptionPane.showMessageDialog(null, message, "Fehler", JOptionPane.ERROR_MESSAGE);
            } finally {
                dialogOpen = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) show.run(); else SwingUtilities.invokeLater(show);
    }

    /**
     * Formats an error for the log file.
     *
     * @param error the error
     * @return timestamp and stack trace
     */
    static String format(Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return "=== " + LocalDateTime.now() + " ===" + System.lineSeparator() + sw + System.lineSeparator();
    }
}
