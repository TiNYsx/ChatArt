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
 * Renders a Minecraft skin face as Adventure text components using Braille
 * Unicode characters (U+2800–U+28FF) with per-character hex colors.
 *
 * Two key techniques (same as Pictogram):
 *  1. ShadowColor.none() — disables the 1-pixel drop shadow Minecraft adds to
 *     all chat text. That shadow bleeds past each character's edge and creates
 *     the apparent gap between adjacent colored blocks.
 *  2. Scaling before rendering — the raw 8×8 face is too few characters to
 *     look good. We scale it up with nearest-neighbor interpolation first so
 *     there are enough characters to produce a recognisable image.
 *
 * Render modes (config.yml → render-mode):
 *
 *   braille (default)
 *     Face scaled 2× to 16×16 → Braille applied → 8 chars × 4 rows.
 *     Each Braille cell covers 1 original column × 2 original rows.
 *     Good balance between compactness and quality.
 *
 *   mini
 *     Face squished to 16×4 → Braille applied → 8 chars × 1 row.
 *     Each Braille cell covers 1 original column × all 8 rows (sampled).
 *     The dot pattern encodes the column's vertical luminance structure.
 *     One chat line, recognisable palette, minimal vertical detail.
 *
 *   full
 *     Raw 8×8 face, one █ per pixel → 8 chars × 8 rows.
 *     Maximum fidelity, most vertical space.
 *
 * Face region in a standard 64×64 Minecraft skin:
 *   Base layer:    x = 8..15,  y = 8..15
 *   Overlay layer: x = 40..47, y = 8..15
 */
public class HeadRenderer {

    private static final int FACE_X    = 8;
    private static final int FACE_Y    = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;
    private static final int FACE_SIZE = 8;

    // Braille U+2800 + pattern.
    // Dots are arranged in a 2-column × 4-row grid; index = row*2 + col.
    //   col 0, row 0 → dot 1 → bit 0x01
    //   col 0, row 1 → dot 2 → bit 0x02
    //   col 0, row 2 → dot 3 → bit 0x04
    //   col 1, row 0 → dot 4 → bit 0x08
    //   col 1, row 1 → dot 5 → bit 0x10
    //   col 1, row 2 → dot 6 → bit 0x20
    //   col 0, row 3 → dot 7 → bit 0x40
    //   col 1, row 3 → dot 8 → bit 0x80
    private static final int[] BRAILLE_BITS = {
        0x01, 0x08,   // row 0 : col0, col1
        0x02, 0x10,   // row 1
        0x04, 0x20,   // row 2
        0x40, 0x80    // row 3
    };

    public enum Mode {
        MINI, BRAILLE, FULL;

        public static Mode of(String name) {
            return switch (name.toLowerCase()) {
                case "mini" -> MINI;
                case "full" -> FULL;
                default     -> BRAILLE;   // braille is the default
            };
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static List<Component> renderRows(BufferedImage skin, Mode mode) {
        int[][] face = extractFace(skin);
        return switch (mode) {
            case MINI    -> renderMini(face);
            case BRAILLE -> renderBraille(face);
            case FULL    -> renderFull(face);
        };
    }

    public static Component renderHover(BufferedImage skin, Mode mode) {
        List<Component> rows = renderRows(skin, mode);
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < rows.size(); i++) {
            b.append(rows.get(i));
            if (i < rows.size() - 1) b.append(Component.newline());
        }
        return b.build();
    }

    // ── MINI — 1 row × 8 chars ────────────────────────────────────────────────
    // Squish the 8×8 face to 16×4 (2× wide, ½ tall), then Braille.
    // Each resulting char covers exactly 1 original face column × 4 sampled rows.
    // The Braille dot pattern encodes which rows in that column are darker.

    private static List<Component> renderMini(int[][] face) {
        int[][] scaled = scaleNearest(face, 16, 4);
        return renderBrailleGrid(scaled, 16, 4);
    }

    // ── BRAILLE — 4 rows × 8 chars ────────────────────────────────────────────
    // Scale the face 2× to 16×16, then Braille.
    // Each resulting char covers 1 original column × 2 original rows.

