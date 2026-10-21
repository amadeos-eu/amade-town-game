package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.iso.DepthSort;
import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.gameengine.sprite.ScrollingLayer;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.town.domain.Building;
import eu.amadeos.games.town.domain.BuildingType;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Ground;
import eu.amadeos.games.town.domain.Rules;
import eu.amadeos.games.town.domain.Work;
import eu.amadeos.games.town.domain.Zone;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Draws the town in four passes over the tiles the camera sees:
 *
 * <ol>
 *   <li>the ground: a picture for each tile, the banks where water meets land, the pavements at the corners
 *       of crossings, and the yards of the buildings;</li>
 *   <li>what lies flat on it: the zones not yet built on, the bridges, the overlay the player chose, what
 *       the mouse is about to build, and the shadows, all at once, so that nothing throws a shadow on a
 *       roof;</li>
 *   <li>everything that stands, sorted by {@link DepthSort}: lots, buildings, trees, pylons, cars;</li>
 *   <li>over it the wires, the signs of buildings without power or water, the building the player is
 *       placing, and the smoke.</li>
 * </ol>
 *
 * <p>The camera moves over the projection's picture; nearly everything drawn is a copy of a sprite.
 */
final class TownPainter {
    /** What the overlay shows. */
    enum Overlay { NONE, POWER, WATER, VALUE, POLLUTION }

    /**
     * What the player is doing, for the marks on the map; any of it may be {@code null}: the tile under the
     * mouse, a road or line being drawn and whether it can be built, a rectangle being zoned or bulldozed
     * ({@code col0, row0, col1, row1}) and its colour, a building being placed and whether it can stand there.
     */
    record Marks(Cell hover, List<Cell> path, boolean pathOk, int[] area, int areaColour, BuildingType placing,
                 Cell place, boolean placeOk, Overlay overlay) {
        static final Marks NONE = new Marks(null, List.of(), false, null, 0, null, null, false, Overlay.NONE);
    }

    static final Color BACKGROUND = new Color(0x1E2A22);
    static final Color SHADOW = new Color(10, 20, 15, 85);
    static final Color TREE_SHADOW = new Color(10, 25, 5, 70);
    static final Color OK = new Color(255, 255, 255, 120);
    static final Color BAD = new Color(240, 80, 60, 130);
    static final Color HOVER = new Color(255, 255, 255, 170);
    /** How far below the view a tile may lie and still reach into it, in tiles: the tallest tower. */
    static final int MARGIN = 6;

    /** Something that stands: a kind, its tile, and for trees and cars a point and a picture. */
    private record Thing(int kind, int col, int row, double x, double y, Object what) {
    }

    private static final int LOT = 0;
    private static final int BUILDING = 1;
    private static final int TREE = 2;
    private static final int PYLON = 3;
    private static final int CAR = 4;
    private static final int RUBBLE = 5;
    private static final int SIGN = 6;
    private static final int WALKER = 7;

