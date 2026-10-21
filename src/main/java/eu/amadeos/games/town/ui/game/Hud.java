package eu.amadeos.games.town.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.iso.DiamondMinimap;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.town.domain.Building;
import eu.amadeos.games.town.domain.BuildingType;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Ground;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Mission;
import eu.amadeos.games.town.domain.Work;
import eu.amadeos.games.town.domain.Zone;
import eu.amadeos.games.town.repository.Progress;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The overlay: the bar along the top with the money, the people, power, water, happiness and the date; the
 * panel on the right with the minimap, the nine tools, the demand for each zone, the box that tells about
 * the tool, the tile under the mouse, the budget or the mission, and four buttons; the messages over the
 * map; and the screens of the title, the choice of a mission, the pause and the end. The canvas draws it
 * after the world, at the full size of the screen, so the letters are sharp in every render mode.
 */
final class Hud {
    static final Color CHALK = new Color(0xE8ECF0);
    static final Color LABEL = new Color(0x98A4B0);
    static final Color AMBER = new Color(0xF8D070);
    static final Color GREEN = new Color(0x8AE07A);
    static final Color RED = new Color(0xF0705A);
    static final Color GREY = new Color(0x707C88);
    static final Color BOX = new Color(0x12161A);
    static final Color EDGE = new Color(0x5A7A9A);
    static final Color DARK = new Color(0x0E1216);
    static final Color LIGHT = new Color(0x5A6674);
    static final Color VEIL = new Color(0, 0, 0, 150);
    static final Color SHADOW = new Color(0, 0, 0, 150);
    static final Color VIEW = new Color(255, 255, 255, 220);
    static final double MESSAGE_SECONDS = 5;
    static final double LEFT = Field.VIEW_WIDTH;
    static final double TOP = Field.BAR;
    static final Rectangle2D.Double MINIMAP = new Rectangle2D.Double(LEFT + 8, TOP + 6, 112, 82);
    static final double TOOL_TOP = 116;
    static final double TOOL_WIDTH = 36;
    static final double TOOL_HEIGHT = 30;
    static final Rectangle2D.Double DEMAND = new Rectangle2D.Double(LEFT + 8, 216, 112, 62);
    static final Rectangle2D.Double INFO = new Rectangle2D.Double(LEFT + 8, 284, 112, 82);
    static final Rectangle2D.Double BUDGET = new Rectangle2D.Double(LEFT + 8, 372, 55, 12);
    static final Rectangle2D.Double OVERLAY = new Rectangle2D.Double(LEFT + 65, 372, 55, 12);
    static final Rectangle2D.Double MISSION = new Rectangle2D.Double(LEFT + 8, 386, 55, 12);
    static final Rectangle2D.Double MENU = new Rectangle2D.Double(LEFT + 65, 386, 55, 12);
    /** The rows of the box: the first baseline, the step. */
    static final double ROW_TOP = INFO.y + 30;
    static final double ROW_STEP = 11;
    static final Rectangle2D.Double TAX_DOWN = new Rectangle2D.Double(INFO.x + INFO.width - 42, ROW_TOP + 3 * ROW_STEP - 8, 10, 10);
    static final Rectangle2D.Double TAX_UP = new Rectangle2D.Double(INFO.x + INFO.width - 15, ROW_TOP + 3 * ROW_STEP - 8, 10, 10);
    static final double CARD_WIDTH = 176;
    static final double CARD_HEIGHT = 170;
    static final double CARD_GAP = 16;
    static final double CARD_TOP = 100;

    /** What the box below the demand shows. */
    enum Info { TOOL, BUDGET, MISSION }

    /**
     * What the panel shows that the game does not know: the tool and what it builds, the box's subject,
     * the tile under the mouse, the rectangle or path being dragged and its cost, the overlay, the speed and
     * where the mouse is.
     */
    record View(Tool tool, String choice, Info info, Cell hover, int dragWidth, int dragHeight, int dragCost,
                TownPainter.Overlay overlay, int speed, double mouseX, double mouseY) {
        boolean over(Rectangle2D.Double box) {
            return box.contains(mouseX, mouseY);
        }
    }

    private final Messages msg;
    private final Map<Integer, Font> fonts = new HashMap<>();
    private final Rectangle2D.Double rectangle = new Rectangle2D.Double();
    /** What stays the same from frame to frame, drawn once: the bar, the panel, the faces of buttons. */
    private final Map<String, Sprite> faces = new HashMap<>();
    private final Sprite[] icons = new Sprite[Tool.values().length];

    Hud(Messages msg) {
        this.msg = msg;
        for (int i = 0; i < icons.length; i++) icons[i] = Pictures.icon(i);
    }

    // ---- the top bar -----------------------------------------------------------------------------