    private static List<Component> renderBraille(int[][] face) {
        int[][] scaled = scaleNearest(face, 16, 16);
        return renderBrailleGrid(scaled, 16, 16);
    }

    // ── FULL — 8 rows × 8 chars ───────────────────────────────────────────────

    private static List<Component> renderFull(int[][] face) {
        List<Component> rows = new ArrayList<>(FACE_SIZE);
        for (int y = 0; y < FACE_SIZE; y++) {
            TextComponent.Builder row = Component.text();
            for (int x = 0; x < FACE_SIZE; x++) {
                row.append(Component.text("█", toStyle(face[x][y])));
            }
            rows.add(row.build());
        }
        return rows;
    }

    // ── Core Braille engine ───────────────────────────────────────────────────

    /**
     * Converts an arbitrary pixel grid into Braille text components.
     * {@code width} must be divisible by 2; {@code height} must be divisible by 4.
     */
    private static List<Component> renderBrailleGrid(int[][] px, int width, int height) {
        int cellsX = width  / 2;
        int cellsY = height / 4;
        List<Component> rows = new ArrayList<>(cellsY);
        for (int cy = 0; cy < cellsY; cy++) {
            TextComponent.Builder row = Component.text();
            for (int cx = 0; cx < cellsX; cx++) {
                // Gather the 8 pixels of this 2×4 cell in [row*2+col] order
                int[] cell = new int[8];
                for (int dr = 0; dr < 4; dr++) {
                    for (int dc = 0; dc < 2; dc++) {
                        cell[dr * 2 + dc] = px[cx * 2 + dc][cy * 4 + dr];
                    }
                }
                char ch = brailleChar(cell);
                row.append(Component.text(String.valueOf(ch), toStyle(avgColor(cell))));
            }
            rows.add(row.build());
        }
        return rows;
    }

    // ── Scaling ───────────────────────────────────────────────────────────────

    /** Nearest-neighbour scale from FACE_SIZE × FACE_SIZE to dstW × dstH. */
    private static int[][] scaleNearest(int[][] face, int dstW, int dstH) {
        int[][] dst = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++) {
            for (int y = 0; y < dstH; y++) {
                dst[x][y] = face[x * FACE_SIZE / dstW][y * FACE_SIZE / dstH];
            }
        }
        return dst;
    }

    // ── Braille helpers ───────────────────────────────────────────────────────

    /**
     * Builds a Braille character from 8 ARGB pixels (indexed [row*2+col]).
     * Pixels darker than the cell average become filled dots, giving the
     * character a luminance-accurate dot pattern. Falls back to U+28FF
     * (all dots) when all pixels have the same brightness.
     */
    private static char brailleChar(int[] cell) {
        int avg = 0;
        for (int c : cell) avg += luma(c);
        avg /= cell.length;

        int pattern = 0;
        for (int i = 0; i < cell.length; i++) {
            if (luma(cell[i]) < avg) pattern |= BRAILLE_BITS[i];
        }
        return pattern == 0 ? '\u28FF' : (char) (0x2800 + pattern);
    }

    // ── Pixel helpers ─────────────────────────────────────────────────────────

    /** Extracts and alpha-blends the 8×8 face into a face[x][y] ARGB array. */
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

    private static int avgColor(int[] argbs) {
        long r = 0, g = 0, b = 0;
        for (int c : argbs) { r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; }
        int n = argbs.length;
        return (0xFF << 24) | ((int)(r/n) << 16) | ((int)(g/n) << 8) | (int)(b/n);
    }

    /** BT.601 perceived brightness (0–255). */
    private static int luma(int argb) {
        return ((argb >> 16) & 0xFF) * 299 / 1000
             + ((argb >>  8) & 0xFF) * 587 / 1000
             + ( argb        & 0xFF) * 114 / 1000;
    }

    /** Color + no shadow. ShadowColor.none() is what eliminates inter-character gaps. */
    private static Style toStyle(int argb) {
        return Style.style(
            TextColor.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF),
            ShadowColor.none()
        );
    }
}
