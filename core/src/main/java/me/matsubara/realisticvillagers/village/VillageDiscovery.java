package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Copies the buildings of a settlement you have found into blueprints you can raise elsewhere.
 * <p>
 * This is what turns the building scanner into something worth having. The plugin already works
 * out where every building in a village stands and how big it is — so the same measurements can
 * be handed to Minecraft's own structure saver and written out as a blueprint. Walk into a
 * village, record it, and its houses become buildings your other settlements can put up.
 * <p>
 * It matters most for the villages this plugin didn't design. A datapack's villages have their
 * own architecture, and there is otherwise no way to get it out of the world and into a menu:
 * you would have to rebuild each house by hand and save it with a structure block. Recording a
 * village you stumbled across is the difference between admiring somebody's work and being able
 * to use it.
 * <p>
 * Buildings are filed under the region they were found in, so a desert village's houses are
 * offered to desert villages and not to a taiga — the architecture stays where it belongs.
 */
public final class VillageDiscovery {

    private final RealisticVillagers plugin;
    private final VillageManager villages;

    /**
     * Smallest thing worth recording, in blocks along a side.
     * <p>
     * The scanner reports lamp posts and garden walls as well as houses. Recording those would
     * fill the menu with two-block curiosities and bury the buildings someone actually wants.
     */
    private static final int MIN_SIDE = 4;

    /** Largest, so a scan that merged half a village doesn't become one enormous blueprint. */
    private static final int MAX_SIDE = 48;

    /**
     * Buildings taken from any one village, before the server says otherwise.
     * <p>
     * A settlement has a handful of shapes and then repeats them, so past a few there is nothing
     * new being learned — only more entries to scroll past. It also bounds what walking into a
     * village can cost, since each one saved is a full read of every block in it.
     * <p>
     * Configurable, and it needs to be: a village of seven buildings quietly losing its seventh
     * is indistinguishable from the scanner failing to see it, and the one thing a limit must
     * never do is look like a bug. Raising it is what somebody who wants the whole village does;
     * the log says which one it is so the question does not have to be guessed at.
     */
    private static final int MAX_PER_VILLAGE = 6;

    private int perVillageLimit() {
        return Math.max(1, me.matsubara.realisticvillagers.files.Config.VILLAGE_BUILD_DISCOVERY_LIMIT
                .asInt(MAX_PER_VILLAGE));
    }

    public VillageDiscovery(@NotNull RealisticVillagers plugin, @NotNull VillageManager villages) {
        this.plugin = plugin;
        this.villages = villages;
    }

    /**
     * Where a world records who has already learned from its villages.
     * <p>
     * Inside the world, beside that world's blueprints. It is a fact about those villages, so
     * deleting the world has to take it with them — otherwise a rebuilt world's villages would
     * count as already recorded and never be learned from at all.
     */
    private @NotNull java.io.File seenFile(@NotNull World world) {
        return new java.io.File(new java.io.File(world.getWorldFolder(), "realisticvillagers"), "recorded.yml");
    }

    /** Worlds whose recorded list has been read back, so it is read once and not per visit. */
    private final java.util.Set<String> loadedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Reads a world's recorded list the first time that world comes up.
     * <p>
     * Done here rather than at start-up on purpose. Reading every world the moment the plugin is
     * enabled depends on the worlds being there to read, and when they were not the list came back
     * empty, every village counted as new, and a walk through one wrote another six copies of the
     * same houses on every restart. Asking for a world's list at the moment that world is actually
     * being used cannot be too early.
     */
    private void ensureLoaded(@NotNull World world) {
        if (!loadedWorlds.add(world.getName())) return;

        java.io.File file = seenFile(world);
        if (!file.isFile()) return;

        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        int villages = 0;
        for (String key : config.getKeys(false)) {
            java.util.UUID player;
            try {
                player = java.util.UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            java.util.Set<java.util.UUID> recorded = seen.computeIfAbsent(
                    player, id -> java.util.concurrent.ConcurrentHashMap.newKeySet());

            for (String id : config.getStringList(key)) {
                try {
                    recorded.add(java.util.UUID.fromString(id));
                    villages++;
                } catch (IllegalArgumentException ignored) {
                    // A line that isn't an id costs that entry and nothing else.
                }
            }
        }

        plugin.getLogger().info("Restored " + villages + " already-recorded village(s) in " + world.getName() + ".");
    }

    /** Writes the list back into the world the villages belong to. */
    private void saveSeen(@NotNull World world) {
        var config = new org.bukkit.configuration.file.YamlConfiguration();

        for (var entry : seen.entrySet()) {
            java.util.List<String> ids = new java.util.ArrayList<>();

            for (java.util.UUID id : entry.getValue()) {
                Village village = villages.getVillage(id);
                if (village != null && world.getName().equals(village.getWorldName())) ids.add(id.toString());
            }

            if (!ids.isEmpty()) config.set(entry.getKey().toString(), ids);
        }

        java.io.File file = seenFile(world);

        try {
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();

            config.save(file);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not save which villages have been recorded in "
                    + world.getName() + ": " + throwable.getMessage());
        }
    }

