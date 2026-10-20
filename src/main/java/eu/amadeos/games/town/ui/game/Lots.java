package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.games.town.domain.Zone;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * What grows on a lot of one tile: for each zone three levels, and of each level a few variants, so that a
 * street of homes is not one house copied. The tile runs from (0, 0) to (16, 16) in world units; the front
 * of a building faces +y, the way the light falls.
 *
 * <ul>
 *   <li>Homes: a cottage with a garden and a fence, a larger house or two row houses, a block of flats.</li>
 *   <li>Shops: a corner shop, a shop with flats above, an office block or a glass tower.</li>
 *   <li>Industry: a workshop with a tank, a brick factory with its chimney, a works with a striped stack.</li>
 * </ul>
 *
 * <p>Each look also says where its shadows fall and where its chimney smokes, from the same numbers as the
 * drawing.
 */
// --8<-- [start:lots]
final class Lots {
    static final int VARIANTS = 4;

    /** How tall the pictures of a zone's levels are, above the tile, in picture units. */
    static double tall(Zone zone, int level) {
        return switch (zone) {
            case RESIDENTIAL -> new double[]{0, 22, 26, 42}[level];
            case COMMERCIAL -> new double[]{0, 22, 30, 66}[level];
            case INDUSTRIAL -> new double[]{0, 26, 42, 52}[level];
        };
    }

    /** A footprint and the height that throws its shadow: {x0, y0, x1, y1, height}. */
    record Look(List<double[]> shadows, double[] chimney) {
    }
    // --8<-- [end:lots]

    private Lots() {
    }

    private static int wall(int variant, int salt) {
        return Art.WALLS[Math.floorMod(variant * 5 + salt, Art.WALLS.length)];
    }

    private static int roof(int variant, int salt) {
        return Art.ROOFS[Math.floorMod(variant * 3 + salt, Art.ROOFS.length)];
    }

    /** The shadows and the chimney of a look, without drawing it. */
    static Look look(Zone zone, int level, int variant) {
        List<double[]> shadows = new ArrayList<>();
        double[] chimney = null;
        double dx = (variant % 3 - 1) * 0.6;
        switch (zone) {
            case RESIDENTIAL -> {
                if (level == 1) shadows.add(new double[]{3 + dx, 3.5, 12.5 + dx, 10, 8.5});
                else if (level == 2) shadows.add(new double[]{1.6, 2.5, 14.4, 10.5, 12.5});
                else shadows.add(new double[]{2, 2, 14, 13, apartment(variant)});
                shadows.add(new double[]{15.2, 1, 16, 15, 2});
            }
            case COMMERCIAL -> {
                if (level == 1) shadows.add(new double[]{2, 3, 14, 13, 9});
                else if (level == 2) shadows.add(new double[]{1.5, 2, 14.5, 13, 16});
                else if (variant % 2 == 0) shadows.add(new double[]{2, 2, 14, 14, 36});
                else shadows.add(new double[]{3, 3, 13, 13, 56});
            }
            case INDUSTRIAL -> {
                if (level == 1) {
                    shadows.add(new double[]{2, 3, 12, 13, 8});
                    shadows.add(new double[]{12, 4.5, 15, 7.5, 6});
                } else if (level == 2) {
                    shadows.add(new double[]{1.5, 2, 12.5, 13, 11});
                    shadows.add(new double[]{13, 3, 15, 5, 30});
                    chimney = new double[]{14, 4, 30};
                } else {
                    shadows.add(new double[]{1, 1.5, 11.5, 14.5, 14});
                    shadows.add(new double[]{12.6, 1.8, 15, 4.2, 40});
                    shadows.add(new double[]{12, 9.5, 15, 12.5, 8});
                    chimney = new double[]{13.8, 3, 40};
                }
            }
        }
        return new Look(shadows, chimney);
    }

    private static double apartment(int variant) {
        return 24 + (variant % 3) * 4;
    }

    /** Draws a look on the tile whose top corner is the projection's origin. */
    static void draw(Graphics2D g, IsoProjection p, Zone zone, int level, int variant) {
        switch (zone) {
            case RESIDENTIAL -> home(g, p, level, variant);
            case COMMERCIAL -> shop(g, p, level, variant);
            case INDUSTRIAL -> works(g, p, level, variant);
        }
    }

