package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Bukkit;
import org.bukkit.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the buildings a settlement can raise from the schematics folder.
 * <p>
 * Every {@code .nbt} in {@code plugins/RealisticVillagers/schematics/} is a building. These are
 * vanilla structure files — what a structure block writes — so making one is: build it, save it,
 * copy the file across. Bukkit reads the format itself, which is the whole reason this route was
 * taken over WorldEdit schematics: no dependency, and nothing to keep in step with another
 * plugin's format.
 */
public final class Blueprints {

    private final RealisticVillagers plugin;

    /** Insertion-ordered, so the menu lists buildings the way the folder does. */
    private Map<String, Blueprint> blueprints = Collections.emptyMap();

    /**
     * Fingerprints of buildings already on file, as measured in the world when they were saved.
     * <p>
     * Keyed by whose they are, so one player's discoveries never mask another's.
     */
    private Map<String, java.util.Set<Long>> known = new java.util.HashMap<>();

    /** Sits beside a folder of buildings and lists what has already been taken from the world. */
    private static final String INDEX_FILE = "index.yml";

    /**
     * Blocks laid per second of build time, by default.
     * <p>
     * Turns a building's size into how long it takes, so a cottage goes up quickly and a keep
     * takes a while without anybody writing a number beside every file. Lower it to make
     * building something a settlement visibly labours over.
     */
    private static final int DEFAULT_BLOCKS_PER_SECOND = 4;

    private static int blocksPerSecond() {
        return Math.max(1, me.matsubara.realisticvillagers.files.Config
                .VILLAGE_BUILD_BLOCKS_PER_SECOND.asInt(DEFAULT_BLOCKS_PER_SECOND));
    }

