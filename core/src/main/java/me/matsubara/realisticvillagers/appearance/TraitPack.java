package me.matsubara.realisticvillagers.appearance;

import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the resource pack that carries the shapes a villager's skin is painted
 * onto.
 * <p>
 * One pack for the whole server, not one per villager: it holds geometry and
 * nothing else, every
 * face left untextured but marked with a tint index. Colour arrives later, per
 * villager, in the
 * item she wears. That is the whole reason this works — a pack cannot know
 * eight hundred skins,
 * and it does not have to.
 * <p>
 * Generated rather than shipped, so a shape is changed by changing arithmetic
 * in
 * {@link BodyShape} instead of by hand-editing a model in a modelling program.
 */
public final class TraitPack {

    public static final String NAMESPACE = "realisticvillagers";

    /**
     * A single white pixel: every face is white and the tint does the colouring.
     */
    private static final String WHITE_TEXTURE = "item/white";

    /**
     * Model space is sixteen units to a block, and a skin pixel is exactly one
     * unit.
     */
    private static final double CENTRE = 8.0d;

    /**
     * What the game shrinks a worn item to: sixteen model units come out 0.625
     * blocks across.
     * <p>
     * The game's number, not a choice. A head worn as a hat is visibly wider than
     * the head under it,
     * and that is this.
     */
    private static final double HEAD_SCALE = 0.625d;

    /**
     * One unit of a display translation, in blocks — the space the shape is
     * positioned in.
     */
    private static final double UNIT = HEAD_SCALE / 16.0d;

    /**
     * Where a worn item is drawn: the middle of the head, in blocks above her feet.
     */
    private static final double HEAD_CENTRE = 1.75d;

    /**
     * The top of a torso, in blocks above her feet. It is skin row 20; rows run
     * down from there.
     */
    private static final double TORSO_TOP = 1.5d;

    /**
     * Half a torso's thickness, in blocks: how far her front is from her centre
     * line.
     */
    private static final double TORSO_FRONT = 0.125d;

    /**
     * Undoes the game's shrinking, so one cell of a shape is one pixel of her skin
     * again.
     * <p>
     * Without it every shape comes out at five eighths of the size the arithmetic
     * asked for, which
     * does not look like a bug on screen — it looks like the numbers were too
     * timid.
     */
    private static final double TRUE_SIZE = 1.0d / HEAD_SCALE;

    private TraitPack() {
    }

    /**
     * Writes the pack into {@code root} and a zip beside it, replacing whatever was
     * there.
     *
     * @return how many shapes it ended up holding.
     */
    public static int write(@NotNull File root) throws IOException {
        List<BodyShape> shapes = BodyShape.all();

        File assets = new File(root, "assets/" + NAMESPACE);
        File models = new File(assets, "models/item");
        File items = new File(assets, "items");
        File textures = new File(assets, "textures/item");

        for (File dir : new File[] { models, items, textures }) {
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("Could not create " + dir);
        }

        writeString(new File(root, "pack.mcmeta"), packMeta());
        writeWhitePixel(new File(textures, "white.png"));

        int written = 0;
        for (BodyShape shape : shapes) {
            List<BodyShape.Cell> cells = shape.cells();

            // One model per size and per "is she wearing armour". A worn item carries no
            // transform
            // anyone can change from the server — whatever the pack says is what every
            // villager
            // wearing it gets — so the variants are baked, and choosing between them is how
            // each
            // villager still ends up looking like herself.
            for (int step = 0; step < BodyShape.SIZES; step++) {
                for (boolean armoured : new boolean[] { false, true }) {
                    String name = shape.variant(step, armoured);

                    writeString(new File(models, name + ".json"),
                            modelJson(shape, cells, BodyShape.sizeAt(step), armoured));
                    writeString(new File(items, name + ".json"), itemJson(name, cells.size()));
                    written++;
                }
            }
        }

        writeArmourLayers(assets, shapes);

        zip(root, new File(root.getParentFile(), "appearance-pack.zip"));
        return written;
    }

