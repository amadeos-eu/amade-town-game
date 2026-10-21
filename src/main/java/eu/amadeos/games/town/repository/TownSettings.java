package eu.amadeos.games.town.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.gameengine.swing.RenderMode;

import java.nio.file.Path;
import java.util.Properties;

/** The settings in {@code ~/.config/amadeos/town/town.properties}: the graphics mode and the sound. */
public final class TownSettings {
    private static final String GRAPHICS = "graphics";
    private static final String SOUND = "sound";

    private final Path file;

    @Inject
    public TownSettings(AppDirectories directories) {
        this(directories.configFile("town.properties"));
    }

    public TownSettings(Path file) {
        this.file = file;
    }

    public RenderMode renderMode() {
        return RenderMode.ofId(PropertiesFile.load(file).getProperty(GRAPHICS, "")).orElse(RenderMode.DIRECT);
    }

    public void setRenderMode(RenderMode mode) {
        set(GRAPHICS, mode.id());
    }

    public boolean sound() {
        return !"off".equals(PropertiesFile.load(file).getProperty(SOUND, "on"));
    }

    public void setSound(boolean on) {
        set(SOUND, on ? "on" : "off");
    }

    private void set(String key, String value) {
        Properties properties = PropertiesFile.load(file);
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-town-game settings");
    }
}
