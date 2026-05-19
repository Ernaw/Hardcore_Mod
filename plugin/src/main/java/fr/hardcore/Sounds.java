package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utilitaire de sons. On utilise les cles texte ("entity.wither.spawn"...)
 * plutot que l'enum Sound : plus robuste entre versions de Minecraft.
 */
public final class Sounds {

    private Sounds() {}

    private static boolean enabled(HardcorePlugin pl) {
        return pl.getConfig().getBoolean("sounds-enabled", true);
    }

    /** Joue un son a tous les joueurs connectes. */
    public static void all(HardcorePlugin pl, String key, float vol, float pitch) {
        if (!enabled(pl)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.playSound(p.getLocation(), key, vol, pitch); } catch (Throwable ignored) {}
        }
    }

    /** Joue un son a un seul joueur. */
    public static void to(HardcorePlugin pl, Player p, String key, float vol, float pitch) {
        if (!enabled(pl)) return;
        try { p.playSound(p.getLocation(), key, vol, pitch); } catch (Throwable ignored) {}
    }
}