    /**
     * The same figures again, as armour rather than as models.
     * <p>
     * A second route to the same picture, and the only one that reaches the torso.
     * A worn model is
     * drawn on the head bone, because the head slot is the only slot the game draws
     * a model in at
     * all — so a figure worn that way turns when she looks around. The chest slot
     * does not draw
     * models; it draws armour, which is a texture stretched over the standard body.
     * That body is the
     * torso, and it does not turn with her head.
     * <p>
     * What is lost is depth. Armour cannot stand out from her, so the figure here
     * is shading rather
     * than shape: light where the body would catch it and shadow beneath, which is
     * the same trick a
     * hand-drawn skin uses to suggest a figure it cannot model. Not as good as
     * geometry from close
     * up, and better than geometry in the wrong place.
     * <p>
     * One texture per shape, no colour of its own. Painted in black and white over
     * whatever she is
     * already wearing, so a single file works for every skin on the server.
     */
    private static void writeArmourLayers(@NotNull File assets, @NotNull List<BodyShape> shapes) throws IOException {
        File equipment = new File(assets, "equipment");
        File textures = new File(assets, "textures/entity/equipment/humanoid");

        for (File dir : new File[] { equipment, textures }) {
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("Could not create " + dir);
        }

        for (BodyShape shape : shapes) {
            writeString(new File(equipment, shape.id() + ".json"),
                    "{\n  \"layers\": {\n    \"humanoid\": [\n      {\"texture\": \""
                            + NAMESPACE + ':' + shape.id() + "\"}\n    ]\n  }\n}\n");

            ImageIO.write(shading(shape), "png", new File(textures, shape.id() + ".png"));
        }
    }

    /**
     * A figure drawn as light and shadow on an otherwise empty armour texture.
     * <p>
     * Armour layer one shares the player model's own layout, so a shape that knows
     * which rows of a
     * skin it wears already knows where to paint: the same columns and rows, on a
     * different sheet.
     * <p>
     * Depth becomes brightness. Where the body would stand furthest out it catches
     * the most light,
     * and the light gives way to shadow below the fullest row, which is what tells
     * an eye that a
     * flat surface is round. Everything else is left transparent.
     */
    private static @NotNull BufferedImage shading(@NotNull BodyShape shape) {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);

        // One row of margin all round, so the surface can be seen falling back to the
        // body at its
        // edges. Without it the shape ends at a cliff and reads as a sticker rather
        // than a form.
        int width = shape.width() + 2;
        int height = shape.height() + 2;

        double[][] depth = new double[height][width];
        double deepest = 0.0d;

        for (BodyShape.Cell cell : shape.cells()) {
            depth[cell.v() + 1][cell.u() + 1] = cell.depth();
            deepest = Math.max(deepest, cell.depth());
        }

        if (deepest <= 0.0d)
            return image;

        for (int v = 0; v < height; v++) {
            for (int u = 0; u < width; u++) {
                // Nothing to shade where the body is flat in every direction around this pixel.
                if (flatAround(depth, u, v))
                    continue;

                double tone = tone(depth, u, v, deepest);
                if (tone == 0.0d)
                    continue;

                int x = shape.skinU() + u - 1;
                int y = shape.skinV() + v - 1;
                if (x < 0 || x >= 64 || y < 0 || y >= 32)
                    continue;

                // The margin may run down and up, but never sideways. A torso's eight columns
                // are
                // its front; the column either side of them is the face that wraps round to her
                // ribs, and a highlight that spilled there would be a stripe down her side.
                if (x < shape.skinU() || x >= shape.skinU() + shape.width())
                    continue;

                int level = tone > 0.0d ? 255 : 0;
                int alpha = (int) Math.round(Math.min(1.0d, Math.abs(tone)) * 165.0d);
                if (alpha < 6)
                    continue;

                image.setRGB(x, y, (alpha << 24) | (level << 16) | (level << 8) | level);
            }
        }

