package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;

/**
 * Gere le monde lobby (hub, void, jamais supprime) et les mondes de jeu
 * (game / game_nether / game_the_end) qui sont entierement regeneres a
 * chaque nouvelle partie avec une seed aleatoire.
 */
public class WorldManager {

    private final HardcorePlugin plugin;
    private final String lobbyName;
    private final String gameName;

    public WorldManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.lobbyName = plugin.getConfig().getString("lobby-world", "hub");
        this.gameName = plugin.getConfig().getString("game-world", "game");
    }

    public String getLobbyName() {
        return lobbyName;
    }

    public String getGameName() {
        return gameName;
    }

    /**
     * Recupere (ou cree) le monde lobby et y construit la plateforme.
     * Le hub est le monde principal (level-name=hub) genere en void.
     */
    public World ensureLobby() {
        World hub = Bukkit.getWorld(lobbyName);
        if (hub == null) {
            WorldCreator wc = new WorldCreator(lobbyName);
            wc.generator(new VoidGenerator());
            hub = wc.createWorld();
        }
        if (hub == null) {
            throw new IllegalStateException("Impossible de creer le monde lobby '" + lobbyName + "'");
        }
        configureLobby(hub);
        buildLobbyPlatform(hub);
        return hub;
    }

    private void configureLobby(World hub) {
        hub.setDifficulty(Difficulty.PEACEFUL);
        hub.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        hub.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        hub.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        hub.setGameRule(GameRule.FALL_DAMAGE, false);
        hub.setGameRule(GameRule.DROWNING_DAMAGE, false);
        hub.setGameRule(GameRule.FIRE_DAMAGE, false);
        hub.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        hub.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        hub.setTime(6000);
        hub.setStorm(false);
        hub.setThundering(false);
        int y = plugin.getConfig().getInt("lobby-spawn.y", 101);
        hub.setSpawnLocation(0, y, 0);
    }

    /** Construit une petite plateforme de quartz sous le spawn du lobby. */
    private void buildLobbyPlatform(World hub) {
        int y = plugin.getConfig().getInt("lobby-spawn.y", 101) - 1;
        int r = 5;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                Block b = hub.getBlockAt(x, y, z);
                boolean edge = (x == -r || x == r || z == -r || z == r);
                b.setType(edge ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BLOCK, false);
                // barriere de securite pour ne pas tomber dans le vide
                hub.getBlockAt(x, y + 1, z).setType(
                        edge ? Material.BARRIER : Material.AIR, false);
            }
        }
        hub.getBlockAt(0, y + 1, 0).setType(Material.AIR, false);
    }

    /**
     * Regenere completement les mondes de jeu : decharge, supprime les
     * dossiers, puis recree avec une seed aleatoire. A appeler depuis le
     * thread principal (creation de monde obligatoirement synchrone).
     *
     * @return le monde principal de jeu nouvellement cree.
     */
    public World regenerateGameWorlds() {
        boolean nether = plugin.getConfig().getBoolean("nether-enabled", true);
        boolean end = plugin.getConfig().getBoolean("end-enabled", true);

        unloadAndDelete(gameName);
        unloadAndDelete(gameName + "_nether");
        unloadAndDelete(gameName + "_the_end");

        long seed = new Random().nextLong();

        WorldCreator main = new WorldCreator(gameName);
        main.environment(World.Environment.NORMAL);
        main.seed(seed);
        World gameWorld = main.createWorld();

        if (nether) {
            WorldCreator wcN = new WorldCreator(gameName + "_nether");
            wcN.environment(World.Environment.NETHER);
            wcN.seed(seed);
            wcN.createWorld();
        }
        if (end) {
            WorldCreator wcE = new WorldCreator(gameName + "_the_end");
            wcE.environment(World.Environment.THE_END);
            wcE.seed(seed);
            wcE.createWorld();
        }

        if (gameWorld == null) {
            throw new IllegalStateException("Echec de la creation du monde de jeu");
        }

        Difficulty diff;
        try {
            diff = Difficulty.valueOf(
                    plugin.getConfig().getString("difficulty", "HARD").toUpperCase());
        } catch (IllegalArgumentException ex) {
            diff = Difficulty.HARD;
        }
        for (String n : new String[]{gameName, gameName + "_nether", gameName + "_the_end"}) {
            World w = Bukkit.getWorld(n);
            if (w == null) continue;
            w.setDifficulty(diff);
            w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            w.setGameRule(GameRule.KEEP_INVENTORY, false);
            w.setGameRule(GameRule.SPAWN_RADIUS, 0);
            // Barre de localisation des joueurs (MC 1.21.6+) : essentielle
            // en coop pour se retrouver dans le monde partage.
            w.setGameRule(GameRule.LOCATOR_BAR, true);
        }
        return gameWorld;
    }

    private void unloadAndDelete(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                World hub = Bukkit.getWorld(lobbyName);
                if (hub != null) p.teleport(hub.getSpawnLocation());
            }
            Bukkit.unloadWorld(w, false);
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        deleteFolderWithRetry(folder);
    }

    private void deleteFolderWithRetry(File folder) {
        if (!folder.exists()) return;
        for (int attempt = 0; attempt < 25; attempt++) {
            try {
                Path root = folder.toPath();
                Files.walk(root)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
                if (!folder.exists()) return;
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
        }
        if (folder.exists()) {
            plugin.getLogger().warning("Suppression incomplete du monde : " + folder.getName()
                    + " (fichiers verrouilles ?). La regeneration peut etre partielle.");
        }
    }
}