    // --8<-- [start:home]
    private static void home(Graphics2D g, IsoProjection p, int level, int variant) {
        double dx = (variant % 3 - 1) * 0.6;
        boolean shutters = variant % 2 == 0;
        // The path from the door to the street, and a pool or a bed of flowers behind.
        Art.with(g, p.ground(0), f -> {
            f.setColor(new Color(0xC8C0AC));
            f.fill(Art.rect(7 + dx, 10, 2, 6));
            if (level == 1 && variant == 2) {
                f.setColor(new Color(0xE8E4DA));
                f.fill(new RoundRectangle2D.Double(12.2, 1.6, 2.8, 5.8, 1, 1));
                f.setPaint(new GradientPaint(12.5f, 2, new Color(0x3A9AC8), 15, 7, new Color(0x8AD8F0)));
                f.fill(Art.rect(12.5, 2, 2.2, 5));
            }
        });
        if (level == 1) {
            Art.gabled(g, p, 3 + dx, 3.5, 12.5 + dx, 10, 6, 4.5, wall(variant, 0), roof(variant, 1), variant % 3 == 0, shutters);
        } else if (level == 2) {
            if (variant % 2 == 0) {
                Art.gabled(g, p, 1.6, 2.5, 14.4, 10.5, 10, 5.5, wall(variant, 2), roof(variant, 0), true, shutters);
            } else {
                Art.gabled(g, p, 1.4, 3, 7.8, 10.5, 9, 5, wall(variant, 1), roof(variant, 2), false, false);
                Art.gabled(g, p, 8.2, 3, 14.6, 10.5, 9, 5, wall(variant, 3), roof(variant, 2), false, true);
            }
        } else {
            Art.building(g, p, 2, 2, 14, 13, apartment(variant), wall(variant, 4), 0x86847E, Art.Style.APARTMENT,
                    40 + variant);
        }
        // A hedge along the side, a car or a tree in the garden, the fence at the front.
        Art.box(g, p, 15.2, 1, 16, 15, 0, 2, 0x3E7A2E, 0x2E5E22, 0x24481A);
        if (level < 3 && variant == 1) Art.car(g, p, 13.4, 12.6, 2, Art.CAR_COLOURS[(variant * 3 + level) % Art.CAR_COLOURS.length]);
        if (variant == 0 || variant == 3 || level == 3) {
            Art.tree(g, p, 3, 13, false, level == 3 ? 0.42 : 0.38, variant * 7L + level);
        }
        if (level == 3) {
            Art.box(g, p, 1, 14.4, 7, 15.4, 0, 1.6, 0x3E7A2E, 0x2E5E22, 0x24481A);
            Art.box(g, p, 9, 14.4, 15, 15.4, 0, 1.6, 0x3E7A2E, 0x2E5E22, 0x24481A);
        } else {
            Art.garden(g, p, 0.8, 6.4 + dx, 15.2, false);
            Art.garden(g, p, 9.6 + dx, 15, 15.2, false);
        }
    }
    // --8<-- [end:home]

    private static void shop(Graphics2D g, IsoProjection p, int level, int variant) {
        if (level == 1) {
            Art.building(g, p, 2, 3, 14, 13, 9, wall(variant, 1), 0x9A968E, Art.Style.SHOP, 10 + variant);
            Art.car(g, p, 6 + variant * 1.5, 14.6, variant % 2, Art.CAR_COLOURS[(variant * 5 + 1) % Art.CAR_COLOURS.length]);
        } else if (level == 2) {
            Art.building(g, p, 1.5, 2, 14.5, 13, 16, wall(variant, 3), 0x8E8A82, Art.Style.SHOP, 20 + variant);
        } else if (variant % 2 == 0) {
            Art.building(g, p, 2, 2, 14, 14, 36, wall(variant, 5), 0x7A7A76, Art.Style.OFFICE, 30 + variant);
        } else {
            Art.building(g, p, 3, 3, 13, 13, 56, 0, 0x6A6E74, Art.Style.GLASS, 30 + variant);
        }
        if (level >= 2) {
            // Planters at the front.
            Art.box(g, p, 2, 14.6, 4, 15.6, 0, 1.4, 0x6A8A3A, 0x8A8070, 0x6A6050);
            Art.box(g, p, 12, 14.6, 14, 15.6, 0, 1.4, 0x6A8A3A, 0x8A8070, 0x6A6050);
        }
    }

    private static void works(Graphics2D g, IsoProjection p, int level, int variant) {
        int shed = variant % 2 == 0 ? 0xB8B4A8 : 0xA85A42;
        if (level == 1) {
            Art.building(g, p, 2, 3, 12, 13, 8, shed, 0x8A8A8A, Art.Style.FACTORY, 50 + variant);
            Art.tank(g, p, 13.5, 6, 1.6, 6);
            crates(g, p, 12.6, 10.5);
        } else if (level == 2) {
            Art.building(g, p, 1.5, 2, 12.5, 13, 11, 0xA85A42, 0x6A6A6A, Art.Style.FACTORY, 60 + variant);
            Art.stack(g, p, 14, 4, 1.4, 30, false);
            Art.lorry(g, p, 14, 11.5, 2);
        } else {
            Art.building(g, p, 1, 1.5, 11.5, 14.5, 14, 0xB8B4A8, 0x8A8A8A, Art.Style.FACTORY, 70 + variant);
            Art.stack(g, p, 13.8, 3, 1.7, 40, true);
            Art.tank(g, p, 13.5, 11, 2, 8);
        }
    }

    private static void crates(Graphics2D g, IsoProjection p, double x, double y) {
        Art.box(g, p, x, y, x + 2, y + 2, 0, 2, 0xB88A5A, 0xA07848, 0x7A5A34);
        Art.box(g, p, x + 0.6, y + 2.4, x + 2.4, y + 4.2, 0, 1.6, 0x5A7AB0, 0x4A6A9A, 0x34507A);
    }

    /** A sign on an empty lot: zoned, waiting for someone to build. */
    static void sign(Graphics2D g, double x, double y, int colour) {
        g.setColor(new Color(10, 20, 5, 70));
        g.fill(Art.rect(x + 1, y, 6, 1.6));
        g.setColor(new Color(0x5A4632));
        g.fill(Art.rect(x - 0.3, y - 6, 0.7, 6));
        g.fill(Art.rect(x + 4.6, y - 6, 0.7, 6));
        g.setColor(new Color(0xF4F0E4));
        g.fill(Art.rect(x - 1, y - 8.5, 7, 4.4));
        g.setColor(Art.col(colour));
        g.fill(Art.rect(x - 1, y - 8.5, 7, 1.4));
        g.setColor(new Color(0x6A6A6A));
        g.fill(Art.rect(x, y - 6.4, 5, 0.5));
        g.fill(Art.rect(x, y - 5.4, 3.5, 0.5));
    }
}
