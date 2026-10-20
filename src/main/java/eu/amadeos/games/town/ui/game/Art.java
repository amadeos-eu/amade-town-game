package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.iso.IsoProjection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * The drawings of the town in the isometric view, in vector shapes: houses, shops, offices, factories, the
 * plants and towers, the services, trees, pylons and cars. Every drawing takes an {@link IsoProjection} and
 * draws in world units, a tile being 16 a side: a house from (2, 3) to (14, 12) stands on one tile. The
 * walls are drawn flat, with their windows and doors, through the projection's wall transforms, and Java2D
 * shears them into place; roofs and yards are drawn flat through its ground transform.
 *
 * <p>The light comes from the top left: the walls facing +y (left on the screen) are lit, those facing +x
 * (right) are in shade, and shadows fall along +x.
 */
final class Art {
    /** How long a shadow is along +x for each unit of height. */
    static final double SHADOW = 0.8;
    static final int ZONE_R = 0x58C858;
    static final int ZONE_C = 0x4A9AF0;
    static final int ZONE_I = 0xF0C040;
    static final int[] WALLS = {0xF2EEE4, 0xEADCC0, 0xD8E0E4, 0xF0E0C8, 0xE4CCB4, 0xE8E4D0};
    static final int[] ROOFS = {0xA8482E, 0x5A5E66, 0x8A5A3A, 0x6A3A2A, 0x4A5A6A};
    static final int[] CAR_COLOURS = {0xC83A2E, 0x2E5A9A, 0xE8E4DC, 0x3A3A3E, 0xE8B030, 0x4A8A5A, 0x9A9AA0, 0x6A3A7A};
    static final int[] AWNINGS = {0xC8382E, 0x2E7A4A, 0x2E5A9A, 0xD8902A, 0x8A3A7A, 0x3A8A8A};

    /** The styles of a flat roofed building's walls. */
    enum Style { APARTMENT, SHOP, OFFICE, GLASS, FACTORY, HALL, FIRE, SCHOOL, PLANT, PUMP, LIBRARY, CLINIC, PLAIN }

    interface Draw {
        void draw(Graphics2D g);
    }

    private Art() {
    }

