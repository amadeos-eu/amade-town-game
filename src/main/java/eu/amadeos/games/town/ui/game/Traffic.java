package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.town.domain.Building;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The cars on the streets, and the fire trucks. The rules know nothing of them: the cars only show that the
 * town lives, more of them the more people it has, driving on the roads that lead to the highway, keeping
 * to the right and turning at random at the crossings. A fire truck drives from its station to the fire by
 * the shortest way along the roads and waits there until the fire is out.
 *
 * <p>On a tile a car follows its lane: in along one side of the road, out along the side of the way it
 * turns, around the point where the two lanes cross. Everything is in world units.
 */
// --8<-- [start:traffic]
final class Traffic {
    /** The directions: along +x, -x, +y, -y. */
    static final int[] DX = {1, -1, 0, 0};
    static final int[] DY = {0, 0, 1, -1};
    /** Where each direction's lane runs across a tile: along x at this y, along y at this x. */
    static final double[] LANE = {11.2, 4.8, 4.8, 11.2};
    static final double SPEED = 24;
    static final double TRUCK_SPEED = 40;
    static final int MOST = 140;
    /** One car for this many people, besides a few that drive anyway. */
    static final int PEOPLE_PER_CAR = 25;

    /** A car on its way across a tile: from {@code in}, out to {@code out}, {@code along} its path. */
    static final class Car {
        int col;
        int row;
        int in;
        int out;
        double along;
        double length;
        final double[] path = new double[8];
        int points;
        final int kind;
        final double speed;
        /** A fire truck's way, the fire it goes to; empty for a car. */
        final ArrayDeque<Cell> route = new ArrayDeque<>();
        int fireCol = -1;
        int fireRow = -1;
        boolean parked;
        double x;
        double y;
        int facing;

        Car(int kind, double speed) {
            this.kind = kind;
            this.speed = speed;
        }

        boolean truck() {
            return fireCol >= 0;
        }

        double x() {
            return x;
        }

        double y() {
            return y;
        }

        int facing() {
            return facing;
        }

        int kind() {
            return kind;
        }
    }
    // --8<-- [end:traffic]

    private final SeededRandom random = new SeededRandom(5);
    private final List<Car> cars = new ArrayList<>();
    private final List<Cell> streets = new ArrayList<>();
    private Game game;
    private double recount;

    void clear() {
        cars.clear();
        streets.clear();
        game = null;
    }

    List<Car> cars() {
        return Collections.unmodifiableList(cars);
    }

    // --8<-- [start:update]
    void update(double seconds, Game game) {
        if (game != this.game) {
            clear();
            this.game = game;
            recount = 0;
        }
        recount -= seconds;
        if (recount <= 0) {
            recount = 1;
            streets.clear();
            for (int row = 0; row < game.height(); row++) {
                for (int col = 0; col < game.width(); col++) if (game.linked(col, row)) streets.add(new Cell(col, row));
            }
            int wanted = Math.min(MOST, Math.min(streets.size() / 3, 3 + game.people() / PEOPLE_PER_CAR));
            long count = cars.stream().filter(c -> !c.truck()).count();
            for (long i = count; i < wanted && !streets.isEmpty(); i++) spawn();
            if (count > wanted) removeOne();
        }
        for (Iterator<Car> it = cars.iterator(); it.hasNext(); ) {
            Car car = it.next();
            if (!drive(car, seconds)) it.remove();
        }
    }
    // --8<-- [end:update]

    private void spawn() {
        Cell start = streets.get(random.nextInt(streets.size()));
        double roll = random.nextDouble();
        int kind = roll < 0.06 ? Pictures.BUS : roll < 0.14 ? Pictures.LORRY : random.nextInt(Art.CAR_COLOURS.length);
        Car car = new Car(kind, SPEED * (0.8 + random.nextDouble() * 0.4));
        List<Integer> ways = ways(start.col(), start.row(), -1);
        if (ways.isEmpty()) return;
        car.col = start.col();
        car.row = start.row();
        car.in = ways.get(random.nextInt(ways.size()));
        car.out = choose(car.col, car.row, car.in);
        enter(car);
        car.along = random.nextDouble() * car.length;
        place(car);
        cars.add(car);
    }

    private void removeOne() {
        for (int i = cars.size() - 1; i >= 0; i--) {
            if (!cars.get(i).truck()) {
                cars.remove(i);
                return;
            }
        }
    }

    // --8<-- [start:truck]
    /** A truck leaves the station for the fire, if a road leads there. */
    void sendTruck(Building station, int fireCol, int fireRow) {
        if (game == null) return;
        Cell from = null;
        for (int row = station.row() - 1; row <= station.row() + station.height() && from == null; row++) {
            for (int col = station.col() - 1; col <= station.col() + station.width() && from == null; col++) {
                if (game.road(col, row)) from = new Cell(col, row);
            }
        }
        if (from == null) return;
        List<Cell> way = way(from, fireCol, fireRow);
        if (way.isEmpty()) return;
        Car truck = new Car(Pictures.FIRE_TRUCK, TRUCK_SPEED);
        truck.fireCol = fireCol;
        truck.fireRow = fireRow;
        truck.route.addAll(way);
        Cell first = truck.route.removeFirst();
        truck.col = first.col();
        truck.row = first.row();
        Cell next = truck.route.peekFirst();
        truck.in = next == null ? 0 : direction(first, next);
        truck.out = truck.in;
        enter(truck);
        place(truck);
        cars.add(truck);
    }

