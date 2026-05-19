package fr.hardcore;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Affiche au-dessus de la barre d'XP (action bar) qui vient de perdre de
 * la vie et combien de coeurs, pendant quelques secondes, pour savoir
 * d'ou vient la perte de vie commune.
 */
public class DamageFeedback implements Listener {

    private final HardcorePlugin plugin;
    private volatile String message = null;
    private volatile long untilMillis = 0L;

    public DamageFeedback(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Lance le rafraichissement de l'action bar (toutes les 5 ticks). */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                String m = message;
                if (m == null) return;
                if (System.currentTimeMillis() > untilMillis) {
                    message = null;
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(m));
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    /** Affiche un message d'action bar pour {seconds} secondes. */
    public void show(String msg, int seconds) {
        this.message = msg;
        this.untilMillis = System.currentTimeMillis() + seconds * 1000L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!plugin.getConfig().getBoolean("damage-indicator-enabled", true)) return;
        if (plugin.getGameManager().getState() != GameState.RUNNING) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!victim.getWorld().getName()
                .startsWith(plugin.getWorldManager().getGameName())) return;

        double dmg = e.getFinalDamage();
        if (dmg <= 0.0) return;

        String hearts = fmt(dmg / 2.0);
        String src = sourceOf(e);
        show("§c❤ §e" + victim.getName()
                + " §7a perdu §c" + hearts + "❤"
                + (src != null ? " §8(" + src + ")" : "")
                /*+ " §7• vie commune"*/, 3);
    }

    private String sourceOf(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent ed) {
            Entity d = ed.getDamager();
            if (d instanceof Player pl) return pl.getName();
            return pretty(d.getType().name());
        }
        return pretty(e.getCause().name());
    }

    private String pretty(String enumName) {
        return enumName.toLowerCase().replace('_', ' ');
    }

    private String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
