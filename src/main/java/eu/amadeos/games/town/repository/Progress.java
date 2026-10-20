package eu.amadeos.games.town.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.gameengine.save.SaveFile;

import java.util.HashMap;
import java.util.Map;

/**
 * How far the player has come, in {@code ~/.local/share/amadeos/games/town/progress.save}: the missions
 * that are open, and the fastest win of each, in game months.
 *
 * <pre>
 * amade-save 1
 * mission.1.best=812
 * open=2
 * crc32 5e1f0c2a
 * </pre>
 */
public final class Progress {
    static final int VERSION = 1;

    private final SaveFile file;
    private final Map<String, String> values = new HashMap<>();

    @Inject
    public Progress(XdgDirectories xdg) {
        this(SaveFile.of(xdg, "town", "progress"));
    }

    public Progress(SaveFile file) {
        this.file = file;
        file.load().ifPresent(saved -> values.putAll(saved.values()));
    }

    /** The highest mission that may be played; at least the first. */
    public int open() {
        return Math.max(1, number("open", 1));
    }

    /** The fastest win of a mission in game months; 0 when it was never won. */
    public int best(int mission) {
        return Math.max(0, number("mission." + mission + ".best", 0));
    }

    /** Enters a won mission, opens the next of {@code missions}, and saves at once; whether the time is a new best. */
    public boolean won(int mission, int months, int missions) {
        boolean faster = best(mission) == 0 || months < best(mission);
        if (faster) values.put("mission." + mission + ".best", Integer.toString(months));
        if (mission < missions && open() <= mission) values.put("open", Integer.toString(mission + 1));
        file.store(VERSION, values);
        return faster;
    }

    private int number(String key, int fallback) {
        try {
            return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
