package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The music of Town, written in MML: an easy going tune in F major over a bass that walks, looped while the
 * town grows, and short tunes for a mission won and lost.
 */
final class Music {
    /** Main street: four bars of tune over a walking bass, played twice with a different ending. */
    final Sound street = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.035, "t116 o4 l8 f a >c< a g4 f g | a4 >c4< a g f4 | d f a f e4 d e | f2 r4 c4"
                    + " | f a >c< a g4 f g | a4 >c4 d4 c< a4 | b- a g f e4 g4 | f2 r2"),
            Tune.voice(Wave.TRIANGLE, 0.16, "t116 o2 l4 f a >c< a | f a >c< a | b- >d< b- g | c e g e"
                    + " | f a >c< a | f a b- b | g b- c c | f c f2"));

    /** A mission won. */
    final Sound won = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.06, "t132 o4 l8 f a >c f4 c f a4 f2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t132 o2 l8 f >c f4 < f4 >c4 f2"));

    /** A mission lost. */
    final Sound lost = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.05, "t72 o4 l4 c < b- a g f2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t72 o2 l2 f c f1"));
}
