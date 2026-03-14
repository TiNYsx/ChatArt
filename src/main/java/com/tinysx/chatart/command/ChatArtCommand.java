package com.tinysx.chatart.command;

import com.tinysx.chatart.ChatArt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /chatart <reload>
 */
public class ChatArtCommand implements CommandExecutor {

    private final ChatArt plugin;

    public ChatArtCommand(ChatArt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(Component.text("ChatArt config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /chatart reload", NamedTextColor.RED));
        return true;
    }
}
