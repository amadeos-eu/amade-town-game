package eu.amadeos.games.town.ui.game;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.town.bootstrap.TownModule;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Planner;
import eu.amadeos.games.town.repository.Progress;
import eu.amadeos.games.town.repository.SaveSlot;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * How long a frame takes to draw, after two years of the plan building the first town, at both zooms and two
 * window sizes: {@code ./gradlew benchmark}. The view drifts slowly over the town. The frames are drawn
 * into a compatible image, as the canvas draws, but without waiting for the screen.
 */
public final class Benchmark {
    static final int WARM_UP = 60;
    static final int FRAMES = 300;
    static final double STEP = 1.0 / TownGame.UPDATES_PER_SECOND;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("town");
        Messages msg = Messages.load(TownModule.class, TownModule.MESSAGES, Locale.ENGLISH);
        TownGame game = new TownGame(new Keyboard(), new Mouse(), msg,
                new Progress(new SaveFile(dir.resolve("progress.save"))),
                new SaveSlot(new SaveFile(dir.resolve("town.save"))), new Mixer());
        GraphicsConfiguration gc = GraphicsEnvironment.isHeadless() ? null
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Game town = new Game(game.level(1), game.rules(), 7);
        Planner planner = new Planner(game.rules().plan());
        while (town.months() < 24) {
            planner.update(town, STEP);
            town.update(STEP);
            if (town.phase() != Game.Phase.PLAYING) town.playOn();
        }
        TownGame.PlayScene play = game.play(town);
        System.out.println("ms per frame              zoom   frame   cars");
        for (int scale : new int[]{2, 3}) {
            for (int zoom : TownGame.ZOOMS) {
                play.zoomTo(zoom);
                play.camera().jumpTo(play.iso().pictureX(18 * Field.TILE, 30 * Field.TILE),
                        play.iso().pictureY(18 * Field.TILE, 30 * Field.TILE, 0));
                int w = (int) Field.WIDTH * scale;
                int h = (int) Field.HEIGHT * scale;
                BufferedImage image = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                long total = 0;
                int cars = 0;
                for (int i = 0; i < WARM_UP + FRAMES; i++) {
                    game.update(STEP);
                    game.update(STEP);
                    play.camera().moveBy(0.5, 0);
                    Graphics2D g = image.createGraphics();
                    g.scale(scale, scale);
                    long start = System.nanoTime();
                    game.render(g, 0.5);
                    game.renderOverlay(g, 0.5);
                    if (i >= WARM_UP) {
                        total += System.nanoTime() - start;
                        cars += game.traffic().cars().size();
                    }
                    g.dispose();
                }
                System.out.printf(Locale.ROOT, "%4d x %4d                %d    %6.2f   %4d%n", w, h, zoom,
                        total / 1e6 / FRAMES, cars / FRAMES);
            }
        }
        System.exit(0);
    }
}
