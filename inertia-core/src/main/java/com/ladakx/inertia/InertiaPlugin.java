package com.ladakx.inertia;

import co.aikar.commands.PaperCommandManager;
import com.ladakx.inertia.commands.Commands;
import com.ladakx.inertia.files.BlocksFile;
import com.ladakx.inertia.files.MessagesFile;
import com.ladakx.inertia.files.config.BlocksCFG;
import com.ladakx.inertia.files.config.PluginCFG;
import com.ladakx.inertia.nativelib.JoltNatives;
import com.ladakx.inertia.nms.bullet.BulletNMSTools;
import com.ladakx.inertia.nms.bullet.BulletTools;
import com.ladakx.inertia.nms.nbt.NBTPersistent;
import com.ladakx.inertia.nms.nbt.NBTPersistentTools;
import com.ladakx.inertia.nms.player.PlayerNMSTools;
import com.ladakx.inertia.nms.player.PlayerTools;
import com.ladakx.inertia.bullet.BulletManager;
import com.ladakx.inertia.bullet.listeners.PlayerQuitListener;
import com.ladakx.inertia.bullet.listeners.WorldLoadListener;
import com.ladakx.inertia.performance.pool.SimulationThreadPool;
import com.ladakx.inertia.utils.block.BlockUtils;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Main class of the plugin
 */
public final class InertiaPlugin extends JavaPlugin {

    // ******************
    // Constant
    private static final Logger logger = Logger.getLogger("Minecraft");

    private static InertiaPlugin instance = null;

    public static boolean enableHitboxRender = false;
    private BukkitScheduler scheduler;

    // ******************
    // Configs
    private MessagesFile messages;
    private BlocksFile blocksFile;
    private PluginCFG pluginCFG;
    private BlocksCFG blocksCFG;

    // ******************
    // Pool threads
    private SimulationThreadPool simulationThread;

    // ******************
    // Simulation && Physics
    private BulletManager bulletManager;

    // ******************
    // Managers
    private PaperCommandManager paperCommandManager;
    private BukkitAudiences adventure;

    // ******************
    // NMS Tools
    private PlayerNMSTools playerNMSTools;
    private BulletNMSTools bulletNMSTools;
    private NBTPersistent nbtPersistent;

    // ******************
    // Misc
    private boolean worldEditEnabled;

    // ******************
    // Native library loader
    private JoltNatives joltNatives;

