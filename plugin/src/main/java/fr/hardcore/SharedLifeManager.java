package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coeur de la mecanique "vie et faim partagees".
 *
 * Principe : un seul pool de vie / faim / saturation pour TOUT le groupe.
 * A chaque tick on mesure l'ecart de chaque joueur par rapport a la
 * derniere valeur synchronisee (degats subis, faim consommee, nourriture
 * mangee, regeneration...), on additionne tous ces ecarts dans le pool
 * commun, puis on reapplique la valeur commune a TOUS les joueurs.
 *
 * Resultat : si un joueur prend des degats ou mange, c'est toute l'equipe
 * qui en subit / profite. Quand la vie commune tombe a 0 -> game over.
 */
public class SharedLifeManager {

    private final HardcorePlugin plugin;

    private double sharedHealth;
    private double sharedFood;
    private double sharedSaturation;
    private double maxHealth = 20.0;

    private final Map<UUID, Double> lastHealth = new HashMap<>();
    private final Map<UUID, Double> lastFood = new HashMap<>();
    private final Map<UUID, Double> lastSaturation = new HashMap<>();

    private boolean gameOverFired = false;

    public SharedLifeManager(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Reinitialise le pool partage au demarrage d'une partie. */
    public void reset() {
        this.maxHealth = 20.0;
        this.sharedHealth = plugin.getConfig().getDouble("start-health", 20.0);
        this.sharedFood = plugin.getConfig().getInt("start-food", 20);
        this.sharedSaturation = 5.0;
        this.gameOverFired = false;
        lastHealth.clear();
        lastFood.clear();
        lastSaturation.clear();
    }

    /** Initialise le suivi d'un joueur (a son entree en partie). */
    public void trackPlayer(Player p) {
        applyToPlayer(p);
        lastHealth.put(p.getUniqueId(), sharedHealth);
        lastFood.put(p.getUniqueId(), sharedFood);
        lastSaturation.put(p.getUniqueId(), sharedSaturation);
    }

    public void untrackPlayer(UUID id) {
        lastHealth.remove(id);
        lastFood.remove(id);
        lastSaturation.remove(id);
    }

    private double maxHealthOf(Player p) {
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            return p.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        return 20.0;
    }

    /**
     * Appele chaque tick par le GameManager pendant qu'une partie tourne.
     */
    public void tick() {
        if (gameOverFired) return;

        java.util.Collection<? extends Player> all = Bukkit.getOnlinePlayers();
        String gameWorld = plugin.getWorldManager().getGameName();

        double healthDelta = 0;
        double foodDelta = 0;
        double satDelta = 0;
        boolean anyInGame = false;

        for (Player p : all) {
            if (!p.getWorld().getName().startsWith(gameWorld)) continue;
            anyInGame = true;
            UUID id = p.getUniqueId();
            maxHealth = maxHealthOf(p);

            double lh = lastHealth.getOrDefault(id, sharedHealth);
            double lf = lastFood.getOrDefault(id, sharedFood);
            double ls = lastSaturation.getOrDefault(id, sharedSaturation);

            healthDelta += (p.getHealth() - lh);
            foodDelta += (p.getFoodLevel() - lf);
            satDelta += (p.getSaturation() - ls);
        }

        if (!anyInGame) return;

        sharedHealth = clamp(sharedHealth + healthDelta, 0, maxHealth);
        sharedFood = clamp(sharedFood + foodDelta, 0, 20);
        sharedSaturation = clamp(sharedSaturation + satDelta, 0, 20);

        // Plus de vie commune -> fin de partie.
        if (sharedHealth <= 0.0001) {
            gameOverFired = true;
            plugin.getGameManager().onGameOver("La vie commune est epuisee");
            for (Player p : all) {
                if (p.getWorld().getName().startsWith(gameWorld)
                        && p.getHealth() > 0) {
                    p.setHealth(0.0);
                }
            }
            return;
        }

        // Reapplique la valeur commune a tous les joueurs en partie.
        for (Player p : all) {
            if (!p.getWorld().getName().startsWith(gameWorld)) continue;
            UUID id = p.getUniqueId();
            double max = maxHealthOf(p);
            double h = Math.max(0.5, Math.min(sharedHealth, max));
            p.setHealth(h);
            p.setFoodLevel((int) Math.round(sharedFood));
            p.setSaturation((float) sharedSaturation);
            lastHealth.put(id, p.getHealth());
            lastFood.put(id, (double) p.getFoodLevel());
            lastSaturation.put(id, (double) p.getSaturation());
        }
    }

    /** Force un joueur a la valeur commune (entree en partie / respawn). */
    public void applyToPlayer(Player p) {
        double max = maxHealthOf(p);
        p.setHealth(Math.max(0.5, Math.min(sharedHealth, max)));
        p.setFoodLevel((int) Math.round(sharedFood));
        p.setSaturation((float) sharedSaturation);
    }

    public boolean isGameOverFired() {
        return gameOverFired;
    }

    public double getSharedHealth() {
        return sharedHealth;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
