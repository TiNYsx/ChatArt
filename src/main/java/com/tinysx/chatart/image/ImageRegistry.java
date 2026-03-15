package com.tinysx.chatart.image;

import com.tinysx.chatart.skin.HeadRenderer;
import net.kyori.adventure.text.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Loads PNG / JPG images from the {@code plugins/ChatArt/images/} folder and
 * pre-renders them as Adventure text components for use as custom emoji in chat.
 *
 * Each image is stored as:
 *   - An inline (1-row) component for chat replacement  (:name:)
 *   - A full (multi-row) component list for /chatart preview
 */
public class ImageRegistry {

    private final File imagesDir;
    private final Logger logger;
    private int emojiWidth;
    private int previewWidth;
    private int previewHeight;

    // name → inline 1-row component (for chat emoji)
    private final ConcurrentHashMap<String, Component> inlineMap = new ConcurrentHashMap<>();
    // name → multi-row component list (for /chatart preview)
    private final ConcurrentHashMap<String, List<Component>> previewMap = new ConcurrentHashMap<>();

    public ImageRegistry(File imagesDir, Logger logger,
                         int emojiWidth, int previewWidth, int previewHeight) {
        this.imagesDir = imagesDir;
        this.logger = logger;
        this.emojiWidth = emojiWidth;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    /** Load or reload all images from disk. */
    public void loadAll() {
        inlineMap.clear();
        previewMap.clear();

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
                inlineMap.put(name, HeadRenderer.renderImageInline(image, emojiWidth));
                previewMap.put(name, HeadRenderer.renderImage(image, previewWidth, previewHeight));
                logger.info("Loaded emoji: :" + name + ": from " + file.getName());
            } catch (IOException e) {
                logger.warning("Failed to load image " + file.getName() + ": " + e.getMessage());
            }
        }

        logger.info("Loaded " + inlineMap.size() + " custom emoji image(s).");
    }

    /** Update rendering config and reload. */
    public void reload(int emojiWidth, int previewWidth, int previewHeight) {
        this.emojiWidth = emojiWidth;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        loadAll();
    }

    /** Get inline (1-row) emoji by name. */
    public Component getInline(String name) {
        return inlineMap.get(name.toLowerCase());
    }

    /** Get full preview (multi-row) by name. */
    public List<Component> getPreview(String name) {
        return previewMap.get(name.toLowerCase());
    }

    /** All registered emoji names (sorted). */
    public List<String> getNames() {
        List<String> names = new ArrayList<>(inlineMap.keySet());
        Collections.sort(names);
        return names;
    }

    public boolean has(String name) {
        return inlineMap.containsKey(name.toLowerCase());
    }
}
