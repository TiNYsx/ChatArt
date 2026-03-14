package com.tinysx.chatart;

import com.tinysx.chatart.command.ChatArtCommand;
import com.tinysx.chatart.command.HeadCommand;
import com.tinysx.chatart.listener.ChatListener;
import com.tinysx.chatart.skin.SkinFetcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ChatArt extends JavaPlugin {

    private static ChatArt instance;
    private SkinFetcher skinFetcher;
    private ChatListener chatListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        File cacheDir = new File(getDataFolder(), "skin-cache");
        cacheDir.mkdirs();

        skinFetcher = new SkinFetcher(this, cacheDir);
        chatListener = new ChatListener(this);

        getServer().getPluginManager().registerEvents(chatListener, this);

        HeadCommand headCmd = new HeadCommand(this);
        getCommand("head").setExecutor(headCmd);
        getCommand("head").setTabCompleter(headCmd);
        getCommand("chatart").setExecutor(new ChatArtCommand(this));

        getLogger().info("ChatArt enabled — player heads in chat, no resource pack needed!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChatArt disabled.");
    }

    public void reload() {
        reloadConfig();
        chatListener.clearCache();
        getLogger().info("ChatArt config reloaded.");
    }

    public static ChatArt getInstance() {
        return instance;
    }

    public SkinFetcher getSkinFetcher() {
        return skinFetcher;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }
}
