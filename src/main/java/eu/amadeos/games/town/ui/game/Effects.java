package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.camera.Camera;
import eu.amadeos.gameengine.fx.Particles;
import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.gameengine.random.SeededRandom;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.town.domain.Building;
import eu.amadeos.games.town.domain.Event;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Zone;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the events of a step look and sound like: smoke from the chimneys and the plant, steam from its
 * cooling tower, dust where a building goes up or a lot grows, the smoke of a fire and the water that puts
 * it out, the cost of a building rising over it, and the sounds. The rules know nothing of this. Everything
 * lives in the picture's units, the plane the camera moves over, and is drawn by the painter above the town.
 *
 * <p>Sounds are heard of what the player can see, and of what concerns the whole town, a fire or a new
 * milestone, wherever it is.
 */
final class Effects {
    /** Dust, sparks, water. */
    static final Color[] PALETTE = {new Color(0xC8B898), new Color(0xFFB040), new Color(0x8CC8F0)};
    static final int DUST = 0;
    static final int SPARK = 1;
    static final int WATER = 2;
    static final double PUFF_SECONDS = 3;
    static final double TEXT_SECONDS = 1.6;
    static final int MOST_PUFFS = 400;
    static final double SOUND_GAP = 0.08;
    /** A lot growing is heard at most this often: a town grows many at once. */
    static final double GROW_GAP = 0.6;
    /** Smoke drifts with the wind, to the right and up, in picture units per second. */
    static final double WIND_X = 7;
    static final double WIND_Y = -9;

    /** 0 smoke, 1 dark smoke of a fire, 2 steam. */
    private record Puff(double x, double y, int size, int kind, double born, double life) {
    }

    private record Text(double x, double y, String text, Color colour, double born) {
    }

    private final SoundBank sounds = new SoundBank();
    private final Music music = new Music();
    private final Mixer mixer;
    private final Pictures pictures;
    private final SeededRandom random = new SeededRandom(1);
    private final Particles particles = new Particles(1200, PALETTE);
    private final ArrayDeque<Puff> puffs = new ArrayDeque<>();
    private final List<Text> texts = new ArrayList<>();
    private final Map<Sound, Double> played = new HashMap<>();
    private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private double time;
    private double smoke;

    Effects(Mixer mixer, Pictures pictures) {
        this.mixer = mixer;
        this.pictures = pictures;
    }

    double time() {
        return time;
    }

    /** A new mission, or the title: nothing left from before, no music. */
    void clear() {
        particles.clear();
        puffs.clear();
        texts.clear();
        mixer.stopMusic();
    }

    void music() {
        mixer.playMusic(music.street, true);
    }

    void tick() {
        play(sounds.tick);
    }

    void no() {
        play(sounds.no);
    }

    // --8<-- [start:update]
    /** Time goes on: chimneys smoke, the cooling tower steams, burning lots smoke and throw sparks. */
    void update(double seconds, Game game, IsoProjection iso, Camera camera) {
        time += seconds;
        particles.update(seconds, 30);
        while (!puffs.isEmpty() && time - puffs.peekFirst().born() > PUFF_SECONDS) puffs.removeFirst();
        texts.removeIf(text -> time - text.born() > TEXT_SECONDS);
        if (game == null) return;
        smoke += seconds;
        boolean puff = smoke >= 0.35;
        if (puff) smoke = 0;
        if (puff) {
            int[] v = camera == null ? new int[]{0, 0, game.width() - 1, game.height() - 1}
                    : iso.visible(camera.x(), camera.y(), camera.viewWidth(), camera.viewHeight(), 6);
            for (int row = v[1]; row <= v[3]; row++) {
                for (int col = v[0]; col <= v[2]; col++) {
                    int level = game.lot(col, row);
                    if (level == 0 || game.zone(col, row) != Zone.INDUSTRIAL || game.burning(col, row)) continue;
                    double[] chimney = pictures.look(Zone.INDUSTRIAL, level, TownPainter.variant(col, row)).chimney();
                    if (chimney == null || !game.powered(col, row)) continue;
                    double x = col * Field.TILE + chimney[0];
                    double y = row * Field.TILE + chimney[1];
                    puff(iso.pictureX(x, y), iso.pictureY(x, y, chimney[2] + 2), level - 1, 0);
                }
            }
            for (Building b : game.buildings()) {
                if (!b.working()) continue;
                for (double[] s : pictures.look(b.type().work()).smoke()) {
                    double x = b.col() * Field.TILE + s[0];
                    double y = b.row() * Field.TILE + s[1];
                    puff(iso.pictureX(x, y), iso.pictureY(x, y, s[2]), s[3] > 0 ? 2 : 1, s[3] > 0 ? 2 : 0);
                }
            }
        }
        for (int row = 0; row < game.height(); row++) {
            for (int col = 0; col < game.width(); col++) {
                if (!game.burning(col, row)) continue;
                double x = (col + 0.5) * Field.TILE;
                double y = (row + 0.5) * Field.TILE;
                double px = iso.pictureX(x, y);
                double py = iso.pictureY(x, y, 10);
                if (puff) puff(px + (random.nextDouble() - 0.5) * 8, py, 2, 1);
                if (random.nextDouble() < seconds * 20) sparkle(px, py, 1, SPARK, 18, -20);
                if (game.dousing(col, row) && random.nextDouble() < seconds * 40) {
                    sparkle(px + (random.nextDouble() - 0.5) * 12, py - 6, 1, WATER, 14, 6);
                }
            }
        }
    }
    // --8<-- [end:update]

