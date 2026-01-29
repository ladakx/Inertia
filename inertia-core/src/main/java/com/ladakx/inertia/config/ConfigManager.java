package com.ladakx.inertia.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.*;
import com.ladakx.inertia.config.message.MessageManager;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;

public class ConfigManager {

    // instance
    private static ConfigManager instance;

    // plugin
    private final InertiaPlugin plugin;

    // physics model registry
    private final PhysicsBodyRegistry physicsBodyRegistry;

    // message manager
    private final MessageManager messageManager;

    // Зберігаємо завантажені об'єкти конфігурації
    private InertiaConfig inertiaConfig;
    private BodiesConfig bodiesConfig;
    private RenderConfig renderConfig;
    private WorldsConfig worldsConfig;

    // Файлові обгортки
    private BodiesFile bodiesFile;
    private ItemsFile itemsFile;
    private RenderFile renderFile;
    private WorldsFile worldsFile;
    private MessagesFile messagesFile;

    private ConfigManager(InertiaPlugin plugin) {
        this.plugin = plugin;
        this.physicsBodyRegistry = new PhysicsBodyRegistry();
        this.messageManager = new MessageManager();
    }

    public static void init(InertiaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigManager(plugin);
            instance.reload();
        }
    }

    public void reload() {
        InertiaLogger.info("Loading configurations...");

        try {
            // main config.yml
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.inertiaConfig = new InertiaConfig(plugin.getConfig());

            // items.yml
            this.itemsFile = new ItemsFile(plugin);

            // bodies.yml
            this.bodiesFile = new BodiesFile(plugin);
            this.bodiesConfig = new BodiesConfig(bodiesFile.getConfig());

            // render.yml
            this.renderFile = new RenderFile(plugin);
            this.renderConfig = new RenderConfig(renderFile.getConfig());

            // worlds.yml
            this.worldsFile = new WorldsFile(plugin);
            this.worldsConfig = new WorldsConfig(worldsFile.getConfig());

            // messages.yml
            this.messagesFile = new MessagesFile(plugin);
            this.messageManager.reload(messagesFile.getConfig());

            // оновити реєстр моделей атомарно
            physicsBodyRegistry.reload(bodiesConfig, renderConfig);

            InertiaLogger.info("Configurations loaded successfully.");

        } catch (Exception e) {
            InertiaLogger.error("Failed to load configurations!", e);
        }
    }

    public BodiesConfig getBodiesConfig() {
        return bodiesConfig;
    }

    public RenderConfig getRenderConfig() {
        return renderConfig;
    }

    public WorldsConfig getWorldsConfig() {
        return worldsConfig;
    }

    public InertiaConfig getInertiaConfig() {
        if (inertiaConfig == null) reload();
        return inertiaConfig;
    }

    public ItemsFile getItemsFile() {
        return itemsFile;
    }

    public BodiesFile getBodiesFile() {
        return bodiesFile;
    }

    public RenderFile getRenderFile() {
        return renderFile;
    }

    public WorldsFile getWorldsFile() {
        return worldsFile;
    }

    public MessagesFile getMessagesFile() {
        return messagesFile;
    }

    public PhysicsBodyRegistry getPhysicsBodyRegistry() {
        return physicsBodyRegistry;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager not initialized! Call init() first.");
        }
        return instance;
    }
}