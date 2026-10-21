package eu.amadeos.games.town.ui.game;

import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Planner;
import eu.amadeos.games.town.domain.Rules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkersTest {
    private static final Rules RULES = Rules.load();
    private static final double STEP = 1.0 / 120;

    // --8<-- [start:test-walkers]
    @Test
    void peopleWalkOnlyOnThePavementOfTheStreets() {
        Game game = new Game(Level.load(1, RULES), RULES, 7);
        Planner planner = new Planner(RULES.plan());
        for (int i = 0; i < 120 * 150; i++) {
            planner.update(game, STEP);
            game.update(STEP);
        }
        Traffic traffic = new Traffic();
        for (int i = 0; i < 120 * 20; i++) {
            traffic.update(STEP, game);
            for (Walkers.Walker w : traffic.walkers()) {
                int col = (int) Math.floor(w.x() / Field.TILE);
                int row = (int) Math.floor(w.y() / Field.TILE);
                assertTrue(game.road(col, row) && !game.highway(col, row), "a walker off the street at " + col + ", " + row);
                double x = w.x() - col * Field.TILE;
                double y = w.y() - row * Field.TILE;
                boolean edge = x < 2 || x > 14 || y < 2 || y > 14;
                assertTrue(edge, "a walker in the middle of the road");
            }
        }
        assertFalse(traffic.walkers().isEmpty(), "a town of " + game.people() + " has people on its streets");
    }
    // --8<-- [end:test-walkers]
}
