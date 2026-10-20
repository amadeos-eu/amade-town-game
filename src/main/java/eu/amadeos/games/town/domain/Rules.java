package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.data.Catalog;
import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.data.TextData.DataException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the data files say: the buildings of {@code buildings.txt}, the missions of
 * {@code missions.txt} and the computer's plan in {@code plan.txt}. Read once; a mistake in a file stops the
 * game at the start with the file and the line.
 */
public final class Rules {
    /** Where the data files lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/town/data/";

    private final Map<String, BuildingType> buildings;
    private final Map<Integer, Mission> missions;
    private final Plan plan;

    private Rules(Map<String, BuildingType> buildings, Map<Integer, Mission> missions, Plan plan) {
        this.buildings = buildings;
        this.missions = missions;
        this.plan = plan;
    }

    /** The rules of the game, from its data files. */
    public static Rules load() {
        return read(TextData.load(Rules.class, FOLDER + "buildings.txt"),
                TextData.load(Rules.class, FOLDER + "missions.txt"), TextData.load(Rules.class, FOLDER + "plan.txt"));
    }

    public static Rules read(List<TextData.Line> buildingLines, List<TextData.Line> missionLines,
                             List<TextData.Line> planLines) {
        Map<String, BuildingType> buildings = buildings(buildingLines);
        Map<Integer, Mission> missions = missions(missionLines, buildings);
        return new Rules(buildings, missions, plan(planLines, buildings, missions));
    }

    // --8<-- [start:buildings]
    private static Map<String, BuildingType> buildings(List<TextData.Line> lines) {
        Map<String, BuildingType> buildings = new LinkedHashMap<>();
        for (Catalog.Entry building : Catalog.read(lines, "building")) {
            TextData.Line size = building.line("size");
            if (size.size() != 3) throw size.error("size takes the width and the height in tiles");
            int width = size.integer(1);
            int height = size.integer(2);
            if (width < 1 || height < 1 || width > 4 || height > 4) throw size.error("a building is 1 to 4 tiles a side");
            Work work = choice(building, "work", Work.class);
            int cost = building.integer("cost");
            if (cost < 1) throw building.line("cost").error("a building costs something");
            int power = building.integer("power", 0);
            if ((work == Work.PLANT || work == Work.WIND) && power < 1) {
                throw building.error("a plant makes power: power 1 or more");
            }
            int water = building.integer("water", 0);
            if (work == Work.PUMP && water < 1) throw building.error("a pump gives water: water 1 or more");
            int reach = building.integer("reach", 0);
            boolean serves = work != Work.PLANT && work != Work.WIND && work != Work.PUMP;
            if (serves && reach < 1) throw building.error("a building that serves the town needs a reach");
            BuildingType type = new BuildingType(building.name(), choice(building, "tool", BuildingType.Tool.class),
                    work, width, height, cost, building.number("upkeep", 0), power, water, reach,
                    building.integer("smoke", 0), yes(building, "shore"), yes(building, "one"));
            building.finish();
            buildings.put(type.id(), type);
        }
        if (buildings.isEmpty()) throw new DataException("buildings.txt: no building");
        return buildings;
    }
    // --8<-- [end:buildings]

    private static Map<Integer, Mission> missions(List<TextData.Line> lines, Map<String, BuildingType> buildings) {
        Map<Integer, Mission> missions = new LinkedHashMap<>();
        for (Catalog.Entry mission : Catalog.read(lines, "mission")) {
            int number;
            try {
                number = Integer.parseInt(mission.name());
            } catch (NumberFormatException e) {
                throw mission.error("a mission is a number");
            }
            if (number != missions.size() + 1) throw mission.error("the missions are numbered 1, 2, 3 and so on");
            int goal = mission.integer("goal");
            if (goal < 1) throw mission.line("goal").error("the goal is 1 person or more");
            int happiness = mission.integer("happiness", 0);
            if (happiness < 0 || happiness > 100) throw mission.line("happiness").error("happiness is 0 to 100 percent");
            Set<String> forbidden = new LinkedHashSet<>();
            if (mission.has("forbid")) {
                TextData.Line forbid = mission.line("forbid");
                for (int i = 1; i < forbid.size(); i++) {
                    if (!buildings.containsKey(forbid.word(i))) throw forbid.error('"' + forbid.word(i) + "\" is not a building");
                    forbidden.add(forbid.word(i));
                }
            }
            missions.put(number, new Mission(number, mission.text("map"), mission.integer("money"), goal, happiness,
                    mission.integer("years", 0), yes(mission, "fire"), forbidden));
            mission.finish();
        }
        if (missions.isEmpty()) throw new DataException("missions.txt: no mission");
        return missions;
    }

