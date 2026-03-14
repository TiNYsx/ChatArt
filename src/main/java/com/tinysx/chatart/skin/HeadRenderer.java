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
 * Unicode characters (U+2800–U+28FF) with per-character hex colours and
 * {@code ShadowColor.none()} (same technique as Pictogram).
 *
 * The caller specifies the output dimensions in <em>characters wide</em> and
 * <em>rows tall</em>. Internally the face is scaled up via nearest-neighbour
 * to ({@code charsWide × 2}) × ({@code rowsTall × 4}) pixels, then the
 * Braille engine converts each 2×4 pixel cell into one character.
 *
 * Modes:
 *   BRAILLE — charsWide × rowsTall  (configurable, default 16×4)
 *   MINI    — charsWide × 1 row     (squishes face height into 4 pixels)
 *   FULL    — raw 8×8 using █ blocks, ignores charsWide/rowsTall
 */
public class HeadRenderer {

    private static final int FACE_X    = 8;
    private static final int FACE_Y    = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;
    private static final int FACE_SIZE = 8;

    // Braille dot-bit mapping.  Index = row*2 + col within the 2×4 cell.
    private static final int[] BRAILLE_BITS = {
        0x01, 0x08,   // row 0
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
                default     -> BRAILLE;
            };
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * @param charsWide  output width in Braille characters (used by BRAILLE & MINI)
     * @param rowsTall   output height in text rows (used by BRAILLE only; MINI is always 1)
     */
    public static List<Component> renderRows(BufferedImage skin, Mode mode,
                                             int charsWide, int rowsTall) {
        int[][] face = extractFace(skin);
        return switch (mode) {
            case BRAILLE -> renderBraille(face, charsWide, rowsTall);
            case MINI    -> renderMini(face, charsWide);
            case FULL    -> renderFull(face);
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

    // ── BRAILLE — charsWide × rowsTall ────────────────────────────────────────

    private static List<Component> renderBraille(int[][] face, int charsWide, int rowsTall) {
        int pxW = charsWide * 2;   // each Braille char = 2 pixels wide
        int pxH = rowsTall  * 4;   // each Braille char = 4 pixels tall
        int[][] scaled = scaleNearest(face, pxW, pxH);
        return renderBrailleGrid(scaled, pxW, pxH);
    }

    // ── MINI — charsWide × 1 row ──────────────────────────────────────────────

    private static List<Component> renderMini(int[][] face, int charsWide) {
        int pxW = charsWide * 2;
        int pxH = 4;   // always 1 Braille row = 4 pixels tall
        int[][] scaled = scaleNearest(face, pxW, pxH);
        return renderBrailleGrid(scaled, pxW, pxH);
    }

    // ── FULL — raw 8×8 blocks ─────────────────────────────────────────────────

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

    private static List<Component> renderBrailleGrid(int[][] px, int width, int height) {
        int cellsX = width  / 2;
        int cellsY = height / 4;
        List<Component> rows = new ArrayList<>(cellsY);
        for (int cy = 0; cy < cellsY; cy++) {
            TextComponent.Builder row = Component.text();
            for (int cx = 0; cx < cellsX; cx++) {
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

    private static int[][] scaleNearest(int[][] face, int dstW, int dstH) {
        int srcW = face.length;
        int srcH = face[0].length;
        int[][] dst = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++) {
            for (int y = 0; y < dstH; y++) {
                dst[x][y] = face[x * srcW / dstW][y * srcH / dstH];
            }
        }
        return dst;
    }

    // ── Braille helpers ───────────────────────────────────────────────────────

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

    private static int luma(int argb) {
        return ((argb >> 16) & 0xFF) * 299 / 1000
             + ((argb >>  8) & 0xFF) * 587 / 1000
             + ( argb        & 0xFF) * 114 / 1000;
    }

    private static Style toStyle(int argb) {
        return Style.style(
            TextColor.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF),
            ShadowColor.none()
        );
    }
}
