package eu.amadeos.games.town.repository;

import eu.amadeos.gameengine.swing.RenderMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownSettingsTest {
    @TempDir
    Path dir;

    @Test
    void settingsAreStoredAndReadBack() {
        TownSettings settings = new TownSettings(dir.resolve("town.properties"));
        assertEquals(RenderMode.DIRECT, settings.renderMode());
        assertTrue(settings.sound());
        settings.setRenderMode(RenderMode.PIXEL);
        settings.setSound(false);
        TownSettings again = new TownSettings(dir.resolve("town.properties"));
        assertEquals(RenderMode.PIXEL, again.renderMode());
        assertFalse(again.sound());
    }
}
