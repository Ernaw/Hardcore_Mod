package fr.hardcore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Persistance des statistiques globales dans stats.yml.
 */
public class StatsStore {

    private final File file;
    private YamlConfiguration cfg;

    private int gamesPlayed;
    private long lastGameDurationSec;
    private long bestGameDurationSec;

    public StatsStore(HardcorePlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            cfg = new YamlConfiguration();
            save();
        } else {
            cfg = YamlConfiguration.loadConfiguration(file);
        }
        gamesPlayed = cfg.getInt("games-played", 0);
        lastGameDurationSec = cfg.getLong("last-game-duration-sec", 0);
        bestGameDurationSec = cfg.getLong("best-game-duration-sec", 0);
    }

    public void save() {
        cfg.set("games-played", gamesPlayed);
        cfg.set("last-game-duration-sec", lastGameDurationSec);
        cfg.set("best-game-duration-sec", bestGameDurationSec);
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Appele au demarrage d'une nouvelle partie. */
    public void incrementGames() {
        gamesPlayed++;
        save();
    }

    /** Appele a la fin d'une partie pour enregistrer sa duree. */
    public void recordGameDuration(long seconds) {
        lastGameDurationSec = seconds;
        if (seconds > bestGameDurationSec) {
            bestGameDurationSec = seconds;
        }
        save();
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public long getLastGameDurationSec() {
        return lastGameDurationSec;
    }

    public long getBestGameDurationSec() {
        return bestGameDurationSec;
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }
}
