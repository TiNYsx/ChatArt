package com.tinysx.chatart.skin;

import com.tinysx.chatart.mineskin.MineSkinService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders images as Adventure text components.
 *
 * Two rendering modes:
 *   - ObjectComponent (Pictogram-style): each tile is a tiny player head icon — seamless and compact
 *   - Solid block fallback: uses █ with matching ShadowColor for when MineSkin is not configured
 */
public class HeadRenderer {

    private static final int FACE_X    = 8;
    private static final int FACE_Y    = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;
    private static final int FACE_SIZE = 8;

    private static final String BLOCK = "\u2588"; // █ FULL BLOCK

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

    // ── ObjectComponent API (Pictogram-style) ──────────────────────────────

    /**
     * Create a single inline head icon from a texture value.
     * This renders as a tiny, seamless player head icon in chat/MOTD.
     */
    public static Component createHeadIcon(String textureValue) {
        return Component.object(
            ObjectContents.playerHead()
                .profileProperty(PlayerHeadObjectContents.property("textures", textureValue))
                .build()
        );
    }

    /**
     * Arrange a list of texture results into rows of head icons.
     *
     * @param textures  list of texture results in row-major order
     * @param cols      number of head icons per row
     */
    public static List<Component> renderAsHeadGrid(List<MineSkinService.TextureResult> textures, int cols) {
        List<Component> rows = new ArrayList<>();
        TextComponent.Builder currentRow = Component.text();
        for (int i = 0; i < textures.size(); i++) {
            MineSkinService.TextureResult tex = textures.get(i);
            if (tex != null) {
                currentRow.append(createHeadIcon(tex.value()));
            }
            if ((i + 1) % cols == 0 || i == textures.size() - 1) {
                rows.add(currentRow.build());
                currentRow = Component.text();
            }
        }
        return rows;
    }

    /**
     * Compose a head icon grid into a single component with newlines.
     */
    public static Component renderAsHeadComponent(List<MineSkinService.TextureResult> textures, int cols) {
        List<Component> rows = renderAsHeadGrid(textures, cols);
        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < rows.size(); i++) {
            builder.append(rows.get(i));
            if (i < rows.size() - 1) builder.append(Component.newline());
        }
        return builder.build();
    }

    // ── Solid Block Fallback API ───────────────────────────────────────────

    /**
     * Render a Minecraft skin face as solid block text (fallback mode).
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

    /** Hover tooltip (joins rows with newlines). */
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

    /** Render an arbitrary image as solid block text (fallback for emoji). */
    public static List<Component> renderImage(BufferedImage image,
                                              int charsWide, int rowsTall) {
        int[][] pixels = imageToPixels(image);
        return renderSolidGrid(pixels, charsWide, rowsTall);
    }

    /** Single-row inline render (fallback emoji). */
    public static Component renderImageInline(BufferedImage image, int charsWide) {
        List<Component> rows = renderImage(image, charsWide, 1);
        return rows.isEmpty() ? Component.empty() : rows.get(0);
    }

    // ── Solid block grid ───────────────────────────────────────────────────

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

    private static Style toSolidStyle(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        TextColor color = TextColor.color(r, g, b);
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
        int w = image.getWidth(), h = image.getHeight();
        int[][] pixels = new int[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                pixels[x][y] = image.getRGB(x, y);
        return pixels;
    }

    private static int[][] scaleNearest(int[][] src, int dstW, int dstH) {
        int srcW = src.length, srcH = src[0].length;
        int[][] dst = new int[dstW][dstH];
        for (int x = 0; x < dstW; x++)
            for (int y = 0; y < dstH; y++)
                dst[x][y] = src[x * srcW / dstW][y * srcH / dstH];
        return dst;
    }

    private static int blendPixel(int base, int overlay) {
        int alpha = (overlay >> 24) & 0xFF;
        if (alpha == 0) return base;
        if (alpha == 255) return overlay;
        float a = alpha / 255.0f;
        int r = Math.round(((overlay >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((overlay >>  8) & 0xFF) * a + ((base >>  8) & 0xFF) * (1 - a));
        int b = Math.round(( overlay        & 0xFF) * a + ( base        & 0xFF) * (1 - a));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
