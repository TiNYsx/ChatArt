package com.tinysx.chatart.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tinysx.chatart.ChatArt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads and caches player skin textures from the Mojang API.
 * Skins are stored as PNG files in the plugin's skin-cache directory.
 */
public class SkinFetcher {

    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final ChatArt plugin;
    private final File cacheDir;
    private final HttpClient httpClient;

    // Tracks when each skin was last fetched (UUID → timestamp ms)
    private final Map<UUID, Long> fetchTimestamps = new ConcurrentHashMap<>();

    public SkinFetcher(ChatArt plugin, File cacheDir) {
        this.plugin = plugin;
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Returns the skin texture for the given UUID, using cache when available.
     * Runs on a virtual thread via CompletableFuture.
     */
    public CompletableFuture<BufferedImage> getSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File cachedFile = new File(cacheDir, uuid + ".png");
                long now = System.currentTimeMillis();
                long cacheMs = plugin.getConfig().getInt("skin-cache-minutes", 60) * 60_000L;

                // Serve from disk cache if still fresh
                if (cachedFile.exists()) {
                    Long lastFetch = fetchTimestamps.get(uuid);
                    if (lastFetch != null && (now - lastFetch) < cacheMs) {
                        return ImageIO.read(cachedFile);
                    }
                }

                // Fetch Mojang profile to get skin URL
                String profileJson = get(PROFILE_URL + uuid.toString().replace("-", ""));
                if (profileJson == null) return null;

                JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
                String textureBase64 = profile.getAsJsonArray("properties")
                        .get(0).getAsJsonObject()
                        .get("value").getAsString();

                String decoded = new String(Base64.getDecoder().decode(textureBase64));
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
                String skinUrl = textures.getAsJsonObject("textures")
                        .getAsJsonObject("SKIN")
                        .get("url").getAsString();

                // Download the skin PNG
                HttpRequest skinReq = HttpRequest.newBuilder()
                        .uri(URI.create(skinUrl))
                        .GET()
                        .build();
                HttpResponse<InputStream> skinResp = httpClient.send(skinReq, HttpResponse.BodyHandlers.ofInputStream());
                if (skinResp.statusCode() != 200) return null;

                BufferedImage skin = ImageIO.read(skinResp.body());
                if (skin == null) return null;

                // Cache to disk
                ImageIO.write(skin, "PNG", cachedFile);
                fetchTimestamps.put(uuid, now);

                return skin;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch skin for " + uuid + ": " + e.getMessage());
                return null;
            }
        });
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200 ? resp.body() : null;
    }
}
