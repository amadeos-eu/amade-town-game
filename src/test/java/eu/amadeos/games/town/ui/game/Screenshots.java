package eu.amadeos.games.town.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.town.bootstrap.TownModule;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Mission;
import eu.amadeos.games.town.domain.Planner;
import eu.amadeos.games.town.repository.Progress;
import eu.amadeos.games.town.repository.SaveSlot;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The screenshots of chapter 33, drawn without a screen: the title, the choice of a mission, a town the
 * computer built, zones being dragged, a school looking for its place, the overlays, the budget and a fire.
 * {@code ./gradlew screenshots -Pshots=<folder>}.
 */
public final class Screenshots {
    private static final double STEP = 1.0 / TownGame.UPDATES_PER_SECOND;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private final TownGame game;
    private double mouseX;
    private double mouseY;

    private Screenshots() throws Exception {
        Messages msg = Messages.load(TownModule.class, TownModule.MESSAGES, Locale.ENGLISH);
        Path dir = Files.createTempDirectory("town");
        Progress progress = new Progress(new SaveFile(dir.resolve("progress.save")));
        progress.won(1, 22, 3);      // one mission won, so the choice shows a time and a lock
        game = new TownGame(keyboard, mouse, msg, progress, new SaveSlot(new SaveFile(dir.resolve("town.save"))),
                new Mixer());
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");
        dir.mkdirs();
        Screenshots shots = new Screenshots();
        shots.seconds(40);
        shots.save(new File(dir, "title.png"), false);
        shots.tap(KeyEvent.VK_ENTER);
        shots.steps(60);
        shots.save(new File(dir, "choice.png"), false);

        // Mission 1, built by the plan for a year and a half: roads, zones grown to houses, shops and works.
        TownGame.PlayScene play = shots.town(1, 7, 18);
        shots.seconds(6);
        shots.look(play, 22, 24);
        shots.save(new File(dir, "town.png"), false);
        play.zoomTo(1);
        shots.save(new File(dir, "overview.png"), false);
        play.zoomTo(2);

        shots.zoning(play);
        shots.save(new File(dir, "zoning.png"), true);
        play.dragging(null, null);

        shots.placing(play, Tool.SERVICES, "school");
        shots.save(new File(dir, "placing.png"), true);
        shots.tap(KeyEvent.VK_ESCAPE);

        play.show(Hud.Info.TOOL, TownPainter.Overlay.POWER);
        shots.steps(2);
        shots.save(new File(dir, "power.png"), false);
        play.show(Hud.Info.BUDGET, TownPainter.Overlay.VALUE);
        shots.steps(2);
        shots.save(new File(dir, "budget.png"), false);
        play.show(Hud.Info.TOOL, TownPainter.Overlay.NONE);

        // The first town again, this time with fires, as in the second mission.
        TownGame.PlayScene fire = shots.play(shots.withFires(), 5, 24);
        shots.until(fire, g -> burning(g) != null, 600);
        shots.seconds(3);
        Cell burning = burning(fire.game());
        shots.look(fire, burning.col(), burning.row());
        shots.save(new File(dir, "fire.png"), false);
        System.exit(0);
    }

    private static Cell burning(Game g) {
        for (int row = 0; row < g.height(); row++) {
            for (int col = 0; col < g.width(); col++) if (g.burning(col, row)) return new Cell(col, row);
        }
        return null;
    }

    /** The map of the first mission where houses catch fire. */
    private Level withFires() throws Exception {
        Mission first = game.rules().mission(1);
        Mission fires = new Mission(1, first.map(), first.money(), first.goal(), 0, 0, true, first.forbidden());
        try (Reader reader = new InputStreamReader(Level.class.getResourceAsStream(Level.FOLDER + first.map()),
                StandardCharsets.UTF_8)) {
            return Level.read(fires, first.map(), reader);
        }
    }

    /** Mission {@code number} built by the plan for {@code months}, then shown. */
    private TownGame.PlayScene town(int number, long seed, int months) {
        return play(game.level(number), seed, months);
    }

    private TownGame.PlayScene play(Level level, long seed, int months) {
        Game town = new Game(level, game.rules(), seed);
        Planner planner = new Planner(game.rules().plan());
        while (town.months() < months) {
            planner.update(town, STEP);
            town.update(STEP);
            if (town.phase() != Game.Phase.PLAYING) town.playOn();
        }
        TownGame.PlayScene play = game.play(town);
        steps(2);
        return play;
    }

