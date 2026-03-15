package com.tinysx.chatart.mineskin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Caches MineSkin texture values on disk to avoid re-uploading identical tiles.
 * Key = SHA-256 hash of tile pixel data.
 */
public class TextureCache {

    private final File cacheDir;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<String, MineSkinService.TextureResult> memory = new ConcurrentHashMap<>();

    public TextureCache(File cacheDir, Logger logger) {
        this.cacheDir = cacheDir;
        this.logger = logger;
        cacheDir.mkdirs();
        loadAll();
    }

    /** Get cached texture for a tile, or null if not cached. */
    public MineSkinService.TextureResult get(BufferedImage tile) {
        return memory.get(hash(tile));
    }

    /** Store a texture result for a tile. */
    public void put(BufferedImage tile, MineSkinService.TextureResult result) {
        String key = hash(tile);
        memory.put(key, result);
        saveToDisk(key, result);
    }

    /** Clear all cached data. */
    public void clear() {
        memory.clear();
        File[] files = cacheDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    private void loadAll() {
        File[] files = cacheDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                String key = file.getName().replace(".json", "");
                String value = json.get("value").getAsString();
                String signature = json.get("signature").getAsString();
                memory.put(key, new MineSkinService.TextureResult(value, signature));
            } catch (Exception e) {
                logger.warning("Failed to load cache file " + file.getName() + ": " + e.getMessage());
            }
        }
        logger.info("Loaded " + memory.size() + " cached textures.");
    }

    private void saveToDisk(String key, MineSkinService.TextureResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("value", result.value());
        json.addProperty("signature", result.signature());
        File file = new File(cacheDir, key + ".json");
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            logger.warning("Failed to save cache file: " + e.getMessage());
        }
    }

    /** SHA-256 hash of the tile's ARGB pixel data. */
    private String hash(BufferedImage image) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            int w = image.getWidth(), h = image.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = image.getRGB(x, y);
                    md.update((byte) (argb >> 24));
                    md.update((byte) (argb >> 16));
                    md.update((byte) (argb >> 8));
                    md.update((byte) argb);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
