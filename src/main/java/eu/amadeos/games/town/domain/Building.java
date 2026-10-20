package eu.amadeos.games.town.domain;

/** A building that stands in the town: a plant, a pump, a tower, a service or a park. */
public final class Building {
    private final int id;
    private final BuildingType type;
    private final int col;
    private final int row;
    /** Whether it had power at the last look; a plant always has. */
    boolean powered;

    Building(int id, BuildingType type, int col, int row) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
    }

    public int id() {
        return id;
    }

    public BuildingType type() {
        return type;
    }

    /** The top left tile. */
    public int col() {
        return col;
    }

    public int row() {
        return row;
    }

    public int width() {
        return type.width();
    }

    public int height() {
        return type.height();
    }

    public boolean powered() {
        return powered;
    }

    /** Whether it works: it has power, or needs none. */
    public boolean working() {
        return powered || type.plant() || type.uses() == 0;
    }

    public boolean covers(int c, int r) {
        return c >= col && c < col + type.width() && r >= row && r < row + type.height();
    }

    /** The middle, in tiles. */
    public double centerCol() {
        return col + type.width() / 2.0;
    }

    public double centerRow() {
        return row + type.height() / 2.0;
    }
}
