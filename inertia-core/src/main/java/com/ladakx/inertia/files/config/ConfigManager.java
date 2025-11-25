package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.*;
import com.ladakx.inertia.files.config.message.MessageManager;
import com.ladakx.inertia.physics.registry.PhysicsModelRegistry;

public class ConfigManager {

    private final InertiaPlugin plugin;
    private final PhysicsModelRegistry physicsModelRegistry;

    // Зберігаємо завантажені об'єкти конфігурації
    private InertiaConfig inertiaConfig;
    //    private BlocksConfig blocksConfig;
    private BodiesConfig bodiesConfig;
    //    private RagdollConfig ragdollConfig;
    private RenderConfig renderConfig;
    private WorldsConfig worldsConfig;

    // Файлові обгортки
//    private BlocksFile blocksFile;
    private BodiesFile bodiesFile;
    private ItemsFile itemsFile;
    private RenderFile renderFile;
    //    private RagdollFile ragdollFile;
    private WorldsFile worldsFile;
    private MessagesFile messagesFile;

    // message manager
    private final MessageManager messageManager;

    public ConfigManager(InertiaPlugin plugin, PhysicsModelRegistry physicsModelRegistry) {
        this.plugin = plugin;
        this.physicsModelRegistry = physicsModelRegistry;
        this.messageManager = new MessageManager(plugin);
    }

    public void reload() {
        InertiaLogger.info("Loading configurations...");

        try {
            // main config.yml
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.inertiaConfig = new InertiaConfig(plugin.getConfig());

            // bodies.yml
            this.bodiesFile = new BodiesFile(plugin);
            this.bodiesConfig = new BodiesConfig(bodiesFile.getConfig());

            // render.yml
            this.renderFile = new RenderFile(plugin);
            this.renderConfig = new RenderConfig(renderFile.getConfig());

            // items.yml
            this.itemsFile = new ItemsFile(plugin);

            // worlds.yml
            this.worldsFile = new WorldsFile(plugin);
            this.worldsConfig = new WorldsConfig(worldsFile.getConfig());

            // messages.yml
            this.messagesFile = new MessagesFile(plugin);
            this.messageManager.reload(messagesFile.getConfig());

            // оновити реєстр моделей атомарно
            physicsModelRegistry.reload(bodiesConfig, renderConfig);

            InertiaLogger.info("Configurations loaded successfully.");

        } catch (Exception e) {
            InertiaLogger.error("Failed to load configurations!", e);
        }
    }

    public BodiesConfig getBodiesConfig() {
        return bodiesConfig;
    }

//    public RagdollConfig getRagdollConfig() {
//        return ragdollConfig;
//    }

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

//    public BlocksConfig getBlocksConfig() {
//        if (blocksConfig == null) reload();
//        return blocksConfig;
//    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

//    public BlocksFile getBlocksFile() {
//        return blocksFile;
//    }

    public ItemsFile getItemsFile() {
        return itemsFile;
    }

    public BodiesFile getBodiesFile() {
        return bodiesFile;
    }

    public RenderFile getRenderFile() {
        return renderFile;
    }

//    public RagdollFile getRagdollFile() {
//        return ragdollFile;
//    }

    public WorldsFile getWorldsFile() {
        return worldsFile;
    }

    public MessagesFile getMessagesFile() {
        return messagesFile;
    }
}