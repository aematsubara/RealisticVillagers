package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every {@link Village}: detects them, resolves who lives in them and persists them.
 * <p>
 * Detection is driven by the villagers themselves rather than by scanning for bells. Every
 * vanilla villager tied to a village remembers its meeting point ({@link MemoryKey#MEETING_POINT},
 * i.e. the bell), so grouping villagers by that memory reproduces vanilla's own notion of a
 * village for free — no block scanning, and it stays correct when a village grows or moves.
 */
public final class VillageManager {

    private final RealisticVillagers plugin;
    private final Map<UUID, Village> villages = new ConcurrentHashMap<>();
    private final Map<UUID, CachedResidents> residentCache = new ConcurrentHashMap<>();

    private @Nullable BukkitTask saveTask;

    /** Worlds whose villages have been read back, so each is read once. */
    private final java.util.Set<String> loadedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Bells this close to an existing centre are treated as the same village, not a new one. */
    private static final int SAME_VILLAGE_TOLERANCE = 16;

    public VillageManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        reloadMaterialOverrides();
        load();
        startSaveTask();
    }

    /**
     * Where a world keeps its own villages.
     * <p>
     * In the world, not the plugin folder. A village is a place in a world — deleting the world
     * has to take its villages with it, or the next world generated under that name inherits a
     * list of settlements that were never there, complete with mayors and treasuries.
     */
    private @NotNull File fileFor(@NotNull World world) {
        return new File(new File(world.getWorldFolder(), "realisticvillagers"), "villages.yml");
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Collection<Village> getVillages() {
        return Collections.unmodifiableCollection(villages.values());
    }

    public @Nullable Village getVillage(@Nullable UUID id) {
        return id == null ? null : villages.get(id);
    }

    public boolean isEnabled() {
        return Config.VILLAGE_ENABLED.asBool();
    }

    public int getDefaultRadius() {
        int radius = Config.VILLAGE_DEFAULT_RADIUS.asInt(64);
        return radius > 0 ? radius : 64;
    }

    /** The village covering {@code location}, or {@code null}. Picks the nearest on overlap. */
    public @Nullable Village getVillageAt(@Nullable Location location) {
        if (location == null) return null;

        int defaultRadius = getDefaultRadius();

        Village closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Village village : villages.values()) {
            if (!village.contains(location, defaultRadius)) continue;

            Location center = village.getCenter();
            if (center == null) continue;

            double distance = center.distanceSquared(location);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = village;
            }
        }

        return closest;
    }

    /**
     * The village this villager belongs to, creating it if its meeting point reveals a
     * village we haven't recorded yet. Falls back to a positional lookup for villagers
     * that have no meeting point (freshly spawned, or cut off from their bell).
     */
    public @Nullable Village getVillage(@Nullable Villager villager) {
        if (villager == null || !isEnabled()) return null;
        if (plugin.isDisabledIn(villager.getWorld())) return null;

        Location meetingPoint = getMeetingPoint(villager);
        return meetingPoint != null ? getOrCreateVillageAt(meetingPoint) : getVillageAt(villager.getLocation());
    }

    private @Nullable Location getMeetingPoint(@NotNull Villager villager) {
        try {
            return villager.getMemory(MemoryKey.MEETING_POINT);
        } catch (Throwable throwable) {
            // Memory keys have moved around between versions; a village simply stays
            // undetected here rather than breaking whatever asked for it.
            return null;
        }
    }

    /**
     * Looks for settlements that exist in the world but aren't registered yet.
     * <p>
     * Without this the plugin only ever learned about a village when a player right-clicked one
     * of its villagers, because that was the sole path reaching {@link #getOrCreateVillageAt}.
     * Everything else — elections, mayors, the whole settlement loop — iterates the registered
     * villages, so until somebody clicked a villager there was nothing to process and nothing
     * ever happened: no elections, and not a line in the console.
     * <p>
     * Cheap enough for a timer: it reads each villager's meeting-point memory, which is already
     * in memory, and touches no blocks or chunks.
     */
    /** Re-reads the configured building/terrain material overrides and name palettes. */
    public void reloadMaterialOverrides() {
        VillageBuildings.loadMaterialOverrides(
                plugin.getConfig().getStringList("village.building-materials.building"),
                plugin.getConfig().getStringList("village.building-materials.natural"));

        VillageNames.load(plugin.getConfig().getConfigurationSection("village.names"));
        // Re-read on reload as well as at start-up, so a schematic dropped into the folder
        // shows up without a restart.
        if (plugin.getBlueprints() != null) plugin.getBlueprints().load();
    }

    /**
     * Widens each settlement until its boundary covers the buildings that belong to it.
     * <p>
     * A fixed radius fits no real village: vanilla lays them out along a road, so the outlying
     * house sits well past the boundary on one axis while the other axis has room to spare. This
     * measures the buildings that were actually found and stretches each side to reach them, so
     * the border ends up around the settlement rather than through the middle of it.
     * <p>
     * <b>Only grows, and never moves the centre.</b> Shrinking would drop residents out of their
     * own village between one pass and the next, and moving the centre pushes the villagers
     * themselves out of the box they are looked up by — which leaves a village with no residents,
     * no unemployed, and so no election at all.
     */
    public void fitVillageBounds() {
        if (!isEnabled()) return;

        int configDefault = getDefaultRadius();
        int limit = configDefault * 2;

        for (Village village : villages.values()) {
            World world = village.getWorld();
            if (world == null || plugin.isDisabledIn(world)) continue;

            List<VillageBuildings.Footprint> buildings = VillageBuildings.detect(this, village);
            if (buildings.isEmpty()) continue;

            int wantX = 0;
            int wantZ = 0;

            for (VillageBuildings.Footprint building : buildings) {
                wantX = Math.max(wantX, Math.max(
                        Math.abs(building.minX() - village.getCenterX()),
                        Math.abs(building.maxX() - village.getCenterX())));
                wantZ = Math.max(wantZ, Math.max(
                        Math.abs(building.minZ() - village.getCenterZ()),
                        Math.abs(building.maxZ() - village.getCenterZ())));
            }

            // Breathing room, so a house sitting exactly on the line isn't half in and half out.
            if (village.growToFit(wantX + BOUNDARY_MARGIN, wantZ + BOUNDARY_MARGIN, configDefault, limit)) {
                invalidateResidents(village);
                VillageBuildings.invalidate(village);
            }
        }
    }

    /** How far past the outermost building the boundary is drawn, in blocks. */
    private static final int BOUNDARY_MARGIN = 6;

    public void detectVillages() {
        if (!isEnabled()) return;

        for (World world : Bukkit.getWorlds()) {
            if (plugin.isDisabledIn(world)) continue;

            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                Location meetingPoint = getMeetingPoint(villager);
                if (meetingPoint == null) continue;

                int before = villages.size();
                Village village = getOrCreateVillageAt(meetingPoint);

                if (village != null && villages.size() > before) {
                    plugin.getLogger().info("Found a new settlement at "
                            + village.getCenterX() + ", " + village.getCenterZ()
                            + " in " + world.getName() + ".");
                }
            }
        }
    }

    /**
     * Returns the village centred on this bell, registering a new one when it's unknown.
     *
     * @return {@code null} if the location has no world, since guessing one would file the
     * village under the wrong world and strand it there.
     */
    public @Nullable Village getOrCreateVillageAt(@NotNull Location bell) {
        World world = bell.getWorld();
        if (world == null) return null;

        // Whatever this world already knows, before deciding anything is new.
        ensureLoaded(world);

        Village existing = findByCenter(bell);
        if (existing != null) {
            // Keep an auto-detected centre in sync when the bell moves; overrides are respected.
            existing.setDetectedCenter(bell.getBlockX(), bell.getBlockY(), bell.getBlockZ());
            return existing;
        }

        Village village = new Village(
                UUID.randomUUID(),
                world.getName(),
                bell.getBlockX(),
                bell.getBlockY(),
                bell.getBlockZ());

        // Named once, here, and then stored. Generating it on demand instead would mean a
        // village's name depended on the biome still being whatever it was when it was found,
        // and would quietly rename settlements the mayor had already named.
        village.setName(VillageNames.generate(village.getCenter(), village.getId()));

        villages.put(village.getId(), village);
        return village;
    }

    private @Nullable Village findByCenter(@NotNull Location bell) {
        World world = bell.getWorld();
        if (world == null) return null;

        for (Village village : villages.values()) {
            if (!world.getName().equals(village.getWorldName())) continue;

            Location center = village.getCenter();
            if (center == null) continue;

            double dx = center.getX() - bell.getX();
            double dz = center.getZ() - bell.getZ();
            if (dx * dx + dz * dz <= (double) SAME_VILLAGE_TOLERANCE * SAME_VILLAGE_TOLERANCE) {
                return village;
            }
        }

        return null;
    }

    public void remove(@Nullable Village village) {
        if (village != null) villages.remove(village.getId());
    }

    // ── Residents ─────────────────────────────────────────────────────────────

    /**
     * Every villager currently inside the settlement.
     * <p>
     * Resolved live from the world, so it only ever covers loaded chunks — a village whose
     * chunks are unloaded reports no residents, which is the honest answer.
     * <p>
     * <b>Cached deliberately.</b> The underlying entity query is expensive and this is called
     * from menus, reputation maths and the election loop, several times per tick in the worst
     * case. Results are reused for a short window so those callers cost nothing extra; the
     * window is short enough that a villager walking in or out is picked up promptly.
     */
    public @NotNull List<Villager> getResidents(@Nullable Village village) {
        if (village == null) return Collections.emptyList();

        CachedResidents cached = residentCache.get(village.getId());
        long now = System.currentTimeMillis();

        if (cached != null && now < cached.expiresAt) {
            // Entities can die or despawn while cached, so never hand out stale references.
            List<Villager> alive = new ArrayList<>(cached.residents.size());
            for (Villager villager : cached.residents) {
                if (villager.isValid()) alive.add(villager);
            }
            return alive;
        }

        List<Villager> residents = queryResidents(village);
        residentCache.put(village.getId(), new CachedResidents(now + getCacheMillis(), residents));

        return new ArrayList<>(residents);
    }

    private @NotNull List<Villager> queryResidents(@NotNull Village village) {
        World world = village.getWorld();
        Location center = village.getCenter();
        if (world == null || center == null) return Collections.emptyList();

        int extentX = village.getExtentX(getDefaultRadius());
        int extentZ = village.getExtentZ(getDefaultRadius());

        // Bounded vertical range on purpose. Village#contains ignores Y, but querying the full
        // world height turns this into a ~128x768x128 box and wrecks the tick rate; settlements
        // are surface-level in practice, so a generous slab around the bell is the right trade.
        double height = Math.max(8, Config.VILLAGE_VERTICAL_RANGE.asInt(64));

        // Hoisted out of the loop: this reads from the config, and paying for that once per
        // nearby entity is exactly the kind of thing that shows up as lost ticks.
        int defaultRadius = getDefaultRadius();

        List<Villager> residents = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, extentX, height, extentZ)) {
            if (!(entity instanceof Villager villager)) continue;
            if (!village.contains(villager.getLocation(), defaultRadius)) continue;
            residents.add(villager);
        }

        return residents;
    }

    private long getCacheMillis() {
        return Math.max(250L, Config.VILLAGE_RESIDENT_CACHE_MILLIS.asInt(2000));
    }

    /**
     * Re-centres the settlement on its buildings.
     * <p>
     * <b>Not called automatically.</b> The centre also defines which villagers count as
     * residents, so moving it can push the villagers themselves outside their own village —
     * leaving no residents, no unemployed and therefore no election. Kept for a future centre
     * that is decoupled from the resident radius.
     * <p>
     * The bell only tells us a village is here; it is usually off to one side, so treating it as
     * the middle skews the whole map and can leave outlying buildings outside the boundary. The
     * centre of the buildings is the honest answer, and it doesn't move when someone picks the
     * bell up.
     * <p>
     * A centre pinned through the mayor's menu is left alone.
     */
    public void recenterOnBuildings(@Nullable Village village) {
        if (village == null || village.isCenterOverridden()) return;

        var buildings = VillageBuildings.detect(this, village);
        if (buildings.isEmpty()) return;

        double sumX = 0.0d;
        double sumZ = 0.0d;
        for (VillageBuildings.Footprint building : buildings) {
            sumX += building.centerX();
            sumZ += building.centerZ();
        }

        int x = (int) Math.round(sumX / buildings.size());
        int z = (int) Math.round(sumZ / buildings.size());

        // Nothing to do if it barely moved; re-centring every pass would make the map crawl.
        if (Math.abs(x - village.getCenterX()) <= 2 && Math.abs(z - village.getCenterZ()) <= 2) return;

        village.setDetectedCenter(x, village.getCenterY(), z);
        invalidateResidents(village);
    }

    /** Forces the next {@link #getResidents} call to hit the world again. */
    public void invalidateResidents(@Nullable Village village) {
        if (village != null) residentCache.remove(village.getId());
    }

    private record CachedResidents(long expiresAt, List<Villager> residents) {}

    public @NotNull List<IVillagerNPC> getResidentNPCs(@Nullable Village village) {
        List<IVillagerNPC> npcs = new ArrayList<>();
        for (Villager villager : getResidents(village)) {
            plugin.getConverter().getNPC(villager).ifPresent(npcs::add);
        }
        return npcs;
    }

    /** Residents with no job — the only pool elections may recruit candidates from. */
    public @NotNull List<Villager> getUnemployedResidents(@Nullable Village village) {
        List<Villager> unemployed = new ArrayList<>();
        for (Villager villager : getResidents(village)) {
            // Adults only — a child standing for mayor makes no sense.
            if (!villager.isAdult()) continue;
            if (SecondaryProfession.isUnemployed(villager)) unemployed.add(villager);
        }
        return unemployed;
    }

    // ── Mayor ─────────────────────────────────────────────────────────────────

    public boolean isMayor(@Nullable Village village, @Nullable UUID villagerId) {
        return village != null && villagerId != null && villagerId.equals(village.getMayor());
    }

    /**
     * Whether this villager holds the mayor's seat anywhere.
     * <p>
     * Deliberately cheap — it only walks the village list and compares ids. Callers on hot
     * paths (nametag/head-block rendering runs per player per spawn) must not pay for an
     * entity lookup or a meeting-point resolve.
     */
    public boolean isMayorOfAnyVillage(@Nullable UUID villagerId) {
        if (villagerId == null) return false;

        for (Village village : villages.values()) {
            if (villagerId.equals(village.getMayor())) return true;
        }
        return false;
    }

    /**
     * Finds a villager by id anywhere on the server.
     * <p>
     * Used instead of scanning a village's residents because a candidate can wander outside
     * the radius between an election opening and closing — and one that drifted off must
     * still be reset properly rather than being left in its campaign robes forever.
     */
    public @Nullable Villager findVillagerById(@Nullable UUID id) {
        if (id == null) return null;

        try {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Villager villager) return villager;
        } catch (Throwable ignored) {
            // Fall through to the per-village scan below.
        }

        for (Village village : villages.values()) {
            for (Villager villager : getResidents(village)) {
                if (villager.getUniqueId().equals(id)) return villager;
            }
        }
        return null;
    }

    /**
     * The player's standing with the mayor, per the spec's formula:
     * {@code R_mayor = (sum of reputation with every resident) / X}.
     * <p>
     * The mayor's own reputation is deliberately excluded — standing with the mayor is
     * earned by treating ordinary residents well, never by interacting with the mayor.
     */
    public int getMayorReputation(@Nullable Village village, @Nullable UUID player) {
        if (village == null || player == null) return 0;

        int sum = 0;
        for (IVillagerNPC npc : getResidentNPCs(village)) {
            if (isMayor(village, npc.getUniqueId())) continue;
            sum += npc.getReputation(player);
        }

        int divisor = Config.VILLAGE_MAYOR_REPUTATION_DIVISOR.asInt(10);
        if (divisor <= 0) divisor = 1;

        return sum / divisor;
    }

    public int getMayorReputation(@Nullable Village village, @Nullable Player player) {
        return player == null ? 0 : getMayorReputation(village, player.getUniqueId());
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void startSaveTask() {
        stopSaveTask();

        long seconds = Math.max(30L, Config.VILLAGE_SAVE_INTERVAL.asInt(300));
        long ticks = seconds * 20L;

        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                save();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    private void stopSaveTask() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    public void load() {
        villages.clear();
        loadedWorlds.clear();

        // Not before the worlds exist.
        //
        // Plugins are enabled first, so this runs once with nothing to read and again on the first
        // tick. Migrating during that first call would be destructive: the old file would be read,
        // written back out to no worlds at all, and then deleted — every village gone.
        if (Bukkit.getWorlds().isEmpty()) return;

        migrateLegacyFile();

        for (World world : Bukkit.getWorlds()) readWorld(world);
    }

    /**
     * Reads a world's villages the first time that world comes up.
     * <p>
     * Lazy on purpose. Reading everything at start-up depends on the worlds already being there,
     * and when they are not the list comes back empty and every settlement counts as new — which
     * is exactly how a village ends up registered twice with two different mayors.
     */
    public void ensureLoaded(@Nullable World world) {
        if (world != null) readWorld(world);
    }

    private void readWorld(@NotNull World world) {
        if (!loadedWorlds.add(world.getName())) return;

        File file = fileFor(world);
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("villages");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            Village village = Village.load(id, section);
            if (village != null) villages.put(village.getId(), village);
        }
    }

    /**
     * Moves a villages.yml left in the plugin folder into the world it describes.
     * <p>
     * Villages used to be kept centrally. Anyone upgrading would otherwise lose every mayor,
     * treasury and named settlement they had, so the old file is read once, split by world, and
     * taken away.
     */
    private void migrateLegacyFile() {
        File legacy = new File(plugin.getDataFolder(), "villages.yml");
        if (!legacy.isFile()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection root = config.getConfigurationSection("villages");

        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;

                Village village = Village.load(id, section);
                if (village != null) villages.put(village.getId(), village);
            }
        }

        // Written straight back out, into the worlds this time.
        save();

        if (!legacy.delete()) legacy.deleteOnExit();
        plugin.getLogger().info("Moved villages.yml out of the plugin folder and into the world(s) it describes.");
    }

    /** Writes each world's villages back into that world. */
    public void save() {
        for (World world : Bukkit.getWorlds()) {
            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection root = config.createSection("villages");

            int count = 0;
            for (Village village : villages.values()) {
                if (!world.getName().equals(village.getWorldName())) continue;

                village.save(root.createSection(village.getId().toString()));
                count++;
            }

            File file = fileFor(world);

            // A world with no villages gets no file rather than an empty one left lying about.
            if (count == 0) {
                if (file.isFile() && !file.delete()) file.deleteOnExit();
                continue;
            }

            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();

                config.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Couldn't save villages for " + world.getName()
                        + ": " + exception.getMessage());
            }
        }
    }

    /** Flushes to disk and stops background work; call from {@code onDisable}. */
    public void shutdown() {
        stopSaveTask();
        save();
    }
}
