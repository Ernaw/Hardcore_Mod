package fr.hardcore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Commande /hc start|stop|stats|reload|resetstats
 *
 * La commande est toujours reconnue (aucune permission au niveau
 * plugin.yml) ; le controle d'acces est fait ici, par sous-commande,
 * pour donner un message clair plutot qu'un "commande inconnue".
 */
public class HCCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public HCCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAdmin(CommandSender s) {
        return s.isOp() || s.hasPermission("hardcoreshared.admin");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage("§6=== Hardcore Partage ===");
            s.sendMessage("§6/hc stats §7- afficher les statistiques");
            s.sendMessage("§6/hc start §7- forcer une nouvelle partie §8(admin)");
            s.sendMessage("§6/hc stop §7- terminer la partie en cours §8(admin)");
            s.sendMessage("§6/hc reload §7- recharger la configuration §8(admin)");
            s.sendMessage("§6/hc resetstats §7- remettre les stats a zero §8(admin)");
            s.sendMessage("§6/hc cleanworlds §7- supprimer les anciens mondes §8(admin)");
            s.sendMessage("§6/hc testdamage [coeurs] §7- tester l'indicateur de degats §8(admin)");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats" -> {
                StatsStore st = plugin.getStats();
                World gw = Bukkit.getWorld(plugin.getWorldManager().getGameName());
                s.sendMessage("§6=== Stats Hardcore ===");
                s.sendMessage("§7Parties jouees : §f" + st.getGamesPlayed());
                s.sendMessage("§7Derniere survie : §f"
                        + StatsStore.formatDuration(st.getLastGameDurationSec()));
                s.sendMessage("§7Record de survie : §f"
                        + StatsStore.formatDuration(st.getBestGameDurationSec()));
                s.sendMessage("§7Etat : §f" + plugin.getGameManager().getState());
                if (gw != null) {
                    s.sendMessage("§7Monde : §f" + gw.getName()
                            + " §7| Difficulte : §f" + gw.getDifficulty()
                            + " §7| Seed : §f" + plugin.getWorldManager().getLastSeed());
                }
            }
            case "start" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                if (plugin.getGameManager().forceStart()) {
                    s.sendMessage("§aNouveau cycle lance.");
                } else {
                    s.sendMessage("§cImpossible : une partie/regeneration est deja en cours.");
                }
            }
            case "stop" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                if (plugin.getGameManager().forceStop()) {
                    s.sendMessage("§aPartie terminee, regeneration en cours.");
                } else {
                    s.sendMessage("§cAucune partie en cours.");
                }
            }
            case "reload" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                plugin.reloadConfig();
                s.sendMessage("§aConfiguration rechargee.");
            }
            case "resetstats" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                plugin.getStats().resetAll();
                plugin.getLobbyManager().updateHologram();
                s.sendMessage("§aStatistiques remises a zero.");
            }
            case "testdamage" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                double hearts = 2.0;
                if (args.length > 1) {
                    try { hearts = Double.parseDouble(args[1]); }
                    catch (NumberFormatException ignored) {}
                }
                boolean inGame = s instanceof Player p
                        && plugin.getGameManager().getState() == GameState.RUNNING
                        && p.getWorld().getName().startsWith(
                                plugin.getWorldManager().getGameName());
                if (inGame) {
                    ((Player) s).damage(hearts * 2.0);
                    s.sendMessage("§a" + hearts + "❤ de degats reels appliques "
                            + "(indicateur + son).");
                } else {
                    plugin.getDamageFeedback().show("§c❤ §eTEST §7a perdu §c"
                            + hearts + "❤ §8(simulation) §7• vie commune", 5);
                    s.sendMessage("§aSimulation affichee dans l'action bar "
                            + "(connecte-toi en partie pour un test reel).");
                }
            }
            case "cleanworlds" -> {
                if (!isAdmin(s)) { noPerm(s); return true; }
                int n = plugin.getWorldManager().countGameWorldFolders();
                plugin.getWorldManager().sweepStaleGameWorlds(
                        plugin.getWorldManager().getGameName());
                s.sendMessage("§e" + n + " dossier(s) de monde detecte(s). "
                        + "Nettoyage lance.");
                s.sendMessage("§7Les mondes encore verrouilles par le serveur "
                        + "seront supprimes au prochain redemarrage (limite Windows).");
            }
            default -> s.sendMessage("§cSous-commande inconnue. Tape §6/hc§c.");
        }
        return true;
    }

    private void noPerm(CommandSender s) {
        s.sendMessage("§cReserve aux administrateurs (OP). "
                + "Demande l'OP en console : §fop <pseudo>§c.");
    }
}
