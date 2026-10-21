package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.gameengine.tile.Coverage;
import eu.amadeos.gameengine.tile.SupplyGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole town: the land, the roads and power lines, the zones and what grew on them, the buildings, the
 * money, and the months going by. The rules of the game without pictures or time of their own: the game
 * loop calls {@link #update(double)} with the seconds of a step, the player's mouse calls the actions,
 * and what happened comes out as {@link #events()}.
 *
 * <p>The player does not build homes, shops or workshops. The player zones land, and the town decides what
 * grows there: a lot grows when its zone is in demand, it has a road that leads to the highway, power, and
 * from the second level water, and the land is worth enough. Demand comes from the balance of the town:
 * people want jobs, shops want customers, industry wants workers. That is the whole game in one sentence:
 * keep the three in balance and keep the services coming.
 *
 * <p>Eight times a month the game surveys the town (roads, power, water, services, pollution, land value)
 * and looks at an eighth of the lots, so they grow one by one and not all at once. Once a month it works
 * out demand, the budget and how happy the people are.
 */
public final class Game {
    public enum Phase { PLAYING, WON, LOST }

    /** Whether an action can be done, and if not, why. */
    public enum Result { OK, OUTSIDE, BLOCKED, MONEY, SHORE, ONE, FORBIDDEN, NOTHING }

    /** The people a town counts as a milestone. */
    static final int[] MILESTONES = {100, 500, 1000, 2000, 3000, 5000, 10000, 20000};

    final Level level;
    final Rules rules;
    final int width;
    final int height;
    SeededRandom random;

    // The tiles, by row * width + col.
    final Ground[] ground;
    final boolean[] road;
    final boolean[] line;
    final Zone[] zone;
    final int[] lot;
    final int[] built;
    final int[] lack;
    final int[] rubble;
    final double[] fire;
    final double[] douse;

    // What the survey found, eight times a month.
    final boolean[] linked;
    final boolean[] watered;
    final int[] value;
    final int[] smoke;
    final int[] shore;
    final SupplyGrid power;
    final Coverage towers;
    final Map<Work, Coverage> services = new EnumMap<>(Work.class);

    final Map<Integer, Building> buildings = new LinkedHashMap<>();
    int nextId = 1;
    double money;
    int tax = Field.TAX;
    double time;
    double clock;
    int slice;
    int months;
    int milestone;
    final int[] hinted = new int[Event.Hint.values().length];
    Phase phase = Phase.PLAYING;
    /** No goal and no end: the title screen's town, or a town played on after its mission was won. */
    boolean endless;

    int people;
    int shopJobs;
    int workJobs;
    double demandR;
    double demandC;
    double demandI;
    int happiness = 50;
    double income;
    double expenses;
    double waterSupply;
    double waterDemand;

    private final List<Event> events = new ArrayList<>();

    public Game(Level level, Rules rules, long seed) {
        this.level = level;
        this.rules = rules;
        this.width = level.width();
        this.height = level.height();
        this.random = new SeededRandom(seed);
        int n = width * height;
        ground = new Ground[n];
        road = new boolean[n];
        line = new boolean[n];
        zone = new Zone[n];
        lot = new int[n];
        built = new int[n];
        lack = new int[n];
        rubble = new int[n];
        fire = new double[n];
        douse = new double[n];
        linked = new boolean[n];
        watered = new boolean[n];
        value = new int[n];
        smoke = new int[n];
        shore = new int[n];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int i = index(col, row);
                ground[i] = level.ground(col, row);
                road[i] = level.highway(col, row);
            }
        }
        // How far each tile lies from the water, up to 4: the riverside is worth more.
        for (int i = 0; i < n; i++) shore[i] = 4;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (ground[index(col, row)] != Ground.WATER) continue;
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (!contains(col + dx, row + dy)) continue;
                        int j = index(col + dx, row + dy);
                        shore[j] = Math.min(shore[j], Math.max(Math.abs(dx), Math.abs(dy)));
                    }
                }
            }
        }
        power = new SupplyGrid(width, height);
        towers = new Coverage(width, height);
        for (Work work : List.of(Work.SCHOOL, Work.LIBRARY, Work.CLINIC, Work.FIRE, Work.HALL, Work.PARK)) {
            services.put(work, new Coverage(width, height));
        }
        money = level.mission().money();
        Arrays.fill(douse, -1);
        survey();
    }

    // ---- Time ------------------------------------------------------------------------------------

    // --8<-- [start:update]
    /** One step of {@code seconds}; the events of the step are in {@link #events()} afterwards. */
    public void update(double seconds) {
        events.clear();
        if (phase != Phase.PLAYING) return;
        time += seconds;
        fires(seconds);
        clock += seconds;
        double sliceLength = Field.MONTH / Field.SLICES;
        while (clock >= sliceLength && phase == Phase.PLAYING) {
            clock -= sliceLength;
            survey();
            grow(slice);
            if (++slice == Field.SLICES) {
                slice = 0;
                month();
            }
        }
    }
    // --8<-- [end:update]

    // --8<-- [start:survey]
    /** Looks at the whole town: which roads reach the highway, power, water, services, pollution, land value. */
    void survey() {
        linkRoads();
        power.clear();
        for (int i = 0; i < width * height; i++) {
            int col = i % width;
            int row = i / width;
            if (line[i] || zone[i] != null || built[i] != 0) power.conductor(col, row);
            if (zone[i] != null && lot[i] > 0 && fire[i] <= 0) power.load(col, row, Field.LOT_POWER[lot[i]]);
        }
        for (Building b : buildings.values()) {
            if (b.type().plant()) power.source(b.col(), b.row(), b.type().power());
            else if (b.type().uses() > 0) power.load(b.col(), b.row(), b.type().uses());
        }
        power.solve();
        for (Building b : buildings.values()) b.powered = b.type().plant() || power.supplied(b.col(), b.row());

        towers.clear();
        for (Coverage coverage : services.values()) coverage.clear();
        waterSupply = 0;
        for (Building b : buildings.values()) {
            if (!b.working()) continue;
            Work work = b.type().work();
            if (work == Work.PUMP) waterSupply += b.type().water();
            else if (work == Work.TOWER) towers.add(b.centerCol(), b.centerRow(), b.type().reach());
            else if (services.containsKey(work)) services.get(work).add(b.centerCol(), b.centerRow(), b.type().reach());
        }
        waterDemand = 0;
        for (int i = 0; i < width * height; i++) {
            if (zone[i] != null && lot[i] > 0 && towers.covered(i % width, i / width)) waterDemand += Field.LOT_WATER[lot[i]];
        }
        // Too little water: the same lots go dry every time, those whose number comes up last.
        double share = waterDemand <= 0 ? 1 : Math.min(1, waterSupply / waterDemand);
        for (int i = 0; i < width * height; i++) {
            watered[i] = towers.covered(i % width, i / width) && waterSupply > 0 && (share >= 1 || spread(i) < share);
        }
        pollute();
        appraise();
        count();
    }
    // --8<-- [end:survey]

    /** The road tiles joined to the highway, by a flood from the highway along the roads. */
    private void linkRoads() {
        Arrays.fill(linked, false);
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (level.highway(col, row) && road[index(col, row)]) {
                    linked[index(col, row)] = true;
                    queue[tail++] = index(col, row);
                }
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int col = i % width;
            int row = i / width;
            int[][] next = {{col - 1, row}, {col + 1, row}, {col, row - 1}, {col, row + 1}};
            for (int[] c : next) {
                if (!contains(c[0], c[1])) continue;
                int j = index(c[0], c[1]);
                if (road[j] && !linked[j]) {
                    linked[j] = true;
                    queue[tail++] = j;
                }
            }
        }
    }

    // --8<-- [start:pollute]
    /** Smoke from industry and the coal plant, falling off with the distance. */
    private void pollute() {
        Arrays.fill(smoke, 0);
        for (int i = 0; i < width * height; i++) {
            if (zone[i] == Zone.INDUSTRIAL && lot[i] > 0) puff(i % width + 0.5, i / width + 0.5, Field.SMOKE[lot[i]], Field.SMOKE_REACH);
        }
        for (Building b : buildings.values()) {
            if (b.type().smoke() > 0 && b.working()) puff(b.centerCol(), b.centerRow(), b.type().smoke(), b.type().reach());
        }
        for (int i = 0; i < width * height; i++) {
            if (ground[i] == Ground.FOREST) smoke[i] = Math.max(0, smoke[i] - 6);
            smoke[i] = Math.min(100, smoke[i]);
        }
    }

    private void puff(double col, double row, int amount, int reach) {
        for (int y = (int) Math.floor(row - reach); y <= (int) Math.ceil(row + reach); y++) {
            for (int x = (int) Math.floor(col - reach); x <= (int) Math.ceil(col + reach); x++) {
                if (!contains(x, y)) continue;
                double distance = Math.hypot(x + 0.5 - col, y + 0.5 - row);
                if (distance > reach) continue;
                smoke[index(x, y)] += (int) Math.round(amount * (1 - distance / (reach + 1)));
            }
        }
    }
    // --8<-- [end:pollute]

    // --8<-- [start:appraise]
    /** What the land is worth: parks, services and the riverside raise it, smoke lowers it. */
    private void appraise() {
        Coverage parks = services.get(Work.PARK);
        for (int i = 0; i < width * height; i++) {
            int col = i % width;
            int row = i / width;
            double worth = 22
                    + 26 * parks.strength(col, row)
                    + 14 * services.get(Work.SCHOOL).strength(col, row)
                    + 8 * services.get(Work.LIBRARY).strength(col, row)
                    + 10 * services.get(Work.CLINIC).strength(col, row)
                    + 6 * services.get(Work.FIRE).strength(col, row)
                    + 10 * services.get(Work.HALL).strength(col, row)
                    + (shore[i] == 0 ? 0 : 4 * (4 - shore[i]))
                    - 0.8 * smoke[i];
            value[i] = (int) Math.max(0, Math.min(100, Math.round(worth)));
        }
    }
    // --8<-- [end:appraise]

    private void count() {
        people = 0;
        shopJobs = 0;
        workJobs = 0;
        for (int i = 0; i < width * height; i++) {
            if (zone[i] == null || lot[i] == 0) continue;
            int capacity = zone[i].capacity(lot[i]);
            switch (zone[i]) {
                case RESIDENTIAL -> people += capacity;
                case COMMERCIAL -> shopJobs += capacity;
                case INDUSTRIAL -> workJobs += capacity;
            }
        }
    }

    /** A number from 0 to 1 that belongs to a tile and does not change: who goes dry first. */
    private static double spread(int i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    // --8<-- [start:grow]
    /** Every lot whose number falls in this slice of the month: grows, waits or falls back. */
    private void grow(int part) {
        double spare = power.supply() - power.served();
        for (int i = part; i < width * height; i += Field.SLICES) {
            Zone z = zone[i];
            if (z == null || fire[i] > 0) continue;
            if (rubble[i] > 0) continue;
            int col = i % width;
            int row = i / width;
            int now = lot[i];
            double demand = demand(z);
            boolean street = street(col, row);
            if (now == 0) {
                // An empty lot: a road to the highway, power to spare and people who want to come.
                if (!street || !power.connected(col, row) || spare < Field.LOT_POWER[1] || demand <= 0) continue;
                if (random.nextDouble() < 0.08 + 0.3 * demand) {
                    lot[i] = 1;
                    spare -= Field.LOT_POWER[1];
                    events.add(new Event.Grew(col, row, 1));
                }
                continue;
            }
            boolean lit = power.supplied(col, row);
            boolean wet = watered[i];
            if (!lit || (now >= 2 && !wet) || !street) {
                if (++lack[i] > Field.PATIENCE) {
                    lack[i] = 0;
                    fall(i, col, row);
                }
                continue;
            }
            lack[i] = 0;
            if (demand < -0.4 && random.nextDouble() < 0.1) {
                fall(i, col, row);
                continue;
            }
            if (now == 3 || !wet || demand <= 0.1 * now) continue;
            int next = now + 1;
            if (value[i] < needed(z, next)) continue;
            if (z == Zone.RESIDENTIAL && next == 3 && !services.get(Work.SCHOOL).covered(col, row)) continue;
            double more = Field.LOT_POWER[next] - Field.LOT_POWER[now];
            if (spare < more) continue;
            if (random.nextDouble() < 0.04 + 0.2 * demand) {
                lot[i] = next;
                spare -= more;
                events.add(new Event.Grew(col, row, next));
            }
        }
    }
    // --8<-- [end:grow]

    private void fall(int i, int col, int row) {
        lot[i]--;
        events.add(new Event.Declined(col, row, lot[i]));
    }

    /** The land value a zone needs to grow to a level: industry asks for less, offices a little less. */
    public static int needed(Zone zone, int level) {
        if (zone == Zone.COMMERCIAL && level == 3) return Field.OFFICE_VALUE;
        int need = level == 3 ? Field.VALUE_3 : level == 2 ? Field.VALUE_2 : 0;
        return zone == Zone.INDUSTRIAL ? need / 2 : need;
    }

    /** Whether a road that leads to the highway lies within reach of a tile. */
    public boolean street(int col, int row) {
        int reach = Field.ROAD_REACH;
        for (int y = row - reach; y <= row + reach; y++) {
            for (int x = col - reach; x <= col + reach; x++) {
                if (contains(x, y) && linked[index(x, y)]) return true;
            }
        }
        return false;
    }

    // --8<-- [start:month]
    /** The end of a month: demand, the budget, the people's mood, fires, advice, and whether the mission is over. */
    private void month() {
        months++;
        weigh();
        double upkeep = 0;
        int roads = 0;
        int lines = 0;
        for (int i = 0; i < width * height; i++) {
            if (road[i] && !level.highway(i % width, i / width)) roads++;
            if (line[i]) lines++;
            if (rubble[i] > 0) rubble[i]--;
        }
        upkeep += roads * Field.ROAD_UPKEEP + lines * Field.LINE_UPKEEP;
        for (Building b : buildings.values()) upkeep += b.type().upkeep();
        income = tax / 100.0 * (people * 7 + (shopJobs + workJobs) * 5);
        expenses = upkeep;
        money += income - expenses;
        mood();
        if (level.mission().fire() && random.nextDouble() < Field.FIRE_CHANCE) ignite();
        events.add(new Event.Month(months % 12, months / 12 + 1));
        while (milestone < MILESTONES.length && people >= MILESTONES[milestone]) {
            events.add(new Event.Milestone(MILESTONES[milestone]));
            milestone++;
        }
        advise();
        Mission mission = level.mission();
        if (endless) return;
        if (people >= mission.goal() && happiness >= mission.happiness()) {
            phase = Phase.WON;
            events.add(new Event.Over(true));
        } else if (money < Field.DEBT || (mission.years() > 0 && months >= mission.years() * 12)) {
            phase = Phase.LOST;
            events.add(new Event.Over(false));
        }
    }
    // --8<-- [end:month]

    // --8<-- [start:demand]
    /**
     * Demand for each zone, from -1 to 1. People come for jobs; shops and industry come for people, and
     * industry also sells out of town, so it starts the town. A grown town also wants offices, clean jobs
     * in the shop zone, and so a little less industry. Taxes above the usual rate put everyone off, a
     * happy town draws people.
     */
    private void weigh() {
        int jobs = shopJobs + workJobs;
        double workers = people * 0.3;
        double homes = (jobs + 16 - workers) / Math.max(24, people * 0.12);
        double offices = Math.max(0, people - Field.OFFICE_TOWN) * Field.OFFICE_JOBS;
        double shopTarget = people * 0.12 + 4 + offices;
        double shops = (shopTarget - shopJobs) / Math.max(8, shopTarget * 0.5);
        double workTarget = people * 0.17 + 10 - offices * 0.5;
        double works = (workTarget - workJobs) / Math.max(10, workTarget * 0.5);
        double taxes = (tax - Field.TAX) * 0.05;
        demandR = clamp(0.5 * demandR + 0.5 * (homes - taxes + (happiness - 50) / 150.0));
        demandC = clamp(0.5 * demandC + 0.5 * (shops - taxes));
        demandI = clamp(0.5 * demandI + 0.5 * (works - taxes));
    }
    // --8<-- [end:demand]

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    // --8<-- [start:mood]
    /** How happy the people are: services, parks, clean air, power, water, jobs and fair taxes. */
    private void mood() {
        if (people == 0) {
            happiness = 50;
            return;
        }
        double school = 0, library = 0, clinic = 0, station = 0, parks = 0, air = 0, dark = 0, dry = 0;
        for (int i = 0; i < width * height; i++) {
            if (zone[i] != Zone.RESIDENTIAL || lot[i] == 0) continue;
            int col = i % width;
            int row = i / width;
            double w = Field.PEOPLE[lot[i]];
            if (services.get(Work.SCHOOL).covered(col, row)) school += w;
            if (services.get(Work.LIBRARY).covered(col, row)) library += w;
            if (services.get(Work.CLINIC).covered(col, row)) clinic += w;
            if (services.get(Work.FIRE).covered(col, row)) station += w;
            parks += w * services.get(Work.PARK).strength(col, row);
            air += w * smoke[i];
            if (!power.supplied(col, row)) dark += w;
            if (lot[i] >= 2 && !watered[i]) dry += w;
        }
        double p = people;
        double target = 36 + 16 * school / p + 8 * library / p + 12 * clinic / p + 8 * station / p
                + Math.min(12, 24 * parks / p) - 0.5 * air / p - 3 * (tax - Field.TAX) - 30 * dark / p - 15 * dry / p
                + (shopJobs + workJobs >= people * 0.3 ? 6 : -6);
        target = Math.max(0, Math.min(100, target));
        happiness = (int) Math.round(0.5 * happiness + 0.5 * target);
    }
    // --8<-- [end:mood]

    // ---- Fire ------------------------------------------------------------------------------------

    /** A fire breaks out on a lot with a building, chosen at random. */
    private void ignite() {
        List<Integer> lots = new ArrayList<>();
        for (int i = 0; i < width * height; i++) if (zone[i] != null && lot[i] > 0 && fire[i] <= 0) lots.add(i);
        if (lots.isEmpty()) return;
        light(lots.get(random.nextInt(lots.size())));
    }

    private void light(int i) {
        int col = i % width;
        int row = i / width;
        fire[i] = Field.BURNS;
        events.add(new Event.Fire(col, row));
        Building station = station(col, row);
        if (station != null) {
            douse[i] = Field.DOUSE;
            events.add(new Event.Truck(station, col, row));
        }
    }

    /** The nearest working fire station that reaches a tile, or null. */
    public Building station(int col, int row) {
        Building best = null;
        double nearest = Double.MAX_VALUE;
        for (Building b : buildings.values()) {
            if (b.type().work() != Work.FIRE || !b.working()) continue;
            double distance = Math.hypot(col + 0.5 - b.centerCol(), row + 0.5 - b.centerRow());
            if (distance <= b.type().reach() && distance < nearest) {
                nearest = distance;
                best = b;
            }
        }
        return best;
    }

    // --8<-- [start:fires]
    private void fires(double seconds) {
        for (int i = 0; i < width * height; i++) {
            if (fire[i] <= 0) continue;
            int col = i % width;
            int row = i / width;
            if (douse[i] >= 0) {
                douse[i] -= seconds;
                if (douse[i] < 0) {
                    fire[i] = 0;
                    douse[i] = -1;
                    events.add(new Event.Doused(col, row));
                    continue;
                }
            }
            double before = fire[i];
            fire[i] -= seconds;
            // Halfway through, the fire reaches for the lots next door.
            if (before > Field.BURNS / 2 && fire[i] <= Field.BURNS / 2) {
                int[][] next = {{col - 1, row}, {col + 1, row}, {col, row - 1}, {col, row + 1}};
                for (int[] c : next) {
                    if (!contains(c[0], c[1])) continue;
                    int j = index(c[0], c[1]);
                    if (zone[j] != null && lot[j] > 0 && fire[j] <= 0 && random.nextDouble() < Field.SPREAD) light(j);
                }
            }
            if (fire[i] <= 0) {
                fire[i] = 0;
                lot[i] = 0;
                rubble[i] = Field.RUBBLE_MONTHS;
                events.add(new Event.Burned(col, row));
            }
        }
    }
    // --8<-- [end:fires]

    // ---- Advice ----------------------------------------------------------------------------------

    /** At most one piece of advice a month, and the same one at most once a year. */
    private void advise() {
        int zoned = 0, streets = 0, unlinked = 0, connected = 0, homes = 0, airAtHome = 0;
        boolean plant = false, pump = false, tower = false, school = false;
        for (Building b : buildings.values()) {
            Work work = b.type().work();
            plant |= b.type().plant();
            pump |= work == Work.PUMP;
            tower |= work == Work.TOWER;
            school |= work == Work.SCHOOL;
        }
        for (int i = 0; i < width * height; i++) {
            if (zone[i] == null) continue;
            int col = i % width;
            int row = i / width;
            zoned++;
            if (street(col, row)) streets++;
            else if (roadNear(col, row)) unlinked++;
            if (power.connected(col, row)) connected++;
            if (zone[i] == Zone.RESIDENTIAL && lot[i] > 0) {
                homes++;
                airAtHome += smoke[i];
            }
        }
        List<Event.Hint> hints = new ArrayList<>();
        if (money < 0) hints.add(Event.Hint.DEBT);
        if (zoned >= 4 && streets == 0 && unlinked > 0) hints.add(Event.Hint.NO_HIGHWAY);
        if (zoned >= 4 && streets == 0 && unlinked == 0) hints.add(Event.Hint.NO_ROAD);
        if (zoned >= 4 && !plant) hints.add(Event.Hint.NO_POWER);
        if (power.demand() > power.supply() && plant) hints.add(Event.Hint.POWER_SHORT);
        if (plant && zoned - connected >= 6) hints.add(Event.Hint.UNPOWERED);
        if (people >= 150 && !(pump && tower)) hints.add(Event.Hint.NO_WATER);
        if (waterDemand > waterSupply && pump && tower) hints.add(Event.Hint.WATER_SHORT);
        if (demandR < -0.3 && Math.max(demandC, demandI) > 0.3) hints.add(Event.Hint.NO_JOBS);
        if (demandR > 0.5 && Math.max(demandC, demandI) < 0) hints.add(Event.Hint.NO_HOMES);
        if (demandC > 0.6 && people > 200) hints.add(Event.Hint.NO_SHOPS);
        if (homes > 0 && airAtHome / homes > 25) hints.add(Event.Hint.POLLUTION);
        if (people >= 400 && !school) hints.add(Event.Hint.SCHOOL);
        if (money < 1000 && income < expenses) hints.add(Event.Hint.MONEY);
        for (Event.Hint hint : hints) {
            int last = hinted[hint.ordinal()];
            if (last != 0 && months - last < 12) continue;
            hinted[hint.ordinal()] = months;
            events.add(new Event.Advice(hint));
            return;
        }
    }

    private boolean roadNear(int col, int row) {
        int reach = Field.ROAD_REACH;
        for (int y = row - reach; y <= row + reach; y++) {
            for (int x = col - reach; x <= col + reach; x++) {
                if (contains(x, y) && road[index(x, y)]) return true;
            }
        }
        return false;
    }

    // ---- Actions ---------------------------------------------------------------------------------

    /**
     * The tiles from one tile to another along the columns first, then along the rows: how a road or a line
     * follows the mouse.
     */
    public static List<Cell> path(int col, int row, int toCol, int toRow) {
        List<Cell> cells = new ArrayList<>();
        int dx = Integer.signum(toCol - col);
        int dy = Integer.signum(toRow - row);
        int c = col;
        cells.add(new Cell(c, row));
        while (c != toCol) {
            c += dx;
            cells.add(new Cell(c, row));
        }
        int r = row;
        while (r != toRow) {
            r += dy;
            cells.add(new Cell(c, r));
        }
        return cells;
    }

    // --8<-- [start:roads]
    /** What a road along {@code cells} costs, or -1 if it cannot be built. A bridge must run straight. */
    public int roadCost(List<Cell> cells) {
        int cost = 0;
        for (int k = 0; k < cells.size(); k++) {
            Cell cell = cells.get(k);
            if (!contains(cell.col(), cell.row())) return -1;
            int i = index(cell.col(), cell.row());
            if (road[i]) continue;
            if (built[i] != 0 || (zone[i] != null && lot[i] > 0)) return -1;
            if (ground[i] == Ground.WATER) {
                if (!straight(cells, k)) return -1;
                cost += Field.BRIDGE;
            } else {
                cost += Field.ROAD + (ground[i] == Ground.FOREST ? Field.TREE : 0);
            }
        }
        return cost;
    }

    /** Whether the tile {@code k} of a path has its neighbours in the path on opposite sides, or is an end. */
    private boolean straight(List<Cell> cells, int k) {
        Cell here = cells.get(k);
        Cell before = k > 0 ? cells.get(k - 1) : null;
        Cell after = k < cells.size() - 1 ? cells.get(k + 1) : null;
        if (before == null || after == null) return cells.size() > 1;
        return before.col() == after.col() && here.col() == before.col()
                || before.row() == after.row() && here.row() == before.row();
    }

    public Result road(List<Cell> cells) {
        int cost = roadCost(cells);
        if (cost < 0) return Result.BLOCKED;
        if (cost == 0) return Result.NOTHING;
        if (cost > money) return Result.MONEY;
        int laid = 0;
        for (Cell cell : cells) {
            int i = index(cell.col(), cell.row());
            if (road[i]) continue;
            road[i] = true;
            zone[i] = null;
            if (ground[i] == Ground.FOREST) ground[i] = Ground.GRASS;
            laid++;
        }
        money -= cost;
        events.add(new Event.Laid("road", laid, cost));
        survey();
        return Result.OK;
    }
    // --8<-- [end:roads]

    /**
     * What a power line along {@code cells} costs, or -1 if it cannot be built. Zones and buildings carry
     * power themselves, so the line skips them.
     */
    public int lineCost(List<Cell> cells) {
        int cost = 0;
        for (Cell cell : cells) {
            if (!contains(cell.col(), cell.row())) return -1;
            int i = index(cell.col(), cell.row());
            if (line[i] || zone[i] != null || built[i] != 0) continue;
            cost += Field.LINE * (ground[i] == Ground.WATER ? 2 : 1) + (ground[i] == Ground.FOREST ? Field.TREE : 0);
        }
        return cost;
    }

    public Result line(List<Cell> cells) {
        int cost = lineCost(cells);
        if (cost < 0) return Result.BLOCKED;
        if (cost == 0) return Result.NOTHING;
        if (cost > money) return Result.MONEY;
        int laid = 0;
        for (Cell cell : cells) {
            int i = index(cell.col(), cell.row());
            if (line[i] || zone[i] != null || built[i] != 0) continue;
            line[i] = true;
            if (ground[i] == Ground.FOREST) ground[i] = Ground.GRASS;
            laid++;
        }
        money -= cost;
        events.add(new Event.Laid("line", laid, cost));
        survey();
        return Result.OK;
    }

    // --8<-- [start:zone]
    /** The tiles of a rectangle that can be zoned: free land, or an empty lot of another zone. */
    public List<Cell> zonable(Zone z, int col, int row, int toCol, int toRow) {
        List<Cell> cells = new ArrayList<>();
        for (int r = Math.min(row, toRow); r <= Math.max(row, toRow); r++) {
            for (int c = Math.min(col, toCol); c <= Math.max(col, toCol); c++) {
                if (!contains(c, r)) continue;
                int i = index(c, r);
                if (ground[i] == Ground.WATER || road[i] || line[i] || built[i] != 0) continue;
                if (zone[i] == z || (zone[i] != null && lot[i] > 0)) continue;
                cells.add(new Cell(c, r));
            }
        }
        return cells;
    }

    public int zoneCost(List<Cell> cells, Zone z) {
        int cost = 0;
        for (Cell cell : cells) cost += z.cost() + (ground(cell.col(), cell.row()) == Ground.FOREST ? Field.TREE : 0);
        return cost;
    }

    public Result zone(Zone z, int col, int row, int toCol, int toRow) {
        List<Cell> cells = zonable(z, col, row, toCol, toRow);
        if (cells.isEmpty()) return Result.NOTHING;
        int cost = zoneCost(cells, z);
        if (cost > money) return Result.MONEY;
        for (Cell cell : cells) {
            int i = index(cell.col(), cell.row());
            zone[i] = z;
            lot[i] = 0;
            lack[i] = 0;
            if (ground[i] == Ground.FOREST) ground[i] = Ground.GRASS;
        }
        money -= cost;
        events.add(new Event.Laid("zone." + z.letter(), cells.size(), cost));
        survey();
        return Result.OK;
    }
    // --8<-- [end:zone]

    /** Whether a building can stand with its top left corner on a tile. */
    public Result placement(BuildingType type, int col, int row) {
        if (level.mission().forbidden().contains(type.id())) return Result.FORBIDDEN;
        boolean water = false;
        for (int r = row; r < row + type.height(); r++) {
            for (int c = col; c < col + type.width(); c++) {
                if (!contains(c, r)) return Result.OUTSIDE;
                int i = index(c, r);
                if (ground[i] == Ground.WATER || road[i] || line[i] || built[i] != 0) return Result.BLOCKED;
                if (zone[i] != null && lot[i] > 0) return Result.BLOCKED;
            }
        }
        for (int r = row - 1; r <= row + type.height(); r++) {
            for (int c = col - 1; c <= col + type.width(); c++) {
                if (contains(c, r) && ground[index(c, r)] == Ground.WATER) water = true;
            }
        }
        if (type.shore() && !water) return Result.SHORE;
        if (type.one() && buildings.values().stream().anyMatch(b -> b.type() == type)) return Result.ONE;
        if (cost(type, col, row) > money) return Result.MONEY;
        return Result.OK;
    }

    /** What a building costs here, with the trees on its tiles. */
    public int cost(BuildingType type, int col, int row) {
        int cost = type.cost();
        for (int r = row; r < row + type.height(); r++) {
            for (int c = col; c < col + type.width(); c++) {
                if (contains(c, r) && ground[index(c, r)] == Ground.FOREST) cost += Field.TREE;
            }
        }
        return cost;
    }

    public Result build(BuildingType type, int col, int row) {
        Result result = placement(type, col, row);
        if (result != Result.OK) return result;
        money -= cost(type, col, row);
        Building b = new Building(nextId++, type, col, row);
        buildings.put(b.id(), b);
        for (int r = row; r < row + type.height(); r++) {
            for (int c = col; c < col + type.width(); c++) {
                int i = index(c, r);
                built[i] = b.id();
                zone[i] = null;
                if (ground[i] == Ground.FOREST) ground[i] = Ground.GRASS;
            }
        }
        survey();
        events.add(new Event.Built(b));
        return Result.OK;
    }

    // --8<-- [start:bulldoze]
    /**
     * What bulldozing a rectangle would clear, as tiles, and what it costs: buildings whole, a lot's building
     * but not its zone, roads (not the highway), lines and trees; an empty zone is unzoned.
     */
    public int bulldozeCost(int col, int row, int toCol, int toRow) {
        int cost = 0;
        for (int r = Math.min(row, toRow); r <= Math.max(row, toRow); r++) {
            for (int c = Math.min(col, toCol); c <= Math.max(col, toCol); c++) {
                if (!contains(c, r)) continue;
                int i = index(c, r);
                if (built[i] != 0 || road[i] && !level.highway(c, r) || line[i] || ground[i] == Ground.FOREST
                        || zone[i] != null && lot[i] > 0) {
                    cost += Field.CLEAR;
                }
            }
        }
        return cost;
    }

    public Result bulldoze(int col, int row, int toCol, int toRow) {
        int cost = bulldozeCost(col, row, toCol, toRow);
        boolean unzone = false;
        for (int r = Math.min(row, toRow); r <= Math.max(row, toRow); r++) {
            for (int c = Math.min(col, toCol); c <= Math.max(col, toCol); c++) {
                if (contains(c, r) && zone[index(c, r)] != null) unzone = true;
            }
        }
        if (cost == 0 && !unzone) return Result.NOTHING;
        if (cost > money) return Result.MONEY;
        int cleared = 0;
        for (int r = Math.min(row, toRow); r <= Math.max(row, toRow); r++) {
            for (int c = Math.min(col, toCol); c <= Math.max(col, toCol); c++) {
                if (!contains(c, r)) continue;
                int i = index(c, r);
                if (built[i] != 0) {
                    demolish(buildings.get(built[i]));
                    cleared++;
                } else if (zone[i] != null && lot[i] > 0) {
                    lot[i] = 0;
                    fire[i] = 0;
                    douse[i] = -1;
                    cleared++;
                } else if (road[i] && !level.highway(c, r) || line[i]) {
                    if (!level.highway(c, r)) road[i] = false;
                    line[i] = false;
                    cleared++;
                } else if (ground[i] == Ground.FOREST) {
                    ground[i] = Ground.GRASS;
                    cleared++;
                } else if (zone[i] != null) {
                    zone[i] = null;
                    rubble[i] = 0;
                    cleared++;
                }
            }
        }
        money -= cost;
        events.add(new Event.Laid("bulldoze", cleared, cost));
        survey();
        return Result.OK;
    }
    // --8<-- [end:bulldoze]

    private void demolish(Building b) {
        buildings.remove(b.id());
        for (int r = b.row(); r < b.row() + b.height(); r++) {
            for (int c = b.col(); c < b.col() + b.width(); c++) built[index(c, r)] = 0;
        }
    }

    /** Plays on without a goal or an end: for the title screen, or after the mission was won. */
    public void playOn() {
        endless = true;
        phase = Phase.PLAYING;
    }

    public boolean endless() {
        return endless;
    }

    /** The tax rate in percent, 0 to {@link Field#MAX_TAX}. */
    public void setTax(int percent) {
        tax = Math.max(0, Math.min(Field.MAX_TAX, percent));
    }

    // ---- What the pictures and the panel ask -----------------------------------------------------

    public int index(int col, int row) {
        return row * width + col;
    }

    public boolean contains(int col, int row) {
        return col >= 0 && row >= 0 && col < width && row < height;
    }

    public Level level() {
        return level;
    }

    public Rules rules() {
        return rules;
    }

    public Mission mission() {
        return level.mission();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Ground ground(int col, int row) {
        return ground[index(col, row)];
    }

    public boolean road(int col, int row) {
        return contains(col, row) && road[index(col, row)];
    }

    public boolean highway(int col, int row) {
        return contains(col, row) && level.highway(col, row);
    }

    /** Whether a road tile leads to the highway. */
    public boolean linked(int col, int row) {
        return contains(col, row) && linked[index(col, row)];
    }

    public boolean line(int col, int row) {
        return contains(col, row) && line[index(col, row)];
    }

    public Zone zone(int col, int row) {
        return contains(col, row) ? zone[index(col, row)] : null;
    }

    /** The level of the building on a lot: 0 for an empty lot, 1 to 3. */
    public int lot(int col, int row) {
        return contains(col, row) ? lot[index(col, row)] : 0;
    }

    public Building buildingAt(int col, int row) {
        return contains(col, row) ? buildings.get(built[index(col, row)]) : null;
    }

    public List<Building> buildings() {
        return List.copyOf(buildings.values());
    }

    /** Whether a lot, or a building's tile, has power; for an empty lot, whether power reaches it. */
    public boolean powered(int col, int row) {
        if (!contains(col, row)) return false;
        int i = index(col, row);
        if (built[i] != 0) return buildings.get(built[i]).working();
        if (zone[i] != null && lot[i] > 0) return power.supplied(col, row);
        return power.connected(col, row);
    }

    public boolean watered(int col, int row) {
        return contains(col, row) && watered[index(col, row)];
    }

    /** Whether a water tower reaches a tile. */
    public boolean piped(int col, int row) {
        return towers.covered(col, row);
    }

    public boolean served(Work work, int col, int row) {
        Coverage coverage = services.get(work);
        return coverage != null && coverage.covered(col, row);
    }

    public int value(int col, int row) {
        return contains(col, row) ? value[index(col, row)] : 0;
    }

    public int smoke(int col, int row) {
        return contains(col, row) ? smoke[index(col, row)] : 0;
    }

    public boolean burning(int col, int row) {
        return contains(col, row) && fire[index(col, row)] > 0;
    }

    /** Whether a fire truck is on its way to a burning lot. */
    public boolean dousing(int col, int row) {
        return burning(col, row) && douse[index(col, row)] >= 0;
    }

    public boolean rubble(int col, int row) {
        return contains(col, row) && rubble[index(col, row)] > 0;
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public double money() {
        return money;
    }

    public int tax() {
        return tax;
    }

    public int people() {
        return people;
    }

    public int jobs() {
        return shopJobs + workJobs;
    }

    public int shopJobs() {
        return shopJobs;
    }

    public int workJobs() {
        return workJobs;
    }

    public double demand(Zone z) {
        return switch (z) {
            case RESIDENTIAL -> demandR;
            case COMMERCIAL -> demandC;
            case INDUSTRIAL -> demandI;
        };
    }

    public int happiness() {
        return happiness;
    }

    /** Last month's taxes and costs. */
    public double income() {
        return income;
    }

    public double expenses() {
        return expenses;
    }

    public double powerSupply() {
        return power.supply();
    }

    public double powerDemand() {
        return power.demand();
    }

    public double waterSupply() {
        return waterSupply;
    }

    public double waterDemand() {
        return waterDemand;
    }

    /** Months since the start; the month of the year is {@code months() % 12}. */
    public int months() {
        return months;
    }

    public int year() {
        return months / 12 + 1;
    }

    public double time() {
        return time;
    }

    public Phase phase() {
        return phase;
    }
}
