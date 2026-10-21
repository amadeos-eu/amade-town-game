package eu.amadeos.games.town.domain;

import eu.amadeos.gameengine.data.TextData.DataException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A mission's map, a text file of one letter per tile, drawn in any text editor:
 *
 * <pre>
 * # Lines that begin with # are comments.
 * ......TTTT....~~~...
 * ======........~~~...       the highway: road that leads out of the map
 * ..............~~~..T
 * </pre>
 *
 * <p>{@code .} is grass, {@code T} forest, {@code ~} water and {@code =} the highway, a road the town starts
 * with, which must touch the edge of the map: it is the town's way to the rest of the country, and only
 * lots on roads that reach it grow. Every row is as long as the first.
 */
public final class Level {
    /** Where the maps lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/town/maps/";

    private final Mission mission;
    private final String source;
    private final int width;
    private final int height;
    private final Ground[] ground;
    private final boolean[] highway;

    private Level(Mission mission, String source, int width, int height, Ground[] ground, boolean[] highway) {
        this.mission = mission;
        this.source = source;
        this.width = width;
        this.height = height;
        this.ground = ground;
        this.highway = highway;
    }

    /** Mission {@code number}, counted from 1, with its map. */
    public static Level load(int number, Rules rules) {
        Mission mission = rules.mission(number);
        String resource = FOLDER + mission.map();
        InputStream in = Level.class.getResourceAsStream(resource);
        if (in == null) throw new DataException(resource + ": not found");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return read(mission, mission.map(), reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --8<-- [start:read]
    public static Level read(Mission mission, String source, Reader reader) {
        List<String> rows = new ArrayList<>();
        try {
            BufferedReader lines = new BufferedReader(reader);
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                String row = line.strip();
                if (row.isEmpty() || row.startsWith("#")) continue;
                if (!rows.isEmpty() && row.length() != rows.getFirst().length()) {
                    throw new DataException(source + ": row " + (rows.size() + 1) + " is " + row.length()
                            + " tiles long, the first " + rows.getFirst().length());
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (rows.size() < 8 || rows.getFirst().length() < 8) throw new DataException(source + ": a map is 8 × 8 tiles at least");
        int width = rows.getFirst().length();
        int height = rows.size();
        Ground[] ground = new Ground[width * height];
        boolean[] highway = new boolean[width * height];
        boolean leaves = false;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                char letter = rows.get(row).charAt(col);
                int i = row * width + col;
                if (letter == '=') {
                    ground[i] = Ground.GRASS;
                    highway[i] = true;
                    leaves |= col == 0 || row == 0 || col == width - 1 || row == height - 1;
                    continue;
                }
                ground[i] = Ground.ofLetter(letter);
                if (ground[i] == null) {
                    throw new DataException(source + ": row " + (row + 1) + ", column " + (col + 1) + ": '" + letter
                            + "' is neither grass (.), forest (T), water (~) nor the highway (=)");
                }
            }
        }
        if (!leaves) throw new DataException(source + ": the highway (=) must reach the edge of the map");
        return new Level(mission, source, width, height, ground, highway);
    }
    // --8<-- [end:read]

    public Mission mission() {
        return mission;
    }

    public int number() {
        return mission.number();
    }

    public String source() {
        return source;
    }

    public Ground ground(int col, int row) {
        return ground[row * width + col];
    }

    /** Whether the town starts with a road on this tile: the highway. */
    public boolean highway(int col, int row) {
        return highway[row * width + col];
    }

    public boolean contains(int col, int row) {
        return col >= 0 && row >= 0 && col < width && row < height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