    private final Pictures pictures;
    private final DepthSort<Thing> sort = new DepthSort<>();
    private final Path2D.Double shadows = new Path2D.Double(Path2D.WIND_NON_ZERO);
    private final Path2D.Double treeShadows = new Path2D.Double(Path2D.WIND_NON_ZERO);
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    private final BasicStroke dashes = new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1,
            new float[]{2.5f, 1.5f}, 0);
    private final BasicStroke thin = new BasicStroke(0.8f);
    /** The ground and the shadows on it, painted again only where the view moved or the town changed. */
    private ScrollingLayer layer;
    private double layerWidth;
    private double layerHeight;

    TownPainter(Rules rules) {
        this.pictures = new Pictures(rules);
    }

    Pictures pictures() {
        return pictures;
    }

    /** The projection of a map: tiles of 16 units drawn 32 × 16, with room above for the tallest tower. */
    static IsoProjection projection(Game game) {
        return IsoProjection.forMap(game.width(), game.height(), Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, Field.ABOVE);
    }

    /** Which of the looks of its level a lot shows: always the same for a tile. */
    static int variant(int col, int row) {
        return (int) (Art.hash(col, row, 31) * Lots.VARIANTS) % Lots.VARIANTS;
    }

    // --8<-- [start:paint]
    /** The town as the camera sees it, and the marks of what the player is doing. */
    void paint(Graphics2D g, Game game, IsoProjection iso, Camera camera, Traffic traffic, Effects effects,
               Marks marks, double alpha) {
        Shape clip = g.getClip();
        AffineTransform screen = g.getTransform();
        rectangle.setRect(camera.screenLeft(), camera.screenTop(), camera.viewWidth(), camera.viewHeight());
        g.clip(rectangle);
        // Whole screen pixels, as the ground layer is drawn, so that nothing standing on it slides.
        double left = ScrollingLayer.snap(camera.x(alpha), screen.getScaleX());
        double top = ScrollingLayer.snap(camera.y(alpha), screen.getScaleY());
        if (layer == null || layerWidth != camera.viewWidth() || layerHeight != camera.viewHeight()) {
            layerWidth = camera.viewWidth();
            layerHeight = camera.viewHeight();
            layer = new ScrollingLayer(layerWidth, layerHeight);
        }
        layer.draw(g, camera.screenLeft(), camera.screenTop(), left, top, groundKey(game),
                (lg, l, t, w, h) -> {
                    lg.setColor(BACKGROUND);
                    lg.fill(new Rectangle2D.Double(l, t, w, h));
                    int[] lv = iso.visible(l, t, w, h, MARGIN);
                    ground(lg, game, iso, lv, new View(l, t, l + w, t + h));
                    shadows(lg, game, iso, lv);
                });
        g.translate(camera.screenLeft() - left, camera.screenTop() - top);
        int[] v = iso.visible(left, top, camera.viewWidth(), camera.viewHeight(), MARGIN);
        View view = new View(left, top, left + camera.viewWidth(), top + camera.viewHeight());
        double time = effects.time();

        flat(g, game, iso, v, view, marks);
        things(g, game, iso, v, view, traffic, time);
        wires(g, game, iso, v, view);
        signs(g, game, iso, v, view, time);
        placing(g, iso, marks);
        effects.render(g, alpha);
        g.setTransform(screen);
        g.setClip(clip);
    }

    /**
     * What the ground layer shows, folded into one number: the town, and of every tile what decides its
     * floor, its road and the shadows on it. When it changes, the layer paints the whole view again.
     */
    static long groundKey(Game game) {
        long key = System.identityHashCode(game);
        for (int row = 0; row < game.height(); row++) {
            for (int col = 0; col < game.width(); col++) {
                Zone zone = game.zone(col, row);
                Building b = game.buildingAt(col, row);
                long tile = game.ground(col, row).ordinal()
                        | (game.road(col, row) ? 4 : 0) | (game.line(col, row) ? 8 : 0) | (game.rubble(col, row) ? 16 : 0)
                        | (zone == null ? 0 : zone.ordinal() + 1L) << 5 | (long) game.lot(col, row) << 8
                        | (b == null ? 0 : b.id() + 1L) << 12;
                key = key * 31 + tile;
            }
        }
        return key;
    }
    // --8<-- [end:paint]

    /** The part of the picture in view, to skip tiles outside it. */
    private record View(double left, double top, double right, double bottom) {
        boolean sees(double x0, double y0, double x1, double y1) {
            return x1 >= left && x0 <= right && y1 >= top && y0 <= bottom;
        }
    }

    private static double tileX(IsoProjection iso, int col, int row) {
        return iso.pictureX(col * Field.TILE, row * Field.TILE);
    }

    private static double tileY(IsoProjection iso, int col, int row) {
        return iso.pictureY(col * Field.TILE, row * Field.TILE, 0);
    }

    // ---- 1. the ground ---------------------------------------------------------------------------

    // --8<-- [start:ground]
    private void ground(Graphics2D g, Game game, IsoProjection iso, int[] v, View view) {
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                double x = tileX(iso, col, row);
                double y = tileY(iso, col, row);
                if (!view.sees(x - 17, y, x + 17, y + 17)) continue;
                double sx = x - Pictures.FLOOR_X;
                double sy = y - Pictures.FLOOR_Y;
                Ground ground = game.ground(col, row);
                boolean water = ground == Ground.WATER;
                if (game.road(col, row) && !water) {
                    int mask = roads(game, col, row);
                    pictures.road(mask, game.highway(col, row)).draw(g, sx, sy);
                    corners(g, game, col, row, mask, sx, sy);
                    continue;
                }
                pictures.floor(floor(game, col, row, ground), col, row).draw(g, sx, sy);
                if (water) banks(g, game, col, row, sx, sy);
            }
        }
        for (Building b : game.buildings()) {
            if (!sees(iso, view, b, 0)) continue;
            pictures.yard(b.type().work()).draw(g, tileX(iso, b.col(), b.row()) - b.height() * Field.TILE_WIDTH / 2.0 - 1,
                    tileY(iso, b.col(), b.row()) - 1);
        }
    }

    private static Pictures.Floor floor(Game game, int col, int row, Ground ground) {
        if (ground == Ground.WATER) return Pictures.Floor.WATER;
        Building b = game.buildingAt(col, row);
        if (b != null) {
            Work work = b.type().work();
            return work == Work.WIND || work == Work.TOWER || work == Work.PARK ? Pictures.Floor.GRASS : Pictures.Floor.YARD;
        }
        Zone zone = game.zone(col, row);
        if (zone != null && game.lot(col, row) > 0) {
            return switch (zone) {
                case RESIDENTIAL -> Pictures.Floor.LAWN;
                case COMMERCIAL -> Pictures.Floor.PAVED;
                case INDUSTRIAL -> Pictures.Floor.CONCRETE;
            };
        }
        if (game.rubble(col, row)) return Pictures.Floor.RUBBLE;
        return ground == Ground.FOREST ? Pictures.Floor.FOREST : Pictures.Floor.GRASS;
    }

    /** Which sides of a road tile have road beyond them; the highway goes on past the edge of the map. */
    static int roads(Game game, int col, int row) {
        int mask = 0;
        for (int d = 0; d < 4; d++) {
            int c = col + Traffic.DX[d];
            int r = row + Traffic.DY[d];
            boolean on = game.road(c, r) || !game.contains(c, r) && game.highway(col, row);
            if (on) mask |= switch (d) {
                case 0 -> 2;
                case 1 -> 8;
                case 2 -> 4;
                default -> 1;
            };
        }
        return mask;
    }

    private void corners(Graphics2D g, Game game, int col, int row, int mask, double sx, double sy) {
        boolean up = (mask & 1) != 0, right = (mask & 2) != 0, down = (mask & 4) != 0, left = (mask & 8) != 0;
        if (left && up && !game.road(col - 1, row - 1)) pictures.corners[0].draw(g, sx, sy);
        if (right && up && !game.road(col + 1, row - 1)) pictures.corners[1].draw(g, sx, sy);
        if (right && down && !game.road(col + 1, row + 1)) pictures.corners[2].draw(g, sx, sy);
        if (left && down && !game.road(col - 1, row + 1)) pictures.corners[3].draw(g, sx, sy);
    }

    /** The banks of a water tile: on each side where land begins, and at a corner of land between water. */
    private void banks(Graphics2D g, Game game, int col, int row, double sx, double sy) {
        boolean up = land(game, col, row - 1), right = land(game, col + 1, row);
        boolean down = land(game, col, row + 1), left = land(game, col - 1, row);
        if (up) pictures.banks[0].draw(g, sx, sy);
        if (right) pictures.banks[1].draw(g, sx, sy);
        if (down) pictures.banks[2].draw(g, sx, sy);
        if (left) pictures.banks[3].draw(g, sx, sy);
        if (!left && !up && land(game, col - 1, row - 1)) pictures.banks[4].draw(g, sx, sy);
        if (!right && !up && land(game, col + 1, row - 1)) pictures.banks[5].draw(g, sx, sy);
        if (!right && !down && land(game, col + 1, row + 1)) pictures.banks[6].draw(g, sx, sy);
        if (!left && !down && land(game, col - 1, row + 1)) pictures.banks[7].draw(g, sx, sy);
    }

    private static boolean land(Game game, int col, int row) {
        return game.contains(col, row) && game.ground(col, row) != Ground.WATER;
    }
    // --8<-- [end:ground]

    // ---- 2. what lies flat -----------------------------------------------------------------------

    // --8<-- [start:flat]
    private void flat(Graphics2D g, Game game, IsoProjection iso, int[] v, View view, Marks marks) {
        AffineTransform before = g.getTransform();
        g.transform(iso.ground(0));
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                Zone zone = game.zone(col, row);
                if (zone == null || game.lot(col, row) > 0) continue;
                zoneTile(g, game, col, row, zone, 60);
            }
        }
        if (marks.overlay() != Overlay.NONE) overlay(g, game, v, marks.overlay());
        if (marks.area() != null) {
            int[] a = marks.area();
            int c0 = Math.min(a[0], a[2]), c1 = Math.max(a[0], a[2]), r0 = Math.min(a[1], a[3]), r1 = Math.max(a[1], a[3]);
            g.setColor(Art.col(marks.areaColour(), 90));
            rectangle.setRect(c0 * Field.TILE, r0 * Field.TILE, (c1 - c0 + 1) * Field.TILE, (r1 - r0 + 1) * Field.TILE);
            g.fill(rectangle);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1f));
            g.draw(rectangle);
        }
        for (Cell cell : marks.path()) {
            g.setColor(marks.pathOk() ? OK : BAD);
            rectangle.setRect(cell.col() * Field.TILE + 1, cell.row() * Field.TILE + 1, Field.TILE - 2, Field.TILE - 2);
            g.fill(rectangle);
        }
        if (marks.placing() != null && marks.place() != null) {
            Cell p = marks.place();
            g.setColor(marks.placeOk() ? new Color(120, 240, 110, 90) : BAD);
            rectangle.setRect(p.col() * Field.TILE, p.row() * Field.TILE, marks.placing().width() * Field.TILE,
                    marks.placing().height() * Field.TILE);
            g.fill(rectangle);
        }
        if (marks.hover() != null && marks.area() == null && marks.path().isEmpty() && marks.placing() == null) {
            g.setColor(HOVER);
            g.setStroke(thin);
            rectangle.setRect(marks.hover().col() * Field.TILE, marks.hover().row() * Field.TILE, Field.TILE, Field.TILE);
            g.draw(rectangle);
        }
        g.setTransform(before);
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                if (game.road(col, row) && game.ground(col, row) == Ground.WATER) bridge(g, game, iso, col, row);
            }
        }
    }
    // --8<-- [end:flat]

    /** A zoned tile: tinted, with a dashed line where the zone ends. */
    private void zoneTile(Graphics2D g, Game game, int col, int row, Zone zone, int alpha) {
        int colour = Pictures.zoneColour(zone);
        double x = col * Field.TILE;
        double y = row * Field.TILE;
        g.setColor(Art.col(colour, alpha));
        rectangle.setRect(x, y, Field.TILE, Field.TILE);
        g.fill(rectangle);
        g.setColor(Art.col(colour, 230));
        g.setStroke(dashes);
        if (game.zone(col, row - 1) != zone) g.draw(new Line2D.Double(x + 1, y + 1, x + Field.TILE - 1, y + 1));
        if (game.zone(col + 1, row) != zone) g.draw(new Line2D.Double(x + Field.TILE - 1, y + 1, x + Field.TILE - 1, y + Field.TILE - 1));
        if (game.zone(col, row + 1) != zone) g.draw(new Line2D.Double(x + 1, y + Field.TILE - 1, x + Field.TILE - 1, y + Field.TILE - 1));
        if (game.zone(col - 1, row) != zone) g.draw(new Line2D.Double(x + 1, y + 1, x + 1, y + Field.TILE - 1));
    }

    // --8<-- [start:overlay]
    /** The overlay: power, water, land value or pollution, a colour on each tile. */
    private void overlay(Graphics2D g, Game game, int[] v, Overlay overlay) {
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                Color colour = switch (overlay) {
                    case POWER -> {
                        boolean conducts = game.line(col, row) || game.zone(col, row) != null || game.buildingAt(col, row) != null;
                        yield !conducts ? null : game.powered(col, row) ? new Color(248, 208, 48, 120) : new Color(232, 74, 58, 140);
                    }
                    case WATER -> {
                        boolean lot = game.zone(col, row) != null && game.lot(col, row) > 0;
                        if (lot && game.lot(col, row) >= 2 && !game.watered(col, row)) yield new Color(232, 74, 58, 140);
                        yield game.piped(col, row) ? new Color(58, 150, 232, game.watered(col, row) || !lot ? 110 : 60) : null;
                    }
                    case VALUE -> {
                        if (game.ground(col, row) == Ground.WATER) yield null;
                        int value = game.value(col, row);
                        yield Art.col(Art.mix(0xE84A3A, 0x58E058, value / 70.0), 120);
                    }
                    case POLLUTION -> {
                        int smoke = game.smoke(col, row);
                        yield smoke <= 0 ? null : Art.col(0x6A4A2A, Math.min(200, 40 + smoke * 2));
                    }
                    case NONE -> null;
                };
                if (colour == null) continue;
                g.setColor(colour);
                rectangle.setRect(col * Field.TILE + 0.5, row * Field.TILE + 0.5, Field.TILE - 1, Field.TILE - 1);
                g.fill(rectangle);
            }
        }
    }
    // --8<-- [end:overlay]

    // --8<-- [start:bridge]
    /** A tile of a bridge: its shadow on the water, the side of the deck, the deck, the railings. */
    private void bridge(Graphics2D g, Game game, IsoProjection iso, int col, int row) {
        boolean alongX = game.road(col - 1, row) || game.road(col + 1, row);
        double x = col * Field.TILE;
        double y = row * Field.TILE;
        double t = Field.TILE;
        double deck = 3;
        Art.with(g, iso.ground(0), f -> {
            f.setColor(new Color(10, 30, 40, 90));
            f.fill(alongX ? Art.rect(x + 4, y + t, t, 5) : Art.rect(x + t, y + 2, 5, t));
        });
        if (alongX) {
            Art.with(g, iso.frontWall(x, y + t), f -> {
                f.setColor(new Color(0x6A665E));
                f.fill(Art.rect(0, -deck, t, 3.4));
                f.setColor(new Color(0x4A4640));
                if (col % 2 == 0) f.fill(Art.rect(6, 0.4, 3, 4));
            });
        } else {
            Art.with(g, iso.sideWall(x + t, y + t), f -> {
                f.setColor(new Color(0x5A564E));
                f.fill(Art.rect(0, -deck, t, 3.4));
                f.setColor(new Color(0x3A3630));
                if (row % 2 == 0) f.fill(Art.rect(6, 0.4, 3, 4));
            });
        }
        Art.with(g, iso.ground(deck), f -> {
            f.setColor(new Color(0x9A968E));
            f.fill(Art.rect(x, y, t, t));
            f.setColor(new Color(0x48494C));
            f.fill(alongX ? Art.rect(x, y + 2, t, 12) : Art.rect(x + 2, y, 12, t));
            f.setColor(new Color(240, 230, 190, 190));
            f.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
            f.draw(alongX ? new Line2D.Double(x, y + 8, x + t, y + 8) : new Line2D.Double(x + 8, y, x + 8, y + t));
        });
        g.setColor(new Color(0x2A2A2C));
        g.setStroke(new BasicStroke(0.5f));
        for (double side : new double[]{0.6, t - 0.6}) {
            double ax = alongX ? x : x + side, ay = alongX ? y + side : y;
            double bx = alongX ? x + t : x + side, by = alongX ? y + side : y + t;
            g.draw(new Line2D.Double(Art.at(iso, ax, ay, deck + 3), Art.at(iso, bx, by, deck + 3)));
            for (double k = 1; k < t; k += 3) {
                double px = alongX ? x + k : ax, py = alongX ? ay : y + k;
                g.draw(new Line2D.Double(Art.at(iso, px, py, deck), Art.at(iso, px, py, deck + 3)));
            }
        }
    }
    // --8<-- [end:bridge]

    // ---- shadows ---------------------------------------------------------------------------------

    // --8<-- [start:shadows]
    /**
     * All shadows in one path, filled once: where two overlap, the ground is not darker, and nothing that
     * stands is drawn yet, so no shadow falls on a roof. Each is its footprint stretched along +x by its height.
     */
    private void shadows(Graphics2D g, Game game, IsoProjection iso, int[] v) {
        shadows.reset();
        treeShadows.reset();
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                int level = game.lot(col, row);
                Zone zone = game.zone(col, row);
                if (zone != null && level > 0) {
                    for (double[] s : pictures.look(zone, level, variant(col, row)).shadows()) shadow(col * Field.TILE, row * Field.TILE, s);
                } else if (game.ground(col, row) == Ground.FOREST && game.buildingAt(col, row) == null) {
                    for (int i = 0; i < trees(col, row); i++) {
                        double[] t = tree(col, row, i);
                        treeShadows.append(Art.treeShadow(t[0], t[1], t[4] > 0, Pictures.TREE_SIZES[(int) t[2]]), false);
                    }
                }
                if (pylon(game, col, row)) shadow(col * Field.TILE, row * Field.TILE, new double[]{1.6, 1.6, 2.4, 2.4, Art.PYLON});
            }
        }
        for (Building b : game.buildings()) {
            if (b.col() + b.width() < v[0] || b.row() + b.height() < v[1] || b.col() > v[2] || b.row() > v[3]) continue;
            for (double[] s : pictures.look(b.type().work()).shadows()) shadow(b.col() * Field.TILE, b.row() * Field.TILE, s);
        }
        AffineTransform before = g.getTransform();
        g.transform(iso.ground(0));
        g.setColor(SHADOW);
        g.fill(shadows);
        g.setColor(TREE_SHADOW);
        g.fill(treeShadows);
        g.setTransform(before);
    }

    private void shadow(double ox, double oy, double[] s) {
        shadows.append(new Rectangle2D.Double(ox + s[0], oy + s[1], s[2] - s[0] + s[4] * Art.SHADOW, s[3] - s[1]), false);
    }
    // --8<-- [end:shadows]

    /** How many trees stand on a forest tile: one or two. */
    static int trees(int col, int row) {
        return Art.hash(col, row, 61) < 0.45 ? 2 : 1;
    }

    /** Where tree {@code i} of a forest tile stands, its size and shape: {x, y, size, shape, conifer}. */
    static double[] tree(int col, int row, int i) {
        double x = (col + 0.25 + Art.hash(col, row, 62 + i) * 0.5) * Field.TILE;
        double y = (row + 0.25 + Art.hash(col, row, 64 + i) * 0.5) * Field.TILE;
        if (i == 1) {
            x = (col + (x / Field.TILE - col < 0.5 ? 0.78 : 0.22)) * Field.TILE;
            y = (row + (y / Field.TILE - row < 0.5 ? 0.78 : 0.22)) * Field.TILE;
        }
        int size = (int) (Art.hash(col, row, 66 + i) * Pictures.TREE_SIZES.length);
        if (i == 1) size = Math.max(0, size - 1);
        int shape = (int) (Art.hash(col, row, 68 + i) * Pictures.TREE_SHAPES);
        double conifer = Art.noise(col / 5.0, row / 5.0, 41) + (Art.hash(col, row, 70 + i) - 0.5) * 0.3 > 0.58 ? 1 : 0;
        return new double[]{x, y, size, shape, conifer};
    }

    // ---- 3. everything that stands ---------------------------------------------------------------

    // --8<-- [start:things]
    private void things(Graphics2D g, Game game, IsoProjection iso, int[] v, View view, Traffic traffic, double time) {
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                double x = tileX(iso, col, row);
                double y = tileY(iso, col, row);
                if (x + 17 < view.left() || x - 17 > view.right() || y > view.bottom()) continue;
                double tx = col * Field.TILE;
                double ty = row * Field.TILE;
                Zone zone = game.zone(col, row);
                int level = game.lot(col, row);
                if (zone != null && level > 0) {
                    double tall = Lots.tall(zone, level);
                    sort.add(tx, ty, tx + Field.TILE, ty + Field.TILE, x - 17, y - tall - 1, x + 17, y + 17,
                            new Thing(LOT, col, row, 0, 0, null));
                } else if (game.rubble(col, row)) {
                    sort.add(tx + 2, ty + 2, tx + 14, ty + 14, x - 17, y - 7, x + 17, y + 17, new Thing(RUBBLE, col, row, 0, 0, null));
                } else if (zone != null && Art.hash(col, row, 71) < 0.12) {
                    double sx = tx + 11, sy = ty + 12;
                    double px = iso.pictureX(sx, sy), py = iso.pictureY(sx, sy, 0);
                    sort.add(sx - 0.5, sy - 0.5, sx + 0.5, sy + 0.5, px - 2, py - 11, px + 8, py + 2, new Thing(SIGN, col, row, sx, sy, zone));
                } else if (game.ground(col, row) == Ground.FOREST && game.buildingAt(col, row) == null && !game.road(col, row)) {
                    for (int i = 0; i < trees(col, row); i++) {
                        double[] t = tree(col, row, i);
                        double px = iso.pictureX(t[0], t[1]), py = iso.pictureY(t[0], t[1], 0);
                        sort.add(t[0] - 1, t[1] - 1, t[0] + 1, t[1] + 1, px - Pictures.TREE_X, py - Pictures.TREE_Y,
                                px + Pictures.TREE_X, py + 4, new Thing(TREE, col, row, t[0], t[1], t));
                    }
                }
                if (pylon(game, col, row)) {
                    double px = iso.pictureX(tx + 2, ty + 2), py = iso.pictureY(tx + 2, ty + 2, 0);
                    sort.add(tx + 1.5, ty + 1.5, tx + 2.5, ty + 2.5, px - 8, py - 21, px + 8, py + 3,
                            new Thing(PYLON, col, row, tx + 2, ty + 2, null));
                }
            }
        }
        for (Building b : game.buildings()) {
            Structures.Look look = pictures.look(b.type().work());
            if (!sees(iso, view, b, look.tall())) continue;
            double x = tileX(iso, b.col(), b.row());
            double y = tileY(iso, b.col(), b.row());
            int across = b.width() + b.height();
            sort.add(b.col() * Field.TILE, b.row() * Field.TILE, (b.col() + b.width()) * Field.TILE, (b.row() + b.height()) * Field.TILE,
                    x - b.height() * 16.0 - Pictures.STRUCTURE_SIDE, y - look.tall() - 2,
                    x + b.width() * 16.0 + Pictures.STRUCTURE_SIDE, y + across * 8.0 + 2, new Thing(BUILDING, b.col(), b.row(), 0, 0, b));
        }
        for (Traffic.Car car : traffic.cars()) {
            double px = iso.pictureX(car.x(), car.y()), py = iso.pictureY(car.x(), car.y(), 0);
            if (!view.sees(px - 12, py - 12, px + 12, py + 6)) continue;
            boolean alongX = car.facing() < 2;
            double half = car.kind() >= Pictures.BUS ? 5.5 : 3.2;
            double x0 = alongX ? car.x() - half : car.x() - 1.6, x1 = alongX ? car.x() + half : car.x() + 1.6;
            double y0 = alongX ? car.y() - 1.6 : car.y() - half, y1 = alongX ? car.y() + 1.6 : car.y() + half;
            sort.add(x0, y0, x1, y1, px - Pictures.CAR_X, py - Pictures.CAR_Y, px + Pictures.CAR_X, py + 5,
                    new Thing(CAR, 0, 0, car.x(), car.y(), car));
        }
        for (Walkers.Walker w : traffic.walkers()) {
            double px = iso.pictureX(w.x(), w.y()), py = iso.pictureY(w.x(), w.y(), 0);
            if (!view.sees(px - 3, py - 8, px + 3, py + 2)) continue;
            sort.add(w.x() - 0.4, w.y() - 0.4, w.x() + 0.4, w.y() + 0.4, px - Pictures.PERSON_X, py - Pictures.PERSON_Y,
                    px + Pictures.PERSON_X, py + 1, new Thing(WALKER, 0, 0, w.x(), w.y(), w));
        }
        for (Thing thing : sort.sorted()) draw(g, game, iso, thing, time);
    }
    // --8<-- [end:things]

    private static boolean sees(IsoProjection iso, View view, Building b, double tall) {
        double x = tileX(iso, b.col(), b.row());
        double y = tileY(iso, b.col(), b.row());
        return view.sees(x - b.height() * 16.0 - 8, y - tall - 4, x + b.width() * 16.0 + 8, y + (b.width() + b.height()) * 8.0 + 4);
    }

    private void draw(Graphics2D g, Game game, IsoProjection iso, Thing thing, double time) {
        int col = thing.col();
        int row = thing.row();
        switch (thing.kind()) {
            case LOT -> {
                Zone zone = game.zone(col, row);
                int level = game.lot(col, row);
                double tall = Lots.tall(zone, level);
                double x = tileX(iso, col, row);
                double y = tileY(iso, col, row);
                boolean street = game.road(col, row + 1) || game.highway(col, row + 1);
                pictures.lot(zone, level, variant(col, row), street).draw(g, x - Pictures.FLOOR_X, y - tall - Pictures.FLOOR_Y);
                if (game.burning(col, row)) {
                    int frame = (int) (time * 10 + col * 3 + row) % Pictures.FLAMES;
                    pictures.flames[frame].draw(g, x - Pictures.FLAME_X, y + 10 - Pictures.FLAME_Y);
                }
            }
            case RUBBLE -> pictures.rubble.draw(g, tileX(iso, col, row) - Pictures.FLOOR_X, tileY(iso, col, row) - Pictures.FLOOR_Y - 6);
            case SIGN -> pictures.signs[((Zone) thing.what()).ordinal()].draw(g, iso.pictureX(thing.x(), thing.y()) - 2,
                    iso.pictureY(thing.x(), thing.y(), 0) - 10.5);
            case TREE -> {
                double[] t = (double[]) thing.what();
                pictures.tree(t[4] > 0, (int) t[2], (int) t[3]).draw(g, iso.pictureX(t[0], t[1]) - Pictures.TREE_X,
                        iso.pictureY(t[0], t[1], 0) - Pictures.TREE_Y);
            }
            case PYLON -> {
                boolean alongX = game.line(col - 1, row) || game.line(col + 1, row);
                pictures.pylons[alongX ? 1 : 0].draw(g, iso.pictureX(thing.x(), thing.y()) - Pictures.PYLON_X,
                        iso.pictureY(thing.x(), thing.y(), 0) - Pictures.PYLON_Y);
            }
            case CAR -> {
                Traffic.Car car = (Traffic.Car) thing.what();
                pictures.vehicle(car.kind(), car.facing()).draw(g, iso.pictureX(car.x(), car.y()) - Pictures.CAR_X,
                        iso.pictureY(car.x(), car.y(), 0) - Pictures.CAR_Y);
            }
            case WALKER -> {
                Walkers.Walker w = (Walkers.Walker) thing.what();
                pictures.people[w.look()][w.frame()].draw(g, iso.pictureX(w.x(), w.y()) - Pictures.PERSON_X,
                        iso.pictureY(w.x(), w.y(), 0) - Pictures.PERSON_Y);
            }
            case BUILDING -> building(g, iso, (Building) thing.what(), time);
            default -> {
            }
        }
    }

    private void building(Graphics2D g, IsoProjection iso, Building b, double time) {
        Work work = b.type().work();
        Sprite sprite = pictures.structure(work);
        double x = tileX(iso, b.col(), b.row());
        double y = tileY(iso, b.col(), b.row());
        sprite.draw(g, x - b.height() * 16.0 - Pictures.STRUCTURE_SIDE, y - pictures.look(work).tall() - 2);
        if (work == Work.WIND) {
            double hx = iso.pictureX(b.col() * Field.TILE + 8, b.row() * Field.TILE + 8) + 3.4;
            double hy = iso.pictureY(b.col() * Field.TILE + 8, b.row() * Field.TILE + 8, Art.TURBINE_HUB);
            double turn = b.working() ? time * 2.2 + b.id() : b.id();
            Art.rotor(g, hx, hy, turn);
        }
    }

    // ---- 4. over everything ----------------------------------------------------------------------

    // --8<-- [start:pylons]
    /**
     * Whether a tile of a power line carries a pylon: at its ends, corners and branches, and along a straight
     * run only on every other tile, so a long line reads as a line and not as a fence.
     */
    static boolean pylon(Game game, int col, int row) {
        if (!game.line(col, row)) return false;
        boolean x = game.line(col - 1, row) && game.line(col + 1, row);
        boolean y = game.line(col, row - 1) && game.line(col, row + 1);
        boolean sideX = game.line(col - 1, row) || game.line(col + 1, row);
        boolean sideY = game.line(col, row - 1) || game.line(col, row + 1);
        if (x && !sideY) return col % 2 == 0;
        if (y && !sideX) return row % 2 == 0;
        return true;
    }

    /** The wires from each pylon to the next one along x and along y, over the tiles between without one. */
    private void wires(Graphics2D g, Game game, IsoProjection iso, int[] v, View view) {
        for (int row = v[1] - 2; row <= v[3]; row++) {
            for (int col = v[0] - 2; col <= v[2]; col++) {
                if (!pylon(game, col, row)) continue;
                double x = col * Field.TILE + 2;
                double y = row * Field.TILE + 2;
                int c = col + 1;
                while (game.line(c, row) && !pylon(game, c, row)) c++;
                if (game.line(c, row)) Art.wires(g, iso, x, y, x + (c - col) * Field.TILE, y, true);
                int r = row + 1;
                while (game.line(col, r) && !pylon(game, col, r)) r++;
                if (game.line(col, r)) Art.wires(g, iso, x, y, x, y + (r - row) * Field.TILE, false);
            }
        }
    }
    // --8<-- [end:pylons]

    // --8<-- [start:signs]
    /** Over a lot or a building without power, a crossed out bolt; without water, a crossed out drop; blinking. */
    private void signs(Graphics2D g, Game game, IsoProjection iso, int[] v, View view, double time) {
        if (time % 1.2 > 0.8) return;
        for (int row = v[1]; row <= v[3]; row++) {
            for (int col = v[0]; col <= v[2]; col++) {
                Zone zone = game.zone(col, row);
                int level = game.lot(col, row);
                if (zone == null || level == 0 || game.burning(col, row)) continue;
                Sprite sign = !game.powered(col, row) ? pictures.noPower
                        : level >= 2 && !game.watered(col, row) ? pictures.noWater : null;
                if (sign == null) continue;
                double x = iso.pictureX((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE);
                double y = iso.pictureY((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE, Math.min(30, Lots.tall(zone, level) * 0.7));
                sign.draw(g, x - 6, y - 6);
            }
        }
        for (Building b : game.buildings()) {
            if (b.working()) continue;
            double x = iso.pictureX(b.centerCol() * Field.TILE, b.centerRow() * Field.TILE);
            double y = iso.pictureY(b.centerCol() * Field.TILE, b.centerRow() * Field.TILE, Math.min(34, pictures.look(b.type().work()).tall() * 0.6));
            if (view.sees(x - 6, y - 6, x + 6, y + 6)) pictures.noPower.draw(g, x - 6, y - 6);
        }
    }
    // --8<-- [end:signs]

    /** The building the player is about to place, see-through over the town. */
    private void placing(Graphics2D g, IsoProjection iso, Marks marks) {
        BuildingType type = marks.placing();
        Cell place = marks.place();
        if (type == null || place == null) return;
        Composite before = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, marks.placeOk() ? 0.75f : 0.4f));
        double x = tileX(iso, place.col(), place.row());
        double y = tileY(iso, place.col(), place.row());
        pictures.structure(type.work()).draw(g, x - type.height() * 16.0 - Pictures.STRUCTURE_SIDE,
                y - pictures.look(type.work()).tall() - 2);
        g.setComposite(before);
    }
}
