package com.tinysx.chatart.image;

import com.tinysx.chatart.mineskin.MineSkinService;
import com.tinysx.chatart.mineskin.TextureCache;
import com.tinysx.chatart.skin.HeadRenderer;
import net.kyori.adventure.text.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Loads images from plugins/ChatArt/images/ and renders them as:
 *   - ObjectComponent head icon grids (when MineSkin is configured)
 *   - Solid block text (fallback)
 *
 * Players use :name: in chat to insert inline emoji.
 */
public class ImageRegistry {

    private final File imagesDir;
    private final Logger logger;
    private final MineSkinService mineSkin;
    private final TextureCache textureCache;

    // Fallback rendering dimensions
    private int fallbackWidth;
    private int fallbackPreviewWidth;
    private int fallbackPreviewHeight;

    // name → inline component (single row for chat)
    private final ConcurrentHashMap<String, Component> inlineMap = new ConcurrentHashMap<>();
    // name → multi-row component list (for /chatart preview and MOTD)
    private final ConcurrentHashMap<String, List<Component>> previewMap = new ConcurrentHashMap<>();
    // name → texture results (for MOTD usage)
    private final ConcurrentHashMap<String, ImageData> imageDataMap = new ConcurrentHashMap<>();

    public ImageRegistry(File imagesDir, Logger logger, MineSkinService mineSkin,
                         TextureCache textureCache,
                         int fallbackWidth, int fallbackPreviewWidth, int fallbackPreviewHeight) {
        this.imagesDir = imagesDir;
        this.logger = logger;
        this.mineSkin = mineSkin;
        this.textureCache = textureCache;
        this.fallbackWidth = fallbackWidth;
        this.fallbackPreviewWidth = fallbackPreviewWidth;
        this.fallbackPreviewHeight = fallbackPreviewHeight;
    }

    /** Load all images. If MineSkin is configured, upload tiles asynchronously. */
    public void loadAll() {
        inlineMap.clear();
        previewMap.clear();
        imageDataMap.clear();

        if (!imagesDir.isDirectory()) return;

        File[] files = imagesDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (files == null) return;

        for (File file : files) {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    logger.warning("Could not read image: " + file.getName());
                    continue;
                }
                String name = file.getName().replaceFirst("\\.[^.]+$", "").toLowerCase();

                if (mineSkin.isConfigured()) {
                    loadWithMineSkin(name, image);
                } else {
                    loadFallback(name, image);
                }
            } catch (IOException e) {
                logger.warning("Failed to load image " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Upload tiles to MineSkin and create ObjectComponent grids. */
    private void loadWithMineSkin(String name, BufferedImage image) {
        List<BufferedImage> tiles = ImageSlicer.slice(image);
        int cols = ImageSlicer.cols(image);

        logger.info("Processing :" + name + ": — " + tiles.size() + " tiles (" + cols + " cols)...");

        // Upload all tiles (check cache first)
        List<CompletableFuture<MineSkinService.TextureResult>> futures = new ArrayList<>();
        for (BufferedImage tile : tiles) {
            MineSkinService.TextureResult cached = textureCache.get(tile);
            if (cached != null) {
                futures.add(CompletableFuture.completedFuture(cached));
            } else {
                futures.add(mineSkin.uploadTile(tile).thenApply(result -> {
                    if (result != null) textureCache.put(tile, result);
                    return result;
                }));
            }
        }

        // When all tiles are uploaded, build the components
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                List<MineSkinService.TextureResult> results = new ArrayList<>();
                boolean allSuccess = true;
                for (var f : futures) {
                    MineSkinService.TextureResult r = f.join();
                    if (r == null) allSuccess = false;
                    results.add(r);
                }

                if (!allSuccess) {
                    logger.warning("Some tiles failed for :" + name + ":, falling back to solid blocks.");
                    loadFallback(name, image);
                    return;
                }

                // Build head icon grid
                List<Component> rows = HeadRenderer.renderAsHeadGrid(results, cols);
                previewMap.put(name, rows);

                // Inline: first row only (or single component if 1 row)
                if (!rows.isEmpty()) {
                    inlineMap.put(name, rows.get(0));
                }

                imageDataMap.put(name, new ImageData(results, cols));
                logger.info("Loaded emoji :" + name + ": with " + results.size() + " head icons.");
            })
            .exceptionally(ex -> {
                logger.warning("Failed to process :" + name + ":: " + ex.getMessage());
                loadFallback(name, image);
                return null;
            });
    }

    /** Fallback: render with solid blocks when MineSkin is not available. */
    private void loadFallback(String name, BufferedImage image) {
        inlineMap.put(name, HeadRenderer.renderImageInline(image, fallbackWidth));
        previewMap.put(name, HeadRenderer.renderImage(image, fallbackPreviewWidth, fallbackPreviewHeight));
        logger.info("Loaded emoji :" + name + ": (solid block fallback).");
    }

    /** Reload with new config values. */
    public void reload(int fallbackWidth, int fallbackPreviewWidth, int fallbackPreviewHeight) {
        this.fallbackWidth = fallbackWidth;
        this.fallbackPreviewWidth = fallbackPreviewWidth;
        this.fallbackPreviewHeight = fallbackPreviewHeight;
        loadAll();
    }

    public Component getInline(String name) { return inlineMap.get(name.toLowerCase()); }
    public List<Component> getPreview(String name) { return previewMap.get(name.toLowerCase()); }
    public ImageData getImageData(String name) { return imageDataMap.get(name.toLowerCase()); }
    public boolean has(String name) { return inlineMap.containsKey(name.toLowerCase()); }

    public List<String> getNames() {
        List<String> names = new ArrayList<>(inlineMap.keySet());
        Collections.sort(names);
        return names;
    }

    /** Stores texture results and grid info for MOTD usage. */
    public record ImageData(List<MineSkinService.TextureResult> textures, int cols) {}
}