    // --8<-- [start:react]
    /** Shows what happened in the last step, and plays it if {@code audible}, as far as {@code camera} sees it. */
    void react(Game game, IsoProjection iso, Camera camera, Traffic traffic, boolean audible) {
        for (Event event : game.events()) {
            switch (event) {
                case Event.Laid(String what, int tiles, int cost) -> {
                    if (!audible) break;
                    if (what.equals("road")) play(sounds.paved);
                    else if (what.equals("line")) play(sounds.wired);
                    else if (what.equals("bulldoze")) play(sounds.bulldozed);
                    else play(sounds.zoned);
                }
                case Event.Built(Building b) -> {
                    double x = b.centerCol() * Field.TILE;
                    double y = b.centerRow() * Field.TILE;
                    dust(iso, b.col(), b.row(), b.width(), b.height(), 18);
                    say(iso.pictureX(x, y), iso.pictureY(x, y, 24), "-$" + b.type().cost(), Hud.RED);
                    if (audible) play(sounds.built);
                }
                case Event.Grew(int col, int row, int level) -> {
                    dust(iso, col, row, 1, 1, 5);
                    if (heard(iso, camera, audible, col, row)) playSeldom(sounds.grew, GROW_GAP);
                }
                case Event.Declined(int col, int row, int level) -> dust(iso, col, row, 1, 1, 4);
                case Event.Fire(int col, int row) -> {
                    if (audible) play(sounds.alarm);
                }
                case Event.Truck(Building station, int col, int row) -> {
                    traffic.sendTruck(station, col, row);
                    if (audible) play(sounds.siren);
                }
                case Event.Doused(int col, int row) -> {
                    double x = (col + 0.5) * Field.TILE;
                    double y = (row + 0.5) * Field.TILE;
                    sparkle(iso.pictureX(x, y), iso.pictureY(x, y, 8), 20, WATER, 30, 0);
                    puff(iso.pictureX(x, y), iso.pictureY(x, y, 12), 3, 2);
                    if (audible) play(sounds.doused);
                }
                case Event.Burned(int col, int row) -> {
                    dust(iso, col, row, 1, 1, 20);
                    double x = (col + 0.5) * Field.TILE;
                    double y = (row + 0.5) * Field.TILE;
                    for (int i = 0; i < 4; i++) puff(iso.pictureX(x, y) + (random.nextDouble() - 0.5) * 16, iso.pictureY(x, y, 6), 3, 1);
                    if (audible) play(sounds.collapse);
                }
                case Event.Milestone(int people) -> {
                    if (audible) play(sounds.milestone);
                }
                case Event.Advice(Event.Hint hint) -> {
                    if (audible) play(sounds.advice);
                }
                case Event.Over(boolean won) -> {
                    if (audible) mixer.playMusic(won ? music.won : music.lost, false);
                }
                case Event.Month month -> {
                }
            }
        }
    }
    // --8<-- [end:react]

