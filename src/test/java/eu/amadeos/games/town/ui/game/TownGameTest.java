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
import eu.amadeos.games.town.domain.Ground;
import eu.amadeos.games.town.repository.Progress;
import eu.amadeos.games.town.repository.SaveSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game without a window and without the loop thread: the test moves the mouse, clicks, presses keys and
 * calls the steps itself, so everything happens in order on the test thread.
 */
class TownGameTest {
    private static final double STEP = 1.0 / TownGame.UPDATES_PER_SECOND;

    @TempDir
    Path dir;

    private final long[] now = {0};
    private final Keyboard keyboard = new Keyboard(() -> now[0]);
    private final Mouse mouse = new Mouse();
    private SaveSlot slot;
    private TownGame game;

    @BeforeEach
    void create() {
        Messages msg = Messages.load(TownModule.class, TownModule.MESSAGES, Locale.ENGLISH);
        slot = new SaveSlot(new SaveFile(dir.resolve("town.save")));
        game = new TownGame(keyboard, mouse, msg, new Progress(new SaveFile(dir.resolve("progress.save"))), slot,
                new Mixer());
    }

    private void tap(int key) {
        keyboard.pressed(key, now[0]);
        keyboard.released(key, now[0] + 1_000_000);
        steps(5);
    }

    private void steps(int n) {
        for (int i = 0; i < n; i++) {
            now[0] += (long) (STEP * 1e9);
            game.update(STEP);
        }
    }

