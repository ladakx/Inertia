package com.ladakx.inertia;

import com.ladakx.inertia.commands.Commands;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.items.ItemManager;
import com.ladakx.inertia.jolt.JoltManager;
import com.ladakx.inertia.jolt.listeners.WorldLoadListener;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.nativelib.JoltNatives;
import com.ladakx.inertia.nativelib.Precision;
import com.ladakx.inertia.nms.jolt.JoltNMSTools;
import com.ladakx.inertia.nms.jolt.JoltTools;
import com.ladakx.inertia.nms.player.PlayerNMSTools;
import com.ladakx.inertia.nms.player.PlayerTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

// Cloud Framework Imports
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * Main class of the Inertia plugin.
 * Refactored to follow Clean Code principles & Cloud Command Framework.
 */
public final class InertiaPlugin extends JavaPlugin {

    // Singleton instance
    private static InertiaPlugin instance;

    // config objects
    private ConfigManager configManager;

    // Systems
    private PaperCommandManager<CommandSender> commandManager;
    private AnnotationParser<CommandSender> annotationParser;

    // Jolt
    private JoltNatives joltNatives;

    // NMS & Tools
    private PlayerNMSTools playerNMSTools;
    private JoltNMSTools joltNMSTools;

    private boolean worldEditEnabled;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Logger
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // Load Integrations (Adventure, WorldEdit)
        setupIntegrations();

        // Load Configurations
        ConfigManager.init(this);
        ItemManager.init();

        // Initialize Native Libraries (Jolt)
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize NMS Tools
        setupNMSTools();

        // Initialize Jolt Manager & Space Manager
        JoltManager.init(this);
        SpaceManager.init(this);

        // Register Commands & Listeners
        setupCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        InertiaLogger.info("Inertia has been disabled.");
    }

    /**
     * Reloads the plugin configurations and spaces.
     */
    public void reload() {
        ConfigManager.getInstance().reload();
        InertiaLogger.info("Inertia configuration reloaded.");
    }

    // ==================================================================
    // Initialization Helpers (Private)
    // ==================================================================

    private void setupIntegrations() {
        var pm = Bukkit.getPluginManager();
        this.worldEditEnabled = pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit");
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
    }

    private void setupCommands() {
        try {
            // 1. Initialize Command Manager
            // Ми додаємо SenderMapper, щоб перетворити Paper CommandSourceStack -> Bukkit CommandSender
            this.commandManager = PaperCommandManager.builder(
                            SenderMapper.create(
                                    (stack) -> stack.getSender(), // Як отримати Sender зі стеку
                                    (sender) -> null // Зворотнє перетворення (зазвичай не потрібне для простих команд)
                            )
                    )
                    .executionCoordinator(ExecutionCoordinator.asyncCoordinator())
                    .buildOnEnable(this);

            // 2. Exception Handling (Обробка помилок)
            registerCommandExceptionHandlers();

            // 3. Initialize Annotation Parser
            this.annotationParser = new AnnotationParser<>(
                    this.commandManager,
                    CommandSender.class
            );

            // 4. Register Commands
            this.annotationParser.parse(new Commands(this));

        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize Cloud Command Framework", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommandExceptionHandlers() {
        // Handle No Permission
        this.commandManager.exceptionController().registerHandler(NoPermissionException.class, context -> {
            CommandSender sender = context.context().sender();
            ConfigManager.getInstance().getMessageManager().send(sender, MessageKey.NO_PERMISSIONS);
        });

        // Handle Invalid Syntax (Unknown command or wrong arguments)
        this.commandManager.exceptionController().registerHandler(InvalidSyntaxException.class, context -> {
            CommandSender sender = context.context().sender();
            // Optional: Send help message or specific error
            ConfigManager.getInstance().getMessageManager().send(sender, MessageKey.HELP_COMMAND);
        });
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
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

    public boolean isWorldEditEnabled() {
        return worldEditEnabled;
    }
}