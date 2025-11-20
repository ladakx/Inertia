package com.ladakx.inertia;

import co.aikar.commands.PaperCommandManager;
import com.ladakx.inertia.items.ItemManager;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.jolt.JoltManager;
import com.ladakx.inertia.jolt.listeners.WorldLoadListener;
import com.ladakx.inertia.commands.Commands;
import com.ladakx.inertia.nativelib.Precision;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.nativelib.JoltNatives;
import com.ladakx.inertia.nms.jolt.JoltNMSTools;
import com.ladakx.inertia.nms.jolt.JoltTools;
import com.ladakx.inertia.nms.nbt.NBTPersistent;
import com.ladakx.inertia.nms.nbt.NBTPersistentTools;
import com.ladakx.inertia.nms.player.PlayerNMSTools;
import com.ladakx.inertia.nms.player.PlayerTools;
import com.ladakx.inertia.performance.pool.SimulationThreadPool;
import com.ladakx.inertia.utils.block.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class of the Inertia plugin.
 * Refactored to follow Clean Code principles.
 */
public final class InertiaPlugin extends JavaPlugin {

    // Singleton instance
    private static InertiaPlugin instance;

    // files
    public static boolean enableHitboxRender = false;

    // config objects
    private ConfigManager configManager;

    // Systems
    private SimulationThreadPool simulationThreadPool;
    private PaperCommandManager paperCommandManager;

    // Jolt
    private JoltNatives joltNatives;

    private JoltManager joltManager;
    private SpaceManager spaceManager;

    private ItemManager itemManager;

    // NMS & Tools
    private PlayerNMSTools playerNMSTools;
    private JoltNMSTools joltNMSTools;
    private NBTPersistent nbtPersistent;

    private boolean worldEditEnabled;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Logger
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // 1. Load Integrations (Adventure, WorldEdit)
        setupIntegrations();

        // 2. Load Configurations
        loadConfigurations();

        this.itemManager = new ItemManager(getConfigManager().getItemsFile());

        // 3. Initialize Native Libraries (Jolt)
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Initialize NMS Tools
        setupNMSTools();

        // 5. Initialize Thread Pools & Managers
        this.simulationThreadPool = new SimulationThreadPool();

        JoltManager.init(this);
        this.spaceManager = new SpaceManager(this);

        // 6. Initialize Cache
        BlockUtils.initCollidableCache();

        // 7. Register Commands & Listeners
        setupCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    @Override
    public void onDisable() {
//        if (this.bulletManager != null) {
////            this.bulletManager.getDebugBlockManager().clearDebugBlocks();
//            this.bulletManager.stopSchedulers();
//            if (this.bulletManager.getSpaceManager() != null) {
//                this.bulletManager.getSpaceManager().unloadSpaces();
//            }
//        }

        if (this.simulationThreadPool != null) {
            this.simulationThreadPool.shutdown();
        }

        InertiaLogger.info("Inertia has been disabled.");
    }

    /**
     * Reloads the plugin configurations and spaces.
     */
    public void reload() {
//        if (this.bulletManager != null) {
//            this.bulletManager.getDebugBlockManager().clearDebugBlocks();
//        }

        loadConfigurations(); // Re-use the centralized loading method

//        if (this.bulletManager != null) {
//            this.bulletManager.getSpaceManager().reloadSpaces();
//        }

        // Update command completions via the existing manager if needed
        registerCommandCompletions();

        InertiaLogger.info("Inertia configuration reloaded.");
    }

    // ==================================================================
    // Initialization Helpers (Private)
    // ==================================================================

    private void setupIntegrations() {
        var pm = Bukkit.getPluginManager();
        this.worldEditEnabled = pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit");
    }

    private void loadConfigurations() {
        this.configManager = new ConfigManager(this);
        this.configManager.reload();
    }

    private boolean setupNativeLibraries() {
        try {
            this.joltNatives = new JoltNatives();

            String precisionStr = this.getConfig().getString("jolt.precision", "SP");
            Precision precision = "DP".equalsIgnoreCase(precisionStr) ? Precision.DP : Precision.SP;

            InertiaLogger.info("Jolt Precision set to: " + precision);
            this.joltNatives.init(this, precision);
            return true;
        } catch (JoltNatives.JoltNativeException e) {
            InertiaLogger.error("Critical error loading Jolt Natives", e);
            return false;
        }
    }

    private void setupNMSTools() {
        this.joltNMSTools = JoltTools.get();
        this.playerNMSTools = PlayerTools.get();
        this.nbtPersistent = NBTPersistentTools.get();
    }

    private void setupCommands() {
        this.paperCommandManager = new PaperCommandManager(this);
        this.paperCommandManager.enableUnstableAPI("HelpEntry");
        this.paperCommandManager.enableUnstableAPI("brigadier");
        this.paperCommandManager.enableUnstableAPI("CommandHelp");

        registerCommandCompletions();
        this.paperCommandManager.registerCommand(new Commands());
    }

    private void registerCommandCompletions() {
        if (this.paperCommandManager == null) return;

        this.paperCommandManager.getCommandCompletions().registerCompletion("debug-blocks", c -> getConfigManager().getInertiaConfig().GENERAL.DEBUG.debugBlocks.keySet());
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
    }

    // ==================================================================
    // Static Accessors (Maintained for backward compatibility)
    // Suggestion: Try to pass 'InertiaPlugin' instance to other classes
    // instead of using these static methods in the future.
    // ==================================================================

    public static InertiaPlugin getInstance() {
        return instance;
    }

    public PlayerNMSTools getPlayerNMSTools() {
        return instance.playerNMSTools;
    }

    public JoltNMSTools getJoltNMSTools() {
        return joltNMSTools;
    }

    public NBTPersistent getNBTPersistent() {
        return nbtPersistent;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public JoltManager getJoltManager() {
        return joltManager;
    }

    public SpaceManager getSpaceManager() {
        return spaceManager;
    }

    public SimulationThreadPool getSimulationThreadPool() {
        return simulationThreadPool;
    }

    public boolean isWorldEditEnabled() {
        return worldEditEnabled;
    }
}