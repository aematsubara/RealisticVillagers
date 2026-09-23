package me.matsubara.realisticvillagers.village;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads WorldEdit {@code .schem} files without WorldEdit.
 * <p>
 * A schem is gzipped NBT holding a palette of block-state strings and an array of indices into
 * it — and those strings are exactly the form {@link Bukkit#createBlockData(String)} already
 * accepts, so once the file is unpacked the game does the hard part. That is the whole reason
 * this is worth having: it is a container format, not a rival description of blocks, so nothing
 * about the building has to be reinterpreted and there is nothing to get subtly wrong.
 * <p>
 * Both layouts are handled. Sponge version 2 keeps {@code Palette} and {@code BlockData} at the
 * top level; version 3 moves them into a {@code Blocks} compound and renames the array to
 * {@code Data}. Everything else about them is the same.
 */
public final class SchematicReader {

    private SchematicReader() {
    }

    /** A schematic once unpacked: its size, and the block at every position. */
    public record Schematic(int width, int height, int length, BlockData[][][] blocks) {}

    /**
     * Reads {@code file}.
     *
     * @return {@code null} if it isn't a schematic this understands, so a stray file costs itself
     * and not the whole folder.
     */
    public static @Nullable Schematic read(@NotNull File file) throws IOException {
        Map<String, Object> root = readRoot(file);
        if (root == null) return null;

        // Version 3 nests everything a level deeper; version 2 keeps it at the top.
        Map<String, Object> blocksSection = asCompound(root.get("Blocks"));

        Map<String, Object> palette = asCompound(
                blocksSection != null ? blocksSection.get("Palette") : root.get("Palette"));

        byte[] indices = asBytes(
                blocksSection != null ? blocksSection.get("Data") : root.get("BlockData"));

        Integer width = asInt(root.get("Width"));
        Integer height = asInt(root.get("Height"));
        Integer length = asInt(root.get("Length"));

        if (palette == null || indices == null || width == null || height == null || length == null) return null;
        if (width <= 0 || height <= 0 || length <= 0) return null;

        // The palette maps a block-state string to the number used for it in the array, so it has
        // to be turned inside out before it can be looked up by number.
        BlockData[] byId = new BlockData[palette.size() + 1];
        int highest = 0;

        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            Integer id = asInt(entry.getValue());
            if (id == null || id < 0) continue;

            if (id >= byId.length) byId = java.util.Arrays.copyOf(byId, id + 1);
            highest = Math.max(highest, id);

            try {
                byId[id] = Bukkit.createBlockData(entry.getKey());
            } catch (Throwable ignored) {
                // A block this server doesn't have — from a newer game, or a mod — is left as a
                // hole rather than failing the building it appears in.
            }
        }

        BlockData[][][] blocks = new BlockData[height][length][width];

        int cursor = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (cursor >= indices.length) break;

                    // Indices are varints: seven bits a byte, top bit set while more follow.
                    int value = 0;
                    int shift = 0;
                    while (cursor < indices.length) {
                        byte b = indices[cursor++];
                        value |= (b & 0x7F) << shift;
                        if ((b & 0x80) == 0) break;
                        shift += 7;
                    }

                    if (value < 0 || value >= byId.length) continue;

                    BlockData data = byId[value];
                    if (data != null && !data.getMaterial().isAir()) blocks[y][z][x] = data;
                }
            }
        }

        return new Schematic(width, height, length, blocks);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    private static @Nullable Map<String, Object> readRoot(@NotNull File file) throws IOException {
        try (InputStream raw = open(file); DataInputStream in = new DataInputStream(raw)) {
            int type = in.readUnsignedByte();
            if (type != 10) return null;

            in.readUTF();

            Object root = readPayload(in, 10);
            return asCompound(root);
        }
    }

    /** Opens the file, transparently un-gzipping it — some tools write it uncompressed. */
    private static @NotNull InputStream open(@NotNull File file) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(
                new BufferedInputStream(new FileInputStream(file)), 2);

        byte[] magic = new byte[2];
        int read = pushback.read(magic);
        if (read > 0) pushback.unread(magic, 0, read);

        boolean gzipped = read == 2 && (magic[0] & 0xFF) == 0x1F && (magic[1] & 0xFF) == 0x8B;
        return gzipped ? new GZIPInputStream(pushback) : pushback;
    }

    private static @Nullable Object readPayload(@NotNull DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                return (int) in.readByte();
            case 2:
                return (int) in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int itemType = in.readUnsignedByte();
                int count = in.readInt();

                List<Object> list = new ArrayList<>(Math.max(0, count));
                for (int i = 0; i < count; i++) list.add(readPayload(in, itemType));
                return list;
            }
            case 10: {
                Map<String, Object> compound = new HashMap<>();
                while (true) {
                    int entryType = in.readUnsignedByte();
                    if (entryType == 0) break;

                    String key = in.readUTF();
                    compound.put(key, readPayload(in, entryType));
                }
                return compound;
            }
            case 11: {
                int count = in.readInt();
                int[] values = new int[count];
                for (int i = 0; i < count; i++) values[i] = in.readInt();
                return values;
            }
            case 12: {
                int count = in.readInt();
                long[] values = new long[count];
                for (int i = 0; i < count; i++) values[i] = in.readLong();
                return values;
            }
            default:
                // An unknown tag means the rest of the stream can't be trusted, so stop reading
                // rather than guessing a length and desynchronising everything after it.
                throw new IOException("Unknown NBT tag: " + type);
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, Object> asCompound(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static @Nullable Integer asInt(@Nullable Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static byte @Nullable [] asBytes(@Nullable Object value) {
        return value instanceof byte[] bytes ? bytes : null;
    }
}
