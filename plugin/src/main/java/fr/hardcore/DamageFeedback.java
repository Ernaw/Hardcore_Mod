package fr.hardcore;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    /**
     * Pour ces causes de degats (DoT issus d'effets partages), on ne
     * veut PAS que chaque joueur subisse son propre tick : sinon 3
     * joueurs empoisonnes = 3 sources de degats sur la vie commune.
     * On laisse un seul "host" (UUID le plus petit) prendre le tick ;
     * son degat est ensuite repliquee normalement sur les autres.
     */
    private static final java.util.Set<EntityDamageEvent.DamageCause> DOT_CAUSES =
            java.util.EnumSet.of(
                    EntityDamageEvent.DamageCause.POISON,
                    EntityDamageEvent.DamageCause.WITHER,
                    EntityDamageEvent.DamageCause.STARVATION,
                    EntityDamageEvent.DamageCause.FREEZE);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoTTick(EntityDamageEvent e) {
        if (plugin.getGameManager().getState() != GameState.RUNNING) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!DOT_CAUSES.contains(e.getCause())) return;
        String prefix = plugin.getWorldManager().getGameName();
        if (!victim.getWorld().getName().startsWith(prefix)) return;

        java.util.UUID host = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().getName().startsWith(prefix)) continue;
            if (!p.isValid() || p.getHealth() <= 0.0) continue;
            if (host == null || p.getUniqueId().compareTo(host) < 0) {
                host = p.getUniqueId();
            }
        }
        if (host != null && !victim.getUniqueId().equals(host)) {
            // Ce joueur a l'effet partage, mais ce n'est pas lui qui
            // doit subir le tick : annule. Le host prendra son tick et
            // la replication s'occupera de distribuer le degat.
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.getGameManager().getState() != GameState.RUNNING) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        String prefix = plugin.getWorldManager().getGameName();
        if (!victim.getWorld().getName().startsWith(prefix)) return;

        // finalDamage = la vie REELLEMENT perdue par la victime (apres
        // son armure / enchantements). C'est ce qu'on duplique tel quel
        // sur tous les coequipiers, en bypassant LEUR armure.
        double dmg = e.getFinalDamage();
        if (dmg <= 0.0) return;

        // 1) Indicateur visuel (action bar) sur la victime + montant.
        if (plugin.getConfig().getBoolean("damage-indicator-enabled", true)) {
            String hearts = fmt(dmg / 2.0);
            String src = sourceOf(e);
            show("§e" + victim.getName()
                    + " §7a perdu §c" + hearts + "❤"
                    + (src != null ? " §8(" + src + ")" : ""), 3);
        }

        // 2) On calcule UNE SEULE FOIS le vecteur de knockback de la
        //    victime (depuis l'attaquant vers la victime, dans le monde),
        //    puis on l'applique TEL QUEL a chaque coequipier : tout le
        //    monde se fait pousser dans la meme direction que la victime.
        Entity damager = resolveAttacker(e);
        Location attLoc = damager != null ? damager.getLocation() : null;

        org.bukkit.util.Vector sharedKnockback;
        float sharedYaw = 0f;
        if (attLoc != null && attLoc.getWorld() == victim.getWorld()) {
            // Source identifiee : vrai vecteur "attaquant -> victime".
            org.bukkit.util.Vector v = victim.getLocation().toVector()
                    .subtract(attLoc.toVector());
            v.setY(0);
            if (v.lengthSquared() < 0.0001) v.setZ(1);
            v.normalize().multiply(0.4);
            v.setY(0.4);
            sharedKnockback = v;
            sharedYaw = (float) (Math.toDegrees(
                    Math.atan2(attLoc.getZ() - victim.getZ(),
                               attLoc.getX() - victim.getX())) - 90.0);
        } else {
            // Pas d'attaquant (chute, vide, lave, noyade, suffocation, feu) :
            // pas de direction horizontale logique -> petit saut vertical
            // pour que tout le monde "ressente" le coup quand meme.
            sharedKnockback = new org.bukkit.util.Vector(0, 0.4, 0);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(victim)) continue;
            if (!p.getWorld().getName().startsWith(prefix)) continue;
            if (!p.isValid() || p.getHealth() <= 0.0) continue;
            applyShared(p, dmg, sharedKnockback, sharedYaw);
        }
    }

    /**
     * Retourne l'entite reellement responsable du degat. Pour un
     * projectile, on remonte au tireur (archer) plutot que de prendre
     * la fleche elle-meme, sinon la direction collerait au point d'impact.
     */
    private Entity resolveAttacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent ed)) return null;
        Entity d = ed.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr
                && pr.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return d;
    }

    /** Applique a un coequipier : meme degat exact + meme vecteur knockback. */
    private void applyShared(Player p, double dmg,
                             org.bukkit.util.Vector kb, float yaw) {
        double newH = Math.max(0.0, p.getHealth() - dmg);
        try { p.setHealth(newH); } catch (Throwable ignored) {}
        if (newH <= 0.0) return; // setHealth(0) declenche PlayerDeathEvent

        try { p.sendHurtAnimation(yaw); } catch (Throwable ignored) {}
        try { p.playSound(p.getLocation(), "entity.player.hurt", 1f, 1f); }
        catch (Throwable ignored) {}
        if (kb != null) {
            try { p.setVelocity(p.getVelocity().add(kb)); }
            catch (Throwable ignored) {}
        }
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
