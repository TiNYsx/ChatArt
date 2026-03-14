package com.tinysx.chatart.command;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.skin.HeadRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /head [player]
 *
 * Works for online players, players who have joined this server before,
 * and any valid Minecraft account via the Mojang API lookup.
 */
public class HeadCommand implements CommandExecutor, TabCompleter {

    private final ChatArt plugin;

    public HeadCommand(ChatArt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Usage: /head <player>", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("Fetching your head...", NamedTextColor.GRAY));
            fetchAndDisplay(sender, p.getUniqueId(), p.getName());
            return true;
        }

        String targetName = args[0];

        // Fast path: player is currently online
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            sender.sendMessage(Component.text("Fetching head for " + online.getName() + "...", NamedTextColor.GRAY));
            fetchAndDisplay(sender, online.getUniqueId(), online.getName());
            return true;
        }

        // Slow path: resolve name → UUID via Mojang API (works for any valid account)
        sender.sendMessage(Component.text("Looking up " + targetName + "...", NamedTextColor.GRAY));
        plugin.getSkinFetcher().getUUIDByName(targetName).thenAcceptAsync(result -> {
            if (result == null) {
                sender.sendMessage(Component.text(
                    "No Minecraft account found for '" + targetName + "'.", NamedTextColor.RED));
                return;
            }
            String resolvedName = result[1];
            UUID uuid = UUID.fromString(result[0]);
            sender.sendMessage(Component.text("Fetching head for " + resolvedName + "...", NamedTextColor.GRAY));
            fetchAndDisplay(sender, uuid, resolvedName);

        }, r -> Bukkit.getScheduler().runTask(plugin, r));

        return true;
    }

    private void fetchAndDisplay(CommandSender sender, UUID uuid, String name) {
        plugin.getSkinFetcher().getSkin(uuid).thenAcceptAsync(skin -> {
            if (skin == null) {
                sender.sendMessage(Component.text("Could not fetch skin for " + name + ".", NamedTextColor.RED));
                return;
            }

            // /head always uses braille for richer detail even when mini mode is set
            HeadRenderer.Mode mode = HeadRenderer.Mode.of(
                plugin.getConfig().getString("render-mode", "braille")
            );
            if (mode == HeadRenderer.Mode.MINI) mode = HeadRenderer.Mode.BRAILLE;

            int w = plugin.getConfig().getInt("head-width", 20);
            int h = plugin.getConfig().getInt("head-height", 8);

            sender.sendMessage(Component.text("--- " + name + "'s Head ---", NamedTextColor.YELLOW));
            HeadRenderer.renderRows(skin, mode, w, h).forEach(sender::sendMessage);
            sender.sendMessage(Component.text("---------------------------", NamedTextColor.YELLOW));

        }, r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
