package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.*;
import com.ladakx.inertia.files.config.message.MessageManager;
import com.ladakx.inertia.items.ItemManager;

public class ConfigManager {

    private final InertiaPlugin plugin;

    // Зберігаємо завантажені об'єкти конфігурації
    private InertiaConfig inertiaConfig;
    private BlocksConfig blocksConfig;
    private BodiesConfig bodiesConfig;
//    private RagdollConfig ragdollConfig;
    private RenderConfig renderConfig;
    private WorldsConfig worldsConfig;

    // Файлові обгортки (твої старі класи, або просто Files)
    private BlocksFile blocksFile;
    private BodiesFile bodiesFile;
    private ItemsFile itemsFile;
    private RenderFile renderFile;
//    private RagdollFile ragdollFile;
    private WorldsFile worldsFile;
    private MessagesFile messagesFile;

    // message manager
    private MessageManager messageManager;

    public ConfigManager(InertiaPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = new MessageManager(plugin);
    }

    public void reload() {
        InertiaLogger.info("Loading configurations...");

        try {
            // Load main config.yml
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            this.inertiaConfig = new InertiaConfig(plugin.getConfig());

            // Load blocks.yml
            this.blocksFile = new BlocksFile(plugin);
            this.blocksConfig = new BlocksConfig(blocksFile.getConfig());

            this.renderFile = new RenderFile(plugin);
            this.renderConfig = new RenderConfig(renderFile.getConfig());

            this.itemsFile = new ItemsFile(plugin);

//            this.ragdollFile = new RagdollFile(plugin);
//            this.ragdollConfig = new RagdollConfig(ragdollConfig.getConfig());

            this.worldsFile = new WorldsFile(plugin);
            this.worldsConfig = new WorldsConfig(worldsFile.getConfig());

            // Load messages.yml
            this.messagesFile = new MessagesFile(plugin);
            this.messageManager.reload(messagesFile.getConfig());

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

    public BlocksConfig getBlocksConfig() {
        if (blocksConfig == null) reload();
        return blocksConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public BlocksFile getBlocksFile() {
        return blocksFile;
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
