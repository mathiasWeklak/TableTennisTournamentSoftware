package controller;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Choosing and writing CSV files (UTF-8 with BOM, so Excel shows umlauts correctly).
 */
final class CsvFiles {

    private CsvFiles() {
    }

    /**
     * Asks for a target file; ".csv" is added if missing, an existing file is only replaced after
     * a question.
     *
     * @param parent      parent component
     * @param title       dialog title
     * @param defaultName proposed file name without extension
     * @return the file, or {@code null} if cancelled
     */
    static File chooseTarget(Component parent, String title, String defaultName) {
        JFileChooser chooser = new JFileChooser(TournamentStorage.defaultSaveDirectory());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV-Dateien (*.csv)", "csv"));
        chooser.setSelectedFile(new File(TournamentStorage.defaultSaveDirectory(), defaultName + ".csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) file = new File(file.getParentFile(), file.getName() + ".csv");
        if (file.exists() && JOptionPane.showConfirmDialog(parent, file.getName() + " gibt es schon. Ersetzen?",
                title, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return file;
    }

    /**
     * Writes CSV text as UTF-8 with BOM; shows an error dialog if that fails.
     *
     * @param parent parent component
     * @param file   the target
     * @param csv    the content
     */
    static void write(Component parent, File file, String csv) {
        try {
            Files.writeString(file.toPath(), "\uFEFF" + csv, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(parent, "Gespeichert:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Fehler beim Speichern nach:\n" + file.getAbsolutePath()
                    + "\n\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
