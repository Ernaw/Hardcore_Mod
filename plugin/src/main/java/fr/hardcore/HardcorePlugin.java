package fr.hardcore;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal : Hardcore coop a vie/faim partagees.
 */
public class HardcorePlugin extends JavaPlugin {

    private StatsStore stats;
    private WorldManager worldManager;
    private LobbyManager lobbyManager;
    private SharedLifeManager sharedLife;
    private GameManager gameManager;
    private DamageFeedback damageFeedback;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        this.stats = new StatsStore(this);
        this.worldManager = new WorldManager(this);
        this.lobbyManager = new LobbyManager(this);
        this.sharedLife = new SharedLifeManager(this);
        this.gameManager = new GameManager(this);
        this.damageFeedback = new DamageFeedback(this);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getServer().getPluginManager().registerEvents(damageFeedback, this);
        damageFeedback.start();
        if (getCommand("hc") != null) {
            getCommand("hc").setExecutor(new HCCommand(this));
        }

        // Le plugin charge en STARTUP (pour fournir le generateur void du
        // monde principal). La creation de mondes etant interdite pendant
        // le STARTUP, on differe l'initialisation au premier tick, une fois
        // le serveur completement charge.
        getServer().getScheduler().runTask(this, () -> {
            gameManager.init();
            getLogger().info("HardcoreShared actif. Partie n°" + stats.getGamesPlayed());
        });
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdown();
        if (stats != null) stats.save();
    }

    /**
     * Generateur du monde lobby (hub). Appele par Bukkit grace a
     * l'entree worlds.hub.generator de bukkit.yml.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }

    public StatsStore getStats() {
        return stats;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public SharedLifeManager getSharedLife() {
        return sharedLife;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public DamageFeedback getDamageFeedback() {
        return damageFeedback;
    }
}
