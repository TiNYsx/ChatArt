package com.tinysx.chatart.listener;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.image.ImageRegistry;
import com.tinysx.chatart.skin.HeadRenderer;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Handles MOTD rendering using ObjectComponent head icon grids (Pictogram-style).
 * Falls back to MiniMessage text when the image isn't ready or MineSkin is not configured.
 */
public class MotdListener implements Listener {

    private final ChatArt plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MotdListener(ChatArt plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        ConfigurationSection motdConfig = plugin.getConfig().getConfigurationSection("motd");
        if (motdConfig == null || !motdConfig.getBoolean("enabled", false)) return;

        String imageName = motdConfig.getString("image", "");
        if (!imageName.isBlank()) {
            // Remove file extension if present
            String name = imageName.replaceFirst("\\.[^.]+$", "").toLowerCase();
            ImageRegistry registry = plugin.getImageRegistry();
            ImageRegistry.ImageData data = registry.getImageData(name);

            if (data != null) {
                // Render as head icon grid
                Component motd = HeadRenderer.renderAsHeadComponent(data.textures(), data.cols());
                event.motd(motd);
                return;
            }
        }

        // Fallback: use MiniMessage text description
        List<String> fallback = motdConfig.getStringList("fallback-description");
        if (!fallback.isEmpty()) {
            Component motd = Component.empty();
            for (int i = 0; i < fallback.size(); i++) {
                motd = motd.append(miniMessage.deserialize(fallback.get(i)));
                if (i < fallback.size() - 1) {
                    motd = motd.append(Component.newline());
                }
            }
            event.motd(motd);
        }
    }
}
