package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The people walking on the pavements. Like the cars, they only show that the town lives: more of them the
 * more people it has, and the rules know nothing of them.
 *
 * <p>A walker goes from corner to corner of the road tiles, one metre in from the edge, where the pavement
 * is. From a corner it walks on along the pavement, round the corner of a block, or now and then across the
 * street to the corner opposite; never onto the highway, and never where four roads leave no pavement.
 */
final class Walkers {
    /** The corners of a road tile, clockwise from the top: (-x, -y), (+x, -y), (+x, +y), (-x, +y). */
    static final double[] CORNER_X = {1, 15, 15, 1};
    static final double[] CORNER_Y = {1, 1, 15, 15};
    static final double SPEED = 4.5;
    static final int MOST = 120;
    /** One walker for this many people, besides a few that walk anyway. */
    static final int PEOPLE_PER_WALKER = 12;
    /** How often a walker crosses the street at a corner instead of keeping to the pavement. */
    static final double CROSS = 0.2;

    /** A walker on the way from one corner to the next. */
    static final class Walker {
        int col;
        int row;
        int corner;
        int toCol;
        int toRow;
        int toCorner;
        double along;
        double length;
        double x;
        double y;
        double step;
        final int look;

        Walker(int look) {
            this.look = look;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }

        /** The look: shirt and trousers, from 0 to {@link Pictures#PEOPLE} - 1. */
        int look() {
            return look;
        }

        /** Which picture of the walk to show, from 0 to {@link Pictures#STEPS} - 1. */
        int frame() {
            return (int) step % Pictures.STEPS;
        }
    }

    private final SeededRandom random = new SeededRandom(9);
    private final List<Walker> walkers = new ArrayList<>();
    private final List<int[]> corners = new ArrayList<>();
    private final List<int[]> ways = new ArrayList<>(6);
    private Game game;
    private double recount;

    void clear() {
        walkers.clear();
        corners.clear();
        game = null;
    }

    List<Walker> walkers() {
        return Collections.unmodifiableList(walkers);
    }

    void update(double seconds, Game game) {
        if (game != this.game) {
            clear();
            this.game = game;
            recount = 0;
        }
        recount -= seconds;
        if (recount <= 0) {
            recount = 1;
            corners.clear();
            for (int row = 0; row < game.height(); row++) {
                for (int col = 0; col < game.width(); col++) {
                    for (int k = 0; k < 4; k++) if (pavement(col, row, k)) corners.add(new int[]{col, row, k});
                }
            }
            int wanted = Math.min(MOST, Math.min(corners.size() / 4, game.people() == 0 ? 0 : 2 + game.people() / PEOPLE_PER_WALKER));
            for (int i = walkers.size(); i < wanted && !corners.isEmpty(); i++) spawn();
            if (walkers.size() > wanted) walkers.removeLast();
        }
        for (Iterator<Walker> it = walkers.iterator(); it.hasNext(); ) {
            Walker w = it.next();
            if (!walk(w, seconds)) it.remove();
        }
    }

    private boolean road(int col, int row) {
        return game.road(col, row) && !game.highway(col, row);
    }

    // --8<-- [start:pavement]
    /** Whether a corner of a road tile is pavement: not where the roads on both its sides and between them meet. */
    boolean pavement(int col, int row, int corner) {
        if (!road(col, row)) return false;
        int dx = corner == 1 || corner == 2 ? 1 : -1;
        int dy = corner >= 2 ? 1 : -1;
        return !(game.road(col + dx, row) && game.road(col, row + dy) && game.road(col + dx, row + dy));
    }
    // --8<-- [end:pavement]

    private void spawn() {
        int[] at = corners.get(random.nextInt(corners.size()));
        Walker w = new Walker(random.nextInt(Pictures.PEOPLE));
        w.col = at[0];
        w.row = at[1];
        w.corner = at[2];
        if (!next(w, -1, -1, -1)) return;
        w.along = random.nextDouble() * w.length;
        w.step = random.nextDouble() * Pictures.STEPS;
        place(w);
        walkers.add(w);
    }

    /** Moves a walker along; false when the pavement under it has gone. */
    private boolean walk(Walker w, double seconds) {
        w.along += SPEED * seconds;
        w.step += seconds * 6;
        while (w.along >= w.length) {
            w.along -= w.length;
            int fromCol = w.col, fromRow = w.row, fromCorner = w.corner;
            w.col = w.toCol;
            w.row = w.toRow;
            w.corner = w.toCorner;
            if (!pavement(w.col, w.row, w.corner) || !next(w, fromCol, fromRow, fromCorner)) return false;
        }
        if (!pavement(w.toCol, w.toRow, w.toCorner)) return false;
        place(w);
        return true;
    }

    // --8<-- [start:next]
    /**
     * Chooses where a walker goes from its corner: to the next corner of the same tile, which is along the
     * pavement or across a street, or to the touching corner of a neighbouring road tile. It does not turn
     * back the way it came, unless that is the only way.
     */
    private boolean next(Walker w, int fromCol, int fromRow, int fromCorner) {
        ways.clear();
        int c = w.col, r = w.row, k = w.corner;
        for (int turn : new int[]{1, 3}) {
            int other = (k + turn) % 4;
            // The side of the tile between the two corners: 0 is -y, 1 is +x, 2 is +y, 3 is -x.
            int side = turn == 1 ? k : other;
            int sx = side == 1 ? 1 : side == 3 ? -1 : 0;
            int sy = side == 2 ? 1 : side == 0 ? -1 : 0;
            boolean across = game.road(c + sx, r + sy);
            if (pavement(c, r, other) && (!across || random.nextDouble() < CROSS)) ways.add(new int[]{c, r, other});
        }
        // The touching corners of the neighbours: the same corner mirrored across the shared edge.
        int dx = k == 1 || k == 2 ? 1 : -1;
        int dy = k >= 2 ? 1 : -1;
        int mirrorX = k ^ 1;
        int mirrorY = 3 - k;
        if (pavement(c + dx, r, mirrorX)) ways.add(new int[]{c + dx, r, mirrorX});
        if (pavement(c, r + dy, mirrorY)) ways.add(new int[]{c, r + dy, mirrorY});
        int[] back = null;
        for (Iterator<int[]> it = ways.iterator(); it.hasNext(); ) {
            int[] way = it.next();
            if (way[0] == fromCol && way[1] == fromRow && way[2] == fromCorner) {
                back = way;
                it.remove();
            }
        }
        if (ways.isEmpty() && back != null) ways.add(back);
        if (ways.isEmpty()) return false;
        int[] way = ways.get(random.nextInt(ways.size()));
        w.toCol = way[0];
        w.toRow = way[1];
        w.toCorner = way[2];
        w.length = Math.max(0.01, Math.hypot(cornerX(w.toCol, w.toCorner) - cornerX(w.col, w.corner),
                cornerY(w.toRow, w.toCorner) - cornerY(w.row, w.corner)));
        return true;
    }
    // --8<-- [end:next]

    private static double cornerX(int col, int corner) {
        return col * Field.TILE + CORNER_X[corner];
    }

    private static double cornerY(int row, int corner) {
        return row * Field.TILE + CORNER_Y[corner];
    }

    private static void place(Walker w) {
        double f = w.along / w.length;
        w.x = cornerX(w.col, w.corner) + (cornerX(w.toCol, w.toCorner) - cornerX(w.col, w.corner)) * f;
        w.y = cornerY(w.row, w.corner) + (cornerY(w.toRow, w.toCorner) - cornerY(w.row, w.corner)) * f;
    }
}