    /**
     * What came of a recording, so the player can be told something specific.
     *
     * @param complete whether every building was actually looked at. False when some sat in
     *                 chunks that weren't loaded, which means the village is worth another look
     *                 rather than being written off as done.
     */
    public record Result(int saved, int skipped, @NotNull String region, boolean complete) {}

    /**
     * Villages each player has already had recorded, kept on disk.
     * <p>
     * This used to live only in memory, and that was the whole of the problem: every restart
     * forgot it, so every village anyone walked back into was scanned again. A building somebody
     * had since edited no longer matched what was saved, so it was filed as a new one — and the
     * menu grew another near-copy of the same house on every restart, for ever.
     * <p>
     * A village is recorded <b>once per player</b> and never revisited. Whatever it looked like
     * that day is what was learned from it; later edits are that village's business, not the
     * blueprint's.
     */
    private final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> seen =
            new java.util.concurrent.ConcurrentHashMap<>();



    /**
     * Records this settlement for the player who has just walked into it.
     * <p>
     * Discovery is the player's, not the server's: finding a village is the interesting part, and
     * a shared list would hand everyone the reward for it the moment the first player arrived.
     * On a server of any size the list would be complete within a day.
     * <p>
     * Nothing is written for a village whose buildings haven't been scanned yet — marking it done
     * having saved nothing is worse than getting to it a minute later.
     */
    public void discover(@NotNull org.bukkit.entity.Player player, @NotNull Village village) {
        if (!me.matsubara.realisticvillagers.files.Config.VILLAGE_BUILD_PERSONAL_DISCOVERY.asBool(true)) {
            // Shared instead: one copy for everybody, recorded the first time anyone arrives.
            if (village.isRecorded()) return;
            if (VillageBuildings.detect(villages, village).isEmpty()) return;

            village.setRecorded(true);
            record(village, null, result -> announce(result, village, null));
            return;
        }

        World world = village.getWorld();
        if (world == null) return;

        ensureLoaded(world);

        java.util.Set<java.util.UUID> visited =
                seen.computeIfAbsent(player.getUniqueId(), id -> java.util.concurrent.ConcurrentHashMap.newKeySet());

        if (visited.contains(village.getId())) return;

        if (VillageBuildings.detect(villages, village).isEmpty()) return;

        record(village, player.getUniqueId(), result -> {
            announce(result, village, player);

            // Written off as learned only when the whole village was actually visible. Coming
            // back costs a second look at buildings already on file and writes none of them
            // again, since each one is recognised by the fingerprint taken when it was saved.
            if (!result.complete()) return;

            visited.add(village.getId());
            saveSeen(world);
        });
    }

    private void announce(@NotNull Result result, @NotNull Village village,
                          @Nullable org.bukkit.entity.Player player) {

        if (result.saved() <= 0) return;

        // The tally itself is logged where it is counted, alongside what became of the rest.
        if (player == null) return;

        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String text = config != null ? config.getString("village.discovered") : null;
        if (text == null) {
            text = "&aYou have learned &f%count% &abuilding(s) from &f%village%&a.";
        }

        player.sendMessage(me.matsubara.realisticvillagers.util.PluginUtils.translate(text
                .replace("%count%", String.valueOf(result.saved()))
                .replace("%village%", village.getDisplayName())));
    }

