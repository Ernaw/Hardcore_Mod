package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Machine a etats du jeu : enchaine lobby -> regeneration -> partie.
 */
public class GameManager {

    private final HardcorePlugin plugin;
    private GameState state = GameState.LOBBY;
    private long gameStartMillis = 0;
    private BukkitTask tickTask;
    private BukkitTask countdownTask;

    public GameManager(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    public GameState getState() {
        return state;
    }

    /** Demarrage du plugin : lobby pret, boucles lancees. */
    public void init() {
        plugin.getWorldManager().ensureLobby();
        state = GameState.LOBBY;

        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getLobbyManager().sendToLobby(p);
        }

        // Boucle principale : vie partagee (quand RUNNING) + veille du lobby.
        tickTask = new BukkitRunnable() {
            int sec = 0;
            @Override
            public void run() {
                if (state == GameState.RUNNING) {
                    plugin.getSharedLife().tick();
                } else if (state == GameState.LOBBY) {
                    if (sec % 20 == 0) lobbyWatch();
                }
                sec++;
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (countdownTask != null) countdownTask.cancel();
    }

    /** Veille du lobby : affichage + lancement auto quand assez de joueurs. */
    private void lobbyWatch() {
        int online = Bukkit.getOnlinePlayers().size();
        int min = plugin.getConfig().getInt("min-players-to-start", 1);
        boolean auto = plugin.getConfig().getBoolean("auto-start", true);

        if (online >= min && online > 0) {
            if (auto) {
                startNewCycle();
                return;
            }
            plugin.getLobbyManager().setBossBar(
                    "Pret ! En attente du lancement (/hc start)", 1.0, BarColor.GREEN);
        } else {
            plugin.getLobbyManager().setBossBar(
                    "En attente de joueurs (" + online + "/" + min + ")",
                    0.0, BarColor.BLUE);
        }
    }

    /**
     * Lance un nouveau cycle : regeneration du monde puis compte a rebours
     * dans le lobby, puis demarrage effectif de la partie.
     */
    public void startNewCycle() {
        if (state == GameState.REGENERATING) return;
        state = GameState.REGENERATING;
        if (countdownTask != null) countdownTask.cancel();

        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getLobbyManager().sendToLobby(p);
        }
        plugin.getLobbyManager().setBossBar(
                "Generation d'un nouveau monde...", 1.0, BarColor.PURPLE);

        // Regeneration synchrone (creation de monde = thread principal),
        // legerement differee pour laisser le temps de tp tout le monde.
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    plugin.getWorldManager().regenerateGameWorlds();
                } catch (Exception e) {
                    e.printStackTrace();
                    Bukkit.broadcastMessage("§cErreur lors de la regeneration du monde !");
                }
                startCountdown();
            }
        }.runTaskLater(plugin, 20L);
    }

    /** Compte a rebours visible avant la prochaine partie. */
    private void startCountdown() {
        final int total = plugin.getConfig().getInt("regen-countdown-seconds", 12);
        plugin.getLobbyManager().refreshAllScoreboards();
        countdownTask = new BukkitRunnable() {
            int remaining = total;
            @Override
            public void run() {
                if (remaining <= 0) {
                    cancel();
                    beginGame();
                    return;
                }
                plugin.getLobbyManager().setBossBar(
                        "§aNouvelle partie dans " + remaining + "s",
                        (double) remaining / total, BarColor.GREEN);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Demarre reellement la partie : les mondes sont deja regeneres. */
    private void beginGame() {
        plugin.getStats().incrementGames();
        plugin.getSharedLife().reset();

        World gw = Bukkit.getWorld(plugin.getWorldManager().getGameName());
        if (gw == null) {
            Bukkit.broadcastMessage("§cMonde de jeu introuvable, retour au lobby.");
            state = GameState.LOBBY;
            return;
        }
        Location spawn = safeSpawn(gw);

        for (Player p : Bukkit.getOnlinePlayers()) {
            preparePlayerForGame(p, spawn);
        }

        state = GameState.RUNNING;
        gameStartMillis = System.currentTimeMillis();
        Bukkit.broadcastMessage("§6§l>> Nouvelle partie ! Vie et faim partagees. Bonne chance ! <<");
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendTitle("§c§lHARDCORE", "§7La survie est commune...", 10, 50, 20));
    }

    private void preparePlayerForGame(Player p, Location spawn) {
        plugin.getLobbyManager().removeFromLobby(p);
        plugin.getLobbyManager().clearScoreboard(p);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.getInventory().clear();
        p.setExp(0f);
        p.setLevel(0);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(spawn);
        p.setBedSpawnLocation(spawn, true);
        plugin.getSharedLife().trackPlayer(p);
    }

    /** Recoit un joueur qui se connecte en pleine partie. */
    public void joinRunningGame(Player p) {
        World gw = Bukkit.getWorld(plugin.getWorldManager().getGameName());
        if (gw == null) {
            plugin.getLobbyManager().sendToLobby(p);
            return;
        }
        Location target = safeSpawn(gw);
        for (Player other : gw.getPlayers()) {
            if (!other.equals(p)) { target = other.getLocation(); break; }
        }
        plugin.getLobbyManager().removeFromLobby(p);
        plugin.getLobbyManager().clearScoreboard(p);
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(target);
        plugin.getSharedLife().trackPlayer(p);
        p.sendMessage("§eTu rejoins la partie en cours - vie et faim partagees !");
    }

    /** Fin de partie (mort d'un joueur ou vie commune epuisee). */
    public void onGameOver(String reason) {
        if (state != GameState.RUNNING) return;
        state = GameState.REGENERATING;

        long duration = (System.currentTimeMillis() - gameStartMillis) / 1000L;
        plugin.getStats().recordGameDuration(duration);

        Bukkit.broadcastMessage("§4§l>> PARTIE TERMINEE << §r§c" + reason);
        Bukkit.broadcastMessage("§7Survie : §f" + StatsStore.formatDuration(duration)
                + " §7| Partie n°§f" + plugin.getStats().getGamesPlayed());

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§4§lMORT", "§7Le monde se regenere entierement...", 10, 60, 20);
        }

        // Petit delai pour laisser voir le titre, puis retour lobby + cycle.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.getLobbyManager().sendToLobby(p);
                }
                state = GameState.LOBBY; // autorise startNewCycle
                startNewCycle();
            }
        }.runTaskLater(plugin, 60L);
    }

    /** Trouve un point d'apparition sur. */
    private Location safeSpawn(World w) {
        Location s = w.getSpawnLocation();
        int x = s.getBlockX();
        int z = s.getBlockZ();
        int y = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
        if (loc.getBlock().getType() == Material.LAVA
                || loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.LAVA) {
            // repli simple : remonte au-dessus
            loc.setY(Math.max(y + 1, 80));
        }
        return loc;
    }

    public boolean forceStart() {
        if (state == GameState.LOBBY) {
            startNewCycle();
            return true;
        }
        return false;
    }

    public boolean forceStop() {
        if (state == GameState.RUNNING) {
            onGameOver("Arret force par un administrateur");
            return true;
        }
        return false;
    }
}
