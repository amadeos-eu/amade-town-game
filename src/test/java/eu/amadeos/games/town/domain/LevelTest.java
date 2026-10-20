package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.data.TextData.DataException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelTest {
    private static final Rules RULES = Rules.load();

    @Test
    void everyMapHasWaterAndAHighwayFromTheEdge() {
        for (int number = 1; number <= RULES.missions(); number++) {
            Level level = Level.load(number, RULES);
            boolean water = false;
            boolean edge = false;
            for (int row = 0; row < level.height(); row++) {
                for (int col = 0; col < level.width(); col++) {
                    water |= level.ground(col, row) == Ground.WATER;
                    boolean border = col == 0 || row == 0 || col == level.width() - 1 || row == level.height() - 1;
                    edge |= border && level.highway(col, row);
                }
            }
            assertTrue(water, "water on map " + number);
            assertTrue(edge, "a highway into map " + number);
        }
    }

    @Test
    void badMapsAreRefusedWithTheReason() {
        Mission mission = new Mission(9, "test", 1000, 100, 0, 0, false, Set.of());
        String good = "........\n".repeat(7);
        DataException ragged = assertThrows(DataException.class,
                () -> Level.read(mission, "test", new StringReader("=.......\n" + good + ".......\n")));
        assertTrue(ragged.getMessage().contains("row 9"), ragged.getMessage());
        DataException closed = assertThrows(DataException.class,
                () -> Level.read(mission, "test", new StringReader(good + "........\n")));
        assertTrue(closed.getMessage().contains("highway"), closed.getMessage());
        Level level = Level.read(mission, "test", new StringReader("=..T~...\n" + good));
        assertEquals(8, level.width());
        assertEquals(Ground.FOREST, level.ground(3, 0));
        assertTrue(level.highway(0, 0));
    }
}
