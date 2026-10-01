package hornassistant.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The application version, filled in from the Maven project version at build time. */
public final class Version {

    private static final String RESOURCE = "/hornassistant/version.properties";

    private Version() {}

    public static String current() {
        try (InputStream in = Version.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            var properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