    // --8<-- [start:bar]
    /** The bar along the top: money and the month's balance, people, power, water, happiness, the date, the speed. */
    void bar(Graphics2D g, Game game, int speed) {
        smooth(g);
        face(g, "bar", 0, 0, Field.WIDTH, TOP, (sg, w, h) -> {
            sg.setPaint(new GradientPaint(0, 0, new Color(0x2E353E), 0, (float) TOP, new Color(0x1C2128)));
            sg.fill(new Rectangle2D.Double(0, 0, Field.WIDTH, TOP));
            sg.setColor(EDGE);
            sg.fill(new Rectangle2D.Double(0, TOP - 1, Field.WIDTH, 1));
            double x = 8;
            for (double width : new double[]{116, 62, 70, 64, 46}) {
                sg.setColor(BOX);
                sg.fill(new RoundRectangle2D.Double(x, 3.5, width, 13, 4, 4));
                x += width + 4;
            }
        });
        double balance = game.income() - game.expenses();
        double x = 8;
        chip(g, x, 116, 0, money(game.money()), game.money() < 0 ? RED : CHALK, true);
        g.setFont(font(7.5f, true));
        g.setColor(balance >= 0 ? GREEN : RED);
        String month = msg.format("bar.month", (balance >= 0 ? "+" : "-") + number(Math.abs(balance)));
        g.drawString(month, (float) (x + 112 - g.getFontMetrics().stringWidth(month)), 13.2f);
        x += 120;
        chip(g, x, 62, 1, number(game.people()), game.people() >= game.mission().goal() ? GREEN : CHALK, false);
        x += 66;
        boolean dark = game.powerDemand() > game.powerSupply();
        chip(g, x, 70, 2, number(Math.min(game.powerDemand(), game.powerSupply())) + "/" + number(game.powerSupply()),
                dark ? RED : CHALK, false);
        x += 74;
        boolean dry = game.waterDemand() > game.waterSupply();
        chip(g, x, 64, 3, number(Math.min(game.waterDemand(), game.waterSupply())) + "/" + number(game.waterSupply()),
                dry ? RED : CHALK, false);
        x += 68;
        int needed = game.mission().happiness();
        chip(g, x, 46, 4, game.happiness() + "%", game.happiness() < needed ? AMBER : CHALK, false);
        g.setColor(new Color(0xB8C8D8));
        g.setFont(font(9, true));
        String date = msg.format("bar.date", msg.get("month." + game.months() % 12), game.year());
        g.drawString(date, (float) (Field.WIDTH - 40 - g.getFontMetrics().stringWidth(date)), 13.5f);
        g.setColor(BOX);
        g.fill(new RoundRectangle2D.Double(Field.WIDTH - 32, 4.5, 24, 11, 4, 4));
        g.setFont(font(8, true));
        g.setColor(speed > 1 ? GREEN : LABEL);
        centred(g, "x" + speed, Field.WIDTH - 20, 12.8);
    }
    // --8<-- [end:bar]

    private void chip(Graphics2D g, double x, double width, int icon, String text, Color colour, boolean leftAligned) {
        Pictures.barIcon(g, icon, x + 7.5, 10, icon != 4 || colour != AMBER);
        g.setFont(font(9.5f, true));
        g.setColor(colour);
        if (leftAligned) g.drawString(text, (float) x + 15, 13.5f);
        else g.drawString(text, (float) (x + width - 4 - g.getFontMetrics().stringWidth(text)), 13.5f);
    }

