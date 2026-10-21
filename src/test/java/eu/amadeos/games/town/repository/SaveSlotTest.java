package eu.amadeos.games.town.repository;

import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.town.domain.Archive;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSlotTest {
    private static final Rules RULES = Rules.load();

    @TempDir
    Path dir;

    @Test
    void aSavedTownLoadsBack() {
        SaveSlot slot = new SaveSlot(new SaveFile(dir.resolve("town.save")));
        assertFalse(slot.exists());
        assertTrue(slot.load(RULES).isEmpty());
        Game game = new Game(Level.load(2, RULES), RULES, 7);
        for (int i = 0; i < 120 * 60; i++) game.update(1.0 / 120);
        slot.save(game);
        assertTrue(slot.exists());
        Game loaded = slot.load(RULES).orElseThrow();
        assertEquals(2, loaded.level().number());
        assertEquals(game.time(), loaded.time(), 1e-9);
        assertEquals(game.buildings().size(), loaded.buildings().size());
        assertEquals(Archive.write(game), Archive.write(loaded));
    }

    @Test
    void aBrokenOrOlderFileLoadsNothing() throws Exception {
        SaveFile file = new SaveFile(dir.resolve("town.save"));
        SaveSlot slot = new SaveSlot(file);
        file.store(Archive.VERSION + 1, Map.of("mission", "1"));
        assertTrue(slot.load(RULES).isEmpty(), "another version");
        file.store(Archive.VERSION, Map.of("mission", "1", "buildings", "nonsense"));
        assertTrue(slot.load(RULES).isEmpty(), "values that make no town");
        Files.writeString(dir.resolve("town.save"), "not a save file at all");
        assertTrue(slot.load(RULES).isEmpty(), "a file that is no save");
    }
}