    public Blueprints(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public @NotNull List<Blueprint> all() {
        return new ArrayList<>(blueprints.values());
    }

    /**
     * Whether a building made of exactly these blocks is already known.
     * <p>
     * Checked across every region, not just the one being recorded into: the same cottage turning
     * up in a plains village and a savanna one is still the same cottage, and two copies of it
     * under different folders is the duplication this is here to stop.
     */
    /**
     * Whether this exact building has already been written down.
     * <p>
     * Answered from a note made when it was saved, not by hashing the file again. Comparing a
     * building in the world against the same building read back from a structure file cannot
     * work: the two are walked in different orders, the world counts air as nothing while the
     * file stores it as a block, and the saved box is not quite the measured one. Those hashes
     * never matched, which is why walking into a village wrote a fresh set of copies each time.
     * Recording the number taken from the world and comparing that number is exact.
     */
    public boolean hasIdentical(long fingerprint, @Nullable java.util.UUID owner, @Nullable String world) {
        // The server's own buildings count for everyone, since everyone is offered them.
        if (known.getOrDefault(scopeKey(null, null), java.util.Set.of()).contains(fingerprint)) return true;

        // Otherwise only against what this player would actually be given: two players
        // discovering the same cottage each get their own copy, because neither can see
        // the other's.
        return known.getOrDefault(scopeKey(owner, world), java.util.Set.of()).contains(fingerprint);
    }

    /** Notes a building as recorded, so a later visit recognises it. */
    public void remember(long fingerprint,
                         @Nullable java.util.UUID owner,
                         @Nullable String world,
                         @NotNull File ownerFolder,
                         @NotNull String name) {

        known.computeIfAbsent(scopeKey(owner, world), key -> new java.util.HashSet<>()).add(fingerprint);

        File file = new File(ownerFolder, INDEX_FILE);

        var config = file.isFile()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();

        config.set(Long.toString(fingerprint), name);

        try {
            if (!ownerFolder.isDirectory()) ownerFolder.mkdirs();
            config.save(file);
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not note a recorded building: " + exception.getMessage());
        }
    }

    private void readIndex(@NotNull File ownerFolder,
                           @Nullable java.util.UUID owner,
                           @Nullable String world,
                           @NotNull Map<String, java.util.Set<Long>> into) {

        File file = new File(ownerFolder, INDEX_FILE);
        if (!file.isFile()) return;

        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        java.util.Set<Long> set = into.computeIfAbsent(scopeKey(owner, world), key -> new java.util.HashSet<>());

        boolean pruned = false;

        for (String key : config.getKeys(false)) {
            long fingerprint;
            try {
                fingerprint = Long.parseLong(key);
            } catch (NumberFormatException ignored) {
                // A line that isn't a fingerprint costs that entry and nothing else.
                continue;
            }

            // A note about a building whose file has been deleted is a note about nothing.
            //
            // It still had teeth, though: the note is what stops a village being recorded twice,
            // so a blueprint someone deleted could never be recorded again — the village had been
            // learned, the file was gone, and walking back in wrote nothing because the number
            // still matched. Deleting a building you did not like now means exactly what it looks
            // like it means, and the next visit records it afresh.
            String name = config.getString(key);
            if (name != null && !name.isEmpty() && fileFor(ownerFolder, name) == null) {
                config.set(key, null);
                pruned = true;
                continue;
            }

            set.add(fingerprint);
        }

        if (!pruned) return;

        try {
            config.save(file);
        } catch (java.io.IOException exception) {
            // The pruning held for this run either way; it is only the tidying that failed.
            plugin.getLogger().warning("Could not tidy " + INDEX_FILE + ": " + exception.getMessage());
        }
    }

    /** The file a recorded building was written to, looked for in every region beside the index. */
    private @Nullable File fileFor(@NotNull File ownerFolder, @NotNull String name) {
        File here = find(ownerFolder, name);
        if (here != null) return here;

        File[] regions = ownerFolder.listFiles(File::isDirectory);
        if (regions == null) return null;

        for (File region : regions) {
            File file = find(region, name);
            if (file != null) return file;
        }

        return null;
    }

    private static @NotNull String scopeKey(@Nullable java.util.UUID owner, @Nullable String world) {
        return owner == null ? "server" : owner + "@" + (world == null ? "" : world);
    }

    /**
     * Everything this player may raise in a settlement of this region.
     * <p>
     * The server's buildings plus their own discoveries, and nobody else's.
     */
    public @NotNull List<Blueprint> available(@Nullable String region, @Nullable java.util.UUID player,
                                              @Nullable String world) {

        List<Blueprint> out = new ArrayList<>();
        for (Blueprint blueprint : blueprints.values()) {
            if (blueprint.suits(region) && blueprint.visibleTo(player, world)) out.add(blueprint);
        }
        return out;
    }

    public @Nullable Blueprint get(@Nullable String id) {
        return id == null ? null : blueprints.get(id);
    }

    /** Reads every structure in the folder, creating it with a note inside if it isn't there. */
    /** Set while a reload is already queued for this tick, so a burst of them costs one. */
    private boolean reloadQueued;

    /**
     * Reloads once, at the end of the current tick.
     * <p>
     * Worlds arrive one after another at start-up and each one could hold blueprints, but reading
     * the lot again per world means parsing every structure file as many times as there are
     * worlds. Collapsing them into a single pass reads each file once.
     */
    public void loadSoon() {
        if (reloadQueued) return;
        reloadQueued = true;

        Bukkit.getScheduler().runTask(plugin, () -> {
            reloadQueued = false;
            load();
        });
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "blueprints");

        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the blueprints folder; no buildings will be available.");
            blueprints = Collections.emptyMap();
            return;
        }

        for (String region : REGIONS) {
            File sub = new File(folder, region);
            if (!sub.isDirectory()) sub.mkdirs();
        }

        writeStarterFiles(folder);

        Map<String, Blueprint> loaded = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> seen = new java.util.HashMap<>();

        // The server's own buildings, offered to everybody.
        readRegions(folder, null, null, loaded);
        readIndex(folder, null, null, seen);