    private static Plan plan(List<TextData.Line> lines, Map<String, BuildingType> buildings,
                             Map<Integer, Mission> missions) {
        List<Catalog.Entry> entries = Catalog.read(lines, "plan");
        if (entries.size() != 1) throw new DataException("plan.txt: one plan, please");
        Catalog.Entry plan = entries.getFirst();
        int mission = plan.integer("mission");
        if (!missions.containsKey(mission)) throw plan.line("mission").error("no mission " + mission);
        double every = plan.number("every");
        if (every <= 0) throw plan.line("every").error("every is more than 0 seconds");
        List<Plan.Step> steps = new ArrayList<>();
        for (TextData.Line line : plan.lines("do")) {
            String action = line.size() > 1 ? line.word(1) : "";
            switch (action) {
                case "road", "line", "bulldoze" -> {
                    if (line.size() != 6) throw line.error(action + " takes two tiles: col row col row");
                    steps.add(new Plan.Step(action, "", line.integer(2), line.integer(3), line.integer(4), line.integer(5)));
                }
                case "zone" -> {
                    if (line.size() != 7 || line.word(2).length() != 1 || Zone.ofLetter(line.word(2).charAt(0)) == null) {
                        throw line.error("zone takes r, c or i and two corners: col row col row");
                    }
                    steps.add(new Plan.Step(action, line.word(2), line.integer(3), line.integer(4), line.integer(5),
                            line.integer(6)));
                }
                case "build" -> {
                    if (line.size() != 5 || !buildings.containsKey(line.word(2))) {
                        throw line.error("build takes a building and its top left tile");
                    }
                    steps.add(new Plan.Step(action, line.word(2), line.integer(3), line.integer(4), line.integer(3),
                            line.integer(4)));
                }
                case "wait" -> steps.add(new Plan.Step(action, "", 0, 0, 0, 0));
                default -> throw line.error("do road, line, zone, build, bulldoze or wait");
            }
        }
        Plan result = new Plan(mission, every, steps);
        plan.finish();
        return result;
    }

    private static <E extends Enum<E>> E choice(Catalog.Entry entry, String key, Class<E> type) {
        String word = entry.text(key);
        try {
            return Enum.valueOf(type, word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            List<String> all = new ArrayList<>();
            for (E value : type.getEnumConstants()) all.add(value.name().toLowerCase(Locale.ROOT));
            throw entry.line(key).error('"' + word + "\" is not one of " + String.join(", ", all));
        }
    }

    private static boolean yes(Catalog.Entry entry, String key) {
        String word = entry.text(key, "no");
        return switch (word) {
            case "yes" -> true;
            case "no" -> false;
            default -> throw entry.line(key).error(key + " is yes or no");
        };
    }

    public BuildingType building(String id) {
        BuildingType building = buildings.get(id);
        if (building == null) throw new IllegalArgumentException("no building " + id);
        return building;
    }

    public boolean isBuilding(String id) {
        return buildings.containsKey(id);
    }

    /** All buildings, in the order of the file. */
    public List<BuildingType> buildings() {
        return List.copyOf(buildings.values());
    }

    /** The buildings of one button of the panel, in the order of the file. */
    public List<BuildingType> buildings(BuildingType.Tool tool) {
        return buildings.values().stream().filter(type -> type.tool() == tool).toList();
    }

    public Mission mission(int number) {
        Mission mission = missions.get(number);
        if (mission == null) throw new DataException("missions.txt: no mission " + number);
        return mission;
    }

    public int missions() {
        return missions.size();
    }

    public Plan plan() {
        return plan;
    }
}
