package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.iso.IsoProjection;
import eu.amadeos.games.town.domain.Field;
import eu.amadeos.games.town.domain.Zone;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the program icon from the game's own art, which is part of the repository: an office tower and a
 * house on a round patch of meadow, at 48 and 256 pixels. {@code ./gradlew art}, then for Windows:
 *
 * <pre>
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-town-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 */
public final class MakeArt {
    private MakeArt() {
    }

    public static void main(String[] args) throws Exception {
        new File("src/main/resources/icons").mkdirs();
        ImageIO.write(icon(48), "png", new File("src/main/resources/icons/amade-town-game.png"));
        new File("build").mkdirs();
        ImageIO.write(icon(256), "png", new File("build/icon-256.png"));
    }

    /** A glass tower behind a cottage, seen from above as in the game, on a round patch of meadow. */
    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 48.0, size / 48.0);
        g.setPaint(new RadialGradientPaint(18, 16, 32, new float[]{0, 0.6f, 1},
                new Color[]{new Color(0xA8D070), new Color(0x5E9A38), new Color(0x2E5A1E)}));
        g.fill(new Ellipse2D.Double(1, 1, 46, 46));
        // The tower stands one tile behind the cottage, so it is drawn first.
        draw(g, Zone.COMMERCIAL, 3, 1, 30, 3, 0.46);
        draw(g, Zone.RESIDENTIAL, 1, 0, 19, 20, 0.52);
        g.dispose();
        return image;
    }

    private static void draw(Graphics2D g, Zone zone, int level, int variant, double x, double y, double scale) {
        Graphics2D lot = (Graphics2D) g.create();
        lot.translate(x, y);
        lot.scale(scale, scale);
        IsoProjection p = IsoProjection.at(Field.TILE, Field.TILE_WIDTH, Field.TILE_HEIGHT, 0, Lots.tall(zone, level));
        Lots.draw(lot, p, zone, level, variant, true);
        lot.dispose();
    }
}
