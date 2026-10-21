package eu.amadeos.games.town.repository;

import eu.amadeos.gameengine.save.SaveFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTest {
    @TempDir
    Path dir;

    @Test
    void aWonMissionOpensTheNextAndKeepsTheFastestTime() {
        SaveFile file = new SaveFile(dir.resolve("progress.save"));
        Progress progress = new Progress(file);
        assertEquals(1, progress.open());
        assertEquals(0, progress.best(1));
        assertTrue(progress.won(1, 900, 3));

        Progress later = new Progress(file);             // the next start of the game
        assertEquals(2, later.open());
        assertEquals(900, later.best(1));
        assertFalse(later.won(1, 1000, 3), "slower is no record");
        assertTrue(later.won(1, 700, 3));
        assertEquals(700, new Progress(file).best(1));
        assertEquals(2, new Progress(file).open(), "winning mission 1 again opens nothing more");
    }

    @Test
    void theLastMissionOpensNothing() {
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 600, 3);
        progress.won(2, 600, 3);
        progress.won(3, 600, 3);
        assertEquals(3, progress.open());
    }
}