    /** The shortest way along the roads from a tile to one next to the fire. */
    private List<Cell> way(Cell from, int col, int row) {
        int w = game.width();
        int h = game.height();
        int[] previous = new int[w * h];
        java.util.Arrays.fill(previous, -2);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = from.row() * w + from.col();
        previous[start] = -1;
        queue.add(start);
        while (!queue.isEmpty()) {
            int i = queue.removeFirst();
            int c = i % w;
            int r = i / w;
            if (Math.abs(c - col) <= 1 && Math.abs(r - row) <= 1) {
                List<Cell> way = new ArrayList<>();
                for (int k = i; k >= 0; k = previous[k]) way.add(new Cell(k % w, k / w));
                Collections.reverse(way);
                return way;
            }
            for (int d = 0; d < 4; d++) {
                int nc = c + DX[d];
                int nr = r + DY[d];
                if (!game.road(nc, nr) || previous[nr * w + nc] != -2) continue;
                previous[nr * w + nc] = i;
                queue.add(nr * w + nc);
            }
        }
        return List.of();
    }
    // --8<-- [end:truck]

    private static int direction(Cell a, Cell b) {
        if (b.col() > a.col()) return 0;
        if (b.col() < a.col()) return 1;
        if (b.row() > a.row()) return 2;
        return 3;
    }

    /** The roads that go on from a tile, without turning back the way the car came, if it can help it. */
    private List<Integer> ways(int col, int row, int in) {
        List<Integer> ways = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            if (in >= 0 && d == (in ^ 1)) continue;
            int nc = col + DX[d];
            int nr = row + DY[d];
            if (game.linked(nc, nr) || game.highway(col, row) && !game.contains(nc, nr)) ways.add(d);
        }
        return ways;
    }

    private int choose(int col, int row, int in) {
        List<Integer> ways = ways(col, row, in);
        if (ways.isEmpty()) return in ^ 1;
        // Straight on more often than round the corner.
        if (ways.contains(in) && random.nextDouble() < 0.55) return in;
        return ways.get(random.nextInt(ways.size()));
    }

    // --8<-- [start:drive]
    /** Moves a car along; false when it has left the roads or the map, or a truck's fire is out. */
    private boolean drive(Car car, double seconds) {
        if (car.truck()) {
            if (!game.burning(car.fireCol, car.fireRow)) return false;
            if (car.parked) return true;
        }
        car.along += car.speed * seconds;
        while (car.along >= car.length) {
            car.along -= car.length;
            car.col += DX[car.out];
            car.row += DY[car.out];
            car.in = car.out;
            if (car.truck()) {
                if (car.route.isEmpty()) {
                    car.parked = true;
                    car.along = car.length / 2;
                    car.col -= DX[car.out];
                    car.row -= DY[car.out];
                    break;
                }
                car.route.removeFirst();
                Cell next = car.route.peekFirst();
                car.out = next == null ? car.in : direction(new Cell(car.col, car.row), next);
            } else {
                if (!game.contains(car.col, car.row) || !game.road(car.col, car.row)) return false;
                car.out = choose(car.col, car.row, car.in);
            }
            enter(car);
        }
        if (!car.truck() && !game.road(car.col, car.row)) return false;
        place(car);
        return true;
    }
    // --8<-- [end:drive]

    /** The path across the tile: in along the lane of {@code in}, round the corner, out along the lane of {@code out}. */
    private static void enter(Car car) {
        double[] p = car.path;
        int in = car.in;
        int out = car.out;
        double ox = car.col * Field.TILE;
        double oy = car.row * Field.TILE;
        double t = Field.TILE;
        // Where the car comes in and goes out, on the edges of the tile.
        double inX = in == 0 ? 0 : in == 1 ? t : LANE[in];
        double inY = in == 2 ? 0 : in == 3 ? t : LANE[in];
        double outX = out == 0 ? t : out == 1 ? 0 : LANE[out];
        double outY = out == 2 ? t : out == 3 ? 0 : LANE[out];
        int n = 0;
        p[n++] = ox + inX;
        p[n++] = oy + inY;
        boolean inAlongX = in < 2;
        boolean outAlongX = out < 2;
        if (inAlongX != outAlongX) {
            p[n++] = ox + (inAlongX ? LANE[out] : LANE[in]);
            p[n++] = oy + (inAlongX ? LANE[in] : LANE[out]);
        } else if (in != out) {
            // Turning back at a dead end: across the road in the middle of the tile.
            p[n++] = ox + (inAlongX ? t / 2 : LANE[in]);
            p[n++] = oy + (inAlongX ? LANE[in] : t / 2);
            p[n++] = ox + (inAlongX ? t / 2 : LANE[out]);
            p[n++] = oy + (inAlongX ? LANE[out] : t / 2);
        }
        p[n++] = ox + outX;
        p[n++] = oy + outY;
        car.points = n / 2;
        car.length = 0;
        for (int i = 1; i < car.points; i++) car.length += Math.hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1]);
        car.length = Math.max(car.length, 0.01);
    }

    private static void place(Car car) {
        double[] p = car.path;
        double left = car.along;
        for (int i = 1; i < car.points; i++) {
            double dx = p[2 * i] - p[2 * i - 2];
            double dy = p[2 * i + 1] - p[2 * i - 1];
            double segment = Math.hypot(dx, dy);
            if (left <= segment || i == car.points - 1) {
                double f = segment == 0 ? 0 : Math.min(1, left / segment);
                car.x = p[2 * i - 2] + dx * f;
                car.y = p[2 * i - 1] + dy * f;
                if (Math.abs(dx) > Math.abs(dy)) car.facing = dx > 0 ? 0 : 1;
                else if (dy != 0) car.facing = dy > 0 ? 2 : 3;
                return;
            }
            left -= segment;
        }
    }
}