        return image;
    }

    /**
     * How light this pixel is, from -1 for full shadow to 1 for full light.
     * <p>
     * Worked out from which way the surface faces, rather than from where the pixel
     * sits. The shape
     * is a height field, so the slope across it in each direction gives its normal,
     * and how squarely
     * that normal meets the light gives the tone. That is what makes a painted
     * surface read as round:
     * the top of a curve catches the light, the underside turns away from it, and
     * the change between
     * them is gradual because the slope is.
     * <p>
     * A first attempt did this by position instead — bright above the fullest row,
     * dark below it —
     * and it failed for a reason worth keeping: on a bust the fullest row is near
     * the top, so almost
     * the whole shape fell on the dark side and it came out as a stripe under the
     * chest.
     */
    private static double tone(double[][] depth, int u, int v, double deepest) {
        double across = at(depth, u + 1, v) - at(depth, u - 1, v);
        double down = at(depth, u, v + 1) - at(depth, u, v - 1);

        // The normal, with v running down the texture. Flatter than the true slope on
        // purpose: the
        // shape is only a couple of pixels deep, and its honest normals would barely
        // differ.
        double nx = -across;
        double ny = -down;
        double nz = 1.6d * deepest;

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.0d)
            return 0.0d;

        // From above, in front, and a little to her right, which is where Minecraft's
        // own shading
        // implies the sun is and so the direction a player's eye already expects.
        double lit = (nx * -0.38d + ny * -0.72d + nz * 0.58d) / length;

        // Flat body would read 0.58; only the difference from flat is painted, so
        // untouched skin
        // stays untouched.
        return (lit - 0.58d) * 2.6d;
    }

    private static boolean flatAround(double[][] depth, int u, int v) {
        for (int dv = -1; dv <= 1; dv++) {
            for (int du = -1; du <= 1; du++) {
                if (at(depth, u + du, v + dv) > 0.0d)
                    return false;
            }
        }
        return true;
    }

    private static double at(double[] @NotNull [] depth, int u, int v) {
        if (v < 0 || v >= depth.length || u < 0 || u >= depth[v].length)
            return 0.0d;
        return depth[v][u];
    }

    private static @NotNull String packMeta() {
        // A range rather than a number. 46 is 1.21.4, where the item model system this
        // rests on
        // arrived; the upper end is left far ahead so a server that updates does not
        // start warning
        // players their pack is for an older game when nothing about it has changed.
        return "{\n  \"pack\": {\n    \"pack_format\": 46,\n"
                + "    \"supported_formats\": {\"min_inclusive\": 46, \"max_inclusive\": 200},\n"
                + "    \"description\": \"RealisticVillagers appearance traits\"\n  }\n}\n";
    }

    /**
     * The shape: one small box per cell, every face tinted from that cell's own
     * slot.
     * <p>
     * Each cell starts at the origin plane and reaches out one way only.
     * <p>
     * It was briefly mirrored, front and back, as a way of surviving not knowing
     * which way an item
     * display turns a model. That question has since been answered in game, so the
     * mirror is gone —
     * it cost half the possible depth, since whatever showed in front was also
     * buried behind and
     * had to fit inside her.
     * <p>
     * All six faces are written, not only the ones facing out. A body is seen from
     * the side and
     * from below as often as head on, and an unwritten face is a hole from those
     * angles.
     */
    private static @NotNull String modelJson(@NotNull BodyShape shape,
            @NotNull List<BodyShape.Cell> cells,
            double size,
            boolean armoured) {
        StringBuilder json = new StringBuilder(8192);

        json.append("{\n").append(headDisplay(shape, size, armoured))
                .append("  \"texture_size\": [16, 16],\n  \"textures\": {\n")
                .append("    \"0\": \"").append(NAMESPACE).append(':').append(WHITE_TEXTURE).append("\",\n")
                .append("    \"particle\": \"").append(NAMESPACE).append(':').append(WHITE_TEXTURE).append("\"\n")
                .append("  },\n  \"elements\": [\n");

        double top = CENTRE + shape.height() / 2.0d;
        double left = CENTRE - shape.width() / 2.0d;

        boolean first = true;
        for (BodyShape.Cell cell : cells) {
            for (int sy = 0; sy < 2; sy++) {
                for (int sx = 0; sx < 2; sx++) {
                    double u = cell.u() + sx * 0.5d;
                    double v = cell.v() + sy * 0.5d;
                    double depth = shape.depthAt(u + 0.25d, v + 0.25d);
                    // Asked of the shape rather than written here. The two thresholds have to move
                    // together — the shapes were made shallower, and a cut that stayed where it was
                    // would have eaten the soft rim that made them shallower in the first place.
                    if (depth < BodyShape.minimumDepth()) continue;
                    if (!first) json.append(",\n");
                    first = false;

                    // Laid out right to left, because her skin is. Her body is drawn through the
                    // model's own
                    // mirror and nothing else; a worn item is turned half a circle on top of that,
                    // and the
                    // two flips cancel. So a column that runs one way across her chest runs the
                    // other way
                    // across anything worn over it — invisible on a symmetric skin, and a swapped
                    // left and
                    // right on every other one.
                    double x0 = left + shape.width() - u - 0.5d;
                    double y0 = top - v - 0.5d;
        
                    double z0 = CENTRE - depth;
                    double z1 = CENTRE + 0.25d;
        
                    json.append("    {\"from\": [")
                            .append(round(x0)).append(", ").append(round(y0)).append(", ").append(round(z0))
                            .append("], \"to\": [")
                            .append(round(x0 + 0.5d)).append(", ").append(round(y0 + 0.5d)).append(", ")
                            .append(round(z1))
                            .append("], \"faces\": {");
        
                    String[] faces = { "north", "east", "south", "west", "up", "down" };
                    for (int f = 0; f < faces.length; f++) {
                        if (f > 0)
                            json.append(", ");
                        json.append('"').append(faces[f])
                                .append("\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#0\", \"tintindex\": ")
                                .append(cell.tint()).append('}');
                    }
        
                    json.append("}}");
                }
            }
        }

        return json.append("\n  ]\n}\n").toString();
    }

    /**
     * Where the shape sits when worn on the head, and how big it is.
     * <p>
     * This is the whole of the placement, and it is worked out rather than found by
     * looking. An item
     * in the head slot is drawn on the head bone, which is what makes it follow her
     * every movement
     * without a line of code — but the head is not where a chest is, so the model
     * carries its own
     * walk down to it. Two distances the game fixes settle that walk: a worn item
     * is drawn centred on
     * the middle of the head, and her skin's own rows say which part of her torso a
     * shape belongs to.
     * The difference is the drop.
     * <p>
     * Derived from what the client does to a worn item — turn it half a circle,
     * mirror it, shrink it
     * to five eighths — rather than measured off a screen. Numbers that were
     * measured belonged to a
     * different anchor and have been dropped.
     * <p>
     * Armour gets its own variant rather than a nudge at runtime, because a worn
     * item has no runtime
     * nudge to give it.
     */
    private static @NotNull String headDisplay(@NotNull BodyShape shape, double size, boolean armoured) {
        // Derived only. The live nudges are deliberately not read here any more: a pack that
        // changes when a number changes is a pack every player must download again, and the
        // numbers worth nudging are the ones being nudged repeatedly. They belong to the figures
        // that ride their owner, where they cost a transform rather than a download; what stays
        // baked is the part nobody adjusts, which is where the arithmetic says a chest is.
        double drop = (bandOf(shape) - HEAD_CENTRE) / UNIT;

        double clearance = TORSO_FRONT + (armoured ? 0.046875d : 0.0d);

        // Cells grow out of the origin plane one way only, into -Z, so the model is
        // pushed the same
        // way to stand its back against her chest. Turning it round has to turn the
        // push with it.
        boolean flipped = false;
        double out = (flipped ? 1.0d : -1.0d) * clearance / UNIT;

        double across = TRUE_SIZE * Math.min(1.0d, 0.85d + 0.15d * size);
        double deep = TRUE_SIZE * size;

        return "  \"display\": {\n    \"head\": {\n"
                + (flipped ? "      \"rotation\": [0, 180, 0],\n" : "")
                + "      \"translation\": [0, " + round(drop) + ", " + round(out) + "],\n"
                + "      \"scale\": [" + round(across) + ", " + round(across) + ", " + round(deep) + "]\n"
                + "    },\n" + hiddenEverywhereElse()
                + "  },\n";
    }

    /**
     * Nothing at all, in every context except worn on a head.
     * <p>
     * This exists so a woman can see her own figure. Telling a player's own client
     * that she is
     * wearing this is what makes it show in third person — but her client writes
     * what it is told
     * into her armour slot, so she would find a sheet of paper sitting in her
     * helmet square. Scaled
     * to nothing in every other context, that sheet of paper is drawn as nothing:
     * the slot looks
     * empty, because what is in it has no size.
     * <p>
     * The hand and ground contexts are here for the same reason rather than any
     * known need. The item
     * is a fiction that only ever exists on a head, and a fiction that turns up
     * anywhere else should
     * be invisible there too.
     */
    private static @NotNull String hiddenEverywhereElse() {
        StringBuilder json = new StringBuilder(256);

        String[] contexts = { "gui", "ground", "fixed",
                "thirdperson_righthand", "thirdperson_lefthand",
                "firstperson_righthand", "firstperson_lefthand" };

        for (int i = 0; i < contexts.length; i++) {
            json.append("    \"").append(contexts[i]).append("\": {\"scale\": [0, 0, 0]}")
                    .append(i < contexts.length - 1 ? "," : "").append('\n');
        }

        return json.toString();
    }

    /**
     * Where a shape's own band sits, in blocks above her feet.
     * <p>
     * Read off the skin rather than chosen: a skin's body rows 20 to 31 are the
     * twelve pixels of her
     * torso, top to bottom, so a shape wearing rows 21 to 24 belongs exactly where
     * those rows are
     * drawn. That is what keeps a belly lower than a bust without either being
     * placed by hand.
     */
    private static double bandOf(@NotNull BodyShape shape) {
        return TORSO_TOP - (shape.skinV() + shape.height() / 2.0d - 20.0d) / 16.0d;
    }

    /**
     * The item definition, whose tint list turns a white shape into a person.
     * <p>
     * One entry per face, each reading its own slot of the villager's colour list.
     * The default is
     * a plain skin tone so a villager whose texture could not be read wears
     * something rather than
     * a white blob.
     */
    private static @NotNull String itemJson(@NotNull String variant, int tints) {
        StringBuilder json = new StringBuilder(2048);

        json.append("{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n")
                .append("    \"model\": \"").append(NAMESPACE).append(":item/").append(variant).append("\",\n")
                .append("    \"tints\": [\n");

        for (int i = 0; i < tints; i++) {
            json.append("      {\"type\": \"minecraft:custom_model_data\", \"index\": ").append(i)
                    .append(", \"default\": 13148027}")
                    .append(i < tints - 1 ? "," : "").append('\n');
        }

        return json.append("    ]\n  }\n}\n").toString();
    }

    private static void zip(@NotNull File root, @NotNull File target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            zipInto(zip, root, "");
        }
    }

    private static void zipInto(@NotNull ZipOutputStream zip, @NotNull File dir, String prefix) throws IOException {
        File[] children = dir.listFiles();
        if (children == null)
            return;

        for (File child : children) {
            String name = prefix + child.getName();

            if (child.isDirectory()) {
                zipInto(zip, child, name + "/");
                continue;
            }

            zip.putNextEntry(new ZipEntry(name));
            zip.write(Files.readAllBytes(child.toPath()));
            zip.closeEntry();
        }
    }

    private static void writeWhitePixel(@NotNull File file) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        ImageIO.write(image, "png", file);
    }

    private static void writeString(@NotNull File file, @NotNull String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static @NotNull String round(double value) {
        double rounded = Math.round(value * 1000.0d) / 1000.0d;
        return rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }
}