    private static boolean heard(IsoProjection iso, Camera camera, boolean audible, int col, int row) {
        if (!audible) return false;
        if (camera == null) return true;
        double x = iso.pictureX((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE);
        double y = iso.pictureY((col + 0.5) * Field.TILE, (row + 0.5) * Field.TILE, 0);
        return x >= camera.x() - 16 && x <= camera.x() + camera.viewWidth() + 16
                && y >= camera.y() - 16 && y <= camera.y() + camera.viewHeight() + 32;
    }

    private void play(Sound sound) {
        playSeldom(sound, SOUND_GAP);
    }

    private void playSeldom(Sound sound, double gap) {
        Double last = played.get(sound);
        if (last != null && time - last < gap) return;
        played.put(sound, time);
        mixer.play(sound);
    }

    /** Smoke rising from a point of the picture, of size 0 to 4: 0 smoke, 1 a fire's, 2 steam. */
    private void puff(double x, double y, int size, int kind) {
        puffs.addLast(new Puff(x + (random.nextDouble() - 0.5) * 2, y + (random.nextDouble() - 0.5) * 2,
                Math.clamp(size + random.nextInt(2), 0, Pictures.PUFFS.length - 1), kind, time,
                PUFF_SECONDS * (0.6 + random.nextDouble() * 0.4)));
        if (puffs.size() > MOST_PUFFS) puffs.removeFirst();
    }

    private void dust(IsoProjection iso, int col, int row, int width, int height, int count) {
        for (int i = 0; i < count; i++) {
            double x = (col + random.nextDouble() * width) * Field.TILE;
            double y = (row + random.nextDouble() * height) * Field.TILE;
            double a = random.nextDouble() * 2 * Math.PI;
            double v = 6 + random.nextDouble() * 14;
            particles.spawn(iso.pictureX(x, y), iso.pictureY(x, y, 0), Math.cos(a) * v, Math.sin(a) * v * 0.5 - 12,
                    0.5 + random.nextDouble() * 0.6, 0.8 + random.nextDouble() * 1.0, DUST);
        }
    }

    private void sparkle(double x, double y, int count, int colour, double speed, double up) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * 2 * Math.PI;
            double v = speed * (0.4 + random.nextDouble() * 0.8);
            particles.spawn(x, y, Math.cos(a) * v, Math.sin(a) * v + up, 0.3 + random.nextDouble() * 0.5,
                    0.7 + random.nextDouble() * 1.1, colour);
        }
    }

    private void say(double x, double y, String text, Color colour) {
        texts.add(new Text(x, y, text, colour, time));
    }

    // --8<-- [start:render]
    /** Above everything that stands: smoke, steam, dust, sparks and water, and the little words. */
    void render(Graphics2D g, double alpha) {
        Composite before = g.getComposite();
        for (Puff puff : puffs) {
            double t = (time - puff.born()) / puff.life();
            if (t >= 1) continue;
            int size = Math.min(Pictures.PUFFS.length - 1, puff.size() + (int) (t * 2.5));
            Sprite sprite = switch (puff.kind()) {
                case 1 -> pictures.darkPuffs[size];
                case 2 -> pictures.steam[size];
                default -> pictures.puffs[size];
            };
            double a = (puff.kind() == 1 ? 0.85 : 0.65) * (1 - t) * Math.min(1, t * 6);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            double age = time - puff.born();
            sprite.draw(g, puff.x() + age * WIND_X - sprite.width() / 2, puff.y() + age * WIND_Y - sprite.height() / 2);
        }
        g.setComposite(before);
        particles.render(g, alpha);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        for (Text text : texts) {
            double t = (time - text.born()) / TEXT_SECONDS;
            int a = (int) Math.round(255 * Math.min(1, (1 - t) * 3));
            float x = (float) (text.x() - metrics.stringWidth(text.text()) / 2.0);
            float y = (float) (text.y() - 14 * t);
            g.setColor(new Color(0, 0, 0, a / 2));
            g.drawString(text.text(), x + 0.6f, y + 0.6f);
            Color c = text.colour();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
            g.drawString(text.text(), x, y);
        }
    }
    // --8<-- [end:render]

    int particles() {
        return particles.count();
    }

    int puffs() {
        return puffs.size();
    }
}
