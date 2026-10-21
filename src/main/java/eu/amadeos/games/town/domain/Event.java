package eu.amadeos.games.town.domain;

/** What happened in a step, for the sounds, the pictures and the messages; the rules have dealt with it. */
// --8<-- [start:event]
public sealed interface Event {
    /** The player built a road, a line or zones, or bulldozed: {@code tiles} tiles for {@code cost}. */
    record Laid(String what, int tiles, int cost) implements Event {
    }

    record Built(Building building) implements Event {
    }

    /** A lot grew to {@code level}, or fell back to it. */
    record Grew(int col, int row, int level) implements Event {
    }

    record Declined(int col, int row, int level) implements Event {
    }

    record Fire(int col, int row) implements Event {
    }

    /** A fire station's truck is on its way from {@code station} to the fire. */
    record Truck(Building station, int col, int row) implements Event {
    }

    record Doused(int col, int row) implements Event {
    }

    record Burned(int col, int row) implements Event {
    }

    /** A new month began; 0 is January. */
    record Month(int month, int year) implements Event {
    }

    /** The town reached a round number of people. */
    record Milestone(int people) implements Event {
    }

    /** Something the player should know, said at most once in a while. */
    record Advice(Hint hint) implements Event {
    }

    /** The mission is over: won, or the years or the money ran out. */
    record Over(boolean won) implements Event {
    }

    /** What the town's advisors say. */
    enum Hint {
        NO_ROAD, NO_HIGHWAY, NO_POWER, POWER_SHORT, UNPOWERED, NO_WATER, WATER_SHORT, NO_JOBS, NO_HOMES, NO_SHOPS,
        POLLUTION, SCHOOL, MONEY, DEBT
    }
}
// --8<-- [end:event]
