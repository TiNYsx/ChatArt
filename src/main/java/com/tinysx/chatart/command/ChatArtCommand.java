package com.tinysx.chatart.command;

import com.tinysx.chatart.ChatArt;
import com.tinysx.chatart.image.ImageRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /chatart reload | list | preview &lt;name&gt;
 */
public class ChatArtCommand implements CommandExecutor, TabCompleter {

    private final ChatArt plugin;

    public ChatArtCommand(ChatArt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("ChatArt config & images reloaded.", NamedTextColor.GREEN));
            }
            case "list" -> {
                ImageRegistry registry = plugin.getImageRegistry();
                List<String> names = registry.getNames();
                if (names.isEmpty()) {
                    sender.sendMessage(Component.text("No custom emoji loaded. Place images in plugins/ChatArt/images/", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Available emoji (" + names.size() + "):", NamedTextColor.GOLD));
                for (String name : names) {
                    Component inline = registry.getInline(name);
                    sender.sendMessage(
                        Component.text("  :" + name + ":  ", NamedTextColor.WHITE)
                            .append(inline != null ? inline : Component.empty())
                            .append(Component.text("  [preview]", NamedTextColor.AQUA)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/chatart preview " + name)))
                    );
                }
            }
            case "preview" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /chatart preview <name>", NamedTextColor.RED));
                    return true;
                }
                String name = args[1].toLowerCase();
                ImageRegistry registry = plugin.getImageRegistry();
                List<Component> preview = registry.getPreview(name);
                if (preview == null) {
                    sender.sendMessage(Component.text("Unknown emoji: :" + name + ":", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Preview — :" + name + ":", NamedTextColor.GOLD));
                preview.forEach(sender::sendMessage);
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /chatart <reload|list|preview <name>>", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStart(List.of("reload", "list", "preview"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            return filterStart(plugin.getImageRegistry().getNames(), args[1]);
        }
        return List.of();
    }

    private List<String> filterStart(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) result.add(opt);
        }
        return result;
    }
}
