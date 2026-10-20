package eu.amadeos.games.town.ui.game;

import com.google.inject.Inject;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.input.Controls;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.gameengine.iso.DiamondMinimap;
import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.gameengine.loop.GameLoop;
import eu.amadeos.gameengine.scene.Scene;
import eu.amadeos.gameengine.scene.SceneStack;
import eu.amadeos.gameengine.swing.GameCanvas;
import eu.amadeos.gameengine.tile.Cell;
import eu.amadeos.games.town.domain.Building;
import eu.amadeos.games.town.domain.BuildingType;
import eu.amadeos.games.town.domain.Event;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Planner;
import eu.amadeos.games.town.domain.Rules;
import eu.amadeos.games.town.domain.Zone;
import eu.amadeos.games.town.repository.Progress;
import eu.amadeos.games.town.repository.SaveSlot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The running game: the loop, the mouse and the keys, the effects and the scenes (the title with the
 * computer building a town, the choice of a mission, a mission, the pause and its end). Everything here
 * runs on the game loop thread, except the {@link Arena} methods, which only post commands to it.
 */
public final class TownGame implements Arena {
    enum Action {
        LEFT, RIGHT, UP, DOWN, PAUSE, BACK, OK, SAVE, LOAD, FASTER, SLOWER, OVERLAY, BUDGET, COUNTS, KEEP, ZOOM,
        TOOL_1, TOOL_2, TOOL_3, TOOL_4, TOOL_5, TOOL_6, TOOL_7, TOOL_8, TOOL_9
    }

    static final int UPDATES_PER_SECOND = 120;
    static final int FRAMES_PER_SECOND = 60;
    /** How fast the keys and the edge of the screen move the map, in units per second. */
    static final double PAN_SPEED = 360;
    static final double EDGE = 3;
    /** How far the right button may move before a click becomes dragging the map. */
    static final double CLICK_SLACK = 3;
    /** How long a mission goes on behind its end before the result shows. */
    static final double END_SECONDS = 2;
    /** The title's town grows this many times as fast, and starts again after this long. */
    static final int TITLE_STEPS = 4;
    static final double TITLE_SECONDS = 600;
    static final int[] SPEEDS = {1, 2, 4};
    /** How large the map is drawn: the whole of a district, or close up, as the game begins. */
    static final int[] ZOOMS = {1, 2};
    /** The minimap is painted again at most this often. */
    static final double MINIMAP_SECONDS = 0.5;
    static final double PAUSE_TOP = 170;
    static final double PAUSE_ROW = 24;

    private final Keyboard keyboard;
    private final Mouse mouse;
    private final Messages msg;
    private final Progress progress;
    private final SaveSlot slot;
    private final Mixer mixer;
    private final Rules rules;
    private final TownPainter painter;
    private final Hud hud;
    private final Effects effects;
    private final Traffic traffic = new Traffic();
    private final SceneStack scenes = new SceneStack();
    private final Controls<Action> controls;
    private final List<Level> levels = new ArrayList<>();
    private int chosen = 1;
    private long seed = System.nanoTime();
    private GameLoop loop;

