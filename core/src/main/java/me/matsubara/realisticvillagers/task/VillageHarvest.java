package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.village.SecondaryProfession;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Work that actually changes the world: felling trees and cutting cactus, and putting something
 * back each time.
 * <p>
 * Separate from {@link VillageEconomy} on purpose, and the two are not the same idea wearing
 * different hats. The economy credits a villager for its trade — the goods appear in the stores
 * and no block moves. This takes blocks out of the world and returns a sapling or a cactus in
 * their place, so a lumberjack thins the woods around a settlement over time and the woods grow
 * back. That is a thing a player can walk out and look at, which is the whole point of it.
 * <p>
 * <b>Still not a villager pathing to a tree and swinging an axe.</b> The worker has to be near
 * the tree it fells, so what happens is tied to where the villager actually is, but it does not
 * walk there on purpose — that needs behaviour at the AI level, in every version module. This is
 * the honest half that can be done without it: real blocks, real regrowth, tied to a real worker.
 */
public final class VillageHarvest {

    private final RealisticVillagers plugin;
    private final VillageManager villages;

    private @Nullable BukkitTask task;

    /** How far from itself a worker will take a tree or a cactus, in blocks. */
    private static final int REACH = 10;

    /**
     * Most logs one felling takes.
     * <p>
     * Generous enough for the biggest thing that grows — a dark oak stand or a 2x2 jungle tree
     * runs well past a hundred logs — and still a hard stop, so a tick can't be spent unwinding
     * a forest whose canopies all touch.
     */
    private static final int MAX_LOGS = 300;

    /** How far to either side of the trunk the fill may reach, in blocks. */
    private static final int TREE_SPREAD = 8;

    /** How far above the foot of the trunk the fill may reach, in blocks. */
    private static final int TREE_HEIGHT = 40;

    /** Most leaf blocks one felling clears. A dark oak crown runs to several hundred. */
    private static final int MAX_LEAVES = 700;

    /** Saplings by log, so what grows back is what was cut down. */
    private static @Nullable java.util.Map<Material, Material> saplings;

    public VillageHarvest(@NotNull RealisticVillagers plugin, @NotNull VillageManager villages) {
        this.plugin = plugin;
        this.villages = villages;
    }

    public void start() {
        stop();

        long interval = Math.max(20L, Config.VILLAGE_HARVEST_INTERVAL.asInt(45) * 20L);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void tick() {
        if (!villages.isEnabled() || !Config.VILLAGE_HARVEST_ENABLED.asBool(true)) return;

        for (Village village : villages.getVillages()) {
            World world = village.getWorld();
            if (world == null || plugin.isDisabledIn(world)) continue;
            if (world.getTime() >= 12000L) continue;

            work(village);
        }
    }

    private void work(@NotNull Village village) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (Villager resident : villages.getResidents(village)) {
            if (villages.isMayor(village, resident.getUniqueId())) continue;

            SecondaryProfession role = SecondaryProfession.of(resident);
            if (role == null) continue;

            switch (role) {
                case LUMBERJACK -> {
                    if (random.nextDouble() < 0.5d) fellNearbyTree(village, resident);
                }
                // Cactus is what camels are led with, so somebody has to keep cutting it — and
                // replanting, or a desert settlement strips its own supply within a few days.
                case FOOD_SUPPLIER, FISHER -> {
                    if (random.nextDouble() < 0.35d) cutNearbyCactus(village, resident);
                }
                default -> {
                }
            }
        }
    }

    // ── Trees ─────────────────────────────────────────────────────────────────

