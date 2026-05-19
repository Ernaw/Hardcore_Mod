package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Gere l'experience du lobby : teleportation, nettoyage du joueur,
 * barre de boss (etat / compte a rebours) et tableau de stats.
 */
public class LobbyManager {

    private final HardcorePlugin plugin;
    private final BossBar bossBar;

    public LobbyManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.bossBar = Bukkit.createBossBar(
                "En attente de joueurs...", BarColor.BLUE, BarStyle.SOLID);
        this.bossBar.setVisible(true);
    }

    /** Teleporte et "nettoie" un joueur pour le placer dans le lobby. */
    public void sendToLobby(Player p) {
        World hub = Bukkit.getWorld(plugin.getWorldManager().getLobbyName());
        if (hub == null) return;
        Location spawn = hub.getSpawnLocation().clone().add(0.5, 0, 0.5);
        spawn.setYaw(0);

        p.setGameMode(GameMode.ADVENTURE);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        p.getInventory().clear();
        p.setExp(0f);
        p.setLevel(0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        double max = maxHealth(p);
        p.setHealth(max);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.teleport(spawn);

        bossBar.addPlayer(p);
        applyScoreboard(p);
        // Son d'arrivee au lobby + carillon de stats.
        Sounds.to(plugin, p, "block.note_block.bell", 0.7f, 1.2f);
        Sounds.to(plugin, p, "entity.experience_orb.pickup", 0.6f, 1f);
    }

    public void removeFromLobby(Player p) {
        bossBar.removePlayer(p);
    }

    private double maxHealth(Player p) {
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            return p.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        return 20.0;
    }

    /** Met a jour la barre de boss avec un texte et une progression (0..1). */
    public void setBossBar(String text, double progress, BarColor color) {
        bossBar.setTitle(text);
        bossBar.setColor(color);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    /** Tableau de stats affiche sur le cote pour un joueur du lobby. */
    public void applyScoreboard(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("hc", "dummy",
                "§6§lHARDCORE PARTAGE");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        StatsStore st = plugin.getStats();
        int line = 10;
        obj.getScore("§7§m            ").setScore(line--);
        obj.getScore("§fParties jouees:").setScore(line--);
        obj.getScore("§e" + st.getGamesPlayed()).setScore(line--);
        obj.getScore("§0 ").setScore(line--);
        obj.getScore("§fDerniere survie:").setScore(line--);
        obj.getScore("§a" + StatsStore.formatDuration(st.getLastGameDurationSec()))
                .setScore(line--);
        obj.getScore("§0  ").setScore(line--);
        obj.getScore("§fRecord de survie:").setScore(line--);
        obj.getScore("§b" + StatsStore.formatDuration(st.getBestGameDurationSec()))
                .setScore(line--);
        obj.getScore("§7§m           ").setScore(line--);

        p.setScoreboard(sb);
    }

    /** Rafraichit le tableau de stats de tous les joueurs du lobby. */
    public void refreshAllScoreboards() {
        World hub = Bukkit.getWorld(plugin.getWorldManager().getLobbyName());
        if (hub == null) return;
        for (Player p : hub.getPlayers()) {
            applyScoreboard(p);
        }
    }

    public void clearScoreboard(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