    /**
     * Copies a village's buildings into blueprints, one per tick.
     * <p>
     * Spread rather than done in one go. Reading every block of a building and serialising it is
     * not cheap, and a village contributes several — done together, on the tick somebody walks in,
     * that is a stall the whole server feels. One per tick costs the same in total and nothing
     * that anybody notices.
     *
     * @param done handed the tally once the last building has been dealt with.
     */
    public void record(@NotNull Village village,
                       @Nullable java.util.UUID owner,
                       @NotNull java.util.function.Consumer<Result> done) {

        World world = village.getWorld();
        if (world == null) {
            done.accept(new Result(0, 0, Blueprints.ANY_REGION, false));
            return;
        }

        String region = VillageNames.groupFor(village.getCenter());

        // A player's discoveries go in their own folder; the server's go in the shared one.
        File folder = owner == null
                ? new File(plugin.getDataFolder(), "blueprints/" + region)
                : new File(Blueprints.discoveredFolder(world),
                        Blueprints.DISCOVERED_FOLDER + "/" + owner + "/" + region);

        if (!folder.isDirectory() && !folder.mkdirs()) {
            done.accept(new Result(0, 0, region, false));
            return;
        }

        java.util.Deque<VillageBuildings.Footprint> queue =
                new java.util.ArrayDeque<>(VillageBuildings.detect(villages, village));

        int limit = perVillageLimit();

        new org.bukkit.scheduler.BukkitRunnable() {

            // A cap on how much one village can contribute.
            //
            // A settlement has a handful of shapes and then repeats them, so past a few there is
            // nothing new being learned — only more entries to scroll past.
            private int budget = limit;
            private int saved;
            private int skipped;

            // What became of the ones that were not saved, so the log can say so rather than
            // leaving a tally that looks like the scanner missed a building.
            private int unsuitable;
            private int unloadedAway;
            private int duplicate;
            private int failed;

            /** Cleared the moment a building has to be passed over for being half-unloaded. */
            private boolean complete = true;

            @Override
            public void run() {
                VillageBuildings.Footprint building = queue.poll();

                if (building == null || budget <= 0) {
                    cancel();

                    // Anything written is a building the menu doesn't know about yet.
                    if (saved > 0 && plugin.getBlueprints() != null) plugin.getBlueprints().load();

                    // Whatever is still queued when the budget runs out was never looked at.
                    explain(queue.size() + (building == null ? 0 : 1), limit);

                    done.accept(new Result(saved, skipped, region, complete));
                    return;
                }

                if (!worthRecording(building)) {
                    unsuitable++;
                    skipped++;
                    return;
                }

                // Nothing here may pull a chunk in.
                //
                // Both reading the fingerprint and saving the structure walk every block of the
                // box, and asking for a block in a chunk that isn't loaded loads it — on the main
                // thread, deserialising every entity in it. Doing that for a ring of chunks around
                // a village is enough to stop the server dead for seconds. A building sitting half
                // outside what is loaded is simply left for a visit that can see all of it.
                if (!loaded(world, building)) {
                    complete = false;
                    unloadedAway++;
                    skipped++;
                    return;
                }

                // Measured from the world in exactly the box that would be saved, so it can be
                // compared against everything already on file before anything is written.
                long print = fingerprint(world, building);

                if (plugin.getBlueprints() != null
                        && plugin.getBlueprints().hasIdentical(print, owner, world.getName())) {
                    duplicate++;
                    skipped++;
                    return;
                }

                budget--;

                String written = save(world, building, folder, describe(building));
                if (written != null) {
                    saved++;

                    // Noted with the number measured here, and under the name the file actually
                    // got — which is not always the one asked for, since a village with two
                    // farmhouses writes the second as "farmer_workplace_2". Noting the name that
                    // was asked for left the note pointing at a file that was never written, and
                    // a note pointing nowhere is one that can never be cleared by deleting the
                    // building it claims to describe.
                    if (plugin.getBlueprints() != null) {
                        plugin.getBlueprints().remember(print, owner, world.getName(),
                                folder.getParentFile(), written);
                    }
                } else {
                    failed++;
                    skipped++;
                }
            }

            /**
             * Says in the log what happened to every building the village had.
             * <p>
             * Written because the tally on its own reads as a fault: a village of seven that
             * contributes six looks exactly like a scanner that cannot see the seventh, and the
             * seventh is usually the most interesting building in it. Each one passed over now
             * says why in plain words, so the answer is in the log rather than in the code.
             */
            private void explain(int untouched, int limit) {
                int detected = saved + skipped + untouched;
                if (detected <= 0 || detected == saved) return;

                java.util.List<String> why = new java.util.ArrayList<>();
                if (untouched > 0) {
                    why.add(untouched + " left alone (the limit of " + limit
                            + " a village was reached — village.build.discovery-limit)");
                }
                if (duplicate > 0) why.add(duplicate + " already on file");
                if (unsuitable > 0) why.add(unsuitable + " too small or too large to record");
                if (unloadedAway > 0) why.add(unloadedAway + " in chunks that weren't loaded");
                if (failed > 0) why.add(failed + " that could not be written");

                plugin.getLogger().info("Recorded " + saved + " of " + detected + " building(s) from "
                        + village.getDisplayName()
                        + (why.isEmpty() ? "." : ": " + String.join(", ", why) + "."));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean worthRecording(VillageBuildings.@NotNull Footprint building) {
        int width = building.width();
        int depth = building.depth();
        int height = building.height();

        if (width < MIN_SIDE || depth < MIN_SIDE || height < 3) return false;
        return width <= MAX_SIDE && depth <= MAX_SIDE && height <= MAX_SIDE;
    }

    /**
     * The fingerprint this building would have once saved.
     * <p>
     * Walked in the same order and over the same box as the saved copy, and built from the same
     * block-state strings, so the number it produces is comparable with one taken from a
     * blueprint that is already loaded.
     */
    /**
     * Whether every chunk this building touches is already loaded.
     * <p>
     * Measured over the same box that would be read: one block wider on each side in X and Z,
     * because that is what both the fingerprint and the saved structure cover.
     */
    private static boolean loaded(@NotNull World world, VillageBuildings.@NotNull Footprint building) {
        int fromX = (building.minX() - 1) >> 4;
        int toX = (building.maxX() + 1) >> 4;
        int fromZ = (building.minZ() - 1) >> 4;
        int toZ = (building.maxZ() + 1) >> 4;

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }

        return true;
    }

    private static long fingerprint(@NotNull World world, VillageBuildings.@NotNull Footprint building) {
        int minX = building.minX() - 1;
        int minZ = building.minZ() - 1;
        int maxX = building.maxX() + 1;
        int maxZ = building.maxZ() + 1;

        long hash = 1125899906842597L;

        hash = hash * 31 + (maxX - minX + 1);
        hash = hash * 31 + (building.maxY() - building.minY() + 1);
        hash = hash * 31 + (maxZ - minZ + 1);

        for (int y = building.minY(); y <= building.maxY(); y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    org.bukkit.block.data.BlockData data = world.getBlockAt(x, y, z).getBlockData();
                    hash = hash * 31 + (data.getMaterial().isAir() ? 0 : data.getAsString().hashCode());
                }
            }
        }

        return hash;
    }

