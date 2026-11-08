package view;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Name and version of the application (the version comes from the build).
 */
public final class AppInfo {

    /** Name of the application. */
    public static final String NAME = "Tischtennis Turniersoftware";

    private AppInfo() {
    }

    /**
     * Returns the version, e.g. "1.3.0"; "Entwicklungsversion" if it is not known.
     *
     * @return the version
     */
    public static String version() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version", "");
                if (!v.isBlank() && !v.contains("$")) return v;
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "Entwicklungsversion";
    }
}
