package com.tinysx.chatart.skin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders images as Adventure text components using solid block characters (█)
 * with per-character hex colours and matching {@code ShadowColor} so the shadow
 * fills the 1px inter-character gap, producing a seamless pixel grid — the same
 * technique used by Pictogram.
 */
public class HeadRenderer {

    private static final int FACE_X    = 8;
    private static final int FACE_Y    = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;
    private static final int FACE_SIZE = 8;

    private static final String BLOCK = "█"; // U+2588 FULL BLOCK

    public enum Mode {
        MINI, BRAILLE, FULL;

        public static Mode of(String name) {
            return switch (name.toLowerCase()) {
                case "mini" -> MINI;
                case "full" -> FULL;
                default     -> BRAILLE;
            };
        }
    }

    // ── Public API — Skin Heads ─────────────────────────────────────────────

    /**
     * Render a Minecraft skin face as rows of solid-block text.
     *
     * @param charsWide  output width in characters
     * @param rowsTall   output height in text rows (BRAILLE mode; MINI=1, FULL=8)
     */
    public static List<Component> renderRows(BufferedImage skin, Mode mode,
                                             int charsWide, int rowsTall) {
        int[][] face = extractFace(skin);
        return switch (mode) {
            case BRAILLE -> renderSolidGrid(face, charsWide, rowsTall);
            case MINI    -> renderSolidGrid(face, charsWide, 1);
            case FULL    -> renderSolidGrid(face, FACE_SIZE, FACE_SIZE);
        };
    }

    /** Convenience for hover tooltips (joins rows with newlines). */
    public static Component renderHover(BufferedImage skin, Mode mode,
                                        int charsWide, int rowsTall) {
        List<Component> rows = renderRows(skin, mode, charsWide, rowsTall);
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < rows.size(); i++) {
            b.append(rows.get(i));
            if (i < rows.size() - 1) b.append(Component.newline());
        }
        return b.build();
    }

    // ── Public API — Arbitrary Images ───────────────────────────────────────

    /**
     * Render an arbitrary image (e.g. custom emoji) as rows of solid-block text.
     */
    public static List<Component> renderImage(BufferedImage image,
                                              int charsWide, int rowsTall) {
        int[][] pixels = imageToPixels(image);
        return renderSolidGrid(pixels, charsWide, rowsTall);
    }

    /** Render as a single inline row — for chat emoji. */
    public static Component renderImageInline(BufferedImage image, int charsWide) {
        List<Component> rows = renderImage(image, charsWide, 1);
        return rows.isEmpty() ? Component.empty() : rows.get(0);
    }

    // ── Solid block grid renderer ──────────────────────────────────────────

    private static List<Component> renderSolidGrid(int[][] face,
                                                   int charsWide, int rowsTall) {
        int[][] scaled = scaleNearest(face, charsWide, rowsTall);
        List<Component> rows = new ArrayList<>(rowsTall);
        for (int y = 0; y < rowsTall; y++) {
            TextComponent.Builder row = Component.text();
            for (int x = 0; x < charsWide; x++) {
                row.append(Component.text(BLOCK, toSolidStyle(scaled[x][y])));
            }
            rows.add(row.build());
        }
        return rows;
    }

    // ── Style helper — text color + matching shadow ────────────────────────

    private static Style toSolidStyle(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        TextColor color = TextColor.color(r, g, b);
        // Shadow matches text colour → fills the 1px gap between characters
        ShadowColor shadow = ShadowColor.shadowColor((0xFF << 24) | (r << 16) | (g << 8) | b);
        return Style.style(color, shadow);
    }

    // ── Pixel extraction ───────────────────────────────────────────────────

    private static int[][] extractFace(BufferedImage skin) {
        int[][] face = new int[FACE_SIZE][FACE_SIZE];
        for (int x = 0; x < FACE_SIZE; x++) {
            for (int y = 0; y < FACE_SIZE; y++) {
                face[x][y] = blendPixel(
                    skin.getRGB(FACE_X + x, FACE_Y + y),
                    skin.getRGB(OVERLAY_X + x, OVERLAY_Y + y)
                );
            }
        }
        return face;
    }

    private static int[][] imageToPixels(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[][] pixels = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                pixels[x][y] = image.getRGB(x, y);
            }
        }
        return pixels;
    }

    // ── Scaling ────────────────────────────────────────────────────────────

    private static int[][] scaleNearest(int[][] src, int dstW, int dstH) {
        int srcW = src.length;
        int srcH = src[0].length;
        int[][] dst = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++) {
            for (int y = 0; y < dstH; y++) {
                dst[x][y] = src[x * srcW / dstW][y * srcH / dstH];
            }
        }
        return dst;
    }

    // ── Pixel helpers ──────────────────────────────────────────────────────

    private static int blendPixel(int base, int overlay) {
        int alpha = (overlay >> 24) & 0xFF;
        if (alpha == 0)   return base;
        if (alpha == 255) return overlay;
        float a = alpha / 255.0f;
        int r = Math.round(((overlay >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((overlay >>  8) & 0xFF) * a + ((base >>  8) & 0xFF) * (1 - a));
        int b = Math.round(( overlay        & 0xFF) * a + ( base        & 0xFF) * (1 - a));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
