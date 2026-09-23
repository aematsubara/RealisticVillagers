package me.matsubara.realisticvillagers.appearance;

import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the colours out of a villager's skin.
 * <p>
 * The shape in the resource pack is white; what makes it hers is this list of colours, one per
 * face, lifted straight from the pixels her chest is already painted with. A patterned shirt runs
 * off the body and onto the shape because it is literally the same pixels.
 * <p>
 * Skins are fetched once and kept. There are a few hundred across a server and they never change,
 * so the cache is the difference between one download and one per villager who walks past.
 */
public final class SkinImage {

    /** Colour lists already worked out, by skin URL and shape — each shape reads its own pixels. */
    private static final Map<String, List<Color>> CACHE = new ConcurrentHashMap<>();

    /** Skins already downloading, so twenty villagers sharing one cause a single fetch. */
    private static final Map<String, CompletableFuture<BufferedImage>> PENDING = new ConcurrentHashMap<>();

    /** Skin images kept after download, so a second shape costs no second fetch. */
    private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();

    /** A skin is 64×64; anything else is either legacy or not a skin at all. */
    private static final int SIZE = 64;

    private SkinImage() {
    }

    /** The colours for this skin and shape if they are already known, otherwise {@code null}. */
    public static @Nullable List<Color> cached(@NotNull String url, @NotNull BodyShape shape) {
        return CACHE.get(key(url, shape));
    }

    private static @NotNull String key(@NotNull String url, @NotNull BodyShape shape) {
        return shape.id() + '@' + url;
    }

    /**
     * Fetches and reads a skin, off the main thread.
     * <p>
     * Handed back through a future rather than waited on: this is a network call, and a villager
     * appearing is not worth a stalled tick. The shape simply arrives a moment after she does.
     */
    public static @NotNull CompletableFuture<List<Color>> load(@NotNull String url, @NotNull BodyShape shape) {
        String key = key(url, shape);

        List<Color> done = CACHE.get(key);
        if (done != null) return CompletableFuture.completedFuture(done);

        BufferedImage have = IMAGES.get(url);
        if (have != null) {
            List<Color> colors = read(have, shape);
            if (!colors.isEmpty()) CACHE.put(key, colors);
            return CompletableFuture.completedFuture(colors);
        }

        return PENDING.computeIfAbsent(url, from -> CompletableFuture
                        .supplyAsync(() -> fetch(from))
                        .whenComplete((image, error) -> {
                            PENDING.remove(from);
                            if (image != null) IMAGES.put(from, image);
                        }))
                .thenApply(image -> {
                    if (image == null) return List.<Color>of();

                    List<Color> colors = read(image, shape);
                    if (!colors.isEmpty()) CACHE.put(key, colors);
                    return colors;
                });
    }

    /**
     * Walks the shape's cells and takes the pixel each one wears.
     * <p>
     * The walk is {@link BodyShape#cells()} in its own order, so slot <i>n</i> of this list is
     * the colour of tint <i>n</i> in the pack. Nothing records the mapping because nothing has to:
     * both sides ask the same method the same question.
     */
    private static @NotNull List<Color> read(@NotNull BufferedImage skin, @NotNull BodyShape shape) {
        List<BodyShape.Cell> cells = shape.cells();
        List<Color> colors = new ArrayList<>(cells.size());

        for (BodyShape.Cell cell : cells) {
            int[] pixel = shape.skinPixel(cell);

            int x = Math.min(Math.max(pixel[0], 0), skin.getWidth() - 1);
            int y = Math.min(Math.max(pixel[1], 0), skin.getHeight() - 1);

            int argb = skin.getRGB(x, y);

            // A fully transparent pixel means the skin leaves that spot bare; the body colour
            // underneath is the honest stand-in, and white would read as a hole.
            if (((argb >>> 24) & 0xFF) < 16) {
                colors.add(Color.fromRGB(0xC8, 0x9F, 0x7B));
                continue;
            }

            colors.add(Color.fromRGB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
        }

        return colors;
    }

    private static @Nullable BufferedImage fetch(@NotNull String url) {
        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "RealisticVillagers");

            try (InputStream stream = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) return null;

                // Legacy 64×32 skins carry the body in the same place, so they read fine as they
                // are; anything narrower than a skin is something else and is left alone.
                return image.getWidth() < SIZE ? null : image;
            }
        } catch (Throwable throwable) {
            return null;
        }
    }
}
