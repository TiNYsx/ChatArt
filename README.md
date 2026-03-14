# ChatArt

A Paper plugin that renders **player heads in Minecraft chat** using colored Unicode block characters — **no resource pack needed**.

It uses the same technique as [Pictogram](https://github.com/willemdev/pictogram): each pixel of the player's 8×8 face becomes a `█` (full block) character colored with the pixel's exact hex color via Adventure's text component API. Minecraft has supported arbitrary hex colors in chat since 1.16, and the block character is part of the default font — so it works completely vanilla.

## Features

| Feature | Description |
|---|---|
| `/head [player]` | Prints the player's 8×8 face in chat as 8 rows of colored blocks |
| Hover preview | Hover over any player's name in chat to see their face thumbnail |
| Skin caching | Skins are cached locally so Mojang's API isn't hit every message |
| `/chatart reload` | Reload config without restarting the server |

## How it works

1. The player's UUID is used to fetch their profile from Mojang's Session Server.
2. The skin URL is extracted from the base64-encoded texture property.
3. The skin PNG is downloaded (64×64 pixels). The 8×8 face region is at `x=8..15, y=8..15` (with overlay at `x=40..47, y=8..15`).
4. Each pixel becomes a `█` character with `TextColor.color(r, g, b)` applied.
5. For hover: the 8 rows are assembled into a single component with newlines and attached as a `HoverEvent.showText` on the player's name.

No fonts, no textures, no resource packs — just colored Unicode.

## Requirements

- Paper 1.21+
- Java 21+

## Configuration

```yaml
# config.yml

# Show player's face when you hover over their name in chat
hover-head: true

# Print the full 8-row head above every chat message (verbose — off by default)
show-head-in-chat: false

# Minutes before re-downloading a player's skin from Mojang
skin-cache-minutes: 60
```

## Building

```bash
mvn clean package
```

The shaded jar will be at `target/ChatArt-1.0.jar`.

## Credits

Inspired by [Pictogram](https://github.com/willemdev/pictogram) by willemdev.