    /** Puts the mouse over the middle of a tile. */
    private void over(TownGame.PlayScene play, int col, int row) {
        double px = play.iso().pictureX((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE);
        double py = play.iso().pictureY((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE, 0);
        mouse.moved(play.camera().screenX(px, 1) * play.zoom(), play.camera().screenY(py, 1) * play.zoom());
        steps(2);
    }

    private void press(int button) {
        mouse.pressed(button);
        steps(1);
    }

    private void release(int button) {
        mouse.released(button);
        steps(1);
    }

    /** A free row of meadow, long enough for a road, and in the middle of the view. */
    private Cell meadow(TownGame.PlayScene play, int length) {
        Game g = play.game();
        for (int row = 5; row < g.height() - 5; row++) {
            for (int col = 5; col < g.width() - length - 5; col++) {
                boolean free = true;
                for (int c = col; c < col + length && free; c++) {
                    free = g.ground(c, row) == Ground.GRASS && !g.road(c, row) && !g.highway(c, row);
                }
                if (!free) continue;
                play.camera().jumpTo(play.iso().pictureX((col + length / 2.0) * Field.TILE, row * Field.TILE),
                        play.iso().pictureY((col + length / 2.0) * Field.TILE, row * Field.TILE, 0));
                steps(1);
                return new Cell(col, row);
            }
        }
        throw new AssertionError("no meadow");
    }

    @Test
    void theTitleLeadsToTheChoiceAndTheChoiceToTheFirstMission() {
        assertInstanceOf(TownGame.TitleScene.class, game.top());
        steps(120);
        tap(KeyEvent.VK_ENTER);
        assertInstanceOf(TownGame.ChoiceScene.class, game.top());
        tap(KeyEvent.VK_ENTER);
        TownGame.PlayScene play = assertInstanceOf(TownGame.PlayScene.class, game.top());
        assertEquals(1, play.game().mission().number());
        tap(KeyEvent.VK_P);
        assertInstanceOf(TownGame.PauseScene.class, game.top());
    }

    @Test
    void theNumberKeysChooseToolsAndPressingAgainTheNextBuilding() {
        TownGame.PlayScene play = game.play(1);
        tap(KeyEvent.VK_1);
        assertEquals(Tool.ROAD, play.tool());
        tap(KeyEvent.VK_5);
        assertEquals(Tool.POWER, play.tool());
        assertEquals(Tool.LINE, play.choice());
        tap(KeyEvent.VK_5);
        assertEquals("coal", play.choice());
        tap(KeyEvent.VK_ESCAPE);
        assertNull(play.tool(), "Escape puts the tool away");
    }

    @Test
    void aRoadIsDraggedAcrossTheMap() {
        TownGame.PlayScene play = game.play(1);
        Cell start = meadow(play, 6);
        tap(KeyEvent.VK_1);
        double money = play.game().money();
        over(play, start.col(), start.row());
        press(MouseEvent.BUTTON1);
        over(play, start.col() + 5, start.row());
        assertEquals(6, play.marks().path().size());
        release(MouseEvent.BUTTON1);
        for (int c = start.col(); c <= start.col() + 5; c++) assertTrue(play.game().road(c, start.row()));
        assertEquals(money - 6 * Field.ROAD, play.game().money(), 1e-6);
    }

    @Test
    void escapeDropsADragBeforeTheTool() {
        TownGame.PlayScene play = game.play(1);
        Cell start = meadow(play, 4);
        tap(KeyEvent.VK_2);
        over(play, start.col(), start.row());
        press(MouseEvent.BUTTON1);
        over(play, start.col() + 3, start.row());
        assertNotNull(play.marks().area());
        tap(KeyEvent.VK_ESCAPE);
        release(MouseEvent.BUTTON1);
        assertNull(play.game().zone(start.col(), start.row()));
        assertEquals(Tool.HOMES, play.tool(), "the first Escape drops the drag, not the tool");
    }

    // --8<-- [start:test-zoom]
    @Test
    void theWheelZoomsAndKeepsTheTileUnderTheMouse() {
        TownGame.PlayScene play = game.play(1);
        Cell start = meadow(play, 4);
        assertEquals(2, play.zoom(), "the game begins close up");
        over(play, start.col() + 1, start.row());
        Cell before = play.hover();
        mouse.wheeled(1);
        steps(1);
        assertEquals(1, play.zoom());
        steps(1);
        assertEquals(before, play.hover());
        mouse.wheeled(-1);
        steps(2);
        assertEquals(2, play.zoom());
        assertEquals(before, play.hover());
        tap(KeyEvent.VK_Z);
        assertEquals(1, play.zoom());
    }
    // --8<-- [end:test-zoom]

    // --8<-- [start:test-wide]
    @Test
    void aWideScreenShowsMoreMapAndMovesThePanelToTheRightEdge() {
        game.resize(711);                       // 16:9: 711 × 400
        TownGame.PlayScene play = game.play(1);
        steps(2);
        assertEquals((Field.VIEW_WIDTH + 71) / play.zoom(), play.camera().viewWidth(), 1e-9,
                "the map gets the extra width");
        Rectangle2D.Double road = Hud.tool(Tool.ROAD.ordinal());
        mouse.moved(road.getCenterX() + 71, road.getCenterY());          // the panel is 71 units further right
        steps(1);
        press(MouseEvent.BUTTON1);
        release(MouseEvent.BUTTON1);
        assertEquals(Tool.ROAD, play.tool());
        Cell start = meadow(play, 4);
        over(play, start.col() + 1, start.row());   // zooming keeps the tile under the mouse in the wider view
        Cell before = play.hover();
        mouse.wheeled(1);
        steps(2);
        assertEquals(before, play.hover());
        play.camera().jumpTo(0, 0);
        mouse.moved(710, 200);                  // the real right edge of the screen moves the map
        steps(1);
        double x = play.camera().x();
        steps(10);
        assertTrue(play.camera().x() > x);
    }
    // --8<-- [end:test-wide]

    @Test
    void f5SavesTheTownAndF9LoadsItBack() {
        TownGame.PlayScene play = game.play(1);
        Cell start = meadow(play, 3);
        play.game().road(Game.path(start.col(), start.row(), start.col() + 2, start.row()));
        tap(KeyEvent.VK_F5);
        assertTrue(slot.exists());
        game.play(1);
        tap(KeyEvent.VK_F9);
        TownGame.PlayScene loaded = assertInstanceOf(TownGame.PlayScene.class, game.top());
        assertTrue(loaded.game().road(start.col() + 1, start.row()));
    }

    @Test
    void everySceneDraws() {
        BufferedImage image = new BufferedImage((int) Field.WIDTH, (int) Field.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        game.render(g, 1);
        game.renderOverlay(g, 1);
        tap(KeyEvent.VK_ENTER);
        game.render(g, 1);
        game.renderOverlay(g, 1);
        TownGame.PlayScene play = game.play(1);
        for (TownPainter.Overlay overlay : TownPainter.Overlay.values()) {
            play.show(Hud.Info.BUDGET, overlay);
            steps(2);
            game.render(g, 1);
            game.renderOverlay(g, 1);
        }
        g.dispose();
    }
}
