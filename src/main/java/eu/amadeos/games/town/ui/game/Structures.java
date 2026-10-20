package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.games.town.domain.Work;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Random;

/**
 * The buildings the player builds: the coal plant, the wind turbine, the pump house and the water tower,
 * school, library, clinic, fire station, the town hall and the park. Each has its ground, drawn flat under
 * everything (a sports field, a plaza, a coal heap), and the building itself; both in world units from
 * the building's top corner, its front facing +y.
 */
final class Structures {
    /** Where smoke or steam rises: {x, y, height, 1 for steam}. */
    record Look(double tall, List<double[]> shadows, List<double[]> smoke) {
    }

    private Structures() {
    }

    static Look look(Work work) {
        return switch (work) {
            case PLANT -> new Look(70, List.of(new double[]{3, 4, 27, 20, 22}, new double[]{30, 4, 46, 20, 30},
                    new double[]{30.5, 30.5, 33.5, 33.5, 56}, new double[]{6, 24, 22, 29, 6}),
                    List.of(new double[]{32, 32, 58, 0}, new double[]{38, 12, 31, 1}));
            case WIND -> new Look(Art.TURBINE_HUB + 18, List.of(new double[]{7.4, 7.4, 8.6, 8.6, 34}), List.of());
            case PUMP -> new Look(24, List.of(new double[]{3, 6, 18, 20, 8}, new double[]{22, 9, 28, 15, 10}), List.of());
            case TOWER -> new Look(46, List.of(new double[]{4, 4, 12, 12, 30}), List.of());
            case SCHOOL -> new Look(34, List.of(new double[]{2, 3, 28, 16, 14}), List.of());
            case LIBRARY -> new Look(28, List.of(new double[]{3, 4, 27, 24, 13}), List.of());
            case CLINIC -> new Look(32, List.of(new double[]{2, 3, 26, 22, 20}), List.of());
            case FIRE -> new Look(36, List.of(new double[]{2, 3, 22, 20, 13}, new double[]{23, 3, 29, 9, 26}), List.of());
            case HALL -> new Look(52, List.of(new double[]{4, 4, 44, 22, 15}, new double[]{20, 9, 28, 17, 38}), List.of());
            case PARK -> new Look(20, List.of(), List.of());
        };
    }

