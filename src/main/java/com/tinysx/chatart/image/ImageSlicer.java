package com.tinysx.chatart.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Slices an image into a grid of 8×8 tiles for use as player head textures.
 * Tiles are returned in row-major order (left-to-right, top-to-bottom).
 */
public class ImageSlicer {

    public static final int TILE_SIZE = 8;

    /** Slice an image into 8×8 tiles. Pads with transparent if not evenly divisible. */
    public static List<BufferedImage> slice(BufferedImage image) {
        int cols = (int) Math.ceil((double) image.getWidth() / TILE_SIZE);
        int rows = (int) Math.ceil((double) image.getHeight() / TILE_SIZE);

        // Scale to exact tile grid if needed
        int targetW = cols * TILE_SIZE;
        int targetH = rows * TILE_SIZE;

        BufferedImage padded;
        if (targetW != image.getWidth() || targetH != image.getHeight()) {
            padded = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = padded.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        } else {
            padded = image;
        }

        List<BufferedImage> tiles = new ArrayList<>(cols * rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                tiles.add(padded.getSubimage(
                    col * TILE_SIZE, row * TILE_SIZE,
                    TILE_SIZE, TILE_SIZE
                ));
            }
        }
        return tiles;
    }

    /** Number of columns in the tile grid for the given image. */
    public static int cols(BufferedImage image) {
        return (int) Math.ceil((double) image.getWidth() / TILE_SIZE);
    }

    /** Number of rows in the tile grid for the given image. */
    public static int rows(BufferedImage image) {
        return (int) Math.ceil((double) image.getHeight() / TILE_SIZE);
    }
}
