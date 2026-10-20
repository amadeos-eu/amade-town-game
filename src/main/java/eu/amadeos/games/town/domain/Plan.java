package eu.amadeos.games.town.domain;

import java.util.List;

/**
 * How the computer builds a town on the title screen and in the tests, from {@code plan.txt}: one mission's
 * map and a list of steps, each what the player could do with the mouse.
 */
public record Plan(int mission, double every, List<Step> steps) {
    /**
     * One thing to do: {@code road}, {@code line} or {@code bulldoze} from tile ({@code col}, {@code row})
     * to ({@code col2}, {@code row2}); {@code zone} the rectangle between them with {@code what} r, c or i;
     * {@code build} the building {@code what} with its top left corner on the first tile.
     */
    public record Step(String action, String what, int col, int row, int col2, int row2) {
    }

    public Plan {
        steps = List.copyOf(steps);
    }
}