    // --8<-- [start:yard]
    /** The ground of a building, flat. */
    static void yard(Graphics2D g, IsoProjection p, Work work, int width, int height) {
        double w = width * 16.0;
        double d = height * 16.0;
        Art.with(g, p.ground(0), f -> {
            switch (work) {
                case PLANT -> {
                    Random rnd = new Random(8);
                    for (int i = 0; i < 260; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble());
                        double x = 12 + Math.cos(a) * r * 9, y = 38 + Math.sin(a) * r * 7, s = 0.5 + rnd.nextDouble() * 1.2;
                        int v = 18 + (int) ((1 - r) * 34) + rnd.nextInt(20);
                        f.setColor(new Color(v, v, v + 4));
                        f.fill(new Ellipse2D.Double(x - s, y - s, s * 2, s * 2));
                    }
                    f.setColor(new Color(0x5A4A3A));
                    f.setStroke(new BasicStroke(0.5f));
                    for (double ry : new double[]{44.5, 46.5}) f.draw(new Line2D.Double(1, ry, 30, ry));
                    f.setColor(new Color(0x4A3A2A));
                    for (double k = 1; k < 30; k += 2) f.fill(Art.rect(k, 43.8, 0.8, 3.4));
                    fence(f, w, d);
                }
                case SCHOOL -> {
                    double fx = 32, fy = 5, fw = 12, fh = 22;
                    f.setColor(new Color(0x4E9A3A));
                    f.fill(Art.rect(29, 1.5, 17.5, 29));
                    f.setColor(new Color(0xB8604A));
                    f.setStroke(new BasicStroke(1.6f));
                    f.draw(new RoundRectangle2D.Double(fx - 2, fy - 2, fw + 4, fh + 4, 10, 10));
                    f.setColor(new Color(255, 255, 255, 210));
                    f.setStroke(new BasicStroke(0.4f));
                    f.draw(Art.rect(fx, fy, fw, fh));
                    f.draw(new Line2D.Double(fx, fy + fh / 2, fx + fw, fy + fh / 2));
                    f.draw(new Ellipse2D.Double(fx + fw / 2 - 2.5, fy + fh / 2 - 2.5, 5, 5));
                    f.draw(Art.rect(fx + fw / 2 - 4, fy, 8, 3.5));
                    f.draw(Art.rect(fx + fw / 2 - 4, fy + fh - 3.5, 8, 3.5));
                    f.setColor(new Color(0xC8C0AC));
                    f.fill(Art.rect(13, 16, 4, 16));
                }
                case LIBRARY, CLINIC -> {
                    f.setColor(new Color(0xC8C0AC));
                    f.fill(Art.rect(13, 22, 6, 10));
                    if (work == Work.CLINIC) {
                        f.setColor(new Color(0x5A5C60));
                        f.fill(Art.rect(22, 23, 9, 8));
                        f.setColor(new Color(235, 232, 220, 200));
                        f.setStroke(new BasicStroke(0.4f));
                        for (double k = 23.5; k < 31; k += 3.5) f.draw(new Line2D.Double(k, 23, k, 27));
                    }
                }
                case FIRE -> {
                    f.setColor(new Color(0x9A9890));
                    f.fill(Art.rect(2, 20, 21, 11.5));
                    f.setColor(new Color(0xE8C030));
                    f.setStroke(new BasicStroke(0.5f));
                    f.draw(new Line2D.Double(2, 30.5, 23, 30.5));
                }
                case HALL -> {
                    for (double x = 2; x < 46; x += 4) {
                        for (double y = 23; y < 46; y += 4) {
                            f.setColor(Art.col(Art.mix(0xD2C4A4, 0xB8A888, Art.hash((int) x, (int) y, 3))));
                            f.fill(Art.rect(x, y, 3.6, 3.6));
                        }
                    }
                    double cx = 24, cy = 37;
                    f.setColor(new Color(0x9A9080));
                    f.fill(new Ellipse2D.Double(cx - 7, cy - 7, 14, 14));
                    f.setPaint(new RadialGradientPaint(new Point2D.Double(cx - 2, cy - 2), 6, new float[]{0, 1},
                            new Color[]{new Color(0x8AC8DA), new Color(0x3A7A98)}));
                    f.fill(new Ellipse2D.Double(cx - 6, cy - 6, 12, 12));
                    f.setColor(new Color(255, 255, 255, 150));
                    f.setStroke(new BasicStroke(0.4f));
                    f.draw(new Ellipse2D.Double(cx - 3.5, cy - 3.5, 7, 7));
                }
                case PARK -> {
                    f.setColor(new Color(0xC8B48A));
                    f.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    f.draw(new Line2D.Double(0, 9, 16, 7));
                    f.draw(new Line2D.Double(8, 0, 7.5, 16));
                }
                case PUMP -> {
                    f.setColor(new Color(0x4A5A6A));
                    f.setStroke(new BasicStroke(1.2f));
                    f.draw(new Line2D.Double(18, 14, 31.5, 14));
                    f.draw(new Line2D.Double(10, 20, 10, 31.5));
                }
                default -> {
                }
            }
        });
    }
    // --8<-- [end:yard]

    private static void fence(Graphics2D f, double w, double d) {
        f.setColor(new Color(0x7A7A7A));
        f.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1.2f, 0.8f}, 0));
        f.draw(Art.rect(0.8, 0.8, w - 1.6, d - 1.6));
    }

    // --8<-- [start:draw]
    /** The building, its parts drawn from the back to the front. */
    static void draw(Graphics2D g, IsoProjection p, Work work) {
        switch (work) {
            case PLANT -> {
                Art.building(g, p, 3, 4, 27, 20, 22, 0xB8B0A4, 0x6E6A64, Art.Style.PLANT, 52);
                for (int i = 0; i < 3; i++) {
                    double x = 6 + i * 6;
                    Art.box(g, p, x, 24, x + 4, 29, 0, 6, 0x8A8E92, 0x7A7E82, 0x5A5E62);
                }
                Art.cooling(g, p, 38, 12, 8, 30);
                Art.stack(g, p, 32, 32, 2.6, 56, true);
            }
            case WIND -> Art.turbine(g, p, 8, 8);
            case PUMP -> {
                Art.building(g, p, 3, 6, 18, 20, 8, 0xC8C4B8, 0x6A6E74, Art.Style.PUMP, 44);
                Art.tank(g, p, 25, 12, 3.4, 10);
            }
            case TOWER -> Art.waterTower(g, p, 8, 8);
            case SCHOOL -> {
                Art.building(g, p, 2, 3, 28, 16, 14, 0xE8D4A8, 0x7A7A76, Art.Style.SCHOOL, 43);
                Art.flagpole(g, p, 4, 22, 0x2A5AB0);
                Art.tree(g, p, 8, 27, false, 0.5, 3);
                Art.tree(g, p, 23, 28, false, 0.45, 5);
            }
            case LIBRARY -> {
                Art.building(g, p, 3, 4, 27, 24, 13, 0x9A5A40, 0x5A9A82, Art.Style.LIBRARY, 46);
                Art.bench(g, p, 5, 27);
                Art.tree(g, p, 27, 28, false, 0.5, 9);
            }
            case CLINIC -> {
                Art.building(g, p, 2, 3, 26, 22, 20, 0xF0F0EC, 0x8A9098, Art.Style.CLINIC, 47);
                Art.vehicle(g, p, 28.5, 27, 2, 8, 3.6, 3.6, 0.45, 0.95, 0.8, 0xF4F4F0, 0xC8281E);
                Art.tree(g, p, 6, 27, false, 0.45, 11);
            }
            case FIRE -> {
                Art.building(g, p, 2, 3, 22, 20, 13, 0xA84A36, 0x6A625C, Art.Style.FIRE, 42);
                Art.building(g, p, 23, 3, 29, 9, 26, 0xA84A36, 0x6A625C, Art.Style.PLAIN, 45);
                Art.fireTruck(g, p, 26.5, 22, 2);
            }
            case HALL -> {
                Art.building(g, p, 4, 4, 44, 22, 15, 0xE8E0CC, 0x8A6A58, Art.Style.HALL, 40);
                Art.clockTower(g, p, 20, 9, 28, 17, 15);
                Art.portico(g, p, 16, 32, 22, 25, 15);
                Art.bench(g, p, 8, 32);
                Art.bench(g, p, 35, 42);
                Art.tree(g, p, 6, 42, false, 0.55, 13);
                Art.flagpole(g, p, 41, 30, 0x2A5AB0);
                Art.tree(g, p, 43, 44, false, 0.5, 17);
            }
            case PARK -> {
                Art.tree(g, p, 3.5, 3.5, false, 0.55, 21);
                Art.bench(g, p, 10, 3.6);
                Art.tree(g, p, 3, 12.5, true, 0.45, 22);
                Art.tree(g, p, 12.5, 12.5, false, 0.6, 23);
            }
        }
    }
    // --8<-- [end:draw]
}
