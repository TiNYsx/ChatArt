package com.tinysx.chatart.skin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a Minecraft skin texture into Adventure text components
 * using colored full-block characters (█), one per pixel.
 *
 * The face region in a standard 64×64 skin is:
 *   Base layer:    x=8..15, y=8..15
 *   Overlay layer: x=40..47, y=8..15
 *
 * No resource pack needed — this uses Unicode + hex colors that are
 * natively supported by Minecraft's chat renderer since 1.16.
 */
public class HeadRenderer {

    // U+2588 FULL BLOCK — renders as a solid square in Minecraft's default font
    private static final String PIXEL = "█";

    private static final int FACE_X      = 8;
    private static final int FACE_Y      = 8;
    private static final int OVERLAY_X   = 40;
    private static final int OVERLAY_Y   = 8;
    private static final int FACE_SIZE   = 8;

    /**
     * Renders the 8×8 face as a list of 8 row components.
     * Each row is a sequence of 8 colored █ characters.
     */
    public static List<Component> renderRows(BufferedImage skin) {
        List<Component> rows = new ArrayList<>(FACE_SIZE);
        for (int y = 0; y < FACE_SIZE; y++) {
            TextComponent.Builder row = Component.text();
            for (int x = 0; x < FACE_SIZE; x++) {
                int argb = blendPixel(
                        skin.getRGB(FACE_X + x, FACE_Y + y),
                        skin.getRGB(OVERLAY_X + x, OVERLAY_Y + y)
                );
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                row.append(Component.text(PIXEL, TextColor.color(r, g, b)));
            }
            rows.add(row.build());
        }
        return rows;
    }

    /**
     * Renders the face as a single component with newlines between rows.
     * Suitable for use in hover text (HoverEvent.showText).
     */
    public static Component renderHover(BufferedImage skin) {
        List<Component> rows = renderRows(skin);
        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < rows.size(); i++) {
            builder.append(rows.get(i));
            if (i < rows.size() - 1) {
                builder.append(Component.newline());
            }
        }
        return builder.build();
    }

    /**
     * Alpha-blends the overlay pixel on top of the base pixel.
     * If the overlay is fully transparent, returns the base color.
     */
    private static int blendPixel(int base, int overlay) {
        int alpha = (overlay >> 24) & 0xFF;
        if (alpha == 0)   return base;
        if (alpha == 255) return overlay;

        float a = alpha / 255.0f;

        int br = (base >> 16) & 0xFF;
        int bg = (base >>  8) & 0xFF;
        int bb =  base        & 0xFF;
        int or = (overlay >> 16) & 0xFF;
        int og = (overlay >>  8) & 0xFF;
        int ob =  overlay        & 0xFF;

        int r = Math.round(or * a + br * (1 - a));
        int g = Math.round(og * a + bg * (1 - a));
        int b = Math.round(ob * a + bb * (1 - a));

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
