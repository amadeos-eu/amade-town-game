package eu.amadeos.games.town.domain;

/** The three kinds of land the player zones: homes, shops and offices, and industry. */
// --8<-- [start:zone]
public enum Zone {
    RESIDENTIAL('r', 10), COMMERCIAL('c', 12), INDUSTRIAL('i', 10);

    private final char letter;
    private final int cost;

    Zone(char letter, int cost) {
        this.letter = letter;
        this.cost = cost;
    }
    // --8<-- [end:zone]

    /** The letter in the plan and the save file. */
    public char letter() {
        return letter;
    }

    /** What zoning a tile costs. */
    public int cost() {
        return cost;
    }

    public static Zone ofLetter(char letter) {
        for (Zone zone : values()) if (zone.letter == letter) return zone;
        return null;
    }

    /** People in a home, or jobs in a shop or a workshop, at a level. */
    public int capacity(int level) {
        return switch (this) {
            case RESIDENTIAL -> Field.PEOPLE[level];
            case COMMERCIAL -> Field.SHOP_JOBS[level];
            case INDUSTRIAL -> Field.WORK_JOBS[level];
        };
    }
}