    /**
     * Names a building after what the scanner says it is.
     * <p>
     * The file is named with the plain type key — residential, workplace, mixed, unknown — and
     * nothing else. What a player is <i>shown</i> is looked up from that key at display time, so
     * renaming "Unknown" in the config renames it everywhere it appears. Baking the shown text
     * into the file name would freeze whatever the wording happened to be on the day it was
     * recorded, and the two would drift apart from then on.
     * <p>
     * The trade goes in front where there is one, since "farmer workplace" says more than
     * "workplace" and is still just the type with a word before it.
     */
    private static @NotNull String describe(VillageBuildings.@NotNull Footprint building) {
        String type = building.type().name().toLowerCase(Locale.ROOT);

        if (building.professions().isEmpty()) return type;

        String trade = building.professions().iterator().next()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");

        return trade.isEmpty() ? type : trade + "_" + type;
    }

    /**
     * Writes one building out.
     * <p>
     * Taken a block wider and a block deeper than the scanner measured. A footprint is the walls,
     * and a building saved exactly to its walls loses the doorstep, the overhanging eave and the
     * step down to the path — the parts that make it look built rather than stamped.
     * <p>
     * Sized in blocks rather than cornered on two positions. The two-corner call measures the gap
     * <i>between</i> the corners, not the blocks they stand on, so it saves one block fewer on
     * every axis than it is handed: buildings came back with the top course of their roof missing
     * and the far side of the doorstep shaved off, which is a bug you can only see once the
     * building is standing again in front of you.
     */
    private @Nullable String save(
            @NotNull World world,
            VillageBuildings.@NotNull Footprint building,
            @NotNull File folder,
            @NotNull String name) {

        try {
            Structure structure = Bukkit.getStructureManager().createStructure();

            Location from = new Location(world, building.minX() - 1, building.minY(), building.minZ() - 1);

            // Entities left out: a villager standing in the doorway when the picture was taken is
            // not part of the house, and would be copied into every one ever built from it.
            structure.fill(from, new org.bukkit.util.BlockVector(
                    building.width() + 2,
                    building.height(),
                    building.depth() + 2), false);

            // Numbered only when a name is already taken, so the first farmhouse is "farmhouse"
            // and not "farmhouse 1".
            String written = name;
            File file = new File(folder, written + ".nbt");
            for (int suffix = 2; file.exists() && suffix <= 50; suffix++) {
                written = name + "_" + suffix;
                file = new File(folder, written + ".nbt");
            }

            if (file.exists()) return null;

            Bukkit.getStructureManager().saveStructure(file, structure);
            return written;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not record a building at "
                    + building.minX() + ", " + building.minY() + ", " + building.minZ()
                    + ": " + throwable.getMessage());
            return null;
        }
    }

}
