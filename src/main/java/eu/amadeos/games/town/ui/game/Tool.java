package eu.amadeos.games.town.ui.game;

import eu.amadeos.games.town.domain.BuildingType;
import eu.amadeos.games.town.domain.Rules;
import eu.amadeos.games.town.domain.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * The tools of the panel, in the order of their buttons and of the keys 1 to 9. Power, water and the
 * services have several things to build; pressing the key again, or a click on the list, picks the next.
 */
enum Tool {
    ROAD, HOMES, SHOPS, INDUSTRY, POWER, WATER, SERVICES, PARK, BULLDOZE;

    /** The power line among the power tool's choices; the rest are buildings. */
    static final String LINE = "line";

    /** The zone a tool lays out, or {@code null}. */
    Zone zone() {
        return switch (this) {
            case HOMES -> Zone.RESIDENTIAL;
            case SHOPS -> Zone.COMMERCIAL;
            case INDUSTRY -> Zone.INDUSTRIAL;
            default -> null;
        };
    }

    /** True for the tools that are dragged across the map; the others are clicked onto a tile. */
    boolean drags(String choice) {
        return this == ROAD || this == BULLDOZE || zone() != null || LINE.equals(choice);
    }

    /** What a tool can build: the power line and the buildings of its button. Empty for roads, zones and the bulldozer. */
    List<String> choices(Rules rules) {
        BuildingType.Tool button = switch (this) {
            case POWER -> BuildingType.Tool.POWER;
            case WATER -> BuildingType.Tool.WATER;
            case SERVICES -> BuildingType.Tool.SERVICES;
            case PARK -> BuildingType.Tool.PARK;
            default -> null;
        };
        if (button == null) return List.of();
        List<String> choices = new ArrayList<>();
        if (this == POWER) choices.add(LINE);
        for (BuildingType type : rules.buildings(button)) choices.add(type.id());
        return choices;
    }

    String key() {
        return name().toLowerCase();
    }
}