        // Then every player's discoveries, kept inside the world they were made in — so a world
        // that is deleted takes its blueprints with it rather than leaving them behind in the
        // plugin folder, haunting whatever world comes next.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            File discovered = new File(discoveredFolder(world), DISCOVERED_FOLDER);
            File[] owners = discovered.listFiles(File::isDirectory);
            if (owners == null) continue;

            for (File owner : owners) {
                java.util.UUID id;
                try {
                    id = java.util.UUID.fromString(owner.getName());
                } catch (IllegalArgumentException ignored) {
                    // Not a player folder; leave whatever it is alone.
                    continue;
                }

                readRegions(owner, id, world.getName(), loaded);
                readIndex(owner, id, world.getName(), seen);
            }
        }

        blueprints = Collections.unmodifiableMap(loaded);
        known = seen;

        if (blueprints.isEmpty()) {
            plugin.getLogger().info("No buildings found in the blueprints folder — see the readme in it "
                    + "for how to save one with a structure block.");
            return;
        }

        plugin.getLogger().info("Loaded " + blueprints.size() + " building(s) from the blueprints folder.");

        // Said out loud because it is the one thing about a building that cannot be seen until it
        // has been built. A file that starts at its first course of walls has no floor in it at
        // all, so whatever it is stood on becomes its floor. Anything this plugin recorded before
        // the scanner could find floors reads that way, and recording the village again is what
        // fixes it — which nobody would think to do without being told.
        long floorless = blueprints.values().stream().filter(blueprint -> !blueprint.hasOwnFloor()).count();
        if (floorless > 0) {
            plugin.getLogger().info(floorless + " of them have no floor of their own, so the ground they are "
                    + "put on serves as one. A building recorded before floors were understood reads that "
                    + "way: delete it and visit the village again to record it with its floor.");
        }
    }

    /** The folder whose buildings suit any landscape. */
    public static final String ANY_REGION = "allbiomes";

    /**
     * One folder per kind of country, matching the regions villages are already sorted into for
     * their names and their dialogue — so a desert village offers desert buildings.
     */
    public static final List<String> REGIONS =
            List.of(ANY_REGION, "plains", "desert", "savanna", "taiga", "snow", "jungle", "swamp");

    /** Where each player's own discoveries live, one folder per player. */
    public static final String DISCOVERED_FOLDER = "discovered";

    /** Where a world keeps the buildings discovered in it. */
    public static @NotNull File discoveredFolder(@NotNull org.bukkit.World world) {
        return new File(world.getWorldFolder(), "realisticvillagers");
    }

    private void readRegions(@NotNull File root, @Nullable java.util.UUID owner, @Nullable String world,
                             @NotNull Map<String, Blueprint> into) {

        readFolder(new File(root, ANY_REGION), null, owner, world, into);

        for (String region : REGIONS) {
            if (region.equals(ANY_REGION)) continue;
            readFolder(new File(root, region), region, owner, world, into);
        }
    }

    private void readFolder(@NotNull File folder, @Nullable String region, @Nullable java.util.UUID owner,
                            @Nullable String world, @NotNull Map<String, Blueprint> into) {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".nbt") || lower.endsWith(".schem");
        });
        if (files == null) return;

        // Alphabetical, so the menu doesn't reshuffle itself between restarts.
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            String name = file.getName();
            String id = name.substring(0, name.lastIndexOf('.')).toLowerCase(Locale.ROOT);

            // Keyed by owner, folder and name, so two regions — and two players — may each have
            // their own "cottage" without one quietly replacing another.
            String key = (owner == null ? "server" : world + "/" + owner)
                    + "/" + (region == null ? ANY_REGION : region) + "/" + id;

            Blueprint blueprint = read(key, file, region, owner, world);
            if (blueprint != null) into.put(key, blueprint);
        }
    }

    private @Nullable Blueprint read(@NotNull String id, @NotNull File file, @Nullable String region,
                                     @Nullable java.util.UUID owner, @Nullable String world) {
        try {
            Blueprint blueprint = file.getName().toLowerCase(Locale.ROOT).endsWith(".schem")
                    ? fromSchem(id, file, region, owner, world)
                    : Blueprint.of(id, Bukkit.getStructureManager().loadStructure(file), blocksPerSecond(), region, owner, world);

            if (blueprint != null) return blueprint;

            plugin.getLogger().warning("Blueprint '" + file.getName() + "' holds no blocks and was skipped.");
        } catch (Throwable throwable) {
            // Named, so a file that isn't a structure — or came from a newer game — says which
            // one it was rather than costing the whole folder.
            plugin.getLogger().warning("Blueprint '" + file.getName() + "' could not be read and was skipped: "
                    + throwable.getMessage());
        }

        return null;
    }

    private @Nullable Blueprint fromSchem(@NotNull String id, @NotNull File file, @Nullable String region,
                                          @Nullable java.util.UUID owner, @Nullable String world)
            throws java.io.IOException {

        SchematicReader.Schematic schematic = SchematicReader.read(file);
        return schematic == null ? null : Blueprint.of(id, schematic, blocksPerSecond(), region, owner, world);
    }

    /**
     * Renames a blueprint's file, and with it what the building is called everywhere.
     * <p>
     * The file name <i>is</i> the name: nothing else records it, so there is nothing that can
     * fall out of step with it and nothing to migrate. Renaming a building is moving a file,
     * which is also what makes the new name survive a restart without any storage of its own.
     *
     * @return whether it worked. Failure means the name is unusable or already taken.
     */
    public boolean rename(@NotNull Blueprint blueprint, @NotNull String wanted) {
        // The server's buildings are not anybody's to rename: they are shared, so a name changed
        // by one player would change it for everyone.
        if (blueprint.getOwner() == null) return false;

        String cleaned = wanted.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 _-]", "")
                .trim()
                .replaceAll("\s+", "_");

        if (cleaned.isEmpty() || cleaned.length() > 40) return false;

        // id is world/owner/region/name for a discovery.
        String[] parts = blueprint.getId().split("/");
        if (parts.length != 4) return false;

        String region = parts[2];
        String current = parts[3];
        if (cleaned.equals(current)) return false;

        org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) return false;

        File folder = new File(discoveredFolder(world),
                DISCOVERED_FOLDER + "/" + blueprint.getOwner() + "/" + region);

        File from = find(folder, current);
        if (from == null) return false;

        String name = from.getName();
        File to = new File(folder, cleaned + name.substring(name.lastIndexOf('.')));
        if (to.exists() || !from.renameTo(to)) return false;

        load();
        return true;
    }

    private @Nullable File find(@NotNull File folder, @NotNull String name) {
        for (String extension : new String[]{".nbt", ".schem"}) {
            File file = new File(folder, name + extension);
            if (file.isFile()) return file;
        }
        return null;
    }

    /**
     * Puts the shipped readme and starter building in place the first time round.
     * <p>
     * Copied out of the jar rather than written from a string in here: a document belongs in a
     * file where it can be read and edited, not embedded in the code that happens to install it.
     */
    private void writeStarterFiles(@NotNull File folder) {
        copyIfMissing("blueprints/README.txt", new File(folder, "README.txt"));
        copyIfMissing("blueprints/allbiomes/tiny_village.schem",
                new File(folder, ANY_REGION + "/tiny_village.schem"));
    }

    private void copyIfMissing(@NotNull String resource, @NotNull File target) {
        if (target.exists()) return;

        try (java.io.InputStream in = plugin.getResource(resource)) {
            if (in == null) return;

            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();

            java.nio.file.Files.copy(in, target.toPath());
        } catch (Throwable ignored) {
            // A missing starter file costs nothing; the folder still works.
        }
    }
}
