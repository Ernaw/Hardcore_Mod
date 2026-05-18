package fr.hardcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Commande admin : /hc start|stop|stats|reload
 */
public class HCCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public HCCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage("§6/hc start §7- forcer une nouvelle partie");
            s.sendMessage("§6/hc stop §7- terminer la partie en cours");
            s.sendMessage("§6/hc stats §7- afficher les statistiques");
            s.sendMessage("§6/hc reload §7- recharger la configuration");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (plugin.getGameManager().forceStart()) {
                    s.sendMessage("§aNouveau cycle lance.");
                } else {
                    s.sendMessage("§cImpossible : une partie/regeneration est deja en cours.");
                }
            }
            case "stop" -> {
                if (plugin.getGameManager().forceStop()) {
                    s.sendMessage("§aPartie terminee, regeneration en cours.");
                } else {
                    s.sendMessage("§cAucune partie en cours.");
                }
            }
            case "stats" -> {
                StatsStore st = plugin.getStats();
                s.sendMessage("§6=== Stats Hardcore ===");
                s.sendMessage("§7Parties jouees : §f" + st.getGamesPlayed());
                s.sendMessage("§7Derniere survie : §f"
                        + StatsStore.formatDuration(st.getLastGameDurationSec()));
                s.sendMessage("§7Record de survie : §f"
                        + StatsStore.formatDuration(st.getBestGameDurationSec()));
                s.sendMessage("§7Etat : §f" + plugin.getGameManager().getState());
            }
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage("§aConfiguration rechargee.");
            }
            default -> s.sendMessage("§cSous-commande inconnue.");
        }
        return true;
    }
}
