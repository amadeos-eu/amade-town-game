package eu.amadeos.games.town.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.gameengine.audio.Mixer;
import eu.amadeos.gameengine.input.Keyboard;
import eu.amadeos.gameengine.input.Mouse;
import eu.amadeos.games.town.repository.TownSettings;
import eu.amadeos.games.town.repository.Progress;
import eu.amadeos.games.town.repository.SaveSlot;
import eu.amadeos.games.town.ui.game.Arena;
import eu.amadeos.games.town.ui.game.TownGame;
import eu.amadeos.games.town.ui.main.TownPresenter;
import eu.amadeos.games.town.ui.main.TownView;
import eu.amadeos.games.town.ui.main.TownWindow;

import java.util.Locale;

/**
 * amade-town-game bindings. Theme, language and the XDG directories (where {@link Progress} and {@link SaveSlot} save) come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class TownModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.games.town.messages";

    @Override
    protected void configure() {
        bind(TownSettings.class).in(Scopes.SINGLETON);
        bind(Progress.class).in(Scopes.SINGLETON);
        bind(SaveSlot.class).in(Scopes.SINGLETON);
        bind(Keyboard.class).in(Scopes.SINGLETON);
        bind(Mouse.class).in(Scopes.SINGLETON);
        bind(Mixer.class).in(Scopes.SINGLETON);
        bind(TownGame.class).in(Scopes.SINGLETON);
        bind(Arena.class).to(TownGame.class);
        bind(TownWindow.class).in(Scopes.SINGLETON);
        bind(TownView.class).to(TownWindow.class);
        bind(TownPresenter.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(TownModule.class, MESSAGES, locale);
    }
}
