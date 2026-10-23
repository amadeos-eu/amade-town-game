package eu.amadeos.games.town.domain;

/**
 * The numbers of the game that are not in the data files: sizes in logical units, times in seconds, money
 * in dollars. What the buildings cost and do is in {@code buildings.txt}; what each mission gives and asks,
 * in {@code missions.txt}.
 */
public final class Field {
    /** The logical size of the screen: the bar on top, the view of the town, the panel on the right. */
    public static final double WIDTH = 640;
    public static final double HEIGHT = 400;
    public static final double PANEL = 128;
    public static final double BAR = 20;
    public static final double VIEW_WIDTH = WIDTH - PANEL;
    /** How wide the screen may grow on a wide monitor: the map gets the extra width, the panel stays. */
    public static final int MAX_WIDTH = 1024;
    public static final double VIEW_HEIGHT = HEIGHT - BAR;

    /** A tile is 16 world units a side, drawn as a diamond of 32 × 16; room above the map for tall buildings. */
    public static final int TILE = 16;
    public static final int TILE_WIDTH = 32;
    public static final int TILE_HEIGHT = 16;
    public static final int ABOVE = 96;

    /** A month lasts this long at normal speed; the town is looked at in this many slices a month. */
    public static final double MONTH = 4;
    public static final int SLICES = 8;

    /** What a tile of road, bridge and power line costs, clearing a tile, and a tree on it. */
    public static final int ROAD = 10;
    public static final int BRIDGE = 50;
    public static final int LINE = 5;
    public static final int CLEAR = 2;
    public static final int TREE = 3;
    /** What a tile of road and line costs a month. */
    public static final double ROAD_UPKEEP = 0.1;
    public static final double LINE_UPKEEP = 0.02;

    /** A lot grows only with a road this many tiles away at most, across or along. */
    public static final int ROAD_REACH = 2;
    /** The tax rate, in percent, at the start and at most. */
    public static final int TAX = 9;
    public static final int MAX_TAX = 20;
    /** A town this deep in debt is bankrupt, and the mission lost. */
    public static final int DEBT = -5000;

    /** People a home holds, and jobs a shop and a workshop give, by level 1, 2 and 3. */
    public static final int[] PEOPLE = {0, 5, 15, 45};
    public static final int[] SHOP_JOBS = {0, 3, 10, 30};
    public static final int[] WORK_JOBS = {0, 5, 14, 32};
    /** The power a lot uses, by level, in MW; and the water, by level. */
    public static final double[] LOT_POWER = {0, 0.5, 1, 2};
    public static final int[] LOT_WATER = {0, 1, 2, 3};
    /** The land value a lot needs for level 2 and level 3. */
    public static final int VALUE_2 = 30;
    public static final int VALUE_3 = 55;
    /** The land value an office block or a glass tower needs: shops at level 3. */
    public static final int OFFICE_VALUE = 40;
    /** From this many people on a town wants offices, and for each person more this many office jobs. */
    public static final int OFFICE_TOWN = 300;
    public static final double OFFICE_JOBS = 0.25;
    /** Pollution an industrial lot makes, by level, and how far it carries. */
    public static final int[] SMOKE = {0, 10, 18, 26};
    public static final int SMOKE_REACH = 5;

    /** Fire: the chance in a month that a fire breaks out, how long a lot burns, how long rubble lies. */
    public static final double FIRE_CHANCE = 0.12;
    public static final double BURNS = 12;
    public static final double SPREAD = 0.35;
    /** A fire station in reach sends its truck; it puts the fire out this long after the fire began. */
    public static final double DOUSE = 5;
    public static final int RUBBLE_MONTHS = 2;
    /** A lot without power or water this many months in a row falls back a level. */
    public static final int PATIENCE = 2;

    private Field() {
    }
}