    /**
     * On enable logic
     */
    @Override
    public void onEnable() {
        // Set instance class
        instance = this;
        scheduler = Bukkit.getScheduler();

        // Load native library
        try {
            joltNatives = new JoltNatives();
            joltNatives.init(this);
        } catch (JoltNatives.JoltNativeException e) {
            throw new RuntimeException(e);
        }

        // get adventure kyori && init paperCommandManager
        adventure = BukkitAudiences.create(this);
        paperCommandManager = new PaperCommandManager(this);

        // check worldedit
        var pm = Bukkit.getPluginManager();
        worldEditEnabled = pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit");

        // load config.yml blocks.yml and lang.yml
        saveResource("config.yml", false);
        File file = new File(getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        blocksFile = new BlocksFile();
        messages = new MessagesFile();

        blocksCFG = new BlocksCFG(blocksFile.getConfig());
        pluginCFG = new PluginCFG(config);

        bulletNMSTools = BulletTools.get();
        playerNMSTools = PlayerTools.get();
        nbtPersistent = NBTPersistentTools.get();

        // init threads
        this.simulationThread = new SimulationThreadPool();

        // init managers
        this.bulletManager = new BulletManager();
        this.bulletManager.init();

        // init block cache
        BlockUtils.initCollidableCache();

        // Register commands
        registerCommands();
        registerCommandCompletions();

        // register listeners
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
    }

    /**
     *  On disable logic
     */
    @Override
    public void onDisable() {
        // clear debug blocks
        this.bulletManager.getDebugBlockManager().clearDebugBlocks();

        // disable adventure kyori
        if (adventure != null) {
            adventure.close();
            adventure = null;
        }

        // disable bullet manager
        this.bulletManager.stopSchedulers();
        this.bulletManager.getSpaceManager().unloadSpaces();
        this.simulationThread.shutdown();
    }

    /**
     * Reload the plugin
     */
    public void reload() {
        // clear debug blocks
        this.bulletManager.getDebugBlockManager().clearDebugBlocks();

        // load files
        saveDefaultConfig();
        blocksFile = new BlocksFile();
        messages = new MessagesFile();

        // load configs
        blocksCFG = new BlocksCFG(getConfig());
        pluginCFG = new PluginCFG(blocksFile.getConfig());

        // reload spaces
        this.bulletManager.getSpaceManager().reloadSpaces();

        // update command completions
        registerCommandCompletions();
    }

    // ******************
    // register aikar commands

    /**
     * Register commands
     */
    private void registerCommands() {
        PaperCommandManager manager = this.paperCommandManager;

        manager.enableUnstableAPI("HelpEntry");
        manager.enableUnstableAPI("brigadier");
        manager.enableUnstableAPI("CommandHelp");

        registerCommandCompletions();

        manager.registerCommand(new Commands());
    }

    /**
     *  Register command completions
     */
    private void registerCommandCompletions() {
        PaperCommandManager manager = this.paperCommandManager;

        manager.getCommandCompletions().registerCompletion("debug-blocks", c ->
                getPConfig().GENERAL.DEBUG.debugBlocks.keySet()
        );
    }

    // ******************
    // Logging and debug

    /**
     * Log info (WHITE)
     * @param text text to log
     */
    public static void logInfo(String text){
        logger.info(text);
    }

    /**
     * Log warning (YELLOW)
     * @param text text to log
     */
    public static void logWarning(String text){
        logger.warning(text);
    }

    /**
     * Log severe (RED)
     * @param text text to log
     */
    public static void logSevere(String text){
        logger.severe(text);
    }

    /**
     * Log debug ([Inertia] Debug: text)
     * @param str text to log
     */
    public static void debug(String str) {
        logWarning("[Inertia] Debug: "+str);
    }

    // ******************
    // Get instance

    /**
     * Get instance of the plugin
     * @return instance of the plugin
     */
    public static InertiaPlugin getInstance() {
        return instance;
    }

    // ******************
    // Get configs

    /**
     * Get messages file
     * @return messages file
     */
    public static MessagesFile getMessages() {
        return instance.messages;
    }

    /**
     * Get plugin config
     * @return plugin config
     */
    public static PluginCFG getPConfig() {
        return instance.pluginCFG;
    }

    /**
     * Get blocks config
     * @return blocks config
     */
    public static BlocksCFG getBlocksConfig() {
        return instance.blocksCFG;
    }

    /**
     * Get blocks file
     * @return blocks file
     */
    public static BlocksFile getBlocksFile() {
        return instance.blocksFile;
    }

    // ******************
    // Get adventure

    /**
     * Get adventure kyori bukkit audiences
     * @return adventure kyori
     */
    public static BukkitAudiences getAdventure() {
        if (instance.adventure == null)
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");

        return instance.adventure;
    }

    // ******************
    // Get threads

    /**
     * Get simulation thread pool
     * @return simulation thread pool
     */
    public static SimulationThreadPool getSimulationThreadPool() {
        return instance.simulationThread;
    }

    // ******************
    // Get NMS Tools
    /**
     * Get player NMS tools for NMS operations
     * @return player NMS tools
     */
    public static PlayerNMSTools getPlayerNMSTools() {
        return instance.playerNMSTools;
    }

    /**
     * Get bullet NMS tools for NMS operations
     * @return bullet NMS tools
     */
    public static BulletNMSTools getBulletNMSTools() {
        return instance.bulletNMSTools;
    }

    /**
     * Get NBT persistent tools
     * @return NBT persistent tools
     */
    public static NBTPersistent getNBTPersistent() {
        return instance.nbtPersistent;
    }

    /**
     * Check if WorldEdit is enabled
     * @return true if WorldEdit is enabled
     */
    public boolean isWorldEditEnabled() {
        return worldEditEnabled;
    }

    // ******************
    // Get handlers

    /**
     * Get Bullet Manager
     * @return bullet manager
     */
    public static BulletManager getBulletManager() {
        return instance.bulletManager;
    }
}
