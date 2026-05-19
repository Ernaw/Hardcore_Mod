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
    private final String gameBase;

    /** Nom du monde de jeu actuellement actif (alterne gamea / gameb). */
    private String activeGameName;
    private long lastSeed;

    public WorldManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.lobbyName = plugin.getConfig().getString("lobby-world", "hub");
        this.gameBase = plugin.getConfig().getString("game-world", "game");
    }

    public String getLobbyName() {
        return lobbyName;
    }

    /**
     * Nom du monde de jeu actif. Avant la 1re partie, renvoie la base.
     * Les verifications par prefixe (startsWith) restent valides car
     * "gamea"/"gamea_nether" commencent par le nom actif "gamea".
     */
    public String getGameName() {
        return activeGameName != null ? activeGameName : gameBase;
    }

    public long getLastSeed() {
        return lastSeed;
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
        // Garde la zone du lobby toujours chargee (ile + hologramme
        // toujours presents, meme sans joueur connecte).
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                hub.setChunkForceLoaded(cx, cz, true);
            }
        }
        buildLobbyIsland(hub);
        return hub;
    }

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
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

    /**
     * Construit une ile flottante decoree pour le lobby : herbe + relief,
     * dessous en pierre/amethyste qui s'effile, arbres, fleurs, lanternes,
     * place centrale, petit bassin et garde-corps invisible.
     */
    private void buildLobbyIsland(World hub) {
        int spawnY = plugin.getConfig().getInt("lobby-spawn.y", 101);
        int top = spawnY - 1;          // surface d'herbe (le joueur spawn dessus)
        int R = 13;                    // rayon de l'ile

        // ----- Corps de l'ile : surface + sous-sol qui s'effile -----
        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(z, x);
                double edge = R + 1.8 * Math.sin(ang * 3) + 1.2 * Math.cos(ang * 5);
                if (d > edge) continue;

                set(hub, x, top, z, Material.GRASS_BLOCK);
                set(hub, x, top - 1, z, Material.DIRT);
                set(hub, x, top - 2, z, Material.DIRT);

                // profondeur : l'ile s'effile vers une pointe
                int depth = (int) Math.round((edge - d) * 1.15) + 3;
                for (int i = 3; i <= depth; i++) {
                    Material mat = (i >= depth - 1) ? Material.COBBLESTONE
                            : (i % 4 == 0 ? Material.ANDESITE : Material.STONE);
                    set(hub, x, top - i, z, mat);
                }
                // pointe centrale en amethyste + dripstone qui pend
                if (d < 3) {
                    set(hub, x, top - depth - 1, z, Material.AMETHYST_BLOCK);
                    set(hub, x, top - depth - 2, z, Material.POINTED_DRIPSTONE);
                }
                // mousse + lichen lumineux par endroits sur le dessous
                if (((x * 7 + z * 13) & 7) == 0) {
                    set(hub, x, top - 3, z, Material.MOSS_BLOCK);
                }
                // garde-corps invisible sur le pourtour
                if (d > edge - 1.0) {
                    for (int hY = 1; hY <= 3; hY++) {
                        set(hub, x, top + hY, z, Material.BARRIER);
                    }
                }
            }
        }

        // ----- Place centrale (5x5) + gazebo -----
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = (Math.abs(x) == 2 || Math.abs(z) == 2);
                set(hub, x, top, z, ring ? Material.CHISELED_STONE_BRICKS
                        : Material.POLISHED_BLACKSTONE);
                set(hub, x, top + 1, z, Material.AIR);
            }
        }
        set(hub, 2, top, 2, Material.SEA_LANTERN);
        set(hub, -2, top, 2, Material.SEA_LANTERN);
        set(hub, 2, top, -2, Material.SEA_LANTERN);
        set(hub, -2, top, -2, Material.SEA_LANTERN);
        // 4 colonnes + lanternes (gazebo)
        int[][] pil = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] c : pil) {
            for (int hY = 1; hY <= 4; hY++) set(hub, c[0], top + hY, c[1], Material.OAK_FENCE);
            set(hub, c[0], top + 5, c[1], Material.SEA_LANTERN);
            set(hub, c[0], top + 1, c[1], Material.LANTERN);
        }

        // ----- Chemins en briques vers 4 belvederes -----
        for (int i = 3; i <= R - 2; i++) {
            set(hub, i, top, 0, Material.STONE_BRICKS);
            set(hub, -i, top, 0, Material.STONE_BRICKS);
            set(hub, 0, top, i, Material.STONE_BRICKS);
            set(hub, 0, top, -i, Material.STONE_BRICKS);
        }

        // ----- Arbres (alternance chene / cerisier) -----
        int[][] trees = {{8, 3}, {-7, 6}, {5, -8}, {-8, -5}, {9, -2}};
        for (int t = 0; t < trees.length; t++) {
            buildTree(hub, trees[t][0], top + 1, trees[t][1], t % 2 == 0);
        }

        // ----- Fleurs, herbes hautes, lanternes posees -----
        Material[] flowers = {Material.POPPY, Material.DANDELION,
                Material.CORNFLOWER, Material.OXEYE_DAISY, Material.AZURE_BLUET};
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (hub.getBlockAt(x, top, z).getType() != Material.GRASS_BLOCK) continue;
                int h = Math.abs(x * 31 + z * 17);
                if (h % 11 == 0) {
                    set(hub, x, top + 1, z, flowers[h % flowers.length]);
                } else if (h % 4 == 0) {
                    set(hub, x, top + 1, z, Material.SHORT_GRASS);
                }
                // eclairage discret sous la surface
                if (h % 23 == 0) set(hub, x, top - 1, z, Material.SEA_LANTERN);
            }
        }
        set(hub, 6, top + 1, 0, Material.LANTERN);
        set(hub, -6, top + 1, 0, Material.LANTERN);
        set(hub, 0, top + 1, 6, Material.LANTERN);
        set(hub, 0, top + 1, -6, Material.LANTERN);

        // ----- Petit bassin d'eau contenu (sans debordement) -----
        for (int x = 7; x <= 9; x++) {
            for (int z = 6; z <= 8; z++) {
                set(hub, x, top, z, Material.PRISMARINE);
            }
        }
        set(hub, 8, top, 7, Material.WATER);
        set(hub, 8, top + 1, 7, Material.AIR);

        // ----- Petite cascade decorative en verre teinte (cote) -----
        for (int yy = 0; yy < 6; yy++) {
            set(hub, -R, top - yy, 0, yy < 3
                    ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLUE_STAINED_GLASS);
        }

        // ----- Lianes qui pendent du pourtour -----
        for (int a = 0; a < 360; a += 25) {
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * (R - 1));
            int z = (int) Math.round(Math.sin(Math.toRadians(a)) * (R - 1));
            if (hub.getBlockAt(x, top, z).getType() == Material.GRASS_BLOCK) {
                for (int yy = 1; yy <= 3 + (a % 3); yy++) {
                    set(hub, x, top - 2 - yy, z, Material.VINE);
                }
            }
        }

        // colonne d'air au spawn
        for (int yy = 1; yy <= 3; yy++) set(hub, 0, top + yy, 0, Material.AIR);
        hub.setSpawnLocation(0, spawnY, 0);
    }

    /** Petit arbre (chene si oak=true, sinon cerisier). */
    private void buildTree(World w, int x, int y, int z, boolean oak) {
        Material log = oak ? Material.OAK_LOG : Material.CHERRY_LOG;
        Material leaf = oak ? Material.OAK_LEAVES : Material.CHERRY_LEAVES;
        int h = 4 + ((x + z) & 1);
        for (int i = 0; i < h; i++) set(w, x, y + i, z, log);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 2.4) continue;
                    int bx = x + dx, by = y + h - 1 + dy, bz = z + dz;
                    if (w.getBlockAt(bx, by, bz).getType() == Material.AIR) {
                        set(w, bx, by, bz, leaf);
                    }
                }
            }
        }
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

        // Nom de monde UNIQUE et jamais reutilise : "<base>_<id>" avec un
        // compteur monotone persiste. Comme le dossier n'existe jamais au
        // prealable, createWorld() GENERE TOUJOURS un monde neuf avec la
        // nouvelle seed (un dossier existant ferait recharger l'ancien
        // monde et ignorerait la seed -> c'etait le bug des "2 cartes").
        String oldName = activeGameName;
        int id = plugin.getStats().nextWorldId();
        String newName = gameBase + "_" + id;

        long seed = new Random().nextLong();
        this.lastSeed = seed;

        WorldCreator main = new WorldCreator(newName);
        main.environment(World.Environment.NORMAL);
        main.seed(seed);
        World gameWorld = main.createWorld();

        if (nether) {
            WorldCreator wcN = new WorldCreator(newName + "_nether");
            wcN.environment(World.Environment.NETHER);
            wcN.seed(seed);
            wcN.createWorld();
        }
        if (end) {
            WorldCreator wcE = new WorldCreator(newName + "_the_end");
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
        for (String n : new String[]{newName, newName + "_nether", newName + "_the_end"}) {
            World w = Bukkit.getWorld(n);
            if (w == null) continue;
            w.setDifficulty(diff);
            w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            w.setGameRule(GameRule.KEEP_INVENTORY, false);
            w.setGameRule(GameRule.SPAWN_RADIUS, 0);
            // Barre de localisation des joueurs (MC 1.21.6+) : essentielle
            // en coop pour se retrouver dans le monde partage.
            w.setGameRule(GameRule.LOCATOR_BAR, true);
            // ===== CLE DE LA SOLUTION =====
            // Le monde de jeu n'est JAMAIS sauvegarde sur disque : il vit
            // entierement en RAM. Aucun fichier de region .mca n'est ecrit
            // -> aucun verrou Windows, aucune accumulation, suppression du
            // stub triviale, regeneration instantanee SANS redemarrage.
            // Parfait pour du hardcore : le monde n'a aucune raison de
            // persister puisqu'il est detruit a la mort.
            w.setAutoSave(false);
            try { w.setKeepSpawnInMemory(false); } catch (Throwable ignored) {}
        }

        // Remise a zero explicite du temps et de la meteo : die de nuit ->
        // la nouvelle partie recommence bien au matin, clair.
        gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        gameWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        gameWorld.setFullTime(0L);
        gameWorld.setStorm(false);
        gameWorld.setThundering(false);
        gameWorld.setWeatherDuration(0);

        // Le nouveau monde devient l'actif AVANT de supprimer les anciens.
        this.activeGameName = newName;

        // Suppression de TOUS les anciens mondes de jeu (l'ancien + tout
        // residu d'un crash/echec de suppression precedent). La randomness
        // ne depend plus de cette suppression (nom unique), donc meme si
        // un dossier resiste, la prochaine partie sera quand meme aleatoire.
        sweepStaleGameWorlds(newName);

        org.bukkit.Location sp = gameWorld.getSpawnLocation();
        plugin.getLogger().info("Nouveau monde '" + newName + "' | seed=" + seed
                + " | spawn=" + sp.getBlockX() + "," + sp.getBlockZ()
                + " | biome=" + gameWorld.getBiome(sp.getBlockX(), sp.getBlockY(), sp.getBlockZ()));
        return gameWorld;
    }

    /**
     * Decharge (avec verification) puis supprime le dossier d'un monde.
     */
    private void unloadAndDelete(String name) {
        World w = Bukkit.getWorld(name);
        // Chemin EXACT du dossier tel que Paper le connait (capture avant
        // le dechargement). Plus fiable que de reconstruire le chemin.
        File folder = (w != null)
                ? w.getWorldFolder()
                : new File(Bukkit.getWorldContainer(), name);
        if (w != null) {
            World hub = Bukkit.getWorld(lobbyName);
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                if (hub != null) p.teleport(hub.getSpawnLocation());
            }
            boolean unloaded = false;
            for (int i = 0; i < 5 && !unloaded; i++) {
                unloaded = Bukkit.unloadWorld(w, false);
                if (!unloaded) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
            }
            plugin.getLogger().info("Monde '" + name + "' : unload="
                    + unloaded + " | dossier=" + folder.getAbsolutePath()
                    + " | existe=" + folder.exists());
            if (!unloaded) {
                plugin.getLogger().warning("Impossible de decharger '" + name
                        + "' (encore reference) -> suppression au prochain demarrage.");
            }
        }
        scheduleDelete(folder);
    }

    /**
     * Supprime tous les mondes de jeu "<base>_..." sauf ceux du monde
     * actif passe en parametre (null = tout supprimer, au demarrage).
     */
    public void sweepStaleGameWorlds(String keepName) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        if (keepName != null) {
            keep.add(keepName);
            keep.add(keepName + "_nether");
            keep.add(keepName + "_the_end");
        }
        String prefix = gameBase + "_";

        // 1) mondes encore charges
        for (World w : new java.util.ArrayList<>(Bukkit.getWorlds())) {
            String n = w.getName();
            if (n.startsWith(prefix) && !keep.contains(n)) {
                unloadAndDelete(n);
            }
        }
        // 2) dossiers restants sur le disque (residus de crash / echec)
        File container = Bukkit.getWorldContainer();
        File[] dirs = container.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File d : dirs) {
            String n = d.getName();
            if (n.startsWith(prefix) && !keep.contains(n)) {
                scheduleDelete(d);
            }
        }
    }

    /** Nombre de dossiers de mondes de jeu encore presents sur le disque. */
    public int countGameWorldFolders() {
        File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (dirs == null) return 0;
        int c = 0;
        for (File d : dirs) if (d.getName().startsWith(gameBase + "_")) c++;
        return c;
    }

    /** Nettoyage complet des mondes de jeu (appele au demarrage). */
    public void cleanupAllGameWorlds() {
        int before = countGameWorldFolders();
        sweepStaleGameWorlds(null);
        plugin.getLogger().info("Nettoyage demarrage : " + before
                + " ancien(s) monde(s) de jeu detecte(s) (suppression en cours).");
        this.activeGameName = null;
    }

    /** Tente une passe de suppression recursive. Renvoie true si tout est parti. */
    private boolean tryDeleteOnce(File folder) {
        if (!folder.exists()) return true;
        try {
            Files.walk(folder.toPath())
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {
        }
        return !folder.exists();
    }

    /**
     * Suppression robuste et NON bloquante : sous Windows, Paper garde les
     * fichiers de region mappes en memoire ; tant que le GC ne les a pas
     * liberes, Windows refuse de les supprimer. On retente donc en tache
     * asynchrone, en forcant System.gc() entre les essais, sur une longue
     * fenetre. Le serveur n'est jamais gele (aucun sleep sur le main thread).
     */
    private void scheduleDelete(File folder) {
        if (folder == null || !folder.exists()) return;
        if (tryDeleteOnce(folder)) return; // souvent OK immediatement (au demarrage)

        final String fname = folder.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int attempt = 0; attempt < 120; attempt++) {
                System.gc();
                if (tryDeleteOnce(folder)) {
                    if (attempt > 0) {
                        plugin.getLogger().info("Ancien monde supprime : " + fname
                                + " (apres " + (attempt + 1) + " tentatives)");
                    }
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
            plugin.getLogger().warning("Suppression encore impossible : " + fname
                    + " — sera nettoye au prochain demarrage du serveur.");
        });
    }
}
