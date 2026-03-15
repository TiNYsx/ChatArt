package com.tinysx.chatart;

import com.tinysx.chatart.command.ChatArtCommand;
import com.tinysx.chatart.command.HeadCommand;
import com.tinysx.chatart.image.ImageRegistry;
import com.tinysx.chatart.listener.ChatListener;
import com.tinysx.chatart.listener.MotdListener;
import com.tinysx.chatart.mineskin.MineSkinService;
import com.tinysx.chatart.mineskin.TextureCache;
import com.tinysx.chatart.skin.SkinFetcher;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ChatArt extends JavaPlugin {

    private static ChatArt instance;
    private SkinFetcher skinFetcher;
    private ChatListener chatListener;
    private MineSkinService mineSkinService;
    private TextureCache textureCache;
    private ImageRegistry imageRegistry;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Create directories
        File cacheDir = new File(getDataFolder(), "skin-cache");
        cacheDir.mkdirs();
        File imagesDir = new File(getDataFolder(), "images");
        imagesDir.mkdirs();
        File textureCacheDir = new File(getDataFolder(), "texture-cache");
        textureCacheDir.mkdirs();

        // Initialize services
        skinFetcher = new SkinFetcher(this, cacheDir);

        mineSkinService = new MineSkinService(getLogger());
        mineSkinService.init(getConfig().getString("mineskin-key", ""));

        textureCache = new TextureCache(textureCacheDir, getLogger());

        imageRegistry = new ImageRegistry(
            imagesDir, getLogger(), mineSkinService, textureCache,
            getConfig().getInt("emoji-width", 8),
            getConfig().getInt("emoji-preview-width", 20),
            getConfig().getInt("emoji-preview-height", 10)
        );
        imageRegistry.loadAll();

        // Register listeners
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);

        // Register commands
        HeadCommand headCmd = new HeadCommand(this);
        getCommand("head").setExecutor(headCmd);
        getCommand("head").setTabCompleter(headCmd);

        ChatArtCommand chatArtCmd = new ChatArtCommand(this);
        getCommand("chatart").setExecutor(chatArtCmd);
        getCommand("chatart").setTabCompleter(chatArtCmd);

        getLogger().info("ChatArt enabled — player heads, custom emoji & MOTD images!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChatArt disabled.");
    }

    public void reload() {
        reloadConfig();
        chatListener.clearCache();
        mineSkinService.init(getConfig().getString("mineskin-key", ""));
        imageRegistry.reload(
            getConfig().getInt("emoji-width", 8),
            getConfig().getInt("emoji-preview-width", 20),
            getConfig().getInt("emoji-preview-height", 10)
        );
        getLogger().info("ChatArt config reloaded.");
    }

    public static ChatArt getInstance() { return instance; }
    public SkinFetcher getSkinFetcher() { return skinFetcher; }
    public ChatListener getChatListener() { return chatListener; }
    public MineSkinService getMineSkinService() { return mineSkinService; }
    public ImageRegistry getImageRegistry() { return imageRegistry; }
}
