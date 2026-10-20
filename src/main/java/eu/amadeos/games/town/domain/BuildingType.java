package eu.amadeos.games.town.domain;

/**
 * A kind of building, from {@code buildings.txt}: the plants, pumps and towers, the services and the parks.
 * The homes, shops and workshops are not buildings of this kind: they grow by themselves on zoned land.
 *
 * @param tool    the button of the panel it is built from
 * @param width   its size in tiles, along the columns and along the rows (its {@code height})
 * @param cost    what it costs to build
 * @param upkeep  what it costs a month
 * @param power   the power it makes, in MW, if it is a plant; else the power it uses
 * @param water   the water a pump gives
 * @param reach   how far it serves, in tiles: a school's pupils, a tower's water, a park's beauty
 * @param smoke   the pollution it makes on its own tiles, falling off over {@code reach}
 * @param shore   whether it must stand next to water
 * @param one     whether a town has one at most
 */
// --8<-- [start:type]
public record BuildingType(String id, Tool tool, Work work, int width, int height, int cost, double upkeep,
                           int power, int water, int reach, int smoke, boolean shore, boolean one) {
    /** The buttons of the panel that build something other than roads and zones. */
    public enum Tool { POWER, WATER, SERVICES, PARK }

    /** Whether it makes power rather than uses it. */
    public boolean plant() {
        return work == Work.PLANT || work == Work.WIND;
    }
    // --8<-- [end:type]

    /** The power it takes from the grid: none for a plant. */
    public int uses() {
        return plant() ? 0 : power;
    }
}
