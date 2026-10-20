package eu.amadeos.games.town.domain;

/** What a tile of the map is, before anything is built on it. */
public enum Ground {
    GRASS('.'), FOREST('T'), WATER('~');

    private final char letter;

    Ground(char letter) {
        this.letter = letter;
    }

    /** The letter in the map files. */
    public char letter() {
        return letter;
    }

    public static Ground ofLetter(char letter) {
        for (Ground ground : values()) if (ground.letter == letter) return ground;
        return null;
    }
}
