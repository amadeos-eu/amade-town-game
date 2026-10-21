package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.town.domain.BuildingType;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Rules;
import eu.amadeos.games.town.domain.Work;
import eu.amadeos.games.town.domain.Zone;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * The pictures of the town as sprites, each drawn once at its size on the screen and then only copied: the
 * ground tiles, the lots and the buildings, trees, pylons, cars, flames and smoke, the signs and the icons.
 *
 * <p>A ground tile is worked out pixel by pixel, from noise, at the screen's own resolution. The meadow, the
 * forest floor and the water use noise that repeats every {@value #PERIOD} tiles, and there is a picture for
 * each tile of that square: put side by side, they make one meadow without a seam, and the eye does not find
 * the repeat among the trees and houses. Each picture reaches a little over its diamond, so neighbours
 * overlap and no gap shows at any scale.
 *
 * <p>Everything that stands is drawn with an {@link IsoProjection} of its own, {@code IsoProjection.at},
 * whose origin is the top corner of its tile inside the sprite.
 */
// --8<-- [start:pictures]
final class Pictures {
    /** The meadow repeats every this many tiles. */
    static final int PERIOD = 8;
    /** A ground tile's sprite: the diamond and a unit around it; the tile's top corner sits at (17, 1). */
    static final double FLOOR_WIDTH = Field.TILE_WIDTH + 2;
    static final double FLOOR_HEIGHT = Field.TILE_HEIGHT + 2;
    static final double FLOOR_X = Field.TILE_WIDTH / 2.0 + 1;
    static final double FLOOR_Y = 1;
    /** How far a ground tile reaches over its edges, in world units. */
    static final double OVERLAP = 0.8;
    static final double[] TREE_SIZES = {0.5, 0.62, 0.75};
    static final int TREE_SHAPES = 4;
    static final int FLAMES = 6;
    static final double[] PUFFS = {2, 3, 4.5, 6, 8};
    /** A vehicle's sprite, its middle on the ground at ({@link #CAR_X}, {@link #CAR_Y}). */
    static final double CAR_X = 11;
    static final double CAR_Y = 11;
    static final int BUS = Art.CAR_COLOURS.length;
    static final int LORRY = BUS + 1;
    static final int FIRE_TRUCK = BUS + 2;

    /** The ground of a tile. */
    enum Floor { GRASS, FOREST, WATER, LAWN, PAVED, CONCRETE, YARD, RUBBLE }

    /** Gives the colour of a point of a tile, {@code 0xAARRGGBB}, from its world units inside the tile. */
    @FunctionalInterface
    interface Shader {
        int argb(double x, double y);
    }

    private final Map<Floor, Sprite[]> floors = new EnumMap<>(Floor.class);
    /** By the roads around (1 towards -y, 2 +x, 4 +y, 8 -x), plus 16 on the highway. */
    private final Sprite[] roads = new Sprite[32];
    /** The bank where a water tile meets the land: four sides (-y, +x, +y, -x), then four corners. */
    final Sprite[] banks = new Sprite[8];
    /** The pavement at a corner of a crossing: (-x, -y), (+x, -y), (+x, +y), (-x, +y). */
    final Sprite[] corners = new Sprite[4];
    /** By zone, level, variant and whether a street runs along the front (1) or not (0). */
    private final Sprite[][][][] lots = new Sprite[Zone.values().length][4][Lots.VARIANTS][2];
    private final Lots.Look[][][] looks = new Lots.Look[Zone.values().length][4][Lots.VARIANTS];
    private final Map<Work, Sprite> structures = new EnumMap<>(Work.class);
    private final Map<Work, Sprite> yards = new EnumMap<>(Work.class);
    private final Map<Work, Structures.Look> structureLooks = new EnumMap<>(Work.class);
    /** By kind (0 broadleaf, 1 conifer), size and shape. */
    private final Sprite[][][] trees = new Sprite[2][TREE_SIZES.length][TREE_SHAPES];
    final Sprite[] pylons = new Sprite[2];
    /** By colour (then bus, lorry, fire truck) and direction. */
    private final Sprite[][] vehicles = new Sprite[Art.CAR_COLOURS.length + 3][4];
    /** The people walking, by look and step. */
    final Sprite[][] people = new Sprite[PEOPLE][STEPS];
    final Sprite[] flames = new Sprite[FLAMES];
    final Sprite[] puffs = new Sprite[PUFFS.length];
    final Sprite[] darkPuffs = new Sprite[PUFFS.length];
    final Sprite[] steam = new Sprite[PUFFS.length];
    final Sprite rubble;
    final Sprite noPower;
    final Sprite noWater;
    final Sprite[] signs = new Sprite[Zone.values().length];
    // --8<-- [end:pictures]

    Pictures(Rules rules) {
        for (Floor floor : Floor.values()) {
            Sprite[] variants = new Sprite[PERIOD * PERIOD];
            for (int v = 0; v < variants.length; v++) {
                double ox = (v % PERIOD) * Field.TILE;
                double oy = (v / PERIOD) * Field.TILE;
                variants[v] = floor((x, y) -> ground(floor, ox + x, oy + y));
            }
            floors.put(floor, variants);
        }
        for (int mask = 0; mask < roads.length; mask++) {
            int m = mask & 15;
            boolean highway = mask >= 16;
            roads[mask] = floor((x, y) -> road(x, y, m, highway));
        }
        for (int i = 0; i < banks.length; i++) {
            int side = i;
            banks[i] = floor((x, y) -> bank(x, y, side));
        }
        for (int i = 0; i < corners.length; i++) {
            int corner = i;
            corners[i] = floor((x, y) -> corner(x, y, corner));
        }
        for (Zone zone : Zone.values()) {
            for (int level = 1; level <= 3; level++) {
                for (int v = 0; v < Lots.VARIANTS; v++) {
                    int lv = level;
                    int variant = v;
                    double tall = Lots.tall(zone, level);
                    looks[zone.ordinal()][level][v] = Lots.look(zone, level, v);
                    for (int street = 0; street < 2; street++) {
                        boolean front = street == 1;
                        lots[zone.ordinal()][level][v][street] = front && !Lots.parks(zone, level, v)
                                ? lots[zone.ordinal()][level][v][0]
                                : Sprite.drawn(FLOOR_WIDTH, tall + FLOOR_HEIGHT, (g, w, h) -> {
                                    Art.smooth(g);
                                    Lots.draw(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, FLOOR_X,
                                            tall + FLOOR_Y), zone, lv, variant, front);
                                });
                    }
                }
            }
            signs[zone.ordinal()] = Sprite.drawn(10, 12, (g, w, h) -> {
                Art.smooth(g);
                Lots.sign(g, 2, 10.5, zoneColour(zone));
            });
        }
        for (BuildingType type : rules.buildings()) {
            Work work = type.work();
            if (structures.containsKey(work)) continue;
            int across = type.width() + type.height();
            Structures.Look look = Structures.look(work);
            structureLooks.put(work, look);
            double originX = type.height() * Field.TILE_WIDTH / 2.0 + STRUCTURE_SIDE;
            structures.put(work, Sprite.drawn(across * Field.TILE_WIDTH / 2.0 + 2 * STRUCTURE_SIDE,
                    look.tall() + across * Field.TILE_HEIGHT / 2.0 + 4, (g, w, h) -> {
                        Art.smooth(g);
                        Structures.draw(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, originX,
                                look.tall() + 2), work);
                    }));
            yards.put(work, Sprite.drawn(across * Field.TILE_WIDTH / 2.0 + 2, across * Field.TILE_HEIGHT / 2.0 + 2, (g, w, h) -> {
                Art.smooth(g);
                Structures.yard(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT,
                        type.height() * Field.TILE_WIDTH / 2.0 + 1, 1), work, type.width(), type.height());
            }));
        }
        for (int kind = 0; kind < 2; kind++) {
            for (int size = 0; size < TREE_SIZES.length; size++) {
                for (int shape = 0; shape < TREE_SHAPES; shape++) {
                    boolean conifer = kind == 1;
                    double s = TREE_SIZES[size];
                    long seed = shape * 977L + size * 31L + kind * 7L;
                    trees[kind][size][shape] = Sprite.drawn(TREE_WIDTH, TREE_HEIGHT, (g, w, h) -> {
                        Art.smooth(g);
                        Art.tree(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, TREE_X, TREE_Y), 0, 0,
                                conifer, s, seed);
                    });
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            boolean alongX = i == 1;
            pylons[i] = Sprite.drawn(16, 24, (g, w, h) -> {
                Art.smooth(g);
                Art.pylon(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, PYLON_X, PYLON_Y), 0, 0, alongX);
            });
        }
        for (int kind = 0; kind < vehicles.length; kind++) {
            for (int direction = 0; direction < 4; direction++) {
                int k = kind;
                int d = direction;
                vehicles[kind][direction] = Sprite.drawn(2 * CAR_X, CAR_Y + 5, (g, w, h) -> {
                    Art.smooth(g);
                    IsoProjection p = IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, CAR_X, CAR_Y);
                    if (k < BUS) Art.car(g, p, 0, 0, d, Art.CAR_COLOURS[k]);
                    else if (k == BUS) Art.bus(g, p, 0, 0, d);
                    else if (k == LORRY) Art.lorry(g, p, 0, 0, d);
                    else Art.fireTruck(g, p, 0, 0, d);
                });
            }
        }
        for (int look = 0; look < PEOPLE; look++) {
            for (int step = 0; step < STEPS; step++) {
                int l = look;
                int st = step;
                people[look][step] = Sprite.drawn(2 * PERSON_X, PERSON_Y + 1, (g, w, h) -> {
                    Art.smooth(g);
                    Art.person(g, PERSON_X, PERSON_Y, l, st);
                });
            }
        }
        for (int i = 0; i < FLAMES; i++) {
            int frame = i;
            flames[i] = Sprite.drawn(FLAME_WIDTH, FLAME_HEIGHT, (g, w, h) -> {
                Art.smooth(g);
                Art.flames(g, FLAME_X, FLAME_Y, frame);
            });
        }
        for (int i = 0; i < PUFFS.length; i++) {
            double r = PUFFS[i];
            puffs[i] = puff(r, new Color(206, 204, 198, 200), new Color(150, 150, 150, 0));
            darkPuffs[i] = puff(r, new Color(70, 64, 60, 220), new Color(40, 36, 34, 0));
            steam[i] = puff(r * 1.3, new Color(255, 255, 255, 220), new Color(225, 228, 232, 0));
        }
        rubble = Sprite.drawn(FLOOR_WIDTH, FLOOR_HEIGHT + 6, (g, w, h) -> {
            Art.smooth(g);
            Art.rubble(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, FLOOR_X, FLOOR_Y + 6), 8, 8, 5);
        });
        noPower = Sprite.drawn(12, 12, (g, w, h) -> {
            Art.smooth(g);
            Art.noPower(g, 6, 6);
        });
        noWater = Sprite.drawn(12, 12, (g, w, h) -> {
            Art.smooth(g);
            Art.noWater(g, 6, 6);
        });
    }

    static final double STRUCTURE_SIDE = 6;
    static final double TREE_WIDTH = 28;
    static final double TREE_HEIGHT = 28;
    static final double TREE_X = 14;
    static final double TREE_Y = 24;
    static final int PEOPLE = Art.SHIRTS.length;
    static final int STEPS = 4;
    /** A walker's sprite, its feet at ({@link #PERSON_X}, {@link #PERSON_Y}). */
    static final double PERSON_X = 3;
    static final double PERSON_Y = 8;
    static final double PYLON_X = 8;
    static final double PYLON_Y = 21;
    static final double FLAME_WIDTH = 22;
    static final double FLAME_HEIGHT = 20;
    static final double FLAME_X = 10;
    static final double FLAME_Y = 17;

    static int zoneColour(Zone zone) {
        return switch (zone) {
            case RESIDENTIAL -> Art.ZONE_R;
            case COMMERCIAL -> Art.ZONE_C;
            case INDUSTRIAL -> Art.ZONE_I;
        };
    }

    // ---- what the painter asks -------------------------------------------------------------------

    /** The ground picture of a tile of a floor: the one for its place in the repeating square. */
    Sprite floor(Floor floor, int col, int row) {
        return floors.get(floor)[Math.floorMod(row, PERIOD) * PERIOD + Math.floorMod(col, PERIOD)];
    }

    Sprite road(int mask, boolean highway) {
        return roads[(mask & 15) + (highway ? 16 : 0)];
    }

    Sprite lot(Zone zone, int level, int variant, boolean street) {
        return lots[zone.ordinal()][level][variant][street ? 1 : 0];
    }

    Lots.Look look(Zone zone, int level, int variant) {
        return looks[zone.ordinal()][level][variant];
    }

    Sprite structure(Work work) {
        return structures.get(work);
    }

    Sprite yard(Work work) {
        return yards.get(work);
    }

    Structures.Look look(Work work) {
        return structureLooks.get(work);
    }

    Sprite tree(boolean conifer, int size, int shape) {
        return trees[conifer ? 1 : 0][size][shape];
    }

    Sprite vehicle(int kind, int direction) {
        return vehicles[kind][direction];
    }

    // ---- ground tiles ----------------------------------------------------------------------------

    // --8<-- [start:floor]
    /**
     * A ground tile, worked out pixel by pixel at the size it has on the screen: each pixel of the sprite is
     * taken back to its point of the tile and coloured by {@code shader}. Pixels outside the diamond and its
     * overlap stay clear.
     */
    static Sprite floor(Shader shader) {
        return Sprite.drawn(FLOOR_WIDTH, FLOOR_HEIGHT, (g, w, h) -> {
            AffineTransform t = g.getTransform();
            double sx = t.getScaleX();
            double sy = t.getScaleY();
            int pw = Math.max(1, (int) Math.round(w * sx));
            int ph = Math.max(1, (int) Math.round(h * sy));
            BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            double half = Field.TILE / (double) Field.TILE_WIDTH * 2;
            for (int py = 0; py < ph; py++) {
                for (int px = 0; px < pw; px++) {
                    double dx = (px + 0.5) / sx - FLOOR_X;
                    double dy = (py + 0.5) / sy - FLOOR_Y;
                    // The way back from the picture to the ground: x - y = dx, x + y = 2 dy, in world units.
                    double x = (dy * 2 + dx) / 2 * half;
                    double y = (dy * 2 - dx) / 2 * half;
                    if (x < -OVERLAP || y < -OVERLAP || x > Field.TILE + OVERLAP || y > Field.TILE + OVERLAP) continue;
                    image.setRGB(px, py, shader.argb(Math.clamp(x, 0, Field.TILE - 1e-6), Math.clamp(y, 0, Field.TILE - 1e-6)));
                }
            }
            g.transform(AffineTransform.getScaleInstance(1 / sx, 1 / sy));
            g.drawImage(image, 0, 0, null);
            g.setTransform(t);
        });
    }
    // --8<-- [end:floor]

    // --8<-- [start:ground]
    /** The colour of a floor at a point of the repeating square, {@code PERIOD} tiles a side. */
    static int ground(Floor floor, double x, double y) {
        int p = PERIOD;
        double fine = Art.fbm(x / 4, y / 4, p * 4, 9);
        double speck = Art.hash((int) Math.floor(x * 3), (int) Math.floor(y * 3), 7);
        double lit = 0.95 + (Art.fbm(x / 64, y / 64, p / 4, 5) - 0.5) * 0.18;
        int c = switch (floor) {
            case GRASS, FOREST -> {
                double n = Art.fbm(x / 32, y / 32, p / 2, 1);
                int grass = Art.mix(0x548A30, 0x86AE48, n * 1.4 - 0.2);
                grass = Art.mix(grass, 0x386622, (fine - 0.5) * 0.9);
                grass = Art.mix(grass, speck < 0.5 ? 0x2C5018 : 0x9AC260, speck * 0.16);
                if (floor == Floor.FOREST) {
                    grass = Art.mix(grass, 0x2C5418, 0.55);
                    grass = Art.mix(grass, 0x1E3A12, (fine - 0.4) * 0.8);
                } else {
                    grass = flower(grass, x, y);
                }
                yield grass;
            }
            case WATER -> {
                double n = Art.fbm(x / 32, y / 32, p / 2, 19);
                int water = Art.mix(0x3A7488, 0x24546E, n * 1.2 - 0.1);
                double ripple = Math.sin(y * Math.PI * 2 * 24 / (p * Field.TILE) + Art.fbm(x / 8, y / 8, p * 2, 23) * 7);
                water = Art.mix(water, 0x9CD0DA, Art.smoothstep(0.78, 1, ripple) * 0.32);
                yield Art.mix(water, 0x1E4A60, speck * 0.08);
            }
            case LAWN -> {
                int lawn = Art.mix(0x5E9A36, 0x7AB244, Art.fbm(x / 32, y / 32, p / 2, 3));
                if (Math.floorMod((int) Math.floor(x / 4), 2) == 0) lawn = Art.shade(lawn, 1.06);
                yield Art.mix(lawn, speck < 0.5 ? 0x3A6A20 : 0x9ACC66, speck * 0.12);
            }
            case PAVED, YARD -> {
                int base = floor == Floor.PAVED ? 0xB8B3A6 : 0xAEAAA0;
                int paved = Art.mix(base, Art.shade(base, 0.86), fine);
                if ((x + 0.25) % 8 < 0.5 || (y + 0.25) % 8 < 0.5) paved = Art.shade(paved, 0.86);
                yield Art.mix(paved, speck < 0.5 ? 0x7A766C : 0xD8D4C8, speck * 0.16);
            }
            case CONCRETE -> {
                int concrete = Art.mix(0x9C988C, Art.shade(0x9C988C, 0.82), fine);
                concrete = Art.mix(concrete, 0x5E5A50, Art.smoothstep(0.6, 0.78, Art.fbm(x / 16, y / 16, p, 43)) * 0.5);
                if ((x + 0.25) % 16 < 0.5 || (y + 0.25) % 16 < 0.5) concrete = Art.shade(concrete, 0.84);
                yield Art.mix(concrete, speck < 0.5 ? 0x5A564E : 0xC8C4B8, speck * 0.22);
            }
            case RUBBLE -> {
                int dirt = Art.mix(0x8A7A64, 0x6A5A48, fine);
                yield Art.mix(dirt, speck < 0.5 ? 0x3A3028 : 0xA89878, speck * 0.3);
            }
        };
        return 0xFF000000 | Art.shade(c, lit);
    }

    /** Now and then a flower in the meadow: white, yellow, lilac or red. */
    private static int flower(int grass, double x, double y) {
        int cx = (int) Math.floor(x / 2);
        int cy = (int) Math.floor(y / 2);
        int period = PERIOD * Field.TILE / 2;
        double h = Art.hash(Math.floorMod(cx, period), Math.floorMod(cy, period), 11);
        if (h > 0.014) return grass;
        double d = Math.hypot(x - cx * 2 - 1, y - cy * 2 - 1);
        if (d > 0.55) return grass;
        int[] colours = {0xF2ECD8, 0xE8C84A, 0xC8A0E0, 0xE87A6A};
        return colours[(int) (h / 0.014 * 3.99)];
    }
    // --8<-- [end:ground]

    // --8<-- [start:road]
    /** A road tile with roads on the sides of {@code mask}: asphalt, a pavement where no road goes on, the middle line. */
    static int road(double x, double y, int mask, boolean highway) {
        double fine = Art.fbm(x / 4, y / 4, 4, 9 + mask);
        double speck = Art.hash((int) Math.floor(x * 3), (int) Math.floor(y * 3), 13 + mask);
        boolean up = (mask & 1) != 0, right = (mask & 2) != 0, down = (mask & 4) != 0, left = (mask & 8) != 0;
        boolean side = x < 2 && !left || x >= 14 && !right || y < 2 && !up || y >= 14 && !down;
        int c;
        if (side) {
            c = Art.mix(0xC4BFB2, 0xA8A398, fine);
            if ((x + 0.25) % 4 < 0.5 || (y + 0.25) % 4 < 0.5) c = Art.shade(c, 0.88);
            c = Art.mix(c, speck < 0.5 ? 0x8A867C : 0xDAD6CA, speck * 0.2);
        } else {
            c = Art.mix(highway ? 0x46484C : 0x4E5054, 0x3C3E42, fine);
            c = Art.mix(c, 0x5E5A52, Art.smoothstep(0.62, 0.78, Art.fbm(x / 8, y / 8, 2, 41 + mask)) * 0.4);
            c = Art.mix(c, speck < 0.5 ? 0x2A2C30 : 0x6A6C70, speck * 0.3);
            // The far kerbs throw a thin shadow onto the road.
            if (x >= 2 && x < 2.7 && !left || y >= 2 && y < 2.7 && !up) c = Art.shade(c, 0.7);
            boolean alongX = (left || right) && !up && !down;
            boolean alongY = (up || down) && !left && !right;
            double across = alongX ? y : x;
            double along = alongX ? x : y;
            if (alongX || alongY) {
                if (highway) {
                    if (Math.abs(across - 7.5) < 0.3 || Math.abs(across - 8.5) < 0.3) c = Art.mix(c, 0xE8C040, 0.85);
                    if (Math.abs(across - 2.9) < 0.25 || Math.abs(across - 13.1) < 0.25) c = Art.mix(c, 0xE8E4DC, 0.75);
                } else if (Math.abs(across - 8) < 0.4 && along % 8 < 4) {
                    c = Art.mix(c, 0xF0E6BE, 0.8);
                }
            }
        }
        return 0xFF000000 | c;
    }
    // --8<-- [end:road]

    /** The pavement at one corner of a crossing, where the diagonal neighbour is no road. */
    static int corner(double x, double y, int corner) {
        boolean near = switch (corner) {
            case 0 -> x < 2 && y < 2;
            case 1 -> x >= 14 && y < 2;
            case 2 -> x >= 14 && y >= 14;
            default -> x < 2 && y >= 14;
        };
        return near ? road(x, y, 0, false) : 0;
    }

    /** The bank where water meets the land on one side or at one corner: sand, then shallow water fading out. */
    static int bank(double x, double y, int side) {
        double d = switch (side) {
            case 0 -> y;
            case 1 -> Field.TILE - x;
            case 2 -> Field.TILE - y;
            case 3 -> x;
            case 4 -> Math.hypot(x, y);
            case 5 -> Math.hypot(Field.TILE - x, y);
            case 6 -> Math.hypot(Field.TILE - x, Field.TILE - y);
            default -> Math.hypot(x, Field.TILE - y);
        };
        double wobble = (Art.noise(x / 3, y / 3, 41) - 0.5) * 1.6;
        d += wobble;
        int sand = Art.mix(0x9A8C66, 0xBCAE84, Art.noise(x / 1.5, y / 1.5, 17));
        if (d < 2.4) return 0xFF000000 | sand;
        if (d < 3.6) return 0xFF000000 | Art.mix(sand, 0x5E9AAA, (d - 2.4) / 1.2);
        if (d < 7) return ((int) (200 * (1 - (d - 3.6) / 3.4)) << 24) | 0x5E9AAA;
        return 0;
    }

    // ---- small pictures --------------------------------------------------------------------------

    private static Sprite puff(double r, Color inner, Color outer) {
        return Sprite.drawn(2 * r + 2, 2 * r + 2, (g, w, h) -> {
            Art.smooth(g);
            g.setPaint(Art.radial(new Point2D.Double(r + 1 - r * 0.3, r + 1 - r * 0.3), r, new float[]{0, 1},
                    new Color[]{inner, outer}));
            g.fill(new Ellipse2D.Double(1, 1, 2 * r, 2 * r));
        });
    }

    // --8<-- [start:icon]
    /**
     * The picture on a tool's button: a little piece of the town around tile (0, 0), moved onto the middle
     * of the button. 0 road, 1 to 3 the zones, 4 power, 5 water, 6 services, 7 park, 8 bulldozer.
     */
    static Sprite icon(int tool) {
        return Sprite.drawn(36, 22, (g, w, h) -> {
            Art.smooth(g);
            IsoProjection p = IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, 18, 6);
            double cx = 18, cy = 14;
            switch (tool) {
                case 0 -> Art.with(g, p.ground(0), f -> {
                    f.setColor(new Color(0xB8B4A8));
                    f.fill(Art.rect(-8, 1, 32, 14));
                    f.setColor(new Color(0x45474B));
                    f.fill(Art.rect(-8, 3, 32, 10));
                    f.setColor(new Color(0xF0E6BE));
                    f.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 2.5f}, 0));
                    f.draw(new Line2D.Double(-8, 8, 24, 8));
                });
                case 1, 2, 3 -> {
                    int zc = tool == 1 ? Art.ZONE_R : tool == 2 ? Art.ZONE_C : Art.ZONE_I;
                    Art.with(g, p.ground(0), f -> {
                        f.setColor(Art.col(zc, 110));
                        f.fill(Art.rect(-2, -2, 20, 20));
                        f.setColor(Art.col(zc));
                        f.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 1.2f}, 0));
                        f.draw(Art.rect(-2, -2, 20, 20));
                    });
                    if (tool == 1) Art.gabled(g, p, 2, 3, 14, 12, 6, 5, 0xF2EEE4, 0xA8482E, false, false);
                    else if (tool == 2) Art.building(g, p, 1, 2, 15, 13, 9, 0xEADCC0, 0x9A968E, Art.Style.SHOP, 5);
                    else {
                        Art.building(g, p, 0, 3, 12, 14, 7, 0xA85A42, 0x6A6A6A, Art.Style.FACTORY, 6);
                        Art.box(g, p, 13, 4, 16, 7, 0, 14, 0x5A3020, 0xB86A50, 0x7A3A26);
                    }
                }
                case 4 -> {
                    AffineTransform before = g.getTransform();
                    g.translate(cx, cy + 6);
                    g.scale(0.75, 0.75);
                    Art.pylon(g, IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, 0, 0), 0, 0, false);
                    g.setTransform(before);
                }
                case 5 -> {
                    AffineTransform before = g.getTransform();
                    g.translate(cx, cy + 8);
                    g.scale(0.55, 0.55);
                    g.setColor(new Color(0x9AA4AE));
                    g.setStroke(new BasicStroke(1.4f));
                    g.draw(new Line2D.Double(-6, 0, -3, -14));
                    g.draw(new Line2D.Double(6, 0, 3, -14));
                    g.setPaint(Art.radial(new Point2D.Double(-3, -22), 12, new float[]{0, 1},
                            new Color[]{new Color(0xF4F8FA), new Color(0x6A8AA8)}));
                    g.fill(new Ellipse2D.Double(-9, -28, 18, 16));
                    g.setColor(new Color(0x2A6A9A));
                    g.fill(new Arc2D.Double(-9, -22, 18, 4, 180, 180, Arc2D.CHORD));
                    g.setTransform(before);
                }
                case 6 -> {
                    double x = cx, y = cy - 3;
                    Path2D shield = new Path2D.Double();
                    shield.moveTo(x - 6, y - 7);
                    shield.lineTo(x + 6, y - 7);
                    shield.lineTo(x + 6, y);
                    shield.curveTo(x + 6, y + 4, x + 2, y + 6, x, y + 7);
                    shield.curveTo(x - 2, y + 6, x - 6, y + 4, x - 6, y);
                    shield.closePath();
                    g.setPaint(new GradientPaint((float) x, (float) (y - 7), new Color(0xE85A4A), (float) x, (float) (y + 7),
                            new Color(0x9A2A20)));
                    g.fill(shield);
                    g.setColor(Color.WHITE);
                    g.fill(Art.rect(x - 1, y - 4.5, 2, 8));
                    g.fill(Art.rect(x - 4, y - 1.5, 8, 2));
                }
                case 7 -> {
                    Art.tree(g, p, 2, 12, false, 0.62, 3);
                    Art.tree(g, p, 13, 4, true, 0.55, 4);
                }
                default -> {
                    double x = cx, y = cy - 2;
                    g.setColor(new Color(0xE8B030));
                    g.fill(new RoundRectangle2D.Double(x - 6, y - 4, 11, 7, 2, 2));
                    g.setColor(new Color(0x3A3A3A));
                    g.fill(new RoundRectangle2D.Double(x - 6.5, y + 2, 12, 3.6, 2, 2));
                    g.setColor(new Color(0x1E2A34));
                    g.fill(Art.rect(x - 3.4, y - 3, 4, 3));
                    g.setColor(new Color(0xA8A8A8));
                    g.fill(Art.rect(x + 6, y - 5, 2.4, 10));
                }
            }
        });
    }
    // --8<-- [end:icon]

    /** The little pictures of the top bar: 0 money, 1 people, 2 power, 3 water, 4 happiness. */
    static void barIcon(Graphics2D g, int i, double cx, double cy, boolean happy) {
        switch (i) {
            case 0 -> {
                g.setPaint(Art.radial(new Point2D.Double(cx - 1.2, cy - 1.2), 5, new float[]{0, 1},
                        new Color[]{new Color(0xFFE88A), new Color(0xC8901A)}));
                g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
                g.setColor(new Color(0x8A5A10));
                g.setFont(Art.font(Font.BOLD, 6));
                g.drawString("$", (float) cx - 1.7f, (float) cy + 2.2f);
            }
            case 1 -> {
                g.setColor(new Color(0x8AB8E8));
                g.fill(new Ellipse2D.Double(cx - 4, cy - 3.6, 3, 3));
                g.fill(new Arc2D.Double(cx - 5, cy - 0.4, 5, 6, 0, 180, Arc2D.CHORD));
                g.setColor(new Color(0xC8E0F8));
                g.fill(new Ellipse2D.Double(cx - 0.6, cy - 4.4, 3.4, 3.4));
                g.fill(new Arc2D.Double(cx - 1.8, cy - 1, 5.8, 7, 0, 180, Arc2D.CHORD));
            }
            case 2 -> {
                g.setColor(new Color(0xF8D030));
                g.fill(Art.bolt(cx, cy, 1.2));
            }
            case 3 -> {
                g.setPaint(new GradientPaint((float) cx, (float) (cy - 5), new Color(0xA8E0F8), (float) cx, (float) (cy + 5),
                        new Color(0x3A8AD8)));
                g.fill(Art.drop(cx, cy, 0.95));
            }
            default -> {
                g.setColor(happy ? new Color(0x8AE07A) : new Color(0xF0A040));
                g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
                g.setColor(new Color(0x1C3A18));
                g.fill(new Ellipse2D.Double(cx - 2, cy - 1.8, 1.2, 1.2));
                g.fill(new Ellipse2D.Double(cx + 0.8, cy - 1.8, 1.2, 1.2));
                g.setStroke(new BasicStroke(0.7f));
                if (happy) g.draw(new Arc2D.Double(cx - 2.2, cy - 1.6, 4.4, 4, 200, 140, Arc2D.OPEN));
                else g.draw(new Arc2D.Double(cx - 2.2, cy + 1.2, 4.4, 4, 20, 140, Arc2D.OPEN));
            }
        }
    }
}