    /** Centres the view on a tile. */
    private void look(TownGame.PlayScene play, double col, double row) {
        play.camera().jumpTo(play.iso().pictureX(col * Field.TILE, row * Field.TILE),
                play.iso().pictureY(col * Field.TILE, row * Field.TILE, 0));
        steps(2);
    }

    /** Goes on until {@code done}, at most {@code seconds}. */
    private void until(TownGame.PlayScene play, Predicate<Game> done, double seconds) {
        for (int i = 0; i < seconds * TownGame.UPDATES_PER_SECOND; i++) {
            if (done.test(play.game())) return;
            steps(1);
        }
        throw new IllegalStateException("not reached by month " + play.game().months());
    }

    /** A homes zone being dragged across empty meadow next to a road. */
    private void zoning(TownGame.PlayScene play) {
        Game g = play.game();
        play.choose(Tool.HOMES);
        for (int row = 4; row < g.height() - 8; row++) {
            for (int col = 4; col < g.width() - 8; col++) {
                if (!g.road(col, row - 1) || g.highway(col, row - 1) || !free(g, col, row, 5, 3)) continue;
                look(play, col + 2, row + 1);
                Cell a = new Cell(col, row);
                Cell b = new Cell(col + 4, row + 2);
                play.dragging(a, b);
                point(play, b.col() + 0.5, b.row() + 0.5);
                return;
            }
        }
        throw new IllegalStateException("no meadow to zone");
    }

    /** A building from the panel, looking for its place near the middle of the town. */
    private void placing(TownGame.PlayScene play, Tool tool, String id) {
        Game g = play.game();
        play.choose(tool);
        while (!id.equals(play.choice())) play.choose(tool);
        var type = g.rules().building(id);
        for (int row = 4; row < g.height() - 8; row++) {
            for (int col = 4; col < g.width() - 8; col++) {
                if (!g.road(col, row - 1) || g.highway(col, row - 1)) continue;
                if (g.placement(type, col, row) != Game.Result.OK) continue;
                look(play, col + 1, row + 1);
                point(play, col + type.width() / 2.0, row + type.height() / 2.0);
                return;
            }
        }
        throw new IllegalStateException("no place for a " + id);
    }

    private static boolean free(Game g, int col, int row, int w, int h) {
        for (int r = row; r < row + h; r++) {
            for (int c = col; c < col + w; c++) {
                if (!g.contains(c, r) || g.ground(c, r) == eu.amadeos.games.town.domain.Ground.WATER || g.road(c, r)
                        || g.line(c, r) || g.buildingAt(c, r) != null || g.zone(c, r) != null) return false;
            }
        }
        return true;
    }

    private void moveTo(double x, double y) {
        mouseX = x;
        mouseY = y;
        mouse.moved(x, y);
        steps(2);
    }

    /** Puts the mouse over a point of the ground, in tiles. */
    private void point(TownGame.PlayScene play, double col, double row) {
        double px = play.iso().pictureX(col * Field.TILE, row * Field.TILE);
        double py = play.iso().pictureY(col * Field.TILE, row * Field.TILE, 0);
        moveTo(play.camera().screenX(px, 1) * play.zoom(), play.camera().screenY(py, 1) * play.zoom());
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void seconds(double seconds) {
        steps((int) Math.round(seconds * TownGame.UPDATES_PER_SECOND));
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    /** World and overlay at twice the logical size, with the mouse pointer if {@code pointer}. */
    private void save(File file, boolean pointer) throws Exception {
        write(file, g -> {
            game.render(g, 1);
            game.renderOverlay(g, 1);
            if (pointer) pointer(g, mouseX, mouseY);
        });
        System.out.println("[screenshots] " + file);
    }

    private static void pointer(Graphics2D g, double x, double y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x, y + 13);
        p.lineTo(x + 3.5, y + 10);
        p.lineTo(x + 6, y + 15);
        p.lineTo(x + 8, y + 14);
        p.lineTo(x + 5.5, y + 9.5);
        p.lineTo(x + 9.5, y + 9.5);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    private static void write(File file, Consumer<Graphics2D> draw) throws Exception {
        int scale = 2;
        BufferedImage image = new BufferedImage((int) Field.WIDTH * scale, (int) Field.HEIGHT * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        draw.accept(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
