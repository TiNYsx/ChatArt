package com.tinysx.chatart.mineskin;

import org.mineskin.MineSkinClient;
import org.mineskin.request.GenerateRequest;
import org.mineskin.response.GenerateResponse;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Wraps the MineSkin Java client for uploading custom skin textures.
 * Each tile is placed into a 64×64 skin template at the face position (8,8).
 */
public class MineSkinService {

    private static final int SKIN_SIZE = 64;
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;

    private final Logger logger;
    private MineSkinClient client;
    private boolean configured;

    public MineSkinService(Logger logger) {
        this.logger = logger;
    }

    public void init(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            configured = false;
            logger.info("MineSkin API key not set — custom emoji and MOTD images disabled.");
            return;
        }
        try {
            client = MineSkinClient.builder()
                .apiKey(apiKey)
                .userAgent("ChatArt/1.0")
                .requestHandler(org.mineskin.Java11RequestHandler::new)
                .build();
            configured = true;
            logger.info("MineSkin client initialized.");
        } catch (Exception e) {
            configured = false;
            logger.warning("Failed to init MineSkin client: " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return configured && client != null;
    }

    /**
     * Upload an 8×8 tile as a skin texture to MineSkin.
     * The tile is placed at the face area (8,8) of a 64×64 skin template.
     *
     * @return CompletableFuture with texture value and signature
     */
    public CompletableFuture<TextureResult> uploadTile(BufferedImage tile) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }

        BufferedImage skin = createSkinTemplate(tile);

        return client.generate()
            .submitAndWait(GenerateRequest.upload(skin))
            .thenApply(response -> {
                var data = response.getSkin().texture().data();
                return new TextureResult(data.value(), data.signature());
            })
            .exceptionally(ex -> {
                logger.warning("MineSkin upload failed: " + ex.getMessage());
                return null;
            });
    }

    /**
     * Creates a 64×64 skin PNG with the tile placed at the face position.
     */
    private BufferedImage createSkinTemplate(BufferedImage tile) {
        BufferedImage skin = new BufferedImage(SKIN_SIZE, SKIN_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = skin.createGraphics();

        // Fill with a base skin (transparent)
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, SKIN_SIZE, SKIN_SIZE);
        g.setComposite(AlphaComposite.SrcOver);

        // Draw the tile at the face position, scaled to 8×8 if needed
        g.drawImage(tile, FACE_X, FACE_Y, FACE_SIZE, FACE_SIZE, null);

        // Also draw at the head overlay position to ensure visibility
        g.drawImage(tile, 40, FACE_Y, FACE_SIZE, FACE_SIZE, null);

        g.dispose();
        return skin;
    }

    public record TextureResult(String value, String signature) {}
}
