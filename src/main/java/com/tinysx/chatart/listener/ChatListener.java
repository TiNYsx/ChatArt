package com.tinysx.chatart.listener;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.image.ImageRegistry;
import com.tinysx.chatart.skin.HeadRenderer;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Handles chat formatting, head display, and :emoji: replacement.
 * Uses ObjectComponent for inline player head icons when texture data is available.
 */
public class ChatListener implements Listener {

    private static final Pattern EMOJI_PATTERN = Pattern.compile(":([a-zA-Z0-9_-]+):");

    private final ChatArt plugin;

    // Per-UUID caches
    private final ConcurrentHashMap<UUID, String> textureCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Component> hoverCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<Component>> rowCache  = new ConcurrentHashMap<>();

    public ChatListener(ChatArt plugin) {
        this.plugin = plugin;
    }

    private HeadRenderer.Mode getMode() {
        return HeadRenderer.Mode.of(plugin.getConfig().getString("render-mode", "braille"));
    }
    private int getWidth()  { return plugin.getConfig().getInt("head-width", 20); }
    private int getHeight() { return plugin.getConfig().getInt("head-height", 8); }

    // ── Join / Quit ─────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache player's texture property for ObjectComponent head icon
        for (ProfileProperty prop : player.getPlayerProfile().getProperties()) {
            if (prop.getName().equals("textures")) {
                textureCache.put(uuid, prop.getValue());
                break;
            }
        }

        // Cache solid block renders for hover/inline fallback
        plugin.getSkinFetcher().getSkin(uuid).thenAccept(skin -> {
            if (skin == null) return;
            HeadRenderer.Mode mode = getMode();
            int w = getWidth(), h = getHeight();
            hoverCache.put(uuid, HeadRenderer.renderHover(skin, mode, w, h));
            rowCache.put(uuid, HeadRenderer.renderRows(skin, mode, w, h));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        textureCache.remove(uuid);
        hoverCache.remove(uuid);
        rowCache.remove(uuid);
    }

    // ── Chat ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        boolean hoverHead  = plugin.getConfig().getBoolean("hover-head", true);
        boolean showInChat = plugin.getConfig().getBoolean("show-head-in-chat", false);

        // Build emoji replacement
        ImageRegistry registry = plugin.getImageRegistry();
        TextReplacementConfig emojiReplacement = TextReplacementConfig.builder()
            .match(EMOJI_PATTERN)
            .replacement((result, input) -> {
                String name = result.group(1);
                Component emoji = registry.getInline(name);
                return emoji != null ? emoji : Component.text(result.group());
            })
            .build();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component processedMessage = message.replaceText(emojiReplacement);

            Component displayName = sourceDisplayName;

            // Add head icon prefix if configured
            Component headPrefix = Component.empty();
            if (showInChat && textureCache.containsKey(uuid)) {
                headPrefix = HeadRenderer.createHeadIcon(textureCache.get(uuid))
                    .append(Component.text(" "));
            }

            // Add hover preview
            if (hoverHead && hoverCache.containsKey(uuid)) {
                displayName = displayName.hoverEvent(
                    HoverEvent.showText(hoverCache.get(uuid))
                );
            }

            return Component.empty()
                .append(headPrefix)
                .append(displayName)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(processedMessage);
        });
    }

    public void clearCache() {
        textureCache.clear();
        hoverCache.clear();
        rowCache.clear();
    }
}
