package eu.amadeos.games.town.ui.game;

/**
 * What the presenter may ask of the running game. The game runs on its own thread; every method here is
 * safe to call from the Swing thread and takes effect at the start of the game's next step.
 */
public interface Arena {
    /** Back to the title screen. */
    void showTitle();

    /** To the choice of a mission. */
    void chooseLevel();

    /** Pauses a running mission; does nothing elsewhere. */
    void pause();

    void setSound(boolean on);
}