    @Inject
    public TownGame(Keyboard keyboard, Mouse mouse, Messages msg, Progress progress, SaveSlot slot, Mixer mixer) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.msg = msg;
        this.progress = progress;
        this.slot = slot;
        this.mixer = mixer;
        this.rules = Rules.load();
        for (int i = 1; i <= rules.missions(); i++) levels.add(Level.load(i, rules));
        this.painter = new TownPainter(rules);
        this.hud = new Hud(msg);
        this.effects = new Effects(mixer, painter.pictures());
        // --8<-- [start:controls]
        controls = new Controls<>(Action.class, keyboard)
                .bind(Action.LEFT, KeyEvent.VK_LEFT)
                .bind(Action.RIGHT, KeyEvent.VK_RIGHT)
                .bind(Action.UP, KeyEvent.VK_UP)
                .bind(Action.DOWN, KeyEvent.VK_DOWN)
                .bind(Action.PAUSE, KeyEvent.VK_P)
                .bind(Action.BACK, KeyEvent.VK_ESCAPE)
                .bind(Action.OK, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)
                .bind(Action.SAVE, KeyEvent.VK_F5)
                .bind(Action.LOAD, KeyEvent.VK_F9)
                .bind(Action.FASTER, KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS)
                .bind(Action.SLOWER, KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT)
                .bind(Action.OVERLAY, KeyEvent.VK_O)
                .bind(Action.BUDGET, KeyEvent.VK_B)
                .bind(Action.COUNTS, KeyEvent.VK_F3)
                .bind(Action.KEEP, KeyEvent.VK_C)
                .bind(Action.ZOOM, KeyEvent.VK_Z);
        for (int i = 0; i < 9; i++) controls.bind(Action.valueOf("TOOL_" + (i + 1)), KeyEvent.VK_1 + i, KeyEvent.VK_NUMPAD1 + i);
        // --8<-- [end:controls]
        scenes.push(new TitleScene());
    }

    /** Starts the loop; the canvas draws the scenes' worlds and then their overlays. */
    public void start(GameCanvas canvas) {
        loop = new GameLoop(UPDATES_PER_SECOND, FRAMES_PER_SECOND, this::update, canvas::render);
        loop.start();
    }

    public void stop() {
        if (loop != null) loop.stop();
    }

    void update(double seconds) {
        keyboard.poll();
        mouse.poll();
        scenes.update(seconds);
    }

    public void render(Graphics2D g, double alpha) {
        scenes.render(g, alpha);
    }

    public void renderOverlay(Graphics2D g, double alpha) {
        scenes.renderOverlay(g, alpha);
    }

    // ---- Arena: called on the Swing thread, carried out on the loop thread -----------------------

    @Override
    public void showTitle() {
        run(() -> scenes.replace(new TitleScene()));
    }

    @Override
    public void chooseLevel() {
        run(() -> scenes.replace(new ChoiceScene()));
    }

    @Override
    public void pause() {
        run(() -> {
            if (scenes.top() instanceof PlayScene play) scenes.push(new PauseScene(play));
        });
    }

    @Override
    public void setSound(boolean on) {
        mixer.setMuted(!on);
    }

    private void run(Runnable command) {
        if (loop != null && loop.running()) loop.post(command);
        else command.run();
    }

    // ---- for tests and screenshots ---------------------------------------------------------------

    Scene top() {
        return scenes.top();
    }

    int sceneCount() {
        return scenes.size();
    }

    Effects effects() {
        return effects;
    }

    Traffic traffic() {
        return traffic;
    }

    Rules rules() {
        return rules;
    }

    Level level(int number) {
        return levels.get(number - 1);
    }

    /** Plays mission {@code number} at once, without the title and the choice. */
    PlayScene play(int number) {
        return play(newGame(number));
    }

    /** Plays a town prepared by a test. */
    PlayScene play(Game town) {
        PlayScene play = new PlayScene(town);
        scenes.replace(play);
        return play;
    }

    // ---- the scenes ------------------------------------------------------------------------------

    private Game newGame(int number) {
        return new Game(levels.get(number - 1), rules, seed++);
    }

    /** The middle of what the plan builds, or of the map: where the title's camera looks. */
    private static double[] middle(IsoProjection iso, Game game) {
        int c = 0, r = 0, n = 0;
        for (int row = 0; row < game.height(); row++) {
            for (int col = 0; col < game.width(); col++) {
                if (game.road(col, row) && !game.highway(col, row) || game.lot(col, row) > 0) {
                    c += col;
                    r += row;
                    n++;
                }
            }
        }
        double col = n == 0 ? game.width() / 2.0 : c / (double) n;
        double row = n == 0 ? game.height() / 2.0 : r / (double) n;
        return new double[]{iso.pictureX(col * Field.TILE, row * Field.TILE), iso.pictureY(col * Field.TILE, row * Field.TILE, 0)};
    }

    /** Where a mission's view begins: on the highway, a few tiles in from the edge of the map. */
    private static double[] entry(IsoProjection iso, Game game) {
        for (int row = 0; row < game.height(); row++) {
            for (int col = 0; col < game.width(); col++) {
                if (!game.highway(col, row)) continue;
                if (col != 0 && row != 0 && col != game.width() - 1 && row != game.height() - 1) continue;
                double c = Math.clamp(col, 10, game.width() - 11);
                double r = Math.clamp(row, 10, game.height() - 11);
                return new double[]{iso.pictureX(c * Field.TILE, r * Field.TILE), iso.pictureY(c * Field.TILE, r * Field.TILE, 0)};
            }
        }
        return new double[]{iso.width() / 2, iso.height() / 2};
    }

    /** The first map, with the computer building the town; the view drifts across it. */
    final class TitleScene implements Scene {
        private Game demo;
        private Planner planner;
        private IsoProjection iso;
        private Camera camera;
        private double time;

        TitleScene() {
            effects.clear();
            restart();
        }

        private void restart() {
            demo = new Game(levels.getFirst(), rules, 7);
            demo.playOn();
            planner = new Planner(rules.plan());
            iso = TownPainter.projection(demo);
            camera = new Camera(Field.WIDTH, Field.HEIGHT, iso.width(), iso.height());
            time = 0;
            traffic.clear();
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            step(seconds);
        }

        void step(double seconds) {
            time += seconds;
            for (int i = 0; i < TITLE_STEPS; i++) {
                planner.update(demo, seconds);
                demo.update(seconds);
                effects.react(demo, iso, camera, traffic, false);
            }
            effects.update(seconds, demo, iso, camera);
            traffic.update(seconds, demo);
            if (time > TITLE_SECONDS) restart();
            double[] middle = middle(iso, demo);
            camera.jumpTo(middle[0] + Math.sin(time * 0.05) * 120, middle[1] + Math.sin(time * 0.037) * 60);
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            painter.paint(g, demo, iso, camera, traffic, effects, TownPainter.Marks.NONE, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("window.title"), 120, 64, true, Hud.AMBER);
            hud.text(g, msg.get("title.subtitle"), 150, 14, false, Hud.CHALK);
            hud.text(g, msg.get("title.start"), 210, 18, false, Hud.CHALK);
            hud.text(g, msg.get("title.mouse"), 262, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.keys"), 282, 12, false, Hud.CHALK);
            if (slot.exists()) hud.text(g, msg.get("title.load"), 302, 12, false, Hud.CHALK);
            hud.text(g, msg.get("title.screen"), 330, 11, false, Hud.LABEL);
        }

        Game demo() {
            return demo;
        }
    }

    /** Loads the saved town and plays it; false, with a buzz, when there is none. */
    private boolean load() {
        Optional<Game> saved = slot.load(rules);
        if (saved.isEmpty()) {
            effects.no();
            return false;
        }
        scenes.replace(new PlayScene(saved.get()));
        return true;
    }

    // --8<-- [start:choice]
    /** Three cards, one for each mission, each with its map; one opens when the one before it is won. */
    final class ChoiceScene implements Scene {
        private final TitleScene behind = new TitleScene();
        private final Map<Integer, DiamondMinimap> maps = new HashMap<>();

        ChoiceScene() {
            chosen = Math.min(chosen, progress.open());
            for (int i = 0; i < levels.size(); i++) {
                Level level = levels.get(i);
                IsoProjection iso = IsoProjection.forMap(level.width(), level.height(), Field.TILE, Field.TILE_WIDTH,
                        Field.TILE_HEIGHT, Field.ABOVE);
                DiamondMinimap map = new DiamondMinimap(iso, Hud.cardX(i, levels.size()) + 28, Hud.CARD_TOP + 32, Hud.CARD_WIDTH - 56);
                for (int row = 0; row < level.height(); row++) {
                    for (int col = 0; col < level.width(); col++) map.paint(col, row, Hud.mapPixel(level, col, row));
                }
                maps.put(i + 1, map);
            }
        }

        @Override
        public void update(double seconds) {
            behind.step(seconds);
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new TitleScene());
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            int before = chosen;
            int open = Math.min(progress.open(), levels.size());
            if (controls.wasPressed(Action.LEFT)) chosen = Math.max(1, chosen - 1);
            if (controls.wasPressed(Action.RIGHT)) chosen = Math.min(open, chosen + 1);
            int card = cardAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && card > 0 && card <= open) chosen = card;
            if (chosen != before) effects.tick();
            boolean clicked = mouse.wasPressed(MouseEvent.BUTTON1) && card > 0 && card <= open;
            if (controls.wasPressed(Action.OK) || clicked) scenes.replace(new PlayScene(newGame(chosen)));
        }
        // --8<-- [end:choice]

        int cardAt(double x, double y) {
            if (Double.isNaN(x) || y < Hud.CARD_TOP || y > Hud.CARD_TOP + Hud.CARD_HEIGHT) return 0;
            for (int i = 0; i < levels.size(); i++) {
                double left = Hud.cardX(i, levels.size());
                if (x >= left && x < left + Hud.CARD_WIDTH) return i + 1;
            }
            return 0;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            behind.render(g, alpha);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("select.title"), 70, 30, true, Hud.AMBER);
            for (int i = 0; i < levels.size(); i++) {
                int number = i + 1;
                hud.card(g, Hud.cardX(i, levels.size()), Hud.CARD_TOP, rules.mission(number), maps.get(number),
                        number <= progress.open(), number == chosen, progress);
            }
            hud.text(g, msg.get("select.keys"), 330, 12, false, Hud.CHALK);
        }

        int chosen() {
            return chosen;
        }
    }

    // --8<-- [start:play]
    /**
     * A mission. A tool of the panel is chosen with a click or a key; roads, lines, zones and the bulldozer
     * are dragged across the map, buildings are clicked onto it. The right button cancels, or held down
     * drags the map, as do the arrow keys, the edge of the screen and the minimap.
     */
    final class PlayScene implements Scene {
        private final Game game;
        private final IsoProjection iso;
        private Camera camera;
        private int zoom = ZOOMS[ZOOMS.length - 1];
        private final DiamondMinimap minimap;
        private Tool tool;
        private String choice;
        private Hud.Info info = Hud.Info.TOOL;
        private TownPainter.Overlay overlay = TownPainter.Overlay.NONE;
        private Cell hover;
        /** Where the left button went down on the map, and where it is now; null when nothing is dragged. */
        private Cell from;
        private Cell to;
        private Cell place;
        private Game.Result placement;
        private int speed = 1;
        private boolean counts;
        private double panX = Double.NaN;
        private double panY = Double.NaN;
        private double pressX;
        private double pressY;
        private boolean panned;
        private boolean minimapDrag;
        private String message;
        private Color messageColour = Hud.CHALK;
        private double messageShown = Double.MAX_VALUE;
        private double minimapAge = Double.MAX_VALUE;
        private double ended;
        private boolean best;
        private int fires;

        PlayScene(Game town) {
            game = town;
            iso = TownPainter.projection(town);
            camera = view(iso, zoom);
            minimap = new DiamondMinimap(iso, Hud.MINIMAP.x, Hud.MINIMAP.y + (Hud.MINIMAP.height - Hud.MINIMAP.width / 2) / 2,
                    Hud.MINIMAP.width);
            double[] start = town.months() > 0 ? middle(iso, town) : entry(iso, town);
            camera.jumpTo(start[0], start[1]);
            effects.clear();
            traffic.clear();
            effects.music();
            say(msg.format("play.mission", town.mission().number(), hud.name(town.mission().number())), Hud.AMBER);
            paintMinimap();
        }

        @Override
        public void update(double seconds) {
            if (game.phase() != Game.Phase.PLAYING) {
                ended += seconds;
                if (ended >= END_SECONDS) {
                    scenes.push(new ResultScene(this));
                    return;
                }
            }
            if (controls.wasPressed(Action.PAUSE) || controls.wasPressed(Action.BACK) && tool == null && from == null) {
                scenes.push(new PauseScene(this));
                return;
            }
            if (controls.wasPressed(Action.LOAD) && load()) return;
            input(seconds);
            for (int i = 0; i < speed; i++) step(seconds);
            effects.update(seconds, game, iso, camera);
            traffic.update(seconds, game);
            minimapAge += seconds;
            if (minimapAge >= MINIMAP_SECONDS) paintMinimap();
            messageShown += seconds;
        }

        /** One step of the rules, what it did, and what the player hears of it. */
        void step(double seconds) {
            game.update(seconds);
            effects.react(game, iso, camera, traffic, true);
            for (Event event : game.events()) tell(event);
        }
        // --8<-- [end:play]

        private void paintMinimap() {
            minimapAge = 0;
            for (int row = 0; row < game.height(); row++) {
                for (int col = 0; col < game.width(); col++) minimap.paint(col, row, Hud.minimapPixel(game, col, row));
            }
        }

        // --8<-- [start:tell]
        /** What the player hears of: the message at the bottom of the map, and what the end shows. */
        private void tell(Event event) {
            switch (event) {
                case Event.Built(Building b) -> say(msg.format("message.built", msg.get("building." + b.type().id())), Hud.GREEN);
                case Event.Fire fire -> {
                    fires++;
                    say(msg.get("message.fire"), Hud.RED);
                }
                case Event.Doused doused -> say(msg.get("message.doused"), Hud.GREEN);
                case Event.Burned burned -> say(msg.get("message.burned"), Hud.RED);
                case Event.Milestone(int people) -> say(msg.format("message.milestone", Hud.number(people)), Hud.GREEN);
                case Event.Advice(Event.Hint hint) -> say(msg.get("hint." + hint.name().toLowerCase()), Hud.AMBER);
                case Event.Month(int month, int year) -> {
                    if (month == 0) say(msg.format("message.year", year), Hud.CHALK);
                }
                case Event.Over(boolean won) -> {
                    if (won) {
                        best = progress.won(game.mission().number(), game.months(), levels.size());
                        chosen = Math.min(levels.size(), game.mission().number() + 1);
                    }
                }
                default -> {
                }
            }
        }
        // --8<-- [end:tell]

        private void say(String text, Color colour) {
            message = text;
            messageColour = colour;
            messageShown = 0;
        }

        // --8<-- [start:input]
        private void input(double seconds) {
            double mx = mouse.x();
            double my = mouse.y();
            boolean onMap = !Double.isNaN(mx) && mx < Field.VIEW_WIDTH && my >= Field.BAR;
            hover = null;
            if (onMap) {
                double px = camera.worldX(mx / zoom);
                double py = camera.worldY(my / zoom);
                Cell cell = new Cell(iso.col(px, py), iso.row(px, py));
                if (game.contains(cell.col(), cell.row())) hover = cell;
            }
            if (controls.wasPressed(Action.BACK)) back();
            if (controls.wasPressed(Action.SAVE)) {
                slot.save(game);
                say(msg.get("message.saved"), Hud.CHALK);
                effects.tick();
            }
            if (controls.wasPressed(Action.FASTER)) faster(1);
            if (controls.wasPressed(Action.SLOWER)) faster(-1);
            if (controls.wasPressed(Action.COUNTS)) counts = !counts;
            if (controls.wasPressed(Action.OVERLAY)) nextOverlay();
            if (controls.wasPressed(Action.ZOOM)) zoom(zoom == ZOOMS[0] ? 1 : -1, Field.VIEW_WIDTH / 2, Field.BAR + Field.VIEW_HEIGHT / 2);
            if (onMap && mouse.wheel() != 0) zoom(-mouse.wheel(), mx, my);
            if (controls.wasPressed(Action.BUDGET)) info = info == Hud.Info.BUDGET ? Hud.Info.TOOL : Hud.Info.BUDGET;
            for (Tool t : Tool.values()) {
                if (controls.wasPressed(Action.valueOf("TOOL_" + (t.ordinal() + 1)))) choose(t);
            }

            placing();
            if (mouse.wasPressed(MouseEvent.BUTTON1)) {
                if (onMap && hover != null) press(hover);
                else if (!Double.isNaN(mx) && mx >= Field.VIEW_WIDTH) panelClick(mx, my);
            }
            if (from != null && hover != null) to = hover;
            if (mouse.wasReleased(MouseEvent.BUTTON1) && from != null) release();
            if (minimapDrag && mouse.isDown(MouseEvent.BUTTON1) && !Double.isNaN(mx)) jumpMinimap(mx, my);
            if (!mouse.isDown(MouseEvent.BUTTON1)) minimapDrag = false;
            if (mouse.wasPressed(MouseEvent.BUTTON3)) {
                pressX = mx;
                pressY = my;
                panned = false;
            }
            if (mouse.wasReleased(MouseEvent.BUTTON3) && !panned && onMap) back();
            pan(seconds, mx, my);
        }
        // --8<-- [end:input]

        /** Escape or a right click: a drag is dropped, then the tool is put away. */
        private void back() {
            if (from != null) {
                from = null;
                to = null;
            } else if (tool != null) {
                tool = null;
                choice = null;
            }
        }

        private void faster(int by) {
            int i = 0;
            while (SPEEDS[i] != speed) i++;
            int next = SPEEDS[Math.clamp(i + by, 0, SPEEDS.length - 1)];
            if (next != speed) effects.tick();
            speed = next;
        }

        private void nextOverlay() {
            TownPainter.Overlay[] all = TownPainter.Overlay.values();
            overlay = all[(overlay.ordinal() + 1) % all.length];
            effects.tick();
        }

        /** A tool's key or button: the tool; again, the next thing it builds. */
        void choose(Tool t) {
            List<String> choices = t.choices(rules);
            if (t == tool && !choices.isEmpty()) {
                choice = choices.get((choices.indexOf(choice) + 1) % choices.size());
            } else {
                tool = t;
                choice = choices.isEmpty() ? null : choices.getFirst();
            }
            from = null;
            to = null;
            info = Hud.Info.TOOL;
            effects.tick();
        }

        private BuildingType building() {
            return tool == null || choice == null || Tool.LINE.equals(choice) ? null : rules.building(choice);
        }

        /** Where the building being placed would stand, its middle under the mouse. */
        private void placing() {
            BuildingType type = building();
            if (type == null || hover == null) {
                place = null;
                return;
            }
            double px = camera.worldX(mouse.x() / zoom);
            double py = camera.worldY(mouse.y() / zoom);
            int col = (int) Math.floor(iso.worldX(px, py) / Field.TILE - (type.width() - 1) / 2.0);
            int row = (int) Math.floor(iso.worldY(px, py) / Field.TILE - (type.height() - 1) / 2.0);
            place = new Cell(col, row);
            placement = game.placement(type, col, row);
        }

        // --8<-- [start:press]
        /** The left button on the map: a drag begins, or a building is put down. */
        private void press(Cell cell) {
            if (tool == null) return;
            if (tool.drags(choice)) {
                from = cell;
                to = cell;
                return;
            }
            BuildingType type = building();
            if (type == null || place == null) return;
            Game.Result result = game.build(type, place.col(), place.row());
            if (result != Game.Result.OK) refuse(result);
        }

        /** The left button let go: the road, line, zone or bulldozing dragged out is done. */
        private void release() {
            Cell a = from;
            Cell b = to;
            from = null;
            to = null;
            if (a == null || b == null) return;
            Game.Result result;
            if (tool == Tool.ROAD) result = game.road(Game.path(a.col(), a.row(), b.col(), b.row()));
            else if (tool == Tool.POWER) result = game.line(Game.path(a.col(), a.row(), b.col(), b.row()));
            else if (tool == Tool.BULLDOZE) result = game.bulldoze(a.col(), a.row(), b.col(), b.row());
            else result = game.zone(tool.zone(), a.col(), a.row(), b.col(), b.row());
            if (result != Game.Result.OK && result != Game.Result.NOTHING) refuse(result);
        }
        // --8<-- [end:press]

        private void refuse(Game.Result why) {
            effects.no();
            say(msg.get("refuse." + why.name().toLowerCase()), Hud.RED);
        }

        // --8<-- [start:panel]
        /** A click on the panel: the minimap, a tool, a row of the box, the tax, the buttons. */
        private void panelClick(double mx, double my) {
            if (Hud.MINIMAP.contains(mx, my)) {
                jumpMinimap(mx, my);
                minimapDrag = true;
                return;
            }
            Tool clicked = Hud.toolAt(mx, my);
            if (clicked != null) {
                choose(clicked);
                return;
            }
            if (Hud.INFO.contains(mx, my)) {
                if (info == Hud.Info.BUDGET) {
                    if (Hud.TAX_DOWN.contains(mx, my)) tax(-1);
                    if (Hud.TAX_UP.contains(mx, my)) tax(1);
                } else if (tool != null) {
                    List<String> choices = tool.choices(rules);
                    int row = Hud.rowAt(mx, my, choices.size());
                    if (row >= 0) {
                        choice = choices.get(row);
                        effects.tick();
                    }
                }
                return;
            }
            if (Hud.BUDGET.contains(mx, my)) info = info == Hud.Info.BUDGET ? Hud.Info.TOOL : Hud.Info.BUDGET;
            else if (Hud.OVERLAY.contains(mx, my)) nextOverlay();
            else if (Hud.MISSION.contains(mx, my)) info = info == Hud.Info.MISSION ? Hud.Info.TOOL : Hud.Info.MISSION;
            else if (Hud.MENU.contains(mx, my)) {
                scenes.push(new PauseScene(this));
                return;
            } else return;
            effects.tick();
        }
        // --8<-- [end:panel]

        private void tax(int by) {
            int before = game.tax();
            game.setTax(before + by);
            if (game.tax() != before) effects.tick();
            else effects.no();
        }

        private void jumpMinimap(double mx, double my) {
            camera.jumpTo(minimap.pictureX(mx), minimap.pictureY(my));
        }

        /** The map moves by the keys, by the edge of the screen, and by dragging it with the right or middle button. */
        private void pan(double seconds, double mx, double my) {
            boolean dragging = mouse.isDown(MouseEvent.BUTTON2) || mouse.isDown(MouseEvent.BUTTON3);
            double dx = controls.axis(Action.LEFT, Action.RIGHT) * PAN_SPEED * seconds;
            double dy = controls.axis(Action.UP, Action.DOWN) * PAN_SPEED * seconds;
            if (mouse.isInside() && !dragging && !minimapDrag && from == null && !Double.isNaN(mx)) {
                if (mx < EDGE) dx -= PAN_SPEED * seconds;
                if (mx > Field.WIDTH - EDGE) dx += PAN_SPEED * seconds;
                if (my < EDGE) dy -= PAN_SPEED * seconds;
                if (my > Field.HEIGHT - EDGE) dy += PAN_SPEED * seconds;
            }
            if (dragging && !Double.isNaN(mx)) {
                if (Math.hypot(mx - pressX, my - pressY) > CLICK_SLACK) panned = true;
                if (!Double.isNaN(panX)) {
                    dx += panX - mx;
                    dy += panY - my;
                }
            }
            panX = dragging ? mx : Double.NaN;
            panY = dragging ? my : Double.NaN;
            camera.moveBy(dx / zoom, dy / zoom);
        }

        // --8<-- [start:zoom]
        /**
         * The wheel or Z: the map drawn larger or smaller by {@code by} steps. The camera works in picture units,
         * so a zoomed view is a camera with a smaller view, drawn scaled up; the point under the mouse stays put.
         */
        private void zoom(int by, double mx, double my) {
            int i = 0;
            while (ZOOMS[i] != zoom) i++;
            int next = ZOOMS[Math.clamp(i + by, 0, ZOOMS.length - 1)];
            if (next == zoom) return;
            double px = camera.worldX(mx / zoom);
            double py = camera.worldY(my / zoom);
            zoom = next;
            camera = view(iso, zoom);
            camera.jumpTo(px - (mx - Field.VIEW_WIDTH / 2) / zoom, py - (my - Field.BAR - Field.VIEW_HEIGHT / 2) / zoom);
            effects.tick();
        }

        private static Camera view(IsoProjection iso, int zoom) {
            return new Camera(Field.VIEW_WIDTH / zoom, Field.VIEW_HEIGHT / zoom, iso.width(), iso.height())
                    .placedAt(0, Field.BAR / zoom);
        }
        // --8<-- [end:zoom]

        // --8<-- [start:marks]
        /** What the painter shows of what the player is doing. */
        TownPainter.Marks marks() {
            List<Cell> path = List.of();
            boolean pathOk = false;
            int[] area = null;
            int colour = 0;
            if (from != null && to != null) {
                if (tool == Tool.ROAD || tool == Tool.POWER) {
                    path = Game.path(from.col(), from.row(), to.col(), to.row());
                    int cost = tool == Tool.ROAD ? game.roadCost(path) : game.lineCost(path);
                    pathOk = cost >= 0 && cost <= game.money();
                } else {
                    area = new int[]{from.col(), from.row(), to.col(), to.row()};
                    colour = tool == Tool.BULLDOZE ? 0xE85A3A : Pictures.zoneColour(tool.zone());
                }
            }
            return new TownPainter.Marks(hover, path, pathOk, area, colour, building(), place,
                    placement == Game.Result.OK, overlay);
        }

        /** The cost of what is being dragged, and its size in tiles; zeros when nothing is. */
        int[] drag() {
            if (from == null || to == null || tool == null) return new int[3];
            int w = Math.abs(to.col() - from.col()) + 1;
            int h = Math.abs(to.row() - from.row()) + 1;
            int cost;
            if (tool == Tool.ROAD) cost = game.roadCost(Game.path(from.col(), from.row(), to.col(), to.row()));
            else if (tool == Tool.POWER) cost = game.lineCost(Game.path(from.col(), from.row(), to.col(), to.row()));
            else if (tool == Tool.BULLDOZE) cost = game.bulldozeCost(from.col(), from.row(), to.col(), to.row());
            else {
                Zone zone = tool.zone();
                cost = game.zoneCost(game.zonable(zone, from.col(), from.row(), to.col(), to.row()), zone);
            }
            return new int[]{w, h, cost};
        }
        // --8<-- [end:marks]

        @Override
        public void render(Graphics2D g, double alpha) {
            AffineTransform screen = g.getTransform();
            g.scale(zoom, zoom);
            painter.paint(g, game, iso, camera, traffic, effects, marks(), alpha);
            g.setTransform(screen);
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            int[] drag = drag();
            boolean line = tool == Tool.ROAD || tool == Tool.POWER;
            hud.bar(g, game, speed);
            hud.panel(g, game, minimap, camera, new Hud.View(tool, choice, info, hover, line ? 0 : drag[0], drag[1],
                    drag[2], overlay, speed, mouse.x(), mouse.y()), alpha);
            if (from != null && drag[0] > 0 && !Double.isNaN(mouse.x())) {
                String size = line ? msg.format("info.length", Game.path(from.col(), from.row(), to.col(), to.row()).size())
                        : msg.format("info.size", drag[0], drag[1]);
                int colour = tool.zone() != null ? Pictures.zoneColour(tool.zone()) : tool == Tool.BULLDOZE ? 0xE85A3A : 0x8AB8E8;
                hud.dragLabel(g, mouse.x(), mouse.y(), size, Math.max(0, drag[2]), drag[2] >= 0 && drag[2] <= game.money(), colour);
            }
            if (counts) {
                hud.corner(g, msg.format("play.counts", traffic.cars().size(), effects.puffs(), effects.particles()), Field.BAR + 12);
            }
            hud.message(g, message, messageShown, messageColour);
        }

        Game game() {
            return game;
        }

        Camera camera() {
            return camera;
        }

        int zoom() {
            return zoom;
        }

        /** For screenshots: the map at a zoom, centred where it was. */
        void zoomTo(int z) {
            while (zoom < z) zoom(1, Field.VIEW_WIDTH / 2, Field.BAR + Field.VIEW_HEIGHT / 2);
            while (zoom > z) zoom(-1, Field.VIEW_WIDTH / 2, Field.BAR + Field.VIEW_HEIGHT / 2);
        }

        IsoProjection iso() {
            return iso;
        }

        Tool tool() {
            return tool;
        }

        String choice() {
            return choice;
        }

        Hud.Info info() {
            return info;
        }

        TownPainter.Overlay overlay() {
            return overlay;
        }

        int speed() {
            return speed;
        }

        Cell hover() {
            return hover;
        }

        String message() {
            return messageShown < Hud.MESSAGE_SECONDS ? message : null;
        }

        /** For screenshots: a drag held at its two ends. */
        void dragging(Cell a, Cell b) {
            from = a;
            to = b;
        }

        void show(Hud.Info what, TownPainter.Overlay layer) {
            info = what;
            overlay = layer;
        }

        Hud.Tally tally() {
            return new Hud.Tally(game.people(), game.jobs(), game.happiness(), game.buildings().size(), fires, game.months());
        }

        void keepGoing() {
            game.playOn();
            ended = 0;
        }
    }

    // --8<-- [start:pause]
    /** The town stands still: go on, save it, load the saved one, or leave it. */
    final class PauseScene implements Scene {
        enum Choice { CONTINUE, SAVE, LOAD, LEAVE }

        private final PlayScene play;
        private int highlighted;
        private String note;

        PauseScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            if (controls.wasPressed(Action.PAUSE)) {
                scenes.pop();
                return;
            }
            if (controls.wasPressed(Action.BACK)) {
                choose(Choice.LEAVE);
                return;
            }
            if (controls.wasPressed(Action.SAVE)) choose(Choice.SAVE);
            if (controls.wasPressed(Action.LOAD)) {
                choose(Choice.LOAD);
                return;
            }
            int before = highlighted;
            if (controls.wasPressed(Action.UP)) highlighted = Math.max(0, highlighted - 1);
            if (controls.wasPressed(Action.DOWN)) highlighted = Math.min(Choice.values().length - 1, highlighted + 1);
            int row = rowAt(mouse.x(), mouse.y());
            if (mouse.wasMoved() && row >= 0) highlighted = row;
            if (highlighted != before) effects.tick();
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1) && row >= 0) {
                choose(Choice.values()[highlighted]);
            }
        }
        // --8<-- [end:pause]

        private void choose(Choice choice) {
            switch (choice) {
                case CONTINUE -> scenes.pop();
                case SAVE -> {
                    slot.save(play.game);
                    note = msg.get("message.saved");
                    effects.tick();
                }
                case LOAD -> {
                    if (!load()) note = msg.get("pause.nothing");
                }
                case LEAVE -> scenes.replace(new ChoiceScene());
            }
        }

        int rowAt(double x, double y) {
            if (Double.isNaN(x) || Math.abs(x - Field.WIDTH / 2) > 90) return -1;
            int row = (int) Math.floor((y - PAUSE_TOP + PAUSE_ROW * 0.7) / PAUSE_ROW);
            return row >= 0 && row < Choice.values().length ? row : -1;
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.veil(g);
            hud.text(g, msg.get("pause.title"), 120, 36, true, Hud.CHALK);
            String[] keys = {"pause.continue", "pause.save", "pause.load", "pause.leave"};
            for (int i = 0; i < keys.length; i++) {
                hud.text(g, msg.get(keys[i]), PAUSE_TOP + i * PAUSE_ROW, 15, i == highlighted,
                        i == highlighted ? Hud.AMBER : Hud.CHALK);
            }
            if (note != null) hud.text(g, note, PAUSE_TOP + 4 * PAUSE_ROW + 10, 12, false, Hud.GREEN);
        }

        int highlighted() {
            return highlighted;
        }
    }

    /** The end of a mission: the town stays behind the result; Enter goes on or tries again, C keeps building. */
    final class ResultScene implements Scene {
        private final PlayScene play;
        private double shown;

        ResultScene(PlayScene play) {
            this.play = play;
        }

        @Override
        public void update(double seconds) {
            effects.update(seconds, null, null, null);
            shown += seconds;
            if (shown < 0.6) return;     // a short wait, so a click meant for the town does not skip the result
            boolean won = play.game.phase() == Game.Phase.WON;
            if (controls.wasPressed(Action.BACK)) {
                scenes.replace(new ChoiceScene());
                return;
            }
            if (won && controls.wasPressed(Action.KEEP)) {
                play.keepGoing();
                scenes.pop();
                effects.music();
                return;
            }
            if (controls.wasPressed(Action.OK) || mouse.wasPressed(MouseEvent.BUTTON1)) {
                int number = play.game.mission().number();
                if (won && number == levels.size()) scenes.replace(new ChoiceScene());
                else scenes.replace(new PlayScene(newGame(won ? number + 1 : number)));
            }
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }

        @Override
        public void renderOverlay(Graphics2D g, double alpha) {
            hud.result(g, play.game, play.tally(), play.best, play.game.mission().number() == levels.size());
        }
    }
}
