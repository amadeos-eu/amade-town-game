package eu.amadeos.games.town.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.gameengine.save.SaveFile;
import eu.amadeos.games.town.domain.Archive;
import eu.amadeos.games.town.domain.Game;
import eu.amadeos.games.town.domain.Level;
import eu.amadeos.games.town.domain.Rules;

import java.util.Optional;

/**
 * The one saved town, in {@code ~/.local/share/amadeos/games/town/town.save}: the whole game as the
 * {@link Archive} writes it. F5 saves, F9 loads; a file that cannot be read loads nothing.
 */
public final class SaveSlot {
    private final SaveFile file;

    @Inject
    public SaveSlot(XdgDirectories xdg) {
        this(SaveFile.of(xdg, "town", "town"));
    }

    public SaveSlot(SaveFile file) {
        this.file = file;
    }

    public void save(Game game) {
        file.store(Archive.VERSION, Archive.write(game));
    }

    public boolean exists() {
        return file.load().isPresent();
    }

    /** The saved town, or nothing when there is none, it is of another version or it does not fit the rules; a broken file throws a {@link eu.amadeos.gameengine.save.Snapshot.SnapshotException}, an {@link IllegalArgumentException}. */
    public Optional<Game> load(Rules rules) {
        Optional<SaveFile.Saved> saved = file.load();
        if (saved.isEmpty() || saved.get().version() != Archive.VERSION) return Optional.empty();
        try {
            return Optional.of(Archive.read(saved.get().values(), rules, number -> Level.load(number, rules)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }
}
