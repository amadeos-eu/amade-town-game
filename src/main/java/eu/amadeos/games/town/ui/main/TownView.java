package eu.amadeos.games.town.ui.main;

import eu.amadeos.gameengine.swing.RenderMode;

/** What {@link TownPresenter} needs from the window; tests replace it. The town itself is drawn by the game. */
public interface TownView {
    /** Shows the settings in the menu. */
    void showOptions(boolean sound, RenderMode mode);

    /** Switches the canvas to a render mode. */
    void useRenderMode(RenderMode mode);

    /** The About dialog. */
    void showAbout();
}
