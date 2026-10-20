package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.save.Snapshot;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A whole town as the values of a save file, and back: every tile with its ground, road, line, zone and
 * lot, the fires, the buildings, the money, the months and the random generator where it stood. What the
 * survey works out (power, water, land value, pollution) is not saved: the loaded town surveys itself.
 * The map is not saved either, only its mission's number.
 *
 * <p>Loaded, the town goes on exactly as it would have: {@code GameTest} plays on, saves, loads, plays both
 * on and finds them alike to the last bit.
 */
public final class Archive {
    /** Changes when the values change their meaning; an older file is not read. */
    public static final int VERSION = 1;

    private Archive() {
    }

    // --8<-- [start:write]
    public static Map<String, String> write(Game game) {
        Snapshot.Writer w = Snapshot.writer();
        w.put("mission", game.level.number()).put("seed", game.random.seed()).put("random", game.random.state());
        w.put("time", game.time).put("clock", game.clock).put("slice", game.slice).put("months", game.months);
        w.put("milestone", game.milestone).put("money", game.money).put("tax", game.tax).put("next", game.nextId);
        w.put("phase", game.phase).put("endless", game.endless).put("demandR", game.demandR).put("demandC", game.demandC);
        w.put("demandI", game.demandI).put("happiness", game.happiness).put("income", game.income);
        w.put("expenses", game.expenses).put("hinted", ints(game.hinted));
        StringBuilder ground = new StringBuilder();
        StringBuilder zones = new StringBuilder();
        StringBuilder ways = new StringBuilder();
        int n = game.width * game.height;
        for (int i = 0; i < n; i++) {
            ground.append(game.ground[i].letter());
            zones.append(game.zone[i] == null ? '-' : game.zone[i].letter());
            ways.append((char) ('0' + (game.road[i] ? 1 : 0) + (game.line[i] ? 2 : 0)));
        }
        w.put("ground", ground.toString()).put("zones", zones.toString()).put("ways", ways.toString());
        w.put("lots", ints(game.lot)).put("lack", ints(game.lack)).put("rubble", ints(game.rubble));
        w.put("fire", doubles(game.fire)).put("douse", doubles(game.douse));
        w.list("building");
        for (Building b : game.buildings.values()) {
            w.add("building").put("id", b.id()).put("kind", b.type().id()).put("col", b.col()).put("row", b.row());
        }
        return w.values();
    }
    // --8<-- [end:write]

    // --8<-- [start:read]
    /** The saved town; {@code levels} gives the map of a mission number. */
    public static Game read(Map<String, String> values, Rules rules, IntFunction<Level> levels) {
        Snapshot.Reader r = Snapshot.reader(values);
        Level level = levels.apply(r.integer("mission"));
        Game game = new Game(level, rules, r.whole("seed"));
        game.random = SeededRandom.resume(r.whole("seed"), r.whole("random"));
        game.time = r.number("time");
        game.clock = r.number("clock");
        game.slice = r.integer("slice");
        game.months = r.integer("months");
        game.milestone = r.integer("milestone");
        game.money = r.number("money");
        game.tax = r.integer("tax");
        game.nextId = r.integer("next");
        game.phase = r.choice("phase", Game.Phase.class);
        game.endless = r.bool("endless");
        game.demandR = r.number("demandR");
        game.demandC = r.number("demandC");
        game.demandI = r.number("demandI");
        game.happiness = r.integer("happiness");
        game.income = r.number("income");
        game.expenses = r.number("expenses");
        copy(ints(r.text("hinted")), game.hinted);
        int n = game.width * game.height;
        String ground = r.text("ground");
        String zones = r.text("zones");
        String ways = r.text("ways");
        if (ground.length() != n || zones.length() != n || ways.length() != n) {
            throw new Snapshot.SnapshotException("the tiles do not fit the map");
        }
        for (int i = 0; i < n; i++) {
            Ground g = Ground.ofLetter(ground.charAt(i));
            if (g == null) throw new Snapshot.SnapshotException("ground: '" + ground.charAt(i) + "'");
            game.ground[i] = g;
            char z = zones.charAt(i);
            game.zone[i] = z == '-' ? null : Zone.ofLetter(z);
            if (z != '-' && game.zone[i] == null) throw new Snapshot.SnapshotException("zone: '" + z + "'");
            int way = ways.charAt(i) - '0';
            if (way < 0 || way > 3) throw new Snapshot.SnapshotException("ways: '" + ways.charAt(i) + "'");
            game.road[i] = (way & 1) != 0;
            game.line[i] = (way & 2) != 0;
        }
        copy(ints(r.text("lots")), game.lot);
        copy(ints(r.text("lack")), game.lack);
        copy(ints(r.text("rubble")), game.rubble);
        copy(doubles(r.text("fire")), game.fire);
        copy(doubles(r.text("douse")), game.douse);
        for (int lot : game.lot) if (lot < 0 || lot > 3) throw new Snapshot.SnapshotException("a lot of level " + lot);
        for (Snapshot.Reader o : r.list("building")) {
            BuildingType type = rules.building(o.text("kind"));
            Building b = new Building(o.integer("id"), type, o.integer("col"), o.integer("row"));
            for (int row = b.row(); row < b.row() + type.height(); row++) {
                for (int col = b.col(); col < b.col() + type.width(); col++) {
                    if (!game.contains(col, row)) throw new Snapshot.SnapshotException("a building off the map");
                    game.built[game.index(col, row)] = b.id();
                }
            }
            game.buildings.put(b.id(), b);
        }
        game.survey();
        return game;
    }
    // --8<-- [end:read]

    /** Numbers to the last bit, as Base64. */
    private static String doubles(double[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 8);
        for (double value : values) bytes.putDouble(value);
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    private static double[] doubles(String text) {
        ByteBuffer bytes = ByteBuffer.wrap(decode(text));
        double[] values = new double[bytes.remaining() / 8];
        for (int i = 0; i < values.length; i++) values[i] = bytes.getDouble();
        return values;
    }

    private static String ints(int[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 4);
        for (int value : values) bytes.putInt(value);
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    private static int[] ints(String text) {
        ByteBuffer bytes = ByteBuffer.wrap(decode(text));
        int[] values = new int[bytes.remaining() / 4];
        for (int i = 0; i < values.length; i++) values[i] = bytes.getInt();
        return values;
    }

    private static byte[] decode(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new Snapshot.SnapshotException("not Base64");
        }
    }

    private static void copy(double[] from, double[] to) {
        if (from.length != to.length) throw new Snapshot.SnapshotException("a tile list of the wrong size");
        System.arraycopy(from, 0, to, 0, to.length);
    }

    private static void copy(int[] from, int[] to) {
        if (from.length != to.length) throw new Snapshot.SnapshotException("a list of the wrong size");
        System.arraycopy(from, 0, to, 0, to.length);
    }
}
