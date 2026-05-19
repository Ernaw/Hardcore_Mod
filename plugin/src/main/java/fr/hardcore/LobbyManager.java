package fr.hardcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;

/**
 * Gere l'experience du lobby : teleportation, nettoyage du joueur,
 * barre de boss (etat / compte a rebours) et HOLOGRAMME de stats
 * (texte flottant au-dessus de la place centrale de l'ile).
 */
public class LobbyManager {

    private static final String HOLO_TAG = "hc_holo";

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
        updateHologram();
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

    /**
     * (Re)cree l'hologramme de stats flottant au-dessus de la place
     * centrale de l'ile et y ecrit les statistiques courantes.
     */
    public void updateHologram() {
        World hub = Bukkit.getWorld(plugin.getWorldManager().getLobbyName());
        if (hub == null) return;

        // Supprime l'ancien hologramme (evite les doublons).
        for (org.bukkit.entity.Entity e :
                new java.util.ArrayList<>(hub.getEntities())) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains(HOLO_TAG)) {
                e.remove();
            }
        }

        StatsStore st = plugin.getStats();
        String txt = String.join("\n",
                "§6§l✦ HARDCORE PARTAGE ✦",
                "§8§m                        ",
                "§7Parties jouees : §e§l" + st.getGamesPlayed(),
                "§7Derniere survie : §a"
                        + StatsStore.formatDuration(st.getLastGameDurationSec()),
                "§7Record de survie : §b"
                        + StatsStore.formatDuration(st.getBestGameDurationSec()),
                "§8§m                        ",
                "§7Etat : §f" + etatLisible());

        int spawnY = plugin.getConfig().getInt("lobby-spawn.y", 101);
        Location loc = new Location(hub, 0.5, spawnY + 3.4, 0.5);

        TextDisplay td = hub.spawn(loc, TextDisplay.class);
        td.text(LegacyComponentSerializer.legacySection().deserialize(txt));
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setSeeThrough(true);
        td.setShadowed(true);
        td.setDefaultBackground(false);
        try { td.setBackgroundColor(Color.fromARGB(90, 0, 0, 0)); }
        catch (Throwable ignored) {}
        td.setViewRange(3.0f);
        td.addScoreboardTag(HOLO_TAG);
        td.setPersistent(false);
    }

    private String etatLisible() {
        return switch (plugin.getGameManager().getState()) {
            case RUNNING -> "§cPartie en cours";
            case REGENERATING -> "§eRegeneration du monde";
            case LOBBY -> "§aEn attente";
        };
    }
}