    /**
     * Fells one tree near the worker and plants a sapling where its trunk stood.
     * <p>
     * The whole tree goes at once rather than a block per round: a half-felled tree left standing
     * for minutes looks like griefing, and the sapling can only be planted once the trunk is out
     * of the way.
     */
    private void fellNearbyTree(@NotNull Village village, @NotNull Villager worker) {
        Block trunk = findTrunk(worker);
        if (trunk == null) return;
        if (!withinVillage(village, trunk.getLocation())) return;
        if (!nearWorkstation(worker, trunk.getLocation())) return;

        Material log = trunk.getType();
        Location base = trunk.getLocation();

        int felled = 0;

        // Leaves touching a log that comes out, kept so the canopy can be cleared afterwards.
        //
        // Leaving them to vanilla's decay does not work. Decay runs on random block ticks, so a
        // canopy hangs there unsupported for minutes — the tree looks half-cut long after the
        // trunk is gone, which is exactly what a felling is meant not to look like.
        Set<Block> leafSeeds = new HashSet<>();

        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(trunk);
        seen.add(trunk);

        // The budget counts logs actually taken, not blocks looked at.
        //
        // Counting the seen set is what left trees hanging in mid-air: every block examined queues
        // up to twenty-six neighbours, so the set hit its limit after a handful of logs and the
        // fill stopped with the trunk gone and the crown still up there.
        while (!queue.isEmpty() && felled < MAX_LOGS) {
            Block at = queue.poll();
            if (at.getType() != log) continue;

            at.setType(Material.AIR);
            felled++;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block next = at.getRelative(dx, dy, dz);

                        if (isLeaf(next.getType())) {
                            leafSeeds.add(next);
                            continue;
                        }

                        if (next.getType() != log) continue;
                        if (!seen.add(next)) continue;

                        // Bounded around the trunk, not by straight-line distance from its foot.
                        // A jungle tree is thirty blocks tall and four wide, so one radius for
                        // both axes either cuts the top off or reaches into the next tree along.
                        int dxFromBase = Math.abs(next.getX() - base.getBlockX());
                        int dzFromBase = Math.abs(next.getZ() - base.getBlockZ());
                        int dyFromBase = next.getY() - base.getBlockY();

                        if (dxFromBase > TREE_SPREAD || dzFromBase > TREE_SPREAD) continue;
                        if (dyFromBase < -2 || dyFromBase > TREE_HEIGHT) continue;

                        queue.add(next);
                    }
                }
            }
        }

        if (felled <= 0) return;

        clearCanopy(leafSeeds, base);

        village.getStorage().add(log, felled);
        replant(base, log);
    }

    /**
     * Takes down the canopy the felled trunk was holding up.
     * <p>
     * Spreads from the leaves that were touching the trunk, so it clears this tree's crown and
     * stops at the edge of the next one along — and it is bounded to the same box the trunk was,
     * so a forest whose canopies all touch can't be unravelled in one go.
     * <p>
     * Leaves a player placed are left alone: those are marked persistent precisely because they
     * are part of a build rather than a tree, and taking them would make a lumberjack something
     * to be defended against.
     */
    private void clearCanopy(@NotNull Set<Block> seeds, @NotNull Location base) {
        Set<Block> seen = new HashSet<>(seeds);
        ArrayDeque<Block> queue = new ArrayDeque<>(seeds);

        int cleared = 0;

        while (!queue.isEmpty() && cleared < MAX_LEAVES) {
            Block at = queue.poll();
            if (!isLeaf(at.getType())) continue;
            if (isPersistent(at)) continue;

            at.setType(Material.AIR);
            cleared++;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block next = at.getRelative(dx, dy, dz);
                        if (!isLeaf(next.getType())) continue;
                        if (!seen.add(next)) continue;

                        if (Math.abs(next.getX() - base.getBlockX()) > TREE_SPREAD + 1) continue;
                        if (Math.abs(next.getZ() - base.getBlockZ()) > TREE_SPREAD + 1) continue;

                        int dy2 = next.getY() - base.getBlockY();
                        if (dy2 < -2 || dy2 > TREE_HEIGHT) continue;

                        queue.add(next);
                    }
                }
            }
        }
    }

    private static boolean isLeaf(@NotNull Material material) {
        return material.name().endsWith("_LEAVES");
    }

    /** Whether these leaves were placed by hand rather than grown. */
    private static boolean isPersistent(@NotNull Block block) {
        try {
            return block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves
                    && leaves.isPersistent();
        } catch (Throwable ignored) {
            // Unknown block data: treat it as a build and leave it standing.
            return true;
        }
    }

    /** A log the worker can reach, standing on dirt so it is a tree rather than a wall. */
    private @Nullable Block findTrunk(@NotNull Villager worker) {
        World world = worker.getWorld();
        Location at = worker.getLocation();

        List<Block> found = new ArrayList<>();

        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    Block block = world.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (!isLog(block.getType())) continue;

                    // Only the foot of a trunk, so the fill starts at the bottom and the sapling
                    // goes back where the tree actually grew.
                    Block below = block.getRelative(BlockFace.DOWN);
                    if (isLog(below.getType())) continue;
                    if (!isSoil(below.getType())) continue;

                    found.add(block);
                }
            }
        }

        if (found.isEmpty()) return null;
        return found.get(ThreadLocalRandom.current().nextInt(found.size()));
    }

    private void replant(@NotNull Location base, @NotNull Material log) {
        Block spot = base.getBlock();
        if (spot.getType() != Material.AIR) return;
        if (!isSoil(spot.getRelative(BlockFace.DOWN).getType())) return;

        Material sapling = saplings().get(log);
        if (sapling == null) return;

        spot.setType(sapling);
    }

    private static boolean isLog(@NotNull Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM");
    }

    private static boolean isSoil(@NotNull Material material) {
        return switch (material.name()) {
            case "GRASS_BLOCK", "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM", "MOSS_BLOCK" -> true;
            default -> false;
        };
    }

    private static @NotNull java.util.Map<Material, Material> saplings() {
        java.util.Map<Material, Material> map = saplings;
        if (map != null) return map;

        java.util.Map<Material, Material> built = new java.util.HashMap<>();
        for (String wood : new String[]{"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK"}) {
            Material log = Material.matchMaterial(wood + "_LOG");
            Material sapling = Material.matchMaterial(wood + "_SAPLING");

            // A wood this version doesn't have simply isn't replanted; the felling still works.
            if (log != null && sapling != null) built.put(log, sapling);
        }

        return saplings = java.util.Map.copyOf(built);
    }

    // ── Cactus ────────────────────────────────────────────────────────────────

    /**
     * Takes the top off a nearby cactus and leaves the rest standing.
     * <p>
     * Cutting only the head is what makes this sustainable without any replanting logic at all:
     * the stump is still rooted in sand and grows back on its own, exactly as it does when a
     * player harvests one. Clearing the whole plant would strip a desert settlement of the thing
     * its camels are led with.
     */
    private void cutNearbyCactus(@NotNull Village village, @NotNull Villager worker) {
        World world = worker.getWorld();
        Location at = worker.getLocation();

        List<Block> heads = new ArrayList<>();

        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    Block block = world.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (block.getType() != Material.CACTUS) continue;

                    // The head, and only where something is left underneath to regrow from.
                    if (block.getRelative(BlockFace.UP).getType() == Material.CACTUS) continue;
                    if (block.getRelative(BlockFace.DOWN).getType() != Material.CACTUS) continue;

                    if (!withinVillage(village, block.getLocation())) continue;
                    if (!nearWorkstation(worker, block.getLocation())) continue;

                    heads.add(block);
                }
            }
        }

        if (heads.isEmpty()) return;

        Block head = heads.get(ThreadLocalRandom.current().nextInt(heads.size()));
        head.setType(Material.AIR);

        village.getStorage().add(Material.CACTUS, 1);
    }

    /**
     * Whether this spot is close enough to the settlement to harvest.
     * <p>
     * Asked of the tree or the cactus itself, not the worker standing near it: a resident a step
     * inside the boundary can still reach a trunk a step past it through {@link #REACH}, and what
     * a player actually objects to is a tree outside the village coming down, not where the
     * villager who did it happened to be standing.
     * <p>
     * {@code village.harvest.limit-to-village} is the escape hatch, for a server that wants
     * harvesting to follow its residents wherever they wander instead.
     */
    private boolean withinVillage(@NotNull Village village, @NotNull Location location) {
        return !Config.VILLAGE_HARVEST_LIMIT_TO_VILLAGE.asBool(true)
                || village.contains(location, villages.getDefaultRadius());
    }

    /**
     * Whether this spot is close enough to the worker's own workstation.
     * <p>
     * Checked separately from {@link #withinVillage}, and stricter: a village's radius can easily
     * hold a whole decorative forest that no fletcher's bench is anywhere near, and that is
     * exactly the case a village-wide boundary alone cannot rule out. Anchoring on the job site
     * instead — the fletching table a lumberjack (a Fletcher, under the hood) is actually
     * assigned to — is what a "job", rather than a resident who happens to be standing nearby,
     * really means here.
     * <p>
     * A worker with nothing memorised as her job site right now fails this outright rather than
     * being let through: there is no bench to measure "near" from, and a lumberjack between jobs
     * felling trees regardless is exactly the unbounded behaviour this exists to end.
     */
    private boolean nearWorkstation(@NotNull Villager worker, @NotNull Location location) {
        int radius = Config.VILLAGE_HARVEST_WORKSTATION_RADIUS.asInt(24);
        if (radius <= 0) return true;

        Location job;
        try {
            job = worker.getMemory(MemoryKey.JOB_SITE);
        } catch (Throwable ignored) {
            // Memory keys have moved between versions; without one to check against, this worker
            // is left alone rather than treated as having no bounds at all.
            return false;
        }

        if (job == null) return false;

        World jobWorld = job.getWorld();
        if (jobWorld == null || !jobWorld.equals(location.getWorld())) return false;

        return job.distanceSquared(location) <= (double) radius * radius;
    }

    public void shutdown() {
        stop();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
