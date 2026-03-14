package com.tinysx.chatart.command;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.skin.HeadRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /head [player]
 *
 * Renders the target's 8×8 face in chat using colored full-block characters.
 * No resource pack required.
 */
public class HeadCommand implements CommandExecutor, TabCompleter {

    private final ChatArt plugin;

    public HeadCommand(ChatArt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        UUID targetUuid;
        String targetName;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Usage: /head <player>", NamedTextColor.RED));
                return true;
            }
            targetUuid = p.getUniqueId();
            targetName = p.getName();
        } else {
            Player online = Bukkit.getPlayer(args[0]);
            if (online != null) {
                targetUuid = online.getUniqueId();
                targetName = online.getName();
            } else {
                @SuppressWarnings("deprecation")
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
                if (!offline.hasPlayedBefore()) {
                    sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
                    return true;
                }
                targetUuid = offline.getUniqueId();
                targetName = offline.getName() != null ? offline.getName() : args[0];
            }
        }

        sender.sendMessage(Component.text("Fetching head for " + targetName + "...", NamedTextColor.GRAY));

        plugin.getSkinFetcher().getSkin(targetUuid).thenAcceptAsync(skin -> {
            if (skin == null) {
                sender.sendMessage(Component.text("Could not fetch skin for " + targetName + ".", NamedTextColor.RED));
                return;
            }

            // /head always shows in braille (2-row) for best quality
            HeadRenderer.Mode mode = HeadRenderer.Mode.of(
                plugin.getConfig().getString("render-mode", "mini")
            );
            // Upgrade mini to braille for the command so it shows more detail
            if (mode == HeadRenderer.Mode.MINI) mode = HeadRenderer.Mode.BRAILLE;

            sender.sendMessage(Component.text("--- " + targetName + "'s Head ---", NamedTextColor.YELLOW));
            HeadRenderer.renderRows(skin, mode).forEach(sender::sendMessage);
            sender.sendMessage(Component.text("---------------------------", NamedTextColor.YELLOW));

        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));

        return true;
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
