package com.tinysx.chatart.listener;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.skin.HeadRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles chat formatting and skin prefetching on player join.
 *
 * Two modes (configured in config.yml):
 *   hover-head      — hovering over a player's name in chat shows their 8×8 head.
 *   show-head-in-chat — prints the full 8-row head above each chat message.
 */
public class ChatListener implements Listener {

    private final ChatArt plugin;

    // Per-UUID caches, populated when the player joins
    private final ConcurrentHashMap<UUID, Component> hoverCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<Component>> rowCache  = new ConcurrentHashMap<>();

    public ChatListener(ChatArt plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Join / Quit — prefetch skin so it's ready before the first chat message
    // -------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getSkinFetcher().getSkin(uuid).thenAccept(skin -> {
            if (skin == null) return;
            hoverCache.put(uuid, HeadRenderer.renderHover(skin));
            rowCache.put(uuid, HeadRenderer.renderRows(skin));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        hoverCache.remove(uuid);
        rowCache.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Chat — apply hover head and/or inline head rows
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        boolean hoverHead   = plugin.getConfig().getBoolean("hover-head", true);
        boolean showInChat  = plugin.getConfig().getBoolean("show-head-in-chat", false);

        // Apply hover preview to the player's display name in chat
        if (hoverHead && hoverCache.containsKey(uuid)) {
            Component headPreview = hoverCache.get(uuid);
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.empty()
                            .append(sourceDisplayName.hoverEvent(HoverEvent.showText(headPreview)))
                            .append(Component.text(": ", NamedTextColor.WHITE))
                            .append(message)
            );
        }

        // Print the full 8-row head above the chat message for all online players
        if (showInChat && rowCache.containsKey(uuid)) {
            List<Component> rows = rowCache.get(uuid);
            String name = event.getPlayer().getName();
            // Player#sendMessage is async-safe in Paper, but we schedule on main
            // to avoid any potential issues with future API changes.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Component header = Component.text("▼ " + name, NamedTextColor.YELLOW);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(header);
                    rows.forEach(p::sendMessage);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Public API used by reload command
    // -------------------------------------------------------------------------

    /** Clears all cached skin data (re-fetched on next use). */
    public void clearCache() {
        hoverCache.clear();
        rowCache.clear();
    }
}
