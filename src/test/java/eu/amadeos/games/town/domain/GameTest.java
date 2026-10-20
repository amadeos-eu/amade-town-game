package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.tile.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {
    private static final Rules RULES = Rules.load();
    private static final double STEP = 1.0 / 120;

    /** Plays a mission by the built-in plan, the one the title screen shows, until it ends or the time runs out. */
    private static Game played(int mission, int seconds) {
        Game game = new Game(Level.load(mission, RULES), RULES, 7);
        Planner planner = new Planner(RULES.plan());
        for (int i = 0; i < seconds * 120 && game.phase() == Game.Phase.PLAYING; i++) {
            planner.update(game, STEP);
            game.update(STEP);
        }
        return game;
    }

    // --8<-- [start:test-plan]
    @Test
    void thePlanWinsTheFirstMission() {
        Game game = played(1, 60 * 8);
        assertEquals(Game.Phase.WON, game.phase(), "people " + game.people() + " happy " + game.happiness());
        assertTrue(game.people() >= game.mission().goal());
        assertTrue(game.months() <= 24, "won in month " + game.months());
    }
    // --8<-- [end:test-plan]

    @Test
    void theSameSeedPlaysTheSameTown() {
        assertEquals(Archive.write(played(1, 200)), Archive.write(played(1, 200)));
    }

    @Test
    void aSavedTownPlaysOnLikeTheOriginal() {
        Game game = played(1, 150);
        Game copy = Archive.read(Archive.write(game), RULES, n -> Level.load(n, RULES));
        for (int i = 0; i < 120 * 30; i++) {
            game.update(STEP);
            copy.update(STEP);
        }
        assertEquals(Archive.write(game), Archive.write(copy));
    }

    @Test
    void nothingGrowsWithoutARoadToTheHighway() {
        Game game = new Game(Level.load(1, RULES), RULES, 7);
        int[] land = land(game, 4);
        assertEquals(Game.Result.OK, game.zone(Zone.RESIDENTIAL, land[0], land[1], land[0] + 3, land[1] + 3));
        for (int i = 0; i < 120 * 60; i++) game.update(STEP);
        assertEquals(0, game.people());
        assertTrue(game.events().stream().noneMatch(e -> e instanceof Event.Over));
    }

    @Test
    void buildingsAreRefusedWhereTheyCannotStand() {
        Game game = new Game(Level.load(1, RULES), RULES, 7);
        BuildingType pump = RULES.building("pump");
        BuildingType hall = RULES.building("hall");
        int[] land = land(game, 6);
        assertEquals(Game.Result.SHORE, game.placement(pump, land[0] + 1, land[1] + 1));
        assertEquals(Game.Result.OUTSIDE, game.placement(hall, game.width() - 1, game.height() - 1));
        assertEquals(Game.Result.OK, game.build(hall, land[0], land[1]));
        assertEquals(Game.Result.BLOCKED, game.placement(hall, land[0], land[1]));
        int[] more = land(game, 6);
        assertEquals(Game.Result.ONE, game.placement(hall, more[0], more[1]));
    }

    @Test
    void aRoadCostsItsTiles() {
        Game game = new Game(Level.load(1, RULES), RULES, 7);
        int[] land = land(game, 5);
        List<Cell> cells = Game.path(land[0], land[1], land[0] + 4, land[1]);
        assertEquals(5, cells.size());
        double before = game.money();
        int cost = game.roadCost(cells);
        assertEquals(Game.Result.OK, game.road(cells));
        assertEquals(before - cost, game.money(), 1e-9);
        assertTrue(game.road(land[0] + 2, land[1]));
    }

    /** The top left corner of an empty square of meadow, away from the highway. */
    private static int[] land(Game game, int size) {
        for (int row = 2; row < game.height() - size; row++) {
            for (int col = 2; col < game.width() - size; col++) {
                if (free(game, col - 1, row - 1, size + 2)) return new int[] {col, row};
            }
        }
        throw new AssertionError("no free land");
    }

    private static boolean free(Game game, int col, int row, int size) {
        for (int r = row; r < row + size; r++) {
            for (int c = col; c < col + size; c++) {
                if (game.ground(c, r) != Ground.GRASS || game.highway(c, r) || game.road(c, r)
                        || game.buildingAt(c, r) != null || game.zone(c, r) != null) return false;
            }
        }
        return true;
    }
}
