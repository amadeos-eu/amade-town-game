package eu.amadeos.games.town.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;

/** The sound effects of Town, made once at start from tones: no recordings. */
final class SoundBank {
    /** A road is laid: gravel and a roller. */
    final Sound paved = Sound.tone(Wave.NOISE, 0, 0, 0.1, 0.07).then(Sound.tone(Wave.NOISE, 0, 0, 0.14, 0.04));
    /** A power line: a short electric hum. */
    final Sound wired = Sound.mix(Sound.tone(Wave.SQUARE, 100, 100, 0.18, 0.04), Sound.tone(Wave.SQUARE, 200, 200, 0.18, 0.02));
    /** Land is zoned: a stamp. */
    final Sound zoned = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.05, 0.1), Sound.tone(Wave.TRIANGLE, 180, 120, 0.08, 0.3));
    /** A building is put up: three quick hammer blows and a bright note. */
    final Sound built = knock(0.09).then(knock(0.08)).then(knock(0.08))
            .then(Sound.tone(Wave.SQUARE, 659, 659, 0.06, 0.04)).then(Sound.tone(Wave.SQUARE, 988, 988, 0.12, 0.04));
    /** The bulldozer: a growl and a crunch. */
    final Sound bulldozed = Sound.mix(Sound.tone(Wave.SQUARE, 70, 55, 0.3, 0.05), Sound.tone(Wave.NOISE, 0, 0, 0.3, 0.1));
    /** A lot grows: a soft rising pair. */
    final Sound grew = Sound.tone(Wave.TRIANGLE, 660, 660, 0.05, 0.08).then(Sound.tone(Wave.TRIANGLE, 880, 880, 0.08, 0.08));
    /** A round number of people: a short fanfare. */
    final Sound milestone = Sound.tone(Wave.SQUARE, 523, 523, 0.1, 0.05).then(Sound.tone(Wave.SQUARE, 659, 659, 0.1, 0.05))
            .then(Sound.tone(Wave.SQUARE, 784, 784, 0.1, 0.05)).then(Sound.tone(Wave.SQUARE, 1047, 1047, 0.3, 0.05));
    /** The advisor has something to say. */
    final Sound advice = Sound.tone(Wave.TRIANGLE, 988, 988, 0.08, 0.2).then(Sound.tone(Wave.TRIANGLE, 740, 740, 0.14, 0.2));
    /** Fire: the alarm, rising and falling. */
    final Sound alarm = Sound.tone(Wave.SQUARE, 600, 900, 0.3, 0.04).then(Sound.tone(Wave.SQUARE, 900, 600, 0.3, 0.04))
            .then(Sound.tone(Wave.SQUARE, 600, 900, 0.3, 0.04));
    /** The fire truck leaves: two tones of the siren. */
    final Sound siren = Sound.tone(Wave.TRIANGLE, 740, 740, 0.25, 0.12).then(Sound.tone(Wave.TRIANGLE, 988, 988, 0.25, 0.12))
            .then(Sound.tone(Wave.TRIANGLE, 740, 740, 0.25, 0.12));
    /** Water on the fire. */
    final Sound doused = Sound.tone(Wave.NOISE, 0, 0, 0.6, 0.07);
    /** A building burns down: a long rumble. */
    final Sound collapse = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.9, 0.14), Sound.tone(Wave.TRIANGLE, 70, 26, 0.9, 0.35));
    /** Not possible: a low buzz. */
    final Sound no = Sound.tone(Wave.SQUARE, 150, 140, 0.15, 0.06);
    /** A button, or a menu choice moves. */
    final Sound tick = Sound.tone(Wave.SQUARE, 880, 880, 0.03, 0.04);

    private static Sound knock(double gap) {
        return Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.12), Sound.tone(Wave.TRIANGLE, 260, 160, 0.05, 0.3))
                .then(Sound.tone(Wave.NOISE, 0, 0, gap, 0));
    }
}
