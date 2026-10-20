package eu.amadeos.games.town.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.games.town.ui.game.TownGame;
import eu.amadeos.games.town.ui.main.TownPresenter;
import eu.amadeos.games.town.ui.main.TownWindow;

public final class AmadeTownApplication {
    public static final AppInfo APP = new AppInfo("amade-town-game", "Town", "town");

    private AmadeTownApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeTownApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            Mixer mixer = injector.getInstance(Mixer.class);
            mixer.start();
            TownWindow window = injector.getInstance(TownWindow.class);
            TownPresenter presenter = injector.getInstance(TownPresenter.class);
            TownGame game = injector.getInstance(TownGame.class);
            window.bind(presenter);
            presenter.start();
            window.whenClosed(() -> {
                game.stop();
                mixer.close();
                System.exit(0);
            });
            window.setLocationByPlatform(true);
            window.setVisible(true);
            game.start(window.canvas());
        }, new TownModule());
    }
    // --8<-- [end:main]
}