    // ---- colours and shapes ----------------------------------------------------------------------

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static int shade(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f);
        int gr = (int) Math.min(255, ((c >> 8) & 255) * f);
        int b = (int) Math.min(255, (c & 255) * f);
        return (r << 16) | (gr << 8) | b;
    }

    static int mix(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int gr = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (gr << 8) | b;
    }

    static Color col(int c) {
        return new Color(c);
    }

    static Color col(int c, int alpha) {
        return new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha);
    }

    static Rectangle2D rect(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h);
    }

    static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 1).deriveFont(size);
    }

    static RadialGradientPaint radial(Point2D p, double r, float[] f, Color[] c) {
        return new RadialGradientPaint(p, (float) r, f, c);
    }

    /** A number from 0 to 1 for a pair of whole numbers and a seed, always the same. */
    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static double noise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static double fbm(double x, double y, int seed) {
        double v = 0, amp = 0.5, f = 1;
        for (int i = 0; i < 4; i++) {
            v += amp * noise(x * f, y * f, seed + i);
            amp *= 0.5;
            f *= 2;
        }
        return v;
    }

    /**
     * Noise that repeats every {@code period} units in both directions: a ground tile drawn with it fits the
     * tiles around it that are drawn with it, so a few dozen tile pictures make a meadow without seams.
     */
    static double noise(double x, double y, int period, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(xi, period), x1 = Math.floorMod(xi + 1, period);
        int y0 = Math.floorMod(yi, period), y1 = Math.floorMod(yi + 1, period);
        double a = hash(x0, y0, seed), b = hash(x1, y0, seed), c = hash(x0, y1, seed), d = hash(x1, y1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    /** Four octaves of {@link #noise(double, double, int, int)}, each repeating at the same place. */
    static double fbm(double x, double y, int period, int seed) {
        double v = 0, amp = 0.5, f = 1;
        for (int i = 0; i < 4; i++) {
            v += amp * noise(x * f, y * f, (int) (period * f), seed + i);
            amp *= 0.5;
            f *= 2;
        }
        return v;
    }

    static double smoothstep(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static Point2D at(IsoProjection p, double x, double y, double h) {
        return new Point2D.Double(p.pictureX(x, y), p.pictureY(x, y, h));
    }

    static Path2D poly(Point2D... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0].getX(), points[0].getY());
        for (int i = 1; i < points.length; i++) path.lineTo(points[i].getX(), points[i].getY());
        path.closePath();
        return path;
    }

    static void with(Graphics2D g, AffineTransform t, Draw d) {
        AffineTransform before = g.getTransform();
        g.transform(t);
        d.draw(g);
        g.setTransform(before);
    }

    /** A plain box: its two visible walls and its top. */
    static void box(Graphics2D g, IsoProjection p, double x0, double y0, double x1, double y1, double h0, double h1,
                    int top, int left, int right) {
        g.setColor(col(left));
        g.fill(poly(at(p, x0, y1, h0), at(p, x1, y1, h0), at(p, x1, y1, h1), at(p, x0, y1, h1)));
        g.setColor(col(right));
        g.fill(poly(at(p, x1, y1, h0), at(p, x1, y0, h0), at(p, x1, y0, h1), at(p, x1, y1, h1)));
        g.setColor(col(top));
        g.fill(poly(at(p, x0, y0, h1), at(p, x1, y0, h1), at(p, x1, y1, h1), at(p, x0, y1, h1)));
    }

    // ---- houses ----------------------------------------------------------------------------------

    // --8<-- [start:gabled]
    /** A gabled house: the ridge runs along x, the gable faces +x, the door faces +y. */
    static void gabled(Graphics2D g, IsoProjection p, double x0, double y0, double x1, double y1, double wall,
                       double rh, int wallC, int roofC, boolean slate, boolean shutters) {
        Random rnd = new Random((long) (x0 * 131 + y0 * 17 + wallC));
        double ym = (y0 + y1) / 2, top = wall + rh, ov = 1.2, eave = wall - 0.5, len = x1 - x0, dep = y1 - y0;
        // The gable wall, in shade.
        g.setColor(col(shade(wallC, 0.74)));
        g.fill(poly(at(p, x1, y1, 0), at(p, x1, y0, 0), at(p, x1, y0, wall), at(p, x1, ym, top), at(p, x1, y1, wall)));
        with(g, p.sideWall(x1, y1), s -> window(s, dep / 2, -wall * 0.7, shutters, 0.7));
        // The front wall with its door and windows, drawn flat and sheared into place.
        with(g, p.frontWall(x0, y1), f -> {
            f.setPaint(new GradientPaint(0, (float) -wall, col(wallC), 0, 0, col(shade(wallC, 0.8))));
            f.fill(rect(0, -wall, len, wall));
            f.setColor(col(shade(wallC, 0.6)));
            f.fill(rect(0, -0.6, len, 0.6));
            double dx = len * 0.5;
            f.setPaint(new GradientPaint(0, (float) (-wall * 0.7), new Color(0x7A4A2A), 0, 0, new Color(0x4A2A14)));
            f.fill(rect(dx - 1.6, -wall * 0.7, 3.2, wall * 0.7));
            window(f, len * 0.2, -wall * 0.72, shutters, 0.8);
            window(f, len * 0.8, -wall * 0.72, shutters, 0.8);
            f.setPaint(new GradientPaint(0, (float) -wall, new Color(0, 0, 0, 100), 0, (float) (-wall + 1.6), new Color(0, 0, 0, 0)));
            f.fill(rect(0, -wall, len, 1.6));
        });
        // The roof: the far slope, then the near slope over it.
        Point2D nA = at(p, x0 - ov, y0 - ov, eave), nB = at(p, x1 + ov, y0 - ov, eave);
        Point2D rA = at(p, x0 - ov, ym, top), rB = at(p, x1 + ov, ym, top);
        Point2D sA = at(p, x0 - ov, y1 + ov, eave), sB = at(p, x1 + ov, y1 + ov, eave);
        g.setColor(col(shade(roofC, 1.25)));
        g.fill(poly(nA, nB, rB, rA));
        Path2D near = poly(rA, rB, sB, sA);
        g.setPaint(new GradientPaint((float) rA.getX(), (float) rA.getY(), col(shade(roofC, 1.05)), (float) sA.getX(),
                (float) sA.getY(), col(shade(roofC, 0.78))));
        g.fill(near);
        Shape clip = g.getClip();
        g.clip(near);
        g.setStroke(new BasicStroke(0.25f));
        int rows = slate ? 6 : 4;
        for (int i = 1; i < rows; i++) {
            double t = i / (double) rows, yy = ym + t * (y1 + ov - ym), hh = top - t * (top - eave);
            g.setColor(new Color(0, 0, 0, 60));
            g.draw(new Line2D.Double(at(p, x0 - ov, yy, hh), at(p, x1 + ov, yy, hh)));
            double t2 = (i + 1) / (double) rows, yy2 = ym + t2 * (y1 + ov - ym), hh2 = top - t2 * (top - eave);
            double step = slate ? 1.8 + rnd.nextDouble() : 2.2;
            for (double k = x0 - ov + (i % 2) * step / 2; k < x1 + ov; k += step) {
                g.setColor(new Color(0, 0, 0, 40));
                g.draw(new Line2D.Double(at(p, k, yy, hh), at(p, k, yy2, hh2)));
            }
        }
        g.setClip(clip);
        double cx = x0 + len * 0.72, cy = ym - 1.6;
        box(g, p, cx, cy, cx + 2, cy + 2, top - 2.4, top + 1.6, 0x2A1E18, 0xA8583E, 0x7A3A26);
        g.setColor(col(shade(roofC, 0.5)));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(rA, rB));
        g.setColor(col(shade(roofC, 0.45)));
        g.setStroke(new BasicStroke(0.6f));
        g.draw(new Line2D.Double(sA, sB));
        g.draw(new Line2D.Double(rB, sB));
        g.draw(new Line2D.Double(nB, rB));
    }
    // --8<-- [end:gabled]

    static void window(Graphics2D g, double cx, double y, boolean shutters, double s) {
        double w = 3.2 * s, h = 3 * s;
        g.setColor(new Color(0xF4F0E8));
        g.fill(rect(cx - w / 2 - 0.4, y - 0.4, w + 0.8, h + 0.8));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0x2A3A48), 0, (float) (y + h), new Color(0x7A9AB0)));
        g.fill(rect(cx - w / 2, y, w, h));
        if (shutters) {
            g.setColor(new Color(0x3A5A6A));
            g.fill(rect(cx - w / 2 - 1.5, y - 0.2, 1, h + 0.4));
            g.fill(rect(cx + w / 2 + 0.5, y - 0.2, 1, h + 0.4));
        }
    }

    /** A hedge or a low fence along the front of a garden, and a path to the door. */
    static void garden(Graphics2D g, IsoProjection p, double x0, double x1, double y, boolean hedge) {
        if (hedge) {
            box(g, p, x0, y - 1.2, x1, y, 0, 2, 0x3E7A2E, 0x2E5E22, 0x24481A);
            return;
        }
        g.setColor(new Color(0xF4F2EC));
        g.setStroke(new BasicStroke(0.35f));
        g.draw(new Line2D.Double(at(p, x0, y, 1.8), at(p, x1, y, 1.8)));
        g.draw(new Line2D.Double(at(p, x0, y, 0.9), at(p, x1, y, 0.9)));
        for (double k = x0; k <= x1; k += 2) g.draw(new Line2D.Double(at(p, k, y, 0), at(p, k, y, 2.2)));
    }

    // ---- flat roofed buildings -------------------------------------------------------------------

    // --8<-- [start:building]
    /** A flat roofed building: the front wall faces +y (left on the screen), the side wall +x (right). */
    static void building(Graphics2D g, IsoProjection p, double x0, double y0, double x1, double y1, double h,
                         int wallC, int roofC, Style style, int seed) {
        double len = x1 - x0, dep = y1 - y0;
        with(g, p.frontWall(x0, y1), f -> wall(f, len, h, wallC, style, new Random(seed), true));
        with(g, p.sideWall(x1, y1), s -> {
            wall(s, dep, h, wallC, style, new Random(seed + 1), false);
            s.setColor(new Color(0, 0, 20, 70));
            s.fill(rect(0, -h, dep, h));
        });
        g.setColor(new Color(255, 255, 255, 50));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(new Line2D.Double(at(p, x1, y1, 0), at(p, x1, y1, h)));
        with(g, p.ground(h), r -> roof(r, x0, y0, len, dep, roofC, style, new Random(seed + 2)));
    }
    // --8<-- [end:building]

    /** One wall in its own flat coordinates: u along the wall from 0 to {@code len}, the foot at 0, the top at -z. */
    static void wall(Graphics2D g, double len, double z, int wallC, Style style, Random rnd, boolean front) {
        double top = -z;
        if (style == Style.GLASS) {
            g.setPaint(new GradientPaint(0, (float) top, new Color(0x9AC0D8), (float) (len * 0.6), 0, new Color(0x2A4A68)));
            g.fill(rect(0, top, len, z));
            g.setColor(new Color(255, 255, 255, 45));
            Path2D streak = new Path2D.Double();
            streak.moveTo(len * 0.15, top);
            streak.lineTo(len * 0.38, top);
            streak.lineTo(len * 0.12, 0);
            streak.lineTo(0, 0);
            streak.lineTo(0, top + z * 0.5);
            streak.closePath();
            g.fill(streak);
            g.setColor(new Color(0x1C2C3C));
            g.setStroke(new BasicStroke(0.3f));
            for (double fy = top + 4; fy < -1; fy += 4) g.draw(new Line2D.Double(0, fy, len, fy));
            g.setColor(new Color(0xC8D8E0));
            for (double mx = 2.5; mx < len - 0.5; mx += 2.5) g.draw(new Line2D.Double(mx, top, mx, 0));
            g.setColor(new Color(0x3A4A58));
            g.fill(rect(0, -4, len, 4));
            if (front) {
                g.setColor(new Color(0xE8D8A0));
                g.fill(rect(len / 2 - 2.5, -3.4, 5, 3.4));
            }
            return;
        }
        g.setPaint(new GradientPaint(0, (float) top, col(wallC), 0, 0, col(shade(wallC, 0.76))));
        g.fill(rect(0, top, len, z));
        if (style == Style.FACTORY || style == Style.FIRE || style == Style.PLANT || style == Style.LIBRARY) {
            g.setStroke(new BasicStroke(0.2f));
            g.setColor(new Color(0, 0, 0, 40));
            for (double by = top + 1; by < 0; by += 1.1) g.draw(new Line2D.Double(0, by, len, by));
        }
        int floors = Math.max(1, (int) Math.round(z / 6.5));
        double fh = z / floors;
        if (style == Style.LIBRARY) {
            for (double cx = 3.5; cx < len - 2; cx += 5) {
                if (front && Math.abs(cx - len / 2) < 4) continue;
                double wy = top + z * 0.25, wh = z * 0.55;
                g.setColor(new Color(0xE8DCC0));
                g.fill(arched(cx - 1.9, wy - 0.4, 3.8, wh + 0.6));
                g.setPaint(new GradientPaint(0, (float) wy, new Color(0x2A3A4A), 0, (float) (wy + wh), new Color(0xC8A060)));
                g.fill(arched(cx - 1.5, wy, 3, wh));
            }
        }
        for (int f = 0; f < floors; f++) {
            double fy = top + f * fh;
            boolean ground = f == floors - 1;
            switch (style) {
                case APARTMENT, OFFICE, SCHOOL, HALL, CLINIC -> {
                    if (ground && front && (style == Style.HALL || style == Style.OFFICE)) break;
                    double step = style == Style.OFFICE ? 3.4 : 4.4;
                    for (double cx = 2.8; cx < len - 1.6; cx += step) {
                        if (ground && front && Math.abs(cx - len / 2) < 3) continue;
                        boolean lit = rnd.nextDouble() < 0.25;
                        g.setColor(col(shade(wallC, 0.55)));
                        g.fill(rect(cx - 1.5, fy + fh * 0.22 - 0.3, 3, fh * 0.5 + 0.6));
                        g.setPaint(new GradientPaint(0, (float) (fy + fh * 0.22), lit ? new Color(0x8A7A50) : new Color(0x2A3A4A),
                                0, (float) (fy + fh * 0.72), lit ? new Color(0xC8B070) : new Color(0x7A98B0)));
                        g.fill(rect(cx - 1.2, fy + fh * 0.22, 2.4, fh * 0.5));
                        if (style == Style.APARTMENT && !ground && rnd.nextDouble() < 0.45) {
                            g.setColor(new Color(0xE8E4DC));
                            g.fill(rect(cx - 2, fy + fh * 0.62, 4, 1.2));
                        }
                    }
                }
                case SHOP -> {
                    if (!ground || !front) {
                        for (double cx = 3.2; cx < len - 1.6; cx += 5) {
                            g.setColor(new Color(0xF4F0E8));
                            g.fill(rect(cx - 1.6, fy + fh * 0.25 - 0.3, 3.2, fh * 0.5 + 0.6));
                            g.setPaint(new GradientPaint(0, (float) (fy + fh * 0.25), new Color(0x2A3A4A), 0,
                                    (float) (fy + fh * 0.75), new Color(0x7A98B0)));
                            g.fill(rect(cx - 1.3, fy + fh * 0.25, 2.6, fh * 0.5));
                        }
                    }
                }
                case FACTORY, PLANT -> {
                    if (f == 0) {
                        for (double cx = 2.4; cx < len - 2.4; cx += 4.2) {
                            g.setColor(new Color(0x3A4048));
                            g.fill(rect(cx, fy + 1, 2.8, 2));
                            g.setColor(new Color(255, 255, 255, 40));
                            g.fill(rect(cx, fy + 1, 2.8, 0.5));
                        }
                    }
                }
                default -> {
                }
            }
        }
        if (!front) return;
        switch (style) {
            case SHOP -> shopFront(g, len, Math.min(fh, 6), rnd);
            case FACTORY -> {
                double dx = len * 0.62;
                g.setColor(new Color(0x4A4E54));
                g.fill(rect(dx, -5.4, Math.min(7, len * 0.3), 5.4));
                g.setColor(new Color(0x6A6E74));
                g.setStroke(new BasicStroke(0.25f));
                for (double k = -4.9; k < 0; k += 0.9) g.draw(new Line2D.Double(dx, k, dx + Math.min(7, len * 0.3), k));
            }
            case FIRE -> {
                for (int i = 0; i < 2; i++) {
                    double dw = (len - 6) / 2 - 1.5, dx = 3 + i * (dw + 1.5) + 0.5;
                    g.setColor(new Color(0xF2EEE4));
                    g.fill(rect(dx - 0.5, -7, dw + 1, 7));
                    g.setColor(i == 0 ? new Color(0x1A1A1C) : new Color(0xC8C4BC));
                    g.fill(rect(dx, -6.5, dw, 6.5));
                }
                plate(g, len / 2, top + 1.2, "FIRE", 0xF2EEE4, 0xB82A1E);
            }
            case SCHOOL -> {
                door(g, len / 2, 4.4);
                plate(g, len / 2, top + 1.1, "SCHOOL", 0x2A5AB0, 0xF4F0E4);
            }
            case LIBRARY -> {
                door(g, len / 2, 4.4);
                g.setColor(new Color(0xE8DCC0));
                g.fill(rect(len / 2 - 4, -6.4, 8, 1.2));
                plate(g, len / 2, top + 1, "LIBRARY", 0x2A4A3A, 0xF0E4C0);
            }
            case CLINIC -> {
                door(g, len / 2, 4.4);
                plate(g, len / 2, top + 1, "CLINIC", 0xF4F4F0, 0xC8281E);
            }
            case APARTMENT -> door(g, len / 2, 3.2);
            case PUMP -> door(g, len * 0.3, 2.6);
            case HALL, OFFICE -> door(g, len / 2, 4.4);
            default -> {
            }
        }
    }

    static Shape arched(double x, double y, double w, double h) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + h);
        p.lineTo(x, y + w / 2);
        p.append(new Arc2D.Double(x, y, w, w, 180, -180, Arc2D.OPEN), true);
        p.lineTo(x + w, y + h);
        p.closePath();
        return p;
    }

    /** A name board on a facade. */
    static void plate(Graphics2D g, double cx, double y, String text, int bg, int fg) {
        g.setFont(font(Font.BOLD, 3f));
        double tw = g.getFontMetrics().getStringBounds(text, g).getWidth();
        g.setColor(col(bg));
        g.fill(rect(cx - tw / 2 - 1, y, tw + 2, 3.8));
        g.setColor(col(fg));
        g.drawString(text, (float) (cx - tw / 2), (float) (y + 3));
    }

    static void door(Graphics2D g, double cx, double dw) {
        g.setColor(new Color(0x3A3430));
        g.fill(rect(cx - dw / 2 - 0.4, -4.6, dw + 0.8, 4.6));
        g.setPaint(new GradientPaint(0, -4.2f, new Color(0x4A6A80), 0, 0, new Color(0x2A3A48)));
        g.fill(rect(cx - dw / 2, -4.2, dw, 4.2));
    }

    static void shopFront(Graphics2D g, double w, double fh, Random rnd) {
        double gy = -fh + 1.2;
        g.setPaint(new GradientPaint(0, (float) gy, new Color(0x1E2A34), 0, 0, new Color(0x4A6274)));
        g.fill(rect(1.2, gy, w - 2.4, -gy - 0.5));
        for (int i = 0; i < w / 2.5; i++) {
            g.setColor(col(new int[]{0xE8C060, 0xD86050, 0x80B060, 0xF0F0E0, 0x6090D0}[rnd.nextInt(5)], 200));
            g.fill(rect(2 + rnd.nextDouble() * (w - 5), -2.2 - rnd.nextDouble() * 1.4, 0.9, 0.9));
        }
        g.setColor(new Color(0x2A2420));
        g.fill(rect(w * 0.4 + 0.5, gy + 0.5, 2.6, -gy - 0.5));
        int ac = AWNINGS[rnd.nextInt(AWNINGS.length)];
        double ay = gy - 1.4;
        for (double k = 0.4; k < w - 0.4; k += 1.8) {
            g.setColor(((int) (k / 1.8)) % 2 == 0 ? col(ac) : new Color(0xF4F0E4));
            g.fill(rect(k, ay, Math.min(1.8, w - 0.4 - k), 2.2));
        }
        double sw = Math.min(w - 5, 14), sx = (w - sw) / 2, sy = ay - 2.8;
        g.setColor(col(shade(ac, 0.6)));
        g.fill(rect(sx, sy, sw, 2.4));
        g.setColor(new Color(0xFFF4D8));
        for (double k = sx + 1; k < sx + sw - 1.2; k += 1.4 + rnd.nextDouble()) g.fill(rect(k, sy + 0.8, 0.8 + rnd.nextDouble() * 0.5, 0.8));
    }

    /** A flat roof in world coordinates, lifted to the building's height. */
    static void roof(Graphics2D g, double x, double y, double w, double d, int roofC, Style style, Random rnd) {
        g.setColor(col(roofC));
        g.fill(rect(x, y, w, d));
        for (int i = 0; i < w * d / 3; i++) {
            g.setColor(rnd.nextBoolean() ? new Color(255, 255, 255, 30) : new Color(0, 0, 0, 30));
            g.fill(rect(x + rnd.nextDouble() * w, y + rnd.nextDouble() * d, 0.5, 0.5));
        }
        if (style == Style.FACTORY) {
            sawtooth(g, x, y, w, d);
            return;
        }
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(col(shade(roofC, 0.7)));
        g.draw(rect(x + 1, y + 1, w - 2, d - 2));
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(col(shade(roofC, 1.3)));
        g.draw(new Line2D.Double(x, y + 0.25, x + w, y + 0.25));
        g.draw(new Line2D.Double(x + 0.25, y, x + 0.25, y + d));
        if (style == Style.APARTMENT || style == Style.OFFICE || style == Style.SHOP || style == Style.SCHOOL
                || style == Style.FIRE || style == Style.CLINIC) {
            int units = 1 + (int) (w * d / 220);
            for (int i = 0; i < units; i++) {
                double ux = x + 2 + rnd.nextDouble() * Math.max(1, w - 7), uy = y + 2 + rnd.nextDouble() * Math.max(1, d - 6);
                unit(g, ux, uy, rnd.nextBoolean());
            }
        }
        if (style == Style.CLINIC) {
            double cx = x + w / 2, cy = y + d / 2;
            g.setColor(new Color(0xF4F4F0));
            g.fill(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
            g.setColor(new Color(0xC8281E));
            g.fill(rect(cx - 1, cy - 3.5, 2, 7));
            g.fill(rect(cx - 3.5, cy - 1, 7, 2));
        }
        if (style == Style.LIBRARY) {
            g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xB8D8E8), (float) (x + w), (float) (y + d), new Color(0x4A7A98)));
            g.fill(rect(x + w * 0.3, y + d * 0.3, w * 0.4, d * 0.4));
            g.setColor(new Color(0x3A4A58));
            g.setStroke(new BasicStroke(0.25f));
            for (double k = x + w * 0.3; k <= x + w * 0.7; k += 2) g.draw(new Line2D.Double(k, y + d * 0.3, k, y + d * 0.7));
        }
        if (style == Style.GLASS) {
            double hx = x + w / 2, hy = y + d / 2, r = Math.min(w, d) * 0.35;
            g.setColor(new Color(0x5A5E64));
            g.fill(new Ellipse2D.Double(hx - r, hy - r, r * 2, r * 2));
            g.setColor(new Color(0xF0F0E8));
            g.setStroke(new BasicStroke(0.5f));
            g.draw(new Ellipse2D.Double(hx - r * 0.8, hy - r * 0.8, r * 1.6, r * 1.6));
        }
    }

    static void sawtooth(Graphics2D g, double x, double y, double w, double d) {
        for (double k = x; k < x + w - 0.5; k += 4.5) {
            double s = Math.min(4.5, x + w - k);
            g.setPaint(new GradientPaint((float) k, 0, new Color(0x9A9A9A), (float) (k + s), 0, new Color(0x6A6A6A)));
            g.fill(rect(k, y, s, d));
            g.setPaint(new GradientPaint((float) k, 0, new Color(0x9AC8E0), (float) (k + 1.5), 0, new Color(0x4A7A98)));
            g.fill(rect(k, y + 0.5, 1.5, d - 1));
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(rect(k + s - 0.4, y, 0.4, d));
        }
    }

    static void unit(Graphics2D g, double x, double y, boolean fan) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(rect(x + 1, y, 3.6, 3));
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0xD8D8D4), (float) (x + 3.6), (float) (y + 3), new Color(0x8A8A86)));
        g.fill(rect(x, y, 3.6, 3));
        if (fan) {
            g.setColor(new Color(0x4A4A4A));
            g.fill(new Ellipse2D.Double(x + 0.8, y + 0.5, 2, 2));
        }
    }

    // ---- civic pieces ----------------------------------------------------------------------------

    static void portico(Graphics2D g, IsoProjection p, double x0, double x1, double y0, double y1, double z) {
        box(g, p, x0 - 1.5, y1 - 1, x1 + 1.5, y1 + 1.6, 0, 0.8, 0xC8C0AC, 0xB0A894, 0x9A927E);
        box(g, p, x0, y0, x1, y1, z - 1.2, z + 0.8, 0xE8E0CC, 0xD8D0BC, 0xB0A894);
        for (double x = x0 + 0.8; x < x1; x += 3) box(g, p, x, y1 - 1.3, x + 1.3, y1, 0.8, z - 1.2, 0xF4F0E4, 0xF0ECE0, 0xB8B09C);
        Point2D a = at(p, x0, y1, z + 0.8), b = at(p, x1, y1, z + 0.8), apex = at(p, (x0 + x1) / 2, y1, z + 5);
        g.setPaint(new GradientPaint((float) apex.getX(), (float) apex.getY(), new Color(0xF4F0E4), (float) a.getX(),
                (float) a.getY(), new Color(0xC8C0AC)));
        g.fill(poly(a, b, apex));
    }

    static void clockTower(Graphics2D g, IsoProjection p, double x0, double y0, double x1, double y1, double base) {
        double tz = base + 14;
        with(g, p.frontWall(x0, y1), f -> {
            f.setPaint(new GradientPaint(0, (float) -tz, new Color(0xF0E8D4), 0, (float) -base, new Color(0xC8C0AC)));
            f.fill(rect(0, -tz, x1 - x0, tz - base));
            f.setColor(new Color(0xF8F8F0));
            f.fill(new Ellipse2D.Double((x1 - x0) / 2 - 2.5, -tz + 2.5, 5, 5));
            f.setColor(new Color(0x2A2A2A));
            f.setStroke(new BasicStroke(0.35f));
            f.draw(new Ellipse2D.Double((x1 - x0) / 2 - 2.5, -tz + 2.5, 5, 5));
            f.draw(new Line2D.Double((x1 - x0) / 2, -tz + 5, (x1 - x0) / 2, -tz + 3.3));
        });
        with(g, p.sideWall(x1, y1), s -> {
            s.setColor(new Color(0xA8A08C));
            s.fill(rect(0, -tz, y1 - y0, tz - base));
        });
        Point2D apex = at(p, (x0 + x1) / 2, (y0 + y1) / 2, tz + 10);
        g.setColor(new Color(0x7AB89A));
        g.fill(poly(at(p, x0, y1, tz), at(p, x1, y1, tz), apex));
        g.setColor(new Color(0x3A7A62));
        g.fill(poly(at(p, x1, y1, tz), at(p, x1, y0, tz), apex));
    }

    static void flagpole(Graphics2D g, IsoProjection p, double wx, double wy, int colour) {
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY();
        g.setColor(new Color(0xD8D8D8));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(new Line2D.Double(x, y, x, y - 18));
        Path2D f = new Path2D.Double();
        f.moveTo(x, y - 18);
        f.curveTo(x + 2.5, y - 19, x + 4, y - 17, x + 6.5, y - 17.8);
        f.lineTo(x + 6.5, y - 14.6);
        f.curveTo(x + 4, y - 13.8, x + 2.5, y - 15.8, x, y - 15);
        f.closePath();
        g.setColor(col(colour));
        g.fill(f);
    }

    // ---- industry and utilities ------------------------------------------------------------------

    static void stack(Graphics2D g, IsoProjection p, double wx, double wy, double r, double h, boolean bands) {
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY(), rx = r * 1.3;
        g.setPaint(new GradientPaint((float) (x - rx), 0, bands ? new Color(0xE8E4DC) : new Color(0xB86A50),
                (float) (x + rx), 0, bands ? new Color(0x8A8680) : new Color(0x6A3424)));
        g.fill(rect(x - rx, y - h, rx * 2, h));
        g.fill(new Ellipse2D.Double(x - rx, y - rx * 0.5, rx * 2, rx));
        if (bands) {
            g.setColor(new Color(0xC83A2E));
            for (int i = 0; i < 3; i++) g.fill(rect(x - rx, y - h + 2 + i * 6, rx * 2, 2.4));
        } else {
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke(0.2f));
            for (double k = y - h + 1; k < y; k += 1.1) g.draw(new Line2D.Double(x - rx, k, x + rx, k));
        }
        g.setColor(new Color(0x3A3A3A));
        g.fill(new Ellipse2D.Double(x - rx, y - h - rx * 0.5, rx * 2, rx));
        g.setColor(new Color(0x101010));
        g.fill(new Ellipse2D.Double(x - rx * 0.7, y - h - rx * 0.35, rx * 1.4, rx * 0.7));
    }

    static void cooling(Graphics2D g, IsoProjection p, double wx, double wy, double r, double h) {
        Point2D c = at(p, wx, wy, 0);
        double cx = c.getX(), foot = c.getY(), rx = r * 1.414, top = foot - h;
        Path2D shape = new Path2D.Double();
        shape.moveTo(cx - rx, foot);
        shape.curveTo(cx - rx * 0.7, foot - h * 0.5, cx - rx * 0.66, top + h * 0.2, cx - rx * 0.74, top);
        shape.lineTo(cx + rx * 0.74, top);
        shape.curveTo(cx + rx * 0.66, top + h * 0.2, cx + rx * 0.7, foot - h * 0.5, cx + rx, foot);
        shape.closePath();
        g.setPaint(new GradientPaint((float) (cx - rx), 0, new Color(0xE0DCD4), (float) (cx + rx), 0, new Color(0x8A8680)));
        g.fill(shape);
        g.fill(new Ellipse2D.Double(cx - rx, foot - rx * 0.5, rx * 2, rx));
        g.setColor(new Color(0xC8C4BC));
        g.fill(new Ellipse2D.Double(cx - rx * 0.74, top - rx * 0.37, rx * 1.48, rx * 0.74));
        g.setColor(new Color(0x3A3A38));
        g.fill(new Ellipse2D.Double(cx - rx * 0.64, top - rx * 0.3, rx * 1.28, rx * 0.6));
    }

    static void tank(Graphics2D g, IsoProjection p, double wx, double wy, double r, double h) {
        Point2D c = at(p, wx, wy, 0);
        double cx = c.getX(), foot = c.getY(), rx = r * 1.414;
        g.setPaint(new GradientPaint((float) (cx - rx), 0, new Color(0xE8E8E4), (float) (cx + rx), 0, new Color(0x8A8A86)));
        g.fill(rect(cx - rx, foot - h, rx * 2, h));
        g.fill(new Ellipse2D.Double(cx - rx, foot - rx * 0.5, rx * 2, rx));
        g.setPaint(radial(new Point2D.Double(cx - rx * 0.3, foot - h - rx * 0.15), rx, new float[]{0, 1},
                new Color[]{new Color(0xF4F4F0), new Color(0xA8A8A4)}));
        g.fill(new Ellipse2D.Double(cx - rx, foot - h - rx * 0.5, rx * 2, rx));
    }

    // --8<-- [start:tower]
    static void waterTower(Graphics2D g, IsoProjection p, double wx, double wy) {
        double h = 22, r = 7.5;
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY();
        g.setColor(new Color(0x5A5E64));
        g.setStroke(new BasicStroke(0.8f));
        for (double[] leg : new double[][]{{-6, 1}, {6, 1}, {-3, -2.5}, {3, -2.5}}) {
            g.draw(new Line2D.Double(x + leg[0], y + leg[1], x + leg[0] * 0.5, y - h));
        }
        g.setStroke(new BasicStroke(0.35f));
        g.draw(new Line2D.Double(x - 4.5, y - 7, x + 4.5, y - 7));
        g.draw(new Line2D.Double(x - 3.7, y - 14, x + 3.7, y - 14));
        g.draw(new Line2D.Double(x, y, x, y - h));
        double cy = y - h - r + 2;
        g.setPaint(radial(new Point2D.Double(x - r * 0.35, cy - r * 0.4), r * 1.4, new float[]{0, 0.6f, 1},
                new Color[]{new Color(0xF4F8FA), new Color(0xB8C8D4), new Color(0x6A7A88)}));
        g.fill(new Ellipse2D.Double(x - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0x2A6A9A));
        g.fill(new Arc2D.Double(x - r, cy - 2, r * 2, 4, 180, 180, Arc2D.CHORD));
    }
    // --8<-- [end:tower]

    /** The mast and the nacelle of a wind turbine; its rotor turns and is drawn by {@link #rotor}. */
    static void turbine(Graphics2D g, IsoProjection p, double wx, double wy) {
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY();
        g.setColor(new Color(0x9A9A96));
        g.fill(new Ellipse2D.Double(x - 3, y - 1.5, 6, 3));
        g.setPaint(new GradientPaint((float) (x - 1.4), 0, new Color(0xF8F8F4), (float) (x + 1.4), 0, new Color(0xB8BCC0)));
        Path2D mast = new Path2D.Double();
        mast.moveTo(x - 1.4, y);
        mast.lineTo(x - 0.7, y - TURBINE_HUB);
        mast.lineTo(x + 0.7, y - TURBINE_HUB);
        mast.lineTo(x + 1.4, y);
        mast.closePath();
        g.fill(mast);
        g.setColor(new Color(0xE8ECEE));
        g.fill(new RoundRectangle2D.Double(x - 1.6, y - TURBINE_HUB - 1.6, 5, 3, 1.5, 1.5));
    }

    static final double TURBINE_HUB = 34;

    /** Three blades around the hub at {@code x}, {@code y}, turned by {@code angle}, seen a little from the side. */
    static void rotor(Graphics2D g, double x, double y, double angle) {
        g.setColor(new Color(0xF4F6F8));
        for (int i = 0; i < 3; i++) {
            double a = angle + i * Math.PI * 2 / 3;
            double dx = Math.cos(a) * 0.55, dy = Math.sin(a);
            double nx = -dy * 0.55 * 0.9, ny = dx / 0.55 * 0.55 * 0.9;
            Path2D blade = new Path2D.Double();
            blade.moveTo(x + nx, y + ny);
            blade.lineTo(x + dx * 15, y + dy * 15);
            blade.lineTo(x - nx * 0.5, y - ny * 0.5);
            blade.closePath();
            g.fill(blade);
        }
        g.setColor(new Color(0xC8CCD0));
        g.fill(new Ellipse2D.Double(x - 1.1, y - 1.1, 2.2, 2.2));
    }

    // --8<-- [start:pylon]
    static final double PYLON = 18;

    /** A lattice pylon on a point of the ground; its arms run square to the line, along x or along y. */
    static void pylon(Graphics2D g, IsoProjection p, double wx, double wy, boolean alongX) {
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY(), top = y - PYLON;
        g.setColor(new Color(0x6A6E74));
        g.setStroke(new BasicStroke(0.45f));
        g.draw(new Line2D.Double(x - 2.2, y, x - 0.5, top));
        g.draw(new Line2D.Double(x + 2.2, y, x + 0.5, top));
        for (double k = 0; k < 1; k += 0.25) {
            double y0 = y - PYLON * k, y1 = y - PYLON * (k + 0.25), w0 = 2.2 - 1.7 * k, w1 = 2.2 - 1.7 * (k + 0.25);
            g.draw(new Line2D.Double(x - w0, y0, x + w1, y1));
            g.draw(new Line2D.Double(x + w0, y0, x - w1, y1));
        }
        g.setStroke(new BasicStroke(0.6f));
        for (double[] arm : new double[][]{{4.5, 1}, {3.5, 4.5}}) {
            Point2D a = alongX ? at(p, wx, wy - arm[0], PYLON - arm[1]) : at(p, wx - arm[0], wy, PYLON - arm[1]);
            Point2D b = alongX ? at(p, wx, wy + arm[0], PYLON - arm[1]) : at(p, wx + arm[0], wy, PYLON - arm[1]);
            g.draw(new Line2D.Double(a, b));
        }
    }

    /** The four wires from one pylon to the next one along x or y, sagging between them. */
    static void wires(Graphics2D g, IsoProjection p, double ax, double ay, double bx, double by, boolean alongX) {
        g.setColor(new Color(0x2A2C30));
        g.setStroke(new BasicStroke(0.3f));
        for (double[] arm : new double[][]{{-4.5, 1}, {4.5, 1}, {-3.5, 4.5}, {3.5, 4.5}}) {
            double h = PYLON - arm[1] - 0.3;
            double ox = alongX ? 0 : arm[0], oy = alongX ? arm[0] : 0;
            Point2D a = at(p, ax + ox, ay + oy, h), b = at(p, bx + ox, by + oy, h);
            Point2D mid = at(p, (ax + bx) / 2 + ox, (ay + by) / 2 + oy, h - 3);
            g.draw(new QuadCurve2D.Double(a.getX(), a.getY(), 2 * mid.getX() - (a.getX() + b.getX()) / 2,
                    2 * mid.getY() - (a.getY() + b.getY()) / 2, b.getX(), b.getY()));
        }
    }
    // --8<-- [end:pylon]

    // ---- trees -----------------------------------------------------------------------------------

    static void tree(Graphics2D g, IsoProjection p, double wx, double wy, boolean conifer, double s, long seed) {
        Point2D foot = at(p, wx, wy, 0);
        double x = foot.getX(), y = foot.getY();
        Random rnd = new Random(seed);
        g.setColor(new Color(0x4A3424));
        g.fill(rect(x - 0.6 * s, y - 5 * s, 1.2 * s, 5 * s));
        if (conifer) {
            for (int layer = 0; layer < 4; layer++) {
                double base = y - 2.6 * s - layer * 3.6 * s, half = (5.5 - layer * 1.2) * s, tip = base - 6 * s;
                Path2D tier = new Path2D.Double();
                tier.moveTo(x - half, base);
                tier.quadTo(x, base + 1.4 * s, x + half, base);
                tier.lineTo(x + 0.3, tip);
                tier.closePath();
                g.setPaint(new GradientPaint((float) (x - half), 0, new Color(0x3E7A48), (float) (x + half), 0, new Color(0x163A22)));
                g.fill(tier);
            }
            return;
        }
        double cy = y - 8 * s;
        Color dark = new Color(0x1F4418), mid = rnd.nextBoolean() ? new Color(0x3C7A2A) : new Color(0x4E8A2E), lite = new Color(0x8CC05A);
        if (rnd.nextDouble() < 0.15) {
            mid = new Color(0x7A8A2A);
            lite = new Color(0xC8C060);
        }
        for (int i = 0; i < 5; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = (2.6 + rnd.nextDouble() * 2) * s, r = (3.8 + rnd.nextDouble() * 1.8) * s;
            double bx = x + Math.cos(a) * d, by = cy + Math.sin(a) * d * 0.8;
            g.setPaint(radial(new Point2D.Double(bx - r * 0.4, by - r * 0.45), r * 1.1, new float[]{0, 0.55f, 1},
                    new Color[]{lite, mid, dark}));
            g.fill(new Ellipse2D.Double(bx - r, by - r, r * 2, r * 2));
        }
        double r = 4.8 * s;
        g.setPaint(radial(new Point2D.Double(x - 2 * s, cy - 2.4 * s), r * 1.25, new float[]{0, 0.6f, 1},
                new Color[]{lite, mid, dark}));
        g.fill(new Ellipse2D.Double(x - r, cy - r * 1.05, r * 2, r * 2));
    }

    // ---- vehicles --------------------------------------------------------------------------------

    // --8<-- [start:vehicle]
    /**
     * A vehicle as two boxes, body and cabin, centred on {@code x}, {@code y}: {@code direction} 0 drives
     * along +x, 1 along -x, 2 along +y, 3 along -y.
     */
    static void vehicle(Graphics2D g, IsoProjection p, double x, double y, int direction, double len, double wid,
                        double body, double cabFrom, double cabTo, double cabH, int c, int cabC) {
        boolean alongX = direction < 2;
        int sign = direction == 0 || direction == 2 ? 1 : -1;
        double x0 = alongX ? x - len / 2 : x - wid / 2, x1 = alongX ? x + len / 2 : x + wid / 2;
        double y0 = alongX ? y - wid / 2 : y - len / 2, y1 = alongX ? y + wid / 2 : y + len / 2;
        double c0 = sign > 0 ? cabFrom : -cabTo, c1 = sign > 0 ? cabTo : -cabFrom;
        double k0 = alongX ? x + c0 * len / 2 : x - wid / 2 + 0.4, k1 = alongX ? x + c1 * len / 2 : x + wid / 2 - 0.4;
        double l0 = alongX ? y - wid / 2 + 0.4 : y + c0 * len / 2, l1 = alongX ? y + wid / 2 - 0.4 : y + c1 * len / 2;
        box(g, p, x0, y0, x1, y1, 0.3, body, shade(c, 1.15), shade(c, 0.95), shade(c, 0.7));
        box(g, p, k0, l0, k1, l1, body, body + cabH, cabC, 0x2A3A48, 0x1E2A34);
        Point2D light = alongX ? at(p, x1, y1 - 0.7, body * 0.6) : at(p, x1 - 0.7, y1, body * 0.6);
        g.setColor(sign > 0 ? new Color(0xFFF8D0) : new Color(0xC8281E));
        g.fill(new Ellipse2D.Double(light.getX() - 0.5, light.getY() - 0.35, 1, 0.7));
    }
    // --8<-- [end:vehicle]

    static void car(Graphics2D g, IsoProjection p, double x, double y, int direction, int c) {
        vehicle(g, p, x, y, direction, 6.4, 3.2, 1.9, -0.45, 0.35, 1.4, c, shade(c, 1.1));
    }

    static void bus(Graphics2D g, IsoProjection p, double x, double y, int direction) {
        vehicle(g, p, x, y, direction, 12, 3.6, 4.4, -0.95, 0.95, 0.4, 0xE8B030, 0xF4F0E4);
    }

    static void lorry(Graphics2D g, IsoProjection p, double x, double y, int direction) {
        vehicle(g, p, x, y, direction, 11, 3.6, 4.6, 0.55, 0.95, 0.5, 0xE8E4DC, 0x2E5A9A);
    }

    static void fireTruck(Graphics2D g, IsoProjection p, double x, double y, int direction) {
        vehicle(g, p, x, y, direction, 10, 3.8, 3, 0.5, 0.95, 1.2, 0xD8281E, 0xB82018);
        Point2D top = at(p, x, y, 4.6);
        g.setColor(new Color(0x4AA8F8));
        g.fill(rect(top.getX() - 1.4, top.getY() - 0.4, 1.2, 0.8));
        g.setColor(new Color(0xF84A3A));
        g.fill(rect(top.getX() + 0.2, top.getY() - 0.4, 1.2, 0.8));
    }

    // ---- signs and marks -------------------------------------------------------------------------

    /** The sign over a building without power: a bolt, crossed out. */
    static void noPower(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x - 4.4, y - 4, 9.6, 9.6, 3, 3));
        g.setColor(new Color(0x2A2420));
        g.fill(new RoundRectangle2D.Double(x - 4.8, y - 4.8, 9.6, 9.6, 3, 3));
        g.setColor(new Color(0xF8D030));
        g.fill(bolt(x, y, 1));
        g.setColor(new Color(0xE83A2E));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Ellipse2D.Double(x - 3.6, y - 3.6, 7.2, 7.2));
        g.draw(new Line2D.Double(x - 2.6, y - 2.6, x + 2.6, y + 2.6));
    }

    /** The sign over a building without water: a drop, crossed out. */
    static void noWater(Graphics2D g, double x, double y) {
        g.setColor(new Color(0x2A2420));
        g.fill(new RoundRectangle2D.Double(x - 4.8, y - 4.8, 9.6, 9.6, 3, 3));
        g.setPaint(new GradientPaint((float) x, (float) (y - 4), new Color(0xA8E0F8), (float) x, (float) (y + 4), new Color(0x3A8AD8)));
        g.fill(drop(x, y, 0.8));
        g.setColor(new Color(0xE83A2E));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(x - 2.6, y - 2.6, x + 2.6, y + 2.6));
    }

    static Path2D bolt(double x, double y, double s) {
        Path2D b = new Path2D.Double();
        b.moveTo(x + 0.8 * s, y - 3.8 * s);
        b.lineTo(x - 2.4 * s, y + 0.6 * s);
        b.lineTo(x - 0.2 * s, y + 0.6 * s);
        b.lineTo(x - 1 * s, y + 3.8 * s);
        b.lineTo(x + 2.4 * s, y - 0.8 * s);
        b.lineTo(x + 0.2 * s, y - 0.8 * s);
        b.closePath();
        return b;
    }

    static Path2D drop(double x, double y, double s) {
        Path2D d = new Path2D.Double();
        d.moveTo(x, y - 5 * s);
        d.curveTo(x + 1.5 * s, y - 2 * s, x + 3.6 * s, y, x + 3.6 * s, y + 1.6 * s);
        d.curveTo(x + 3.6 * s, y + 4 * s, x + 1.8 * s, y + 5 * s, x, y + 5 * s);
        d.curveTo(x - 1.8 * s, y + 5 * s, x - 3.6 * s, y + 4 * s, x - 3.6 * s, y + 1.6 * s);
        d.curveTo(x - 3.6 * s, y, x - 1.5 * s, y - 2 * s, x, y - 5 * s);
        d.closePath();
        return d;
    }

    /** Flames on a burning lot, at frame {@code frame} of their flicker. */
    static void flames(Graphics2D g, double x, double y, int frame) {
        Random rnd = new Random(frame * 31L + 7);
        for (int i = 0; i < 5; i++) {
            double fx = x - 6 + i * 3 + rnd.nextDouble() * 1.5, h = 6 + rnd.nextDouble() * 8, w = 2.2 + rnd.nextDouble();
            Path2D f = new Path2D.Double();
            f.moveTo(fx - w, y);
            f.quadTo(fx - w * 0.8, y - h * 0.6, fx + (rnd.nextDouble() - 0.5) * 2, y - h);
            f.quadTo(fx + w * 0.8, y - h * 0.6, fx + w, y);
            f.closePath();
            g.setPaint(new GradientPaint((float) fx, (float) (y - h), new Color(255, 230, 120, 230), (float) fx, (float) y,
                    new Color(230, 70, 20, 230)));
            g.fill(f);
        }
    }

    /** Rubble where a building burned down: a heap of broken stone and a charred beam. */
    static void rubble(Graphics2D g, IsoProjection p, double cx, double cy, long seed) {
        Random rnd = new Random(seed);
        for (int i = 0; i < 26; i++) {
            double x = cx + (rnd.nextDouble() - 0.5) * 10, y = cy + (rnd.nextDouble() - 0.5) * 10;
            Point2D q = at(p, x, y, rnd.nextDouble() * 2.5);
            int v = 70 + rnd.nextInt(60);
            g.setColor(new Color(v, v - 6, v - 12));
            g.fill(new Ellipse2D.Double(q.getX() - 1.2, q.getY() - 0.8, 2.4, 1.6));
        }
        g.setColor(new Color(0x2A2420));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(at(p, cx - 4, cy - 2, 0.5), at(p, cx + 3, cy + 3, 2.5)));
    }

    /** A little bench for a park, along x. */
    static void bench(Graphics2D g, IsoProjection p, double x, double y) {
        box(g, p, x, y, x + 4, y + 1.2, 0.9, 1.4, 0x8A6440, 0x7A5432, 0x5A3C22);
    }
}
