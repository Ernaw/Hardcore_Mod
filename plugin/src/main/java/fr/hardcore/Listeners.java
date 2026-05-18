package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tous les ecouteurs d'evenements du plugin.
 */
public class Listeners implements Listener {

    private final HardcorePlugin plugin;

    public Listeners(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inLobby(Player p) {
        return p.getWorld().getName()
                .equals(plugin.getWorldManager().getLobbyName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                if (plugin.getGameManager().getState() == GameState.RUNNING) {
                    plugin.getGameManager().joinRunningGame(p);
                } else {
                    plugin.getLobbyManager().sendToLobby(p);
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getSharedLife().untrackPlayer(e.getPlayer().getUniqueId());
        plugin.getLobbyManager().removeFromLobby(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (plugin.getGameManager().getState() != GameState.RUNNING) return;
        Player p = e.getEntity();
        if (!p.getWorld().getName()
                .startsWith(plugin.getWorldManager().getGameName())) return;
        String cause = e.getDeathMessage() != null && !e.getDeathMessage().isEmpty()
                ? e.getDeathMessage() : p.getName() + " est mort";
        plugin.getGameManager().onGameOver(cause);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (plugin.getGameManager().getState() != GameState.RUNNING) {
            World hub = Bukkit.getWorld(plugin.getWorldManager().getLobbyName());
            if (hub != null) {
                e.setRespawnLocation(hub.getSpawnLocation().clone().add(0.5, 0, 0.5));
            }
            Player p = e.getPlayer();
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()
                            && plugin.getGameManager().getState() != GameState.RUNNING) {
                        plugin.getLobbyManager().sendToLobby(p);
                    }
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // Aucun degat dans le lobby, ni hors partie.
        if (inLobby(p) || plugin.getGameManager().getState() != GameState.RUNNING) {
            e.setCancelled(true);
            p.setFireTicks(0);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location s = p.getWorld().getSpawnLocation().clone().add(0.5, 0, 0.5);
                p.teleport(s);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getFoodLevel() >= 0)) return;
        if (!(e.getEntity() instanceof Player p)) return;
        // Pas de perte de faim hors partie / dans le lobby.
        if (inLobby(p) || plugin.getGameManager().getState() != GameState.RUNNING) {
            e.setCancelled(true);
            e.setFoodLevel(20);
        }
    }

    /**
     * Liaison des portails entre les mondes de jeu non par defaut
     * (game <-> game_nether <-> game_the_end).
     */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        String game = plugin.getWorldManager().getGameName();
        World from = e.getFrom().getWorld();
        if (from == null || !from.getName().startsWith(game)) return;

        World.Environment env = from.getEnvironment();
        Location to = null;

        if (e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (env == World.Environment.NORMAL) {
                World nether = Bukkit.getWorld(game + "_nether");
                if (nether != null) {
                    to = new Location(nether,
                            e.getFrom().getX() / 8.0, e.getFrom().getY(),
                            e.getFrom().getZ() / 8.0);
                }
            } else if (env == World.Environment.NETHER) {
                World normal = Bukkit.getWorld(game);
                if (normal != null) {
                    to = new Location(normal,
                            e.getFrom().getX() * 8.0, e.getFrom().getY(),
                            e.getFrom().getZ() * 8.0);
                }
            }
        } else if (e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (env == World.Environment.NORMAL) {
                World end = Bukkit.getWorld(game + "_the_end");
                if (end != null) to = end.getSpawnLocation();
            } else if (env == World.Environment.THE_END) {
                World normal = Bukkit.getWorld(game);
                if (normal != null) to = normal.getSpawnLocation();
            }
        }

        if (to != null) {
            e.setTo(to);
            e.setCanCreatePortal(true);
            e.setSearchRadius(16);
        }
    }
}
