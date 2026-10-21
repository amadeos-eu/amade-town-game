package eu.amadeos.games.town.domain;

import java.util.Set;

/**
 * A mission of {@code missions.txt}: its map, the money at the start, what the town must reach, and what
 * makes it hard.
 *
 * @param money     the money at the start
 * @param goal      the people the town must have to win
 * @param happiness how happy they must be at the same time, in percent; 0 for any
 * @param years     the goal must be reached before this many years are over; 0 for no limit
 * @param fire      whether houses catch fire
 * @param forbidden buildings the town may not build in this mission
 */
public record Mission(int number, String map, int money, int goal, int happiness, int years, boolean fire,
                      Set<String> forbidden) {
    public Mission {
        forbidden = Set.copyOf(forbidden);
    }
}
