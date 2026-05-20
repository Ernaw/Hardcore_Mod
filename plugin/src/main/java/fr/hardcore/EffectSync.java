package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Replique sur tous les coequipiers les effets de potion appliques /
 * changes / retires sur un joueur en partie. Resultat : un buff/debuff
 * touche tout le monde (poison, force, regen, vitesse, etc.).
 *
 * <p>Pour ne PAS multiplier les degats des effets DoT (poison, wither...),
 * la limitation a une seule source de degats est faite dans
 * {@link DamageFeedback} via l'annulation des ticks des non-hosts.
 */
public class EffectSync implements Listener {

    private final HardcorePlugin plugin;
    private final ThreadLocal<Boolean> propagating =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EffectSync(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (propagating.get()) return;
        if (plugin.getGameManager().getState() != GameState.RUNNING) return;
        if (!(e.getEntity() instanceof Player src)) return;
        String prefix = plugin.getWorldManager().getGameName();
        if (!src.getWorld().getName().startsWith(prefix)) return;

        PotionEffect newEf = e.getNewEffect();
        PotionEffect oldEf = e.getOldEffect();
        EntityPotionEffectEvent.Action act = e.getAction();

        propagating.set(Boolean.TRUE);
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(src)) continue;
                if (!p.getWorld().getName().startsWith(prefix)) continue;
                if (!p.isValid() || p.getHealth() <= 0.0) continue;
                switch (act) {
                    case ADDED, CHANGED -> {
                        if (newEf != null) {
                            p.addPotionEffect(new PotionEffect(
                                    newEf.getType(), newEf.getDuration(),
                                    newEf.getAmplifier(), newEf.isAmbient(),
                                    newEf.hasParticles(), newEf.hasIcon()), true);
                        }
                    }
                    case REMOVED -> {
                        if (oldEf != null) p.removePotionEffect(oldEf.getType());
                    }
                    case CLEARED -> {
                        for (PotionEffect pe :
                                new java.util.ArrayList<>(p.getActivePotionEffects())) {
                            p.removePotionEffect(pe.getType());
                        }
                    }
                }
            }
        } finally {
            propagating.set(Boolean.FALSE);
        }
    }
}
