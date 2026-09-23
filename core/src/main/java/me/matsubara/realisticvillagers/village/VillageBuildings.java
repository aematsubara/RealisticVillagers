package me.matsubara.realisticvillagers.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out where the settlement's buildings are, how big each one is, and what it is for.
 * <p>
 * Derived from what the villagers already remember — their bed ({@link MemoryKey#HOME}) and
 * their workstation ({@link MemoryKey#JOB_SITE}) — rather than by scanning blocks. Those
 * memories are free to read and mark exactly the places that matter to the settlement, so a
 * village's real shape falls out of them without touching a single chunk.
 * <p>
 * Points close together are merged into one footprint, which is what turns a bed and the
 * workstation beside it into a single house rather than two unrelated dots. Merging also
 * records what went in, so a building knows whether it is somewhere people sleep, work, or both.
 */
public final class VillageBuildings {

    private VillageBuildings() {
    }

    /** What a building is for. */
    public enum Type {
        /** People sleep here. */
        RESIDENTIAL,
        /** People work here. */
        WORKPLACE,
        /** Both — a home with a workstation in it. */
        MIXED,
        /** Detected, but nothing tells us what it does. */
        UNKNOWN
    }

    /**
     * A building's box in world block coordinates, plus what it holds.
     * <p>
     * A full box rather than a footprint: the vertical extent is what tells a wall from a fence
     * and a hut from a watchtower, and it is what anything placing a structure or testing whether
     * one would collide with another has to work against. Working it out during the scan costs
     * nothing — the blocks are already being read — whereas recovering it afterwards would mean
     * scanning the whole village a second time.
     *
     * @param minY          the floor: the level the building's rooms stand on.
     * @param maxY          the highest placed block: the roof, or the tip of whatever tops it.
     * @param beds          how many villagers sleep here.
     * @param workstations  how many villagers work here.
     * @param professions   the trades practised here, already prettified for display.
     */
    public record Footprint(
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int minY,
            int maxY,
            int beds,
            int workstations,
            Set<String> professions) {

        public double centerX() {
            return (minX + maxX) / 2.0d;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0d;
        }

        public double centerY() {
            return (minY + maxY) / 2.0d;
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public int height() {
            return maxY - minY + 1;
        }

        /** Whether this building's box overlaps another's — the test a placement has to pass. */
        public boolean intersects(@NotNull Footprint other) {
            return minX <= other.maxX && other.minX <= maxX
                    && minZ <= other.maxZ && other.minZ <= maxZ
                    && minY <= other.maxY && other.minY <= maxY;
        }

        /** Whether a block sits inside this building's box. */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public @NotNull Type type() {
            if (beds > 0 && workstations > 0) return Type.MIXED;
            if (beds > 0) return Type.RESIDENTIAL;
            if (workstations > 0) return Type.WORKPLACE;
            return Type.UNKNOWN;
        }
    }

    private static final long CACHE_MILLIS = 30_000L;

    private record Cached(long expiresAt, List<Footprint> footprints) {}

    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    /**
     * The settlement's buildings.
     * <p>
     * Cached for a good while: buildings change far more slowly than villagers move, and this
     * is read every time the radar is drawn.
     */
    public static @NotNull List<Footprint> detect(@NotNull VillageManager villages, @Nullable Village village) {
        if (village == null) return List.of();

        Cached cached = CACHE.get(village.getId());
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt) return cached.footprints;

        List<Footprint> footprints = sweep(villages, village);
        CACHE.put(village.getId(), new Cached(now + CACHE_MILLIS, footprints));

        return footprints;
    }

    public static void invalidate(@Nullable Village village) {
        if (village != null) CACHE.remove(village.getId());
    }

    /** One remembered place, and what it means to the villager that remembers it. */
    private record Point(int x, int z, boolean bed, @Nullable String profession) {}

    private static @NotNull List<Point> collectPoints(@NotNull VillageManager villages, @NotNull Village village) {
        List<Point> points = new ArrayList<>();

        for (Villager resident : villages.getResidents(village)) {
            Location home = memory(resident, MemoryKey.HOME);
            if (home != null) points.add(new Point(home.getBlockX(), home.getBlockZ(), true, null));

            Location job = memory(resident, MemoryKey.JOB_SITE);
            if (job != null) {
                // Take the trade from the villager rather than reading the workstation block:
                // it is the same answer without touching a chunk.
                points.add(new Point(job.getBlockX(), job.getBlockZ(), false, professionName(resident)));
            }
        }

        return points;
    }

    private static @Nullable String professionName(@NotNull Villager villager) {
        try {
            String raw = villager.getProfession().toString();
            int colon = raw.lastIndexOf(':');
            if (colon >= 0) raw = raw.substring(colon + 1);

            raw = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
            return raw.isEmpty() ? null : raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Location memory(@NotNull Villager villager, @NotNull MemoryKey<Location> key) {
        try {
            return villager.getMemory(key);
        } catch (Throwable ignored) {
            // Memory keys have shifted between versions; a village just shows fewer buildings.
            return null;
        }
    }

    // ── Area sweep ────────────────────────────────────────────────────────────

    /** Spacing of the probes laid across the village, in blocks. */
    private static final int PROBE_SPACING = 3;

    /**
     * Finds every building in the settlement by sweeping its whole area.
     * <p>
     * Probes are laid on a grid across the village and each one that lands on building material
     * grows into the structure around it. This is what makes an empty house, a church or a
     * watchtower show up at all — anchoring on villagers' beds and workstations only ever found
     * buildings that somebody happened to live or work in, and sized them by where the furniture
     * sat rather than where the walls are.
     * <p>
     * Villager memories are still folded in afterwards, purely to say what each building is
     * <i>for</i>: which ones people sleep in, which ones they work in, and at what trade.
     */
    private static @NotNull List<Footprint> sweep(@NotNull VillageManager villages, @NotNull Village village) {
        World world = village.getWorld();
        Location center = village.getCenter();
        if (world == null || center == null) return List.of();

        // Swept over the settlement's real rectangle, so a long village is scanned end to end
        // rather than only as far as its narrower side reaches.
        int radius = Math.max(village.getExtentX(villages.getDefaultRadius()),
                village.getExtentZ(villages.getDefaultRadius()));
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int span = radius * 2 + 1;

        // One visited grid shared by every probe in the village.
        //
        // Each probe used to carry its own, so a building wide enough to be hit by several probes
        // came back once per probe — the same walls measured over and over as partly-overlapping
        // shells, which is what stacked dozens of rectangles on top of each other on the map.
        // Sharing the grid means a building is walked by whichever probe reaches it first and
        // skipped by all the rest, so each structure is reported exactly once, at its true size.
        boolean[] visited = new boolean[span * span];
        List<int[]> found = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx += PROBE_SPACING) {
            for (int dz = -radius; dz <= radius; dz += PROBE_SPACING) {
                if (visited[index(dx, dz, radius, span)]) continue;

                int x = centerX + dx;
                int z = centerZ + dz;

                // Unloaded chunks report air, which would invent buildings out of nothing.
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                // Seeds have to stand on their own: an overhang extends a building, never starts one.
                if (columnExtent(world, x, z, false) == null) continue;

                int[] bounds = growStructure(world, visited, centerX, centerZ, radius, span, x, z);
                if (bounds == null) continue;

                // Lamp posts, well heads and lone fence blocks are two placed blocks in a column
                // too. They are not buildings, and on the map they are indistinguishable noise.
                if (bounds[2] - bounds[0] + 1 < MIN_FOOTPRINT && bounds[3] - bounds[1] + 1 < MIN_FOOTPRINT) continue;

                found.add(bounds);
            }
        }

        return describe(villages, village, found);
    }

    /** Position of a village-relative column in the shared visited grid. */
    private static int index(int dx, int dz, int radius, int span) {
        return (dz + radius) * span + (dx + radius);
    }

    /**
     * Attaches meaning to the scanned shells: who sleeps and works in each one.
     * <p>
     * Anything a villager remembers that falls inside a building's walls belongs to it, so a
     * building's purpose comes from the same memories as before — only now the shape comes from
     * the world instead of from the furniture.
     */
    private static @NotNull List<Footprint> describe(
            @NotNull VillageManager villages,
            @NotNull Village village,
            @NotNull List<int[]> shells) {

        List<Point> points = collectPoints(villages, village);
        List<Footprint> footprints = new ArrayList<>(shells.size());

        for (int[] shell : shells) {
            int beds = 0;
            int workstations = 0;
            Set<String> professions = new LinkedHashSet<>();

            for (Point point : points) {
                if (point.x() < shell[0] || point.x() > shell[2]) continue;
                if (point.z() < shell[1] || point.z() > shell[3]) continue;

                if (point.bed()) {
                    beds++;
                } else {
                    workstations++;
                    if (point.profession() != null) professions.add(point.profession());
                }
            }

            footprints.add(new Footprint(shell[0], shell[1], shell[2], shell[3], shell[4], shell[5],
                    beds, workstations, Set.copyOf(professions)));
        }

        return footprints;
    }

    // ── Structure scanning ────────────────────────────────────────────────────

    /**
     * Walks the one building around a seed column and returns its bounds.
     * <p>
     * Spreads across neighbouring columns that a village building is actually made of and takes
     * the bounding box of what it reaches, so the footprint on the map is the building's real
     * shape rather than a fixed box guessed around a bed or a workstation.
     * <p>
     * Every column it touches — including the empty ground it stops at — is marked in the
     * settlement-wide {@code visited} grid, so the probes that follow skip this building instead
     * of re-measuring parts of it as separate structures.
     * <p>
     * Bounded on every axis: a limited reach from the seed, a vertical band around it, and a cap
     * on how many columns are visited. Village buildings touch each other and share walls, so an
     * unbounded fill would happily swallow the entire settlement as a single rectangle.
     */
    private static @Nullable int[] growStructure(
            @NotNull World world,
            boolean[] visited,
            int centerX,
            int centerZ,
            int radius,
            int span,
            int seedX,
            int seedZ) {

        int minX = seedX;
        int maxX = seedX;
        int minZ = seedZ;
        int maxZ = seedZ;
        int maxY = Integer.MIN_VALUE;

        // Every column the building turns out to be made of, kept until the fill has finished.
        //
        // Where the floor is cannot be settled a column at a time. A column on its own cannot tell
        // the floor of a room from the ground under the eaves of the roof hanging over it: both are
        // open space with a placed block above them, and on anything but flat ground the ground
        // outside is the lower of the two — which is how the box's floor kept ending up in the
        // yard. What separates them is where the column sits in the building, inside the walls or
        // off the edge of them, and that is not known until the whole building has been walked.
        Map<Long, Column> members = new HashMap<>();

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{seedX, seedZ});
        visited[index(seedX - centerX, seedZ - centerZ, radius, span)] = true;

        int steps = 0;

        // Judged over the whole structure rather than column by column.
        //
        // An igloo's dome is solid snow everywhere except above its floor, so asking each column
        // on its own to prove itself hollow kept only the few in the middle and reported a 7x7
        // igloo as a 3x3 one. Asking it of the building means the dome is measured whole, and a
        // snowdrift — which has no room anywhere in it — is still thrown out entirely.
        boolean allAmbiguous = true;
        boolean anyHollow = false;

        while (!queue.isEmpty() && steps < SCAN_BUDGET) {
            int[] at = queue.poll();
            steps++;

            // Everything but the seed may be an overhang, which is how a roof keeps its eaves.
            Column column = columnExtent(world, at[0], at[1], at[0] != seedX || at[1] != seedZ);
            if (column == null) continue;

            if (!column.ambiguousOnly()) allAmbiguous = false;
            if (column.hollow()) anyHollow = true;

            members.put(key(at[0], at[1]), column);
            minX = Math.min(minX, at[0]);
            maxX = Math.max(maxX, at[0]);
            minZ = Math.min(minZ, at[1]);
            maxZ = Math.max(maxZ, at[1]);
            maxY = Math.max(maxY, column.maxY());

            for (int[] step : STEPS) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];

                // Keep the fill near its seed so a terrace of houses sharing walls stays several
                // buildings rather than collapsing into one block-long rectangle.
                if (Math.abs(nx - seedX) > SCAN_RADIUS || Math.abs(nz - seedZ) > SCAN_RADIUS) continue;

                int dx = nx - centerX;
                int dz = nz - centerZ;
                if (Math.abs(dx) > radius || Math.abs(dz) > radius) continue;

                int at2 = index(dx, dz, radius, span);
                if (visited[at2]) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                visited[at2] = true;
                queue.add(new int[]{nx, nz});
            }
        }

        if (members.isEmpty()) return null;

        // Nothing but snow and ice, and not a room anywhere in it: a drift, not an igloo.
        if (allAmbiguous && !anyHollow) return null;

        return new int[]{minX, minZ, maxX, maxZ, settleFloor(members), maxY};
    }

    /** Packs a column's world coordinates into one key, so its neighbours can be looked up. */
    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }

    /**
     * Where this building's floor is.
     * <p>
     * Asked of the columns the building encloses — the ones with more building on all four sides
     * of them — and of no others. A roof overhangs its walls, so the outermost columns of nearly
     * every building are eaves with the <i>surrounding</i> ground beneath them, and taking those at
     * their word is what put a house's floor out on the lawn it was standing above. Enclosed
     * columns are the inside of the building, and the inside is where the floor is.
     * <p>
     * Among those, the level most of them agree on. Floors are flat, so the real one is a level
     * repeated by half a room's worth of columns while a chest, a table or a step up to a loft is
     * one column each. Ties go to the lower level, so a room floored partly in planks and partly
     * in the ground it was built on settles on the ground, with the box holding both.
     * <p>
     * Buildings that enclose nothing — a wall, a bridge, a tower solid to the top — have no inside
     * to ask, and fall back to the lowest course that is standing on something. Standing on
     * something is what keeps the eaves out of the answer there too.
     */
    private static int settleFloor(@NotNull Map<Long, Column> members) {
        int base = commonBase(members);
        Map<Integer, Integer> votes = new HashMap<>();

        for (Map.Entry<Long, Column> entry : members.entrySet()) {
            int floor = entry.getValue().floorY();
            if (floor == NO_FLOOR) continue;
            if (!enclosed(members, entry.getKey())) continue;

            // Below the course the building stands on, so this is not a room in it.
            //
            // The enclosure test alone loses a roof that overhangs by a single block, which is
            // most of them. Two blocks of eave and the inner ring has building on all four sides
            // of it as surely as a bedroom does, while what lies under it is still the yard. A
            // yard lower than the house's own footings is the one thing that gives it away, and
            // on flat ground, where it does not, there is nothing to give away: the yard and the
            // floor are the same level and either answer is the right one.
            if (base != NO_FLOOR && floor < base - 1) continue;

            votes.merge(floor, 1, Integer::sum);
        }

        int floor = mostCommon(votes);
        if (floor != NO_FLOOR) return floor;

        int grounded = Integer.MAX_VALUE;
        int lowest = Integer.MAX_VALUE;

        for (Column column : members.values()) {
            lowest = Math.min(lowest, column.minY());
            if (column.grounded()) grounded = Math.min(grounded, column.minY());
        }

        return grounded != Integer.MAX_VALUE ? grounded : lowest;
    }

    /**
     * The level this building meets the ground at, or {@link #NO_FLOOR} where nothing tells us.
     * <p>
     * Taken from the columns that are standing on something rather than hanging off the side of
     * the roof — the walls, in other words, and the floor they enclose where one was laid. The
     * level most of them share is where the building was set down.
     */
    private static int commonBase(@NotNull Map<Long, Column> members) {
        Map<Integer, Integer> votes = new HashMap<>();
        for (Column column : members.values()) {
            if (column.grounded()) votes.merge(column.minY(), 1, Integer::sum);
        }

        return mostCommon(votes);
    }

    /**
     * The level the most columns agree on, ties going to the lower one.
     * <p>
     * Lower on a tie because these levels are floors and footings: a floor laid partly in planks
     * and partly in the ground it was built on settles on the ground, and the box holds both.
     */
    private static int mostCommon(@NotNull Map<Integer, Integer> votes) {
        int bestY = NO_FLOOR;
        int bestCount = 0;

        for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
            int count = vote.getValue();
            if (count > bestCount || (count == bestCount && vote.getKey() < bestY)) {
                bestCount = count;
                bestY = vote.getKey();
            }
        }

        return bestY;
    }

    /** Whether the building carries on in all four directions from this column. */
    private static boolean enclosed(@NotNull Map<Long, Column> members, long at) {
        int x = keyX(at);
        int z = keyZ(at);

        for (int[] step : STEPS) {
            if (!members.containsKey(key(x + step[0], z + step[1]))) return false;
        }

        return true;
    }

    /** Whether there is anything solid at this block at all — floor, wall or bare ground. */
    private static boolean supports(@NotNull World world, int x, int y, int z) {
        if (y < world.getMinHeight()) return false;

        Material type = world.getBlockAt(x, y, z).getType();
        return !type.isAir() && type.isSolid();
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** How far from the anchor the scan may reach, in blocks. */
    private static final int SCAN_RADIUS = 12;

    /** Hard cap on columns visited per building, so a shared wall can't run away. */
    private static final int SCAN_BUDGET = 1400;

    /** Smallest footprint that counts as a building, in blocks along its longer side. */
    private static final int MIN_FOOTPRINT = 3;

    /**
     * Hardest limit on how far down a column is read, in blocks.
     * <p>
     * A backstop, not the working limit — the scan normally stops as soon as it reaches ground.
     * A fixed window was tried first and does not work: whatever it is set to becomes the tallest
     * building the plugin can see, and a watchtower simply came back with its lower storeys cut
     * off. Anything that has to place a structure or test it for collisions needs the real height,
     * so the window has to follow the building rather than the building fit the window.
     */
    private static final int MAX_COLUMN_SCAN = 64;

    /**
     * Solid natural blocks in a row that mean the column has reached ground.
     * <p>
     * More than one, because a building can stand on a single natural block — a stone slab in a
     * floor, a log in a wall — without that being the bottom of it.
     */
    private static final int TERRAIN_RUN = 3;

    /** Stands in for "this column has no floor of its own", which is what an eave has. */
    private static final int NO_FLOOR = Integer.MIN_VALUE;

    /**
     * The tallest gap between a floor and what stands over it that still counts as a room.
     * <p>
     * A floor is recognised by there being building above it, so something has to say how far
     * above. Without a limit, the first solid block under a house built out over a ravine is the
     * bottom of the ravine, and the house is reported forty blocks tall with its floor down among
     * the bats. Eight is a generous room and nothing like a canyon.
     */
    private static final int MAX_ROOM_HEIGHT = 8;

    /**
     * How many player-placed blocks a column needs before it counts as part of a building.
     * <p>
     * This is what separates a building from a road. Village paths are laid in the same materials
     * as the houses beside them — desert roads are smooth sandstone, exactly what the walls are —
     * so "any player-placed block" called the road a building too, and since the road joins every
     * house in the settlement the fill ran the length of it. The box drawn around a stretch of
     * diagonal road covers a great deal of open ground, which is why outlines were turning up far
     * out in empty desert with nothing to outline.
     * <p>
     * A road is one block thick and a building is not: even its empty middle has a floor under it
     * and a roof over it. Counting placed blocks down the column tells the two apart without
     * having to know which materials a given village happens to be built from.
     * <p>
     * One place a building genuinely is one block thick: the overhanging edge of a roof, which is
     * a single course of stairs with nothing under it at all. Those are let through separately —
     * see {@code grounded} in {@link #columnExtent} — because what tells them from a road is not
     * how many blocks there are but what is underneath. They are let through for their shape
     * alone: the ground under an eave belongs to the yard rather than to the building, so those
     * columns are kept out of {@link #settleFloor}.
     */
    private static final int MIN_BUILDING_BLOCKS = 2;

    /**
     * Blocks that are equally a building and a landscape, and so are judged on shape instead.
     * <p>
     * Snow and ice only: an igloo is snow blocks, and so is a snowy slope. Every other material
     * either occurs naturally or doesn't, and can be decided on its name alone.
     */
    private static boolean isAmbiguous(@NotNull Material material) {
        return switch (material.name()) {
            case "SNOW_BLOCK", "PACKED_ICE", "ICE", "BLUE_ICE", "POWDER_SNOW" -> true;
            default -> false;
        };
    }

    /**
     * What this column is: its vertical span and what it is made of, or {@code null} when it is
     * terrain, or a path laid on top of it.
     * <p>
     * Measured from the column's own top downwards, not from a height carried over from wherever
     * the scan started. A band anchored on the seed sees only thin air above a house that stands
     * lower than it, and — worse — sees only the roof of the house it started on, missing the
     * floor below and so writing off the whole inside of the building.
     * <p>
     * The span is the reason this reports a range rather than a yes/no: gathering it here is what
     * gives buildings a height, and it is free because the blocks are being read anyway.
     */
    private static @Nullable Column columnExtent(@NotNull World world, int x, int z, boolean allowOverhang) {
        int top = world.getHighestBlockYAt(x, z);
        int stop = Math.max(world.getMinHeight(), top - MAX_COLUMN_SCAN);

        int placed = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int terrain = 0;

        // Snow and ice are both what an igloo is built from and what a snowy biome is made of,
        // so no list of materials can tell those two apart. What tells them apart is shape: an
        // igloo is hollow and a snowdrift is not. These two track that.
        int ambiguous = 0;
        boolean hollow = false;
        boolean gapSinceLastPlaced = false;

        // Open blocks since the last solid one, and the lowest floor found under such a gap.
        int gap = 0;
        int floor = NO_FLOOR;

        for (int y = top; y >= stop; y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            // Water ends the column outright.
            //
            // A settlement is not underwater, and water is not solid — so without this the scan
            // treats an ocean as so much empty space, falls thirty blocks through it and lands on
            // the planks of a shipwreck, which are as player-placed as any wall. The village then
            // gains a "building" out at sea that nobody built and nobody lives in.
            if (block.isLiquid()) break;

            boolean building = isBuildingMaterial(type);

            // Air and grass inside a building are not the bottom of it — a hollow tower is mostly
            // air. Only solid ground ends the column, and only a run of it, so a building resting
            // on one natural block isn't cut off at its own foundations.
            if (!building && (type.isAir() || !type.isSolid())) {
                if (type.isAir()) gapSinceLastPlaced = true;
                gap++;
                continue;
            }

            // Something solid with open space above it and building over that space is a floor
            // with a room standing on it — whether the floor was laid as one or is the bare ground
            // the house was put up on, which is how most village houses are floored. Keeping the
            // lowest such level in the column is what sets a two-storey house's box on its ground
            // floor rather than on its landing, and the height limit is what stops a house built
            // out over a hole from taking the bottom of the hole for its floor.
            if (gap > 0 && gap <= MAX_ROOM_HEIGHT && placed > 0) floor = y;
            gap = 0;

            if (building) {
                terrain = 0;
                placed++;
                if (isAmbiguous(type)) ambiguous++;

                // A placed block with open space beneath it and another placed block under that
                // is a roof over a room. Nothing natural is shaped like that.
                if (gapSinceLastPlaced && placed > 1) hollow = true;
                gapSinceLastPlaced = false;

                if (y < lowest) lowest = y;
                if (y > highest) highest = y;
                continue;
            }

            if (++terrain >= TERRAIN_RUN) break;
        }

        if (placed == 0) return null;

        // Whether the lowest course of this column is standing on anything.
        //
        // This is what separates the eaves of a roof from a road, and it does it without knowing
        // anything about either. A road lies on the ground; the edge of a roof hangs off the side
        // of a house with open air beneath it, and nothing in a village is built that way by
        // accident. Judging one-block columns by thickness alone lost every overhang, which is why
        // roofs were coming back with their sides shaved off.
        boolean grounded = supports(world, x, lowest - 1, z);

        // A single course counts only where it is hanging off something, and never as a seed —
        // see the caller — or a slab of path would be enough to start a building of its own.
        if (placed < MIN_BUILDING_BLOCKS && !(allowOverhang && !grounded)) return null;

        return new Column(lowest, highest, floor, ambiguous == placed, hollow, grounded);
    }

    /**
     * One column's verdict.
     *
     * @param minY          its lowest placed block: the bottom course of the building here.
     * @param maxY          its highest placed block.
     * @param floorY        the floor it stands on, or {@link #NO_FLOOR} where it has none — which
     *                      is the case for a solid wall, and for an eave hanging over open air.
     * @param ambiguousOnly nothing in it but snow and ice, so it cannot be judged on material.
     * @param hollow        it has open space enclosed between placed blocks: a roof over a room.
     * @param grounded      its lowest block rests on something rather than hanging off the side
     *                      of the building.
     */
    private record Column(int minY, int maxY, int floorY, boolean ambiguousOnly, boolean hollow, boolean grounded) {}


    /**
     * Whether this block looks like part of a building rather than terrain.
     * <p>
     * Works by <b>excluding nature</b> instead of listing what counts. A whitelist only ever
     * recognises the buildings someone thought of: it missed igloos entirely — snow isn't on any
     * "building material" list, so an igloo registered as nothing but its 1x1 door — and it can
     * never cover structures a datapack introduces. Ruling out the handful of blocks that occur
     * naturally leaves everything a builder placed, whoever placed it.
     * <p>
     * Extra materials can be forced either way from config, so a pack with unusual terrain or
     * unusual walls can be accommodated without a code change.
     */
    private static boolean isBuildingMaterial(@NotNull Material material) {
        if (material.isAir() || !material.isSolid()) return false;

        String name = material.name();

        // Config overrides win, in both directions.
        if (EXTRA_BUILDING.contains(name)) return true;
        if (EXTRA_NATURAL.contains(name)) return false;

        // Ores and their stone variants are terrain wherever they turn up.
        if (name.endsWith("_ORE") || name.startsWith("RAW_")) return false;

        // Trees. Matched by suffix because there is one of these per wood type and the list grows
        // with every update — and because a tree trunk is two or more logs stacked, which is
        // exactly the test a building has to pass, so leaving them out turned every tree near a
        // settlement into a building on the map.
        //
        // A log cabin is still found: its planks, stairs, doors and slabs all count.
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
                || name.endsWith("_LEAVES") || name.endsWith("_SAPLING")
                || name.endsWith("_MUSHROOM_BLOCK") || name.equals("MUSHROOM_STEM")
                // Mangrove roots, plain and muddy. They grow in tall clumps, which is exactly the
                // shape a wall has, so a mangrove swamp came out as a settlement full of houses.
                || name.endsWith("_ROOTS")) {
            return false;
        }

        // Reef. Solid, natural, and one block per colour, so matched by name rather than listed.
        //
        // Prismarine and sea lanterns are deliberately NOT here: an ocean monument is underwater
        // and the water rule already rules it out, whereas a player building in prismarine on dry
        // land means it.
        if (name.contains("CORAL")) return false;

        return !NATURAL.contains(name);
    }

    /**
     * Whether this is ground rather than something built on it.
     * <p>
     * The same question the scan asks of every block, turned outwards: a building saved with a
     * margin around it carries a ring of whatever it was standing next to, and reading that ring
     * is how the ground it sat in is recognised later, with the building long gone.
     */
    public static boolean isGround(@NotNull Material material) {
        return !material.isAir() && material.isSolid() && !isBuildingMaterial(material);
    }

    /**
     * Blocks that occur naturally and therefore never imply a building.
     * <p>
     * Logs and leaves are here because trees vastly outnumber log cabins; a cabin is still found
     * by its planks, stairs and door.
     */
    private static final Set<String> NATURAL = Set.of(
            "STONE", "DEEPSLATE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK",
            "GRANITE", "DIORITE", "ANDESITE", "BASALT", "BLACKSTONE", "NETHERRACK", "END_STONE",
            "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM", "GRASS_BLOCK", "MUD",
            "SAND", "RED_SAND", "GRAVEL", "CLAY", "SOUL_SAND", "SOUL_SOIL",
            "SANDSTONE", "RED_SANDSTONE",
            "OBSIDIAN", "MAGMA_BLOCK", "BEDROCK",
            "SNOW", "POWDER_SNOW", "ICE", "BLUE_ICE", "FROSTED_ICE",
            "MOSS_BLOCK", "SCULK", "AMETHYST_BLOCK", "BUDDING_AMETHYST",
            "COBWEB", "PUMPKIN", "MELON", "CACTUS", "BAMBOO_BLOCK", "SPONGE",

            // Ground a villager walks or farms on. Placed by hand, but never a building.
            "DIRT_PATH", "FARMLAND");

    /** Extra materials the server insists are building blocks. */
    private static Set<String> EXTRA_BUILDING = Set.of();

    /** Extra materials the server insists are terrain. */
    private static Set<String> EXTRA_NATURAL = Set.of();

    /**
     * Applies the configured material overrides.
     * <p>
     * Called on load and reload so a pack's own blocks can be classified without touching code.
     */
    public static void loadMaterialOverrides(@Nullable java.util.List<String> building, @Nullable java.util.List<String> natural) {
        EXTRA_BUILDING = normalise(building);
        EXTRA_NATURAL = normalise(natural);
        CACHE.clear();
    }

    private static @NotNull Set<String> normalise(@Nullable java.util.List<String> names) {
        if (names == null || names.isEmpty()) return Set.of();

        Set<String> out = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            Material material = Material.matchMaterial(name.trim());
            if (material != null) out.add(material.name());
        }
        return Set.copyOf(out);
    }
}
