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
 * Converts a Minecraft skin texture into Adventure text components.
 *
 * Three rendering modes (set via config.yml render-mode):
 *
 *   MINI    — 1 row × 8 chars  — default, emoji-like
 *             Each █ = average color of one 1×8 face column.
 *
 *   BRAILLE — 2 rows × 4 chars — like Pictogram's MOTD rendering
 *             Each ⣿ = average color of a 2×4 pixel cell.
 *             Braille dot pattern encodes luminance detail within the cell.
 *
 *   FULL    — 8 rows × 8 chars — original, one █ per pixel (large).
 *
 * Face region in a standard 64×64 skin:
 *   Base layer:    x = 8..15,  y = 8..15
 *   Overlay layer: x = 40..47, y = 8..15
 */
public class HeadRenderer {

    private static final int FACE_X    = 8;
    private static final int FACE_Y    = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;
    private static final int FACE_SIZE = 8;

    // ── Braille bit map ───────────────────────────────────────────────────────
    // Braille U+2800 + pattern.  Dot positions in a 2-col × 4-row cell:
    //   col 0, row 0 → dot 1 → bit 0x01
    //   col 0, row 1 → dot 2 → bit 0x02
    //   col 0, row 2 → dot 3 → bit 0x04
    //   col 1, row 0 → dot 4 → bit 0x08
    //   col 1, row 1 → dot 5 → bit 0x10
    //   col 1, row 2 → dot 6 → bit 0x20
    //   col 0, row 3 → dot 7 → bit 0x40
    //   col 1, row 3 → dot 8 → bit 0x80
    //
    // Index layout in our pixel arrays: [row * 2 + col]
    private static final int[] BRAILLE_BITS = {
        0x01, 0x08,  // row 0: col0, col1
        0x02, 0x10,  // row 1
        0x04, 0x20,  // row 2
        0x40, 0x80   // row 3
    };

    public enum Mode {
        MINI, BRAILLE, FULL;

        public static Mode of(String name) {
            return switch (name.toLowerCase()) {
                case "braille" -> BRAILLE;
                case "full"    -> FULL;
                default        -> MINI;
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
    // Each █ represents one column of the face with the column's average color.

    private static List<Component> renderMini(int[][] face) {
        TextComponent.Builder row = Component.text();
        for (int x = 0; x < FACE_SIZE; x++) {
            int[] column = new int[FACE_SIZE];
            for (int y = 0; y < FACE_SIZE; y++) column[y] = face[x][y];
            row.append(Component.text("█", toStyle(avgColor(column))));
        }
        return List.of(row.build());
    }

    // ── BRAILLE — 2 rows × 4 chars ────────────────────────────────────────────
    // Each cell covers 2 px wide × 4 px tall.  The Braille dot pattern encodes
    // luminance contrast within the cell; foreground = average cell color.

    private static List<Component> renderBraille(int[][] face) {
        int cellsX = FACE_SIZE / 2; // 4
        int cellsY = FACE_SIZE / 4; // 2

        List<Component> rows = new ArrayList<>(cellsY);
        for (int cy = 0; cy < cellsY; cy++) {
            TextComponent.Builder row = Component.text();
            for (int cx = 0; cx < cellsX; cx++) {
                // Collect the 8 pixels of this 2×4 cell in [row*2+col] order
                int[] cell = new int[8];
                for (int row2 = 0; row2 < 4; row2++) {
                    for (int col = 0; col < 2; col++) {
                        cell[row2 * 2 + col] = face[cx * 2 + col][cy * 4 + row2];
                    }
                }
                char ch = buildBrailleChar(cell);
                row.append(Component.text(String.valueOf(ch), toStyle(avgColor(cell))));
            }
            rows.add(row.build());
        }
        return rows;
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

    // ── Braille helpers ───────────────────────────────────────────────────────

    /**
     * Builds a Braille character from 8 ARGB pixels (indexed [row*2+col]).
     * Pixels darker than the cell average become filled dots.
     * Falls back to U+28FF (all dots) when the pattern would be empty.
     */
    private static char buildBrailleChar(int[] cell) {
        int avgBright = 0;
        for (int c : cell) avgBright += luma(c);
        avgBright /= cell.length;

        int pattern = 0;
        for (int i = 0; i < cell.length; i++) {
            if (luma(cell[i]) < avgBright) pattern |= BRAILLE_BITS[i];
        }
        // If nothing would be drawn (uniform cell), render as full block
        return pattern == 0 ? '\u28FF' : (char) (0x2800 + pattern);
    }

    // ── Pixel helpers ─────────────────────────────────────────────────────────

    /** Extracts and alpha-blends the 8×8 face into a [x][y] ARGB array. */
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
        // BT.601 perceived brightness
        return ((argb >> 16) & 0xFF) * 299 / 1000
             + ((argb >>  8) & 0xFF) * 587 / 1000
             + ( argb        & 0xFF) * 114 / 1000;
    }

    // ShadowColor.none() disables Minecraft's default 1-pixel drop shadow.
    // That shadow is what makes adjacent colored characters appear separated —
    // the dark shadow bleeds past the character edge and creates a visible gap.
    // Pictogram uses the same technique: explicitly set shadow to none.
    private static Style toStyle(int argb) {
        return Style.style(
            TextColor.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF),
            ShadowColor.none()
        );
    }
}
