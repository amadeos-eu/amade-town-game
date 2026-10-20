package eu.amadeos.games.town.domain;

/** What a building does for the town. */
public enum Work {
    /** Makes power from coal, or from the wind. */
    PLANT, WIND,
    /** Pumps water from the river or the lake next to it. */
    PUMP,
    /** Brings the water to the homes around it. */
    TOWER,
    SCHOOL, LIBRARY, CLINIC, FIRE, HALL, PARK
}