    static String money(double dollars) {
        return (dollars < 0 ? "-$ " : "$ ") + number(Math.abs(dollars));
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    // ---- the panel -------------------------------------------------------------------------------

    // --8<-- [start:panel]
    /** The panel: the minimap, the tools, the demand, the box, the buttons. */
    void panel(Graphics2D g, Game game, DiamondMinimap minimap, Camera camera, View view, double alpha) {
        smooth(g);
        face(g, "panel", LEFT, TOP, Field.PANEL, Field.HEIGHT - TOP, (sg, w, h) -> {
            sg.setPaint(new GradientPaint((float) LEFT, 0, new Color(0x2E353E), (float) Field.WIDTH, 0, new Color(0x1C2128)));
            sg.fill(new Rectangle2D.Double(LEFT, TOP, Field.PANEL, Field.HEIGHT - TOP));
            sg.setColor(EDGE);
            sg.fill(new Rectangle2D.Double(LEFT, TOP, 1, Field.HEIGHT - TOP));
            sg.setColor(DARK);
            sg.fill(new Rectangle2D.Double(LEFT + 1, TOP, 1, Field.HEIGHT - TOP));
            sg.setColor(new Color(0x050608));
            sg.fill(MINIMAP);
            bevel(sg, MINIMAP.x - 1, MINIMAP.y - 1, MINIMAP.width + 2, MINIMAP.height + 2, true);
            for (Rectangle2D.Double box : List.of(DEMAND, INFO)) {
                sg.setColor(BOX);
                sg.fill(box);
                bevel(sg, box.x, box.y, box.width, box.height, true);
            }
        });
        minimap.render(g);
        Shape clip = g.getClip();
        g.clip(MINIMAP);
        minimap.view(g, camera, alpha, VIEW);
        g.setClip(clip);
        for (Tool tool : Tool.values()) toolButton(g, tool, tool == view.tool(), view.over(tool(tool.ordinal())));
        demand(g, game);
        switch (view.info()) {
            case BUDGET -> budget(g, game, view);
            case MISSION -> mission(g, game);
            case TOOL -> {
                if (view.tool() != null) tool(g, game, view);
                else if (view.hover() != null) tile(g, game, view.hover());
                else mission(g, game);
            }
        }
        button(g, BUDGET, msg.get("panel.budget"), view.info() == Info.BUDGET, view.over(BUDGET));
        String overlay = view.overlay() == TownPainter.Overlay.NONE ? msg.get("panel.overlay")
                : msg.get("overlay." + view.overlay().name().toLowerCase());
        button(g, OVERLAY, overlay, view.overlay() != TownPainter.Overlay.NONE, view.over(OVERLAY));
        button(g, MISSION, msg.get("panel.mission"), view.info() == Info.MISSION, view.over(MISSION));
        button(g, MENU, msg.get("panel.menu"), false, view.over(MENU));
    }
    // --8<-- [end:panel]

    static Rectangle2D.Double tool(int i) {
        return new Rectangle2D.Double(LEFT + 8 + (i % 3) * (TOOL_WIDTH + 2), TOOL_TOP + (i / 3) * (TOOL_HEIGHT + 2), TOOL_WIDTH, TOOL_HEIGHT);
    }

    /** The tool whose button is under a point, or {@code null}. */
    static Tool toolAt(double x, double y) {
        for (Tool tool : Tool.values()) if (tool(tool.ordinal()).contains(x, y)) return tool;
        return null;
    }

    /** The row of the box's list under a point, or -1. */
    static int rowAt(double x, double y, int rows) {
        if (x < INFO.x || x > INFO.x + INFO.width) return -1;
        for (int i = 0; i < rows; i++) {
            double baseline = ROW_TOP + i * ROW_STEP;
            if (y >= baseline - 8.5 && y < baseline + 2.5) return i;
        }
        return -1;
    }

    private void toolButton(Graphics2D g, Tool tool, boolean chosen, boolean hover) {
        Rectangle2D.Double box = tool(tool.ordinal());
        int zc = tool.zone() == null ? 0x8AB8E8 : Pictures.zoneColour(tool.zone());
        face(g, "tool " + tool + chosen + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint(0, (float) box.y, chosen ? new Color(0x3A5A44) : hover ? new Color(0x46505C)
                    : new Color(0x3A434E), 0, (float) (box.y + box.height), chosen ? new Color(0x24402C) : new Color(0x262D36)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, chosen);
            if (chosen) {
                sg.setColor(Art.col(zc));
                sg.setStroke(new BasicStroke(1f));
                sg.draw(new Rectangle2D.Double(box.x + 0.5, box.y + 0.5, box.width - 1, box.height - 1));
            }
        });
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(box.x + 1, box.y + 1, box.width - 2, box.height - 9));
        icons[tool.ordinal()].draw(g, box.x, box.y + 1);
        g.setClip(clip);
        g.setFont(font(6.3f, true));
        g.setColor(chosen ? new Color(0xE8F8E0) : new Color(0xC8D0D8));
        centred(g, msg.get("tool." + tool.key()), box.getCenterX(), box.y + box.height - 3.5);
    }

    // --8<-- [start:demand]
    /** A bar for each zone, up when the town wants more of it, down when it has too much. */
    private void demand(Graphics2D g, Game game) {
        double x = DEMAND.x;
        double y = DEMAND.y;
        g.setFont(font(8, true));
        g.setColor(CHALK);
        g.drawString(msg.get("panel.demand"), (float) x + 5, (float) y + 11);
        double zero = y + 36;
        g.setColor(new Color(0x3A434E));
        rectangle.setRect(x + 5, zero, 70, 0.8);
        g.fill(rectangle);
        Zone most = null;
        for (Zone zone : Zone.values()) {
            int i = zone.ordinal();
            double d = game.demand(zone);
            if (most == null || d > game.demand(most)) most = zone;
            double bx = x + 12 + i * 22;
            double length = Math.clamp(d, -1, 1) * 20;
            double y0 = length > 0 ? zero - length : zero + 0.8;
            int colour = Pictures.zoneColour(zone);
            g.setPaint(new GradientPaint(0, (float) y0, Art.col(Art.shade(colour, 1.15)), 0, (float) (y0 + Math.abs(length) + 0.1),
                    Art.col(Art.shade(colour, 0.7))));
            rectangle.setRect(bx, y0, 12, Math.abs(length));
            g.fill(rectangle);
            g.setFont(font(7, true));
            g.setColor(Art.col(colour));
            centred(g, msg.get("zone.letter." + zone.letter()), bx + 6, y + 57);
        }
        g.setFont(font(6.3f, false));
        g.setColor(LABEL);
        String wanted = most != null && game.demand(most) > 0.15 ? msg.get("demand." + most.letter()) : msg.get("demand.none");
        lines(g, wanted, x + 80, y + 22, 30, 7);
    }
    // --8<-- [end:demand]

    // ---- the box ---------------------------------------------------------------------------------

    private double title(Graphics2D g, String text, Color bar) {
        g.setColor(bar);
        rectangle.setRect(INFO.x + 5, INFO.y + 5, 3, 10);
        g.fill(rectangle);
        g.setFont(font(text.length() > 18 ? 7.5f : 9, true));
        g.setColor(CHALK);
        g.drawString(text, (float) INFO.x + 11, (float) INFO.y + 13);
        g.setColor(new Color(0x2E353E));
        rectangle.setRect(INFO.x + 5, INFO.y + 19, INFO.width - 10, 0.8);
        g.fill(rectangle);
        return ROW_TOP;
    }

    private void row(Graphics2D g, int i, String label, String value, Color colour) {
        float y = (float) (ROW_TOP + i * ROW_STEP);
        g.setFont(font(7, false));
        g.setColor(LABEL);
        g.drawString(label, (float) INFO.x + 5, y);
        g.setColor(colour);
        g.drawString(value, (float) (INFO.x + INFO.width - 5 - g.getFontMetrics().stringWidth(value)), y);
    }

    // --8<-- [start:tool-info]
    /** The chosen tool: what it costs, and for power, water and services the list of what it builds. */
    private void tool(Graphics2D g, Game game, View view) {
        Tool tool = view.tool();
        Color bar = tool.zone() != null ? Art.col(Pictures.zoneColour(tool.zone())) : tool == Tool.BULLDOZE ? AMBER : EDGE;
        title(g, msg.get("info." + tool.key()), bar);
        List<String> choices = tool.choices(game.rules());
        if (!choices.isEmpty()) {
            for (int i = 0; i < choices.size() && i < 5; i++) {
                String id = choices.get(i);
                boolean chosen = id.equals(view.choice());
                boolean hover = rowAt(view.mouseX(), view.mouseY(), choices.size()) == i;
                if (chosen || hover) {
                    g.setColor(chosen ? new Color(0x2A4A36) : new Color(0x262D36));
                    rectangle.setRect(INFO.x + 3, ROW_TOP + i * ROW_STEP - 8.5, INFO.width - 6, ROW_STEP);
                    g.fill(rectangle);
                }
                boolean forbidden = game.mission().forbidden().contains(id);
                String cost = id.equals(Tool.LINE) ? msg.format("info.per.tile", Field.LINE)
                        : forbidden ? msg.get("info.forbidden") : money(game.rules().building(id).cost());
                row(g, i, msg.get("building." + id), cost, forbidden ? RED : chosen ? AMBER : CHALK);
            }
            if (view.dragCost() > 0) row(g, Math.min(choices.size(), 5), msg.get("info.cost"), money(view.dragCost()), AMBER);
            return;
        }
        int i = 0;
        switch (tool) {
            case ROAD -> {
                row(g, i++, msg.get("info.road.tile"), money(Field.ROAD), CHALK);
                row(g, i++, msg.get("info.bridge"), money(Field.BRIDGE), CHALK);
            }
            case BULLDOZE -> row(g, i++, msg.get("info.clear"), money(Field.CLEAR), CHALK);
            default -> {
                Zone zone = tool.zone();
                row(g, i++, msg.get("info.zone.tile"), money(zone.cost()), CHALK);
                double d = game.demand(zone);
                row(g, i++, msg.get("info.demand"), msg.get(d > 0.5 ? "info.high" : d > 0.1 ? "info.some" : d > -0.2 ? "info.low" : "info.none"),
                        d > 0.1 ? GREEN : d > -0.2 ? CHALK : RED);
            }
        }
        if (view.dragWidth() > 0) {
            row(g, i++, msg.get("info.area"), msg.format("info.size", view.dragWidth(), view.dragHeight()), CHALK);
            row(g, i, msg.get("info.cost"), money(view.dragCost()), view.dragCost() > game.money() ? RED : AMBER);
        } else {
            lines(g, msg.get("help." + tool.key()), INFO.x + 5, ROW_TOP + i * ROW_STEP, INFO.width - 10, 9);
        }
    }
    // --8<-- [end:tool-info]

    // --8<-- [start:tile-info]
    /** No tool: what is on the tile under the mouse. */
    private void tile(Graphics2D g, Game game, Cell cell) {
        int col = cell.col();
        int row = cell.row();
        Building b = game.buildingAt(col, row);
        Zone zone = game.zone(col, row);
        int level = game.lot(col, row);
        int i = 0;
        if (b != null) {
            BuildingType type = b.type();
            title(g, msg.get("building." + type.id()), EDGE);
            if (type.plant()) row(g, i++, msg.get("info.makes"), type.power() + " MW", GREEN);
            else if (type.uses() > 0) row(g, i++, msg.get("info.power"), msg.get(b.working() ? "info.yes" : "info.no"), b.working() ? GREEN : RED);
            if (type.work() == Work.PUMP) row(g, i++, msg.get("info.gives"), number(type.water()), CHALK);
            if (type.reach() > 0 && type.smoke() == 0) row(g, i++, msg.get("info.reach"), msg.format("info.tiles", type.reach()), CHALK);
            row(g, i++, msg.get("info.upkeep"), money(type.upkeep()), CHALK);
            lines(g, msg.get("about." + type.id()), INFO.x + 5, ROW_TOP + i * ROW_STEP, INFO.width - 10, 9);
            return;
        }
        if (zone != null) {
            String what = msg.get("zone." + zone.letter());
            title(g, level == 0 ? msg.format("info.zoned", what) : msg.format("info.level", what, level), Art.col(Pictures.zoneColour(zone)));
            if (level > 0) {
                String key = zone == Zone.RESIDENTIAL ? "info.people" : "info.jobs";
                row(g, i++, msg.get(key), Integer.toString(zone.capacity(level)), CHALK);
            }
            boolean powered = game.powered(col, row);
            row(g, i++, msg.get("info.power"), msg.get(powered ? "info.yes" : "info.no"), powered ? GREEN : RED);
            boolean watered = game.watered(col, row);
            row(g, i++, msg.get("info.water"), msg.get(watered ? "info.yes" : "info.no"), watered ? GREEN : level >= 2 ? RED : LABEL);
            if (!game.street(col, row)) row(g, i++, msg.get("info.street"), msg.get("info.no"), RED);
        } else {
            String what = game.road(col, row) ? (game.highway(col, row) ? "info.highway" : game.linked(col, row) ? "info.road" : "info.road.lost")
                    : game.line(col, row) ? "info.line"
                    : game.rubble(col, row) ? "info.rubble"
                    : "ground." + game.ground(col, row).name().toLowerCase();
            title(g, msg.get(what), game.road(col, row) && !game.linked(col, row) ? RED : EDGE);
        }
        if (game.ground(col, row) == Ground.WATER) return;
        int value = game.value(col, row);
        row(g, i++, msg.get("info.value"), msg.get(value >= 55 ? "info.high" : value >= 30 ? "info.some" : "info.low"),
                value >= 55 ? GREEN : value >= 30 ? CHALK : AMBER);
        int smoke = game.smoke(col, row);
        if (i < 5) row(g, i, msg.get("info.smoke"), msg.get(smoke >= 40 ? "info.high" : smoke >= 10 ? "info.some" : "info.none"),
                smoke >= 40 ? RED : smoke >= 10 ? AMBER : CHALK);
    }
    // --8<-- [end:tile-info]

    // --8<-- [start:budget]
    /** The budget: last month's taxes and upkeep, the balance, and the tax rate with its buttons. */
    private void budget(Graphics2D g, Game game, View view) {
        title(g, msg.get("info.budget"), AMBER);
        double balance = game.income() - game.expenses();
        row(g, 0, msg.get("info.taxes"), money(game.income()), CHALK);
        row(g, 1, msg.get("info.upkeep"), money(game.expenses()), CHALK);
        row(g, 2, msg.get("info.balance"), (balance >= 0 ? "+" : "") + money(balance), balance >= 0 ? GREEN : RED);
        float y = (float) (ROW_TOP + 3 * ROW_STEP);
        g.setFont(font(7, false));
        g.setColor(LABEL);
        g.drawString(msg.get("info.rate"), (float) INFO.x + 5, y);
        g.setFont(font(7, true));
        g.setColor(game.tax() > Field.TAX ? AMBER : CHALK);
        centred(g, game.tax() + "%", (TAX_DOWN.getMaxX() + TAX_UP.x) / 2, y);
        small(g, TAX_DOWN, "-", view.over(TAX_DOWN));
        small(g, TAX_UP, "+", view.over(TAX_UP));
        lines(g, msg.get("help.tax"), INFO.x + 5, ROW_TOP + 4 * ROW_STEP - 1, INFO.width - 10, 7.5);
    }
    // --8<-- [end:budget]

    private void small(Graphics2D g, Rectangle2D.Double box, String text, boolean hover) {
        g.setColor(hover ? new Color(0x46505C) : new Color(0x3A434E));
        g.fill(box);
        bevel(g, box.x, box.y, box.width, box.height, false);
        g.setFont(font(8, true));
        g.setColor(CHALK);
        centred(g, text, box.getCenterX(), box.y + 8);
    }

    /** What the mission asks and how far the town has come. */
    private void mission(Graphics2D g, Game game) {
        Mission mission = game.mission();
        title(g, msg.format("info.mission", mission.number()), EDGE);
        int i = 0;
        row(g, i++, msg.get("info.people"), number(game.people()) + "/" + number(mission.goal()),
                game.people() >= mission.goal() ? GREEN : CHALK);
        if (mission.happiness() > 0) {
            row(g, i++, msg.get("info.happiness"), game.happiness() + "/" + mission.happiness() + "%",
                    game.happiness() >= mission.happiness() ? GREEN : AMBER);
        }
        if (mission.years() > 0) {
            int left = mission.years() * 12 - game.months();
            row(g, i++, msg.get("info.left"), msg.format("info.years.months", left / 12, left % 12), left <= 12 ? AMBER : CHALK);
        }
        row(g, i++, msg.get("info.jobs"), number(game.jobs()), CHALK);
        if (game.endless()) row(g, i, msg.get("info.endless"), "", GREEN);
        else lines(g, msg.get("mission." + mission.number() + ".text"), INFO.x + 5, ROW_TOP + i * ROW_STEP, INFO.width - 10, 9);
    }

    /** A few lines of plain text, broken at spaces to fit {@code width}, none below the box. */
    private void lines(Graphics2D g, String text, double x, double y, double width, double step) {
        g.setFont(font(6.5f, false));
        g.setColor(LABEL);
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        double bottom = y < INFO.y ? DEMAND.y + DEMAND.height - 3 : INFO.y + INFO.height - 3;
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && fm.stringWidth(line + " " + word) > width) {
                if (y <= bottom) g.drawString(line.toString(), (float) x, (float) y);
                y += step;
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty() && y <= bottom) g.drawString(line.toString(), (float) x, (float) y);
    }

    private void button(Graphics2D g, Rectangle2D.Double box, String text, boolean active, boolean hover) {
        face(g, "button " + box.x + " " + box.y + active + hover, box.x, box.y, box.width, box.height, (sg, w, h) -> {
            sg.setPaint(new GradientPaint((float) box.x, (float) box.y, active ? new Color(0x3A6A8A) : hover ? new Color(0x46505C)
                    : new Color(0x3A434E), (float) box.x, (float) (box.y + box.height), active ? new Color(0x24465E) : new Color(0x262D36)));
            sg.fill(box);
            bevel(sg, box.x, box.y, box.width, box.height, false);
        });
        g.setFont(font(7, true));
        g.setColor(CHALK);
        centred(g, text, box.getCenterX(), box.y + 9);
    }

    static void bevel(Graphics2D g, double x, double y, double w, double h, boolean sunk) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(sunk ? DARK : LIGHT);
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + w - 0.5, y + 0.5));
        g.draw(new Line2D.Double(x + 0.5, y + 0.5, x + 0.5, y + h - 0.5));
        g.setColor(sunk ? LIGHT : DARK);
        g.draw(new Line2D.Double(x + 0.5, y + h - 0.5, x + w - 0.5, y + h - 0.5));
        g.draw(new Line2D.Double(x + w - 0.5, y + 0.5, x + w - 0.5, y + h - 0.5));
    }

    /**
     * A part of the screen drawn once and then only copied: its gradients cost more than the rest of the
     * frame together. {@code drawing} draws at {@code x}, {@code y} as if on the screen.
     */
    private void face(Graphics2D g, String key, double x, double y, double w, double h, Sprite.Drawing drawing) {
        faces.computeIfAbsent(key, k -> Sprite.drawn(w, h, (sg, sw, sh) -> {
            smooth(sg);
            sg.translate(-x, -y);
            drawing.draw(sg, sw, sh);
        })).draw(g, x, y);
    }

    // --8<-- [start:minimap]
    /** The colour of a tile on the minimap. */
    static int minimapPixel(Game game, int col, int row) {
        if (game.road(col, row)) return 0x9A9A9A;
        Building b = game.buildingAt(col, row);
        if (b != null) return b.type().work() == Work.PARK ? 0x4A9A3A : b.type().plant() ? 0xC07A3A : 0xD8D4CC;
        Zone zone = game.zone(col, row);
        if (zone != null) {
            boolean built = game.lot(col, row) > 0;
            return switch (zone) {
                case RESIDENTIAL -> built ? 0x6ACB5A : 0x3A7A34;
                case COMMERCIAL -> built ? 0x5A9AE8 : 0x3A5A7A;
                case INDUSTRIAL -> built ? 0xE8C050 : 0x7A6A34;
            };
        }
        if (game.line(col, row)) return 0x8A8270;
        return switch (game.ground(col, row)) {
            case WATER -> 0x3E78B0;
            case FOREST -> 0x2A5226;
            case GRASS -> 0x5E8A3A;
        };
    }

    /** The colour of a tile of a map before anything is built: for the cards of the choice. */
    static int mapPixel(Level level, int col, int row) {
        if (level.highway(col, row)) return 0x9A9A9A;
        return switch (level.ground(col, row)) {
            case WATER -> 0x3E78B0;
            case FOREST -> 0x2A5226;
            case GRASS -> 0x5E8A3A;
        };
    }
    // --8<-- [end:minimap]

    // ---- over the map ----------------------------------------------------------------------------

    /** The size and the cost of what is being dragged, next to the mouse. */
    void dragLabel(Graphics2D g, double x, double y, String size, int cost, boolean affordable, int colour) {
        g.setFont(font(8, true));
        String price = money(cost);
        FontMetrics fm = g.getFontMetrics();
        double width = fm.stringWidth(size) + fm.stringWidth(price) + 16;
        double lx = Math.min(x + 12, Field.VIEW_WIDTH - width - 4);
        double ly = Math.min(y + 4, Field.HEIGHT - 30);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(lx + 1, ly + 1, width, 13, 5, 5));
        g.setColor(new Color(0x20262C));
        g.fill(new RoundRectangle2D.Double(lx, ly, width, 13, 5, 5));
        g.setColor(Art.col(colour));
        g.fill(new RoundRectangle2D.Double(lx, ly, 3, 13, 2, 2));
        g.setColor(new Color(0xF2F4F0));
        g.drawString(size, (float) lx + 6, (float) ly + 9.5f);
        g.setColor(affordable ? AMBER : RED);
        g.drawString(price, (float) (lx + width - 5 - fm.stringWidth(price)), (float) ly + 9.5f);
    }

    /** The newest message at the bottom left of the map, with its badge, fading out in its last second. */
    void message(Graphics2D g, String line, double shown, Color colour) {
        if (line == null || shown >= MESSAGE_SECONDS) return;
        double a = Math.clamp(MESSAGE_SECONDS - shown, 0, 1);
        g.setFont(font(9, true));
        int width = g.getFontMetrics().stringWidth(line);
        g.setColor(new Color(0, 0, 0, (int) (130 * a)));
        g.fill(new RoundRectangle2D.Double(4, Field.HEIGHT - 21, width + 22, 15, 5, 5));
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), (int) (255 * a)));
        g.fill(new Ellipse2D.Double(8.5, Field.HEIGHT - 17, 7, 7));
        g.setColor(new Color(0x3A2410));
        g.setFont(font(6.5f, true));
        centred(g, "!", 12, Field.HEIGHT - 11.3);
        g.setFont(font(9, true));
        g.setColor(new Color(CHALK.getRed(), CHALK.getGreen(), CHALK.getBlue(), (int) (255 * a)));
        g.drawString(line, 20, (float) Field.HEIGHT - 10);
    }

    /** A line at the top left of the map, such as what F3 shows. */
    void corner(Graphics2D g, String line, double y) {
        g.setFont(font(8, true));
        g.setColor(SHADOW);
        g.drawString(line, 9, (float) y + 1);
        g.setColor(CHALK);
        g.drawString(line, 8, (float) y);
    }

    // ---- the screens -----------------------------------------------------------------------------

    // --8<-- [start:card]
    /** One mission on the choice screen: its number and name, its map, what it asks, the fastest win, or a lock. */
    void card(Graphics2D g, double x, double y, Mission mission, DiamondMinimap map, boolean open, boolean chosen, Progress progress) {
        int number = mission.number();
        RoundRectangle2D.Double card = new RoundRectangle2D.Double(x, y, CARD_WIDTH, CARD_HEIGHT, 10, 10);
        g.setPaint(new GradientPaint((float) x, (float) y, chosen ? new Color(0x2E4A5E) : new Color(0x2E353E), (float) x,
                (float) (y + CARD_HEIGHT), new Color(0x161A20)));
        g.fill(card);
        g.setColor(chosen ? AMBER : EDGE);
        g.setStroke(new BasicStroke(chosen ? 1.6f : 1f));
        g.draw(card);
        double cx = x + CARD_WIDTH / 2;
        g.setFont(font(14, true));
        g.setColor(open ? AMBER : GREY);
        centred(g, number + "  " + name(number), cx, y + 22);
        if (open) {
            map.render(g);
        } else {
            lock(g, cx, y + 50);
        }
        g.setFont(font(9, false));
        if (!open) {
            g.setColor(GREY);
            centred(g, msg.get("select.locked"), cx, y + 150);
            return;
        }
        g.setColor(LABEL);
        centred(g, msg.get("mission." + number + ".text"), cx, y + 112);
        g.setColor(CHALK);
        centred(g, msg.format("select.goal", number(mission.goal())), cx, y + 126);
        String hard = (mission.happiness() > 0 ? msg.format("select.happy", mission.happiness()) : msg.get("select.free"))
                + (mission.years() > 0 ? " · " + msg.format("select.years", mission.years()) : "")
                + (mission.fire() ? " · " + msg.get("select.fire") : "");
        g.setFont(font(8, false));
        g.setColor(LABEL);
        centred(g, hard, cx, y + 139);
        int best = progress.best(number);
        g.setFont(font(9, false));
        g.setColor(best > 0 ? CHALK : GREY);
        centred(g, best > 0 ? msg.format("select.best", best / 12, best % 12) : msg.get("select.none"), cx, y + 158);
    }
    // --8<-- [end:card]

    static double cardX(int index, int count) {
        double left = (Field.WIDTH - count * CARD_WIDTH - (count - 1) * CARD_GAP) / 2;
        return left + index * (CARD_WIDTH + CARD_GAP);
    }

    private void lock(Graphics2D g, double cx, double y) {
        g.setColor(GREY);
        g.setStroke(new BasicStroke(3));
        g.draw(new RoundRectangle2D.Double(cx - 7, y - 2, 14, 16, 12, 12));
        rectangle.setRect(cx - 11, y + 7, 22, 16);
        g.fill(rectangle);
        g.setStroke(new BasicStroke(1));
    }

    /** What a mission's end shows besides won or lost. */
    record Tally(int people, int jobs, int happiness, int buildings, int fires, int months) {
    }

    /** The end of a mission: won or lost, how long it took, and what the town became. */
    void result(Graphics2D g, Game game, Tally tally, boolean best, boolean last) {
        veil(g);
        boolean won = game.phase() == Game.Phase.WON;
        text(g, msg.get(won ? "won.title" : "lost.title"), 100, 32, true, won ? AMBER : RED);
        text(g, msg.format("result.time", tally.months() / 12, tally.months() % 12), 135, 14, false, CHALK);
        if (!won) text(g, msg.get(game.money() < Field.DEBT ? "lost.money" : "lost.time"), 155, 12, false, RED);
        else if (best) text(g, msg.get("won.best"), 155, 12, true, GREEN);
        String[] rows = {"result.people", "result.jobs", "result.happiness", "result.buildings", "result.fires"};
        String[] values = {number(tally.people()), number(tally.jobs()), tally.happiness() + "%", number(tally.buildings()),
                number(tally.fires())};
        double top = 185;
        for (int i = 0; i < rows.length; i++) {
            double y = top + i * 18;
            g.setFont(font(11, false));
            g.setColor(CHALK);
            String label = msg.get(rows[i]);
            g.drawString(label, (float) (Field.WIDTH / 2 + 10 - g.getFontMetrics().stringWidth(label)), (float) y);
            g.setFont(font(11, true));
            g.drawString(values[i], (float) Field.WIDTH / 2 + 30, (float) y);
        }
        text(g, msg.get(won ? (last ? "won.last" : "won.next") : "lost.again"), 300, 13, false, CHALK);
        if (won) text(g, msg.get("won.on"), 320, 11, false, LABEL);
    }

    String name(int number) {
        return msg.get("mission." + number);
    }

    static void centred(Graphics2D g, String text, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) y);
    }

    private static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** Darkens the screen so text over it stays readable. */
    void veil(Graphics2D g) {
        g.setColor(VEIL);
        rectangle.setRect(0, 0, Field.WIDTH, Field.HEIGHT);
        g.fill(rectangle);
    }

    /** A line of text centred on the screen, its baseline at {@code y}, with a shadow. */
    void text(Graphics2D g, String line, double y, int size, boolean bold, Color colour) {
        text(g, line, Field.WIDTH / 2, y, size, bold, colour);
    }

    void text(Graphics2D g, String line, double x, double y, int size, boolean bold, Color colour) {
        smooth(g);
        g.setFont(font(size, bold));
        float left = (float) (x - g.getFontMetrics().stringWidth(line) / 2.0);
        g.setColor(SHADOW);
        g.drawString(line, left + 1, (float) y + 1);
        g.setColor(colour);
        g.drawString(line, left, (float) y);
    }

    Font font(float size, boolean bold) {
        return fonts.computeIfAbsent((int) (size * 20) * 2 + (bold ? 1 : 0),
                key -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size));
    }
}
