package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raises commissioned buildings, a few blocks at a time.
 * <p>
 * Deliberately gradual. Materialising a finished house the instant it is paid for would make the
 * settlement's stores feel like a shop counter; watching it go up course by course is what makes
 * it read as the village doing the work. It is also what gives the mayor's build-time platform
 * something to act on.
 * <p>
 * Materials are taken <b>up front</b>, not per block. A half-built house whose funding ran out
 * mid-course would leave the village with a ruin it cannot finish and cannot recover, so the
 * commission is refused outright unless the stores cover the whole thing.
 */
public final class ConstructionManager {

    private final RealisticVillagers plugin;

    private final List<Site> sites = new ArrayList<>();
    private @Nullable BukkitTask task;

    /** How often a course of blocks is laid, in ticks. */
    private static final long TICK_INTERVAL = 10L;

    public ConstructionManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /** One building going up: where it stands, what it is, and how far along it is. */
    private static final class Site {

        private final UUID villageId;
        private final Blueprint blueprint;
        private final Location origin;
        private final int rotation;

        /** Blocks still to place, in build order — bottom course first. */
        private final List<int[]> pending;

        /** Every block of the finished building, kept for the settling pass at the end. */
        private final List<int[]> all;

        /**
         * Blocks laid per pass, kept fractional.
         * <p>
         * Rounding this up is what made buildings finish early: at 347 blocks over 172 passes the
         * true rate is 2.02 a pass, and laying 3 finished the job in 58 seconds against the 86 it
         * advertised — a third faster than the menu said, and faster still the smaller the
         * building. Carrying the fraction and laying whole blocks out of it keeps the total right.
         */
        private final double perPass;

        /** Left-over fraction of a block from the last pass. */
        private double carry;

        private Site(UUID villageId, Blueprint blueprint, Location origin, int rotation,
                     List<int[]> pending, double perPass) {
            this.all = List.copyOf(pending);
            this.villageId = villageId;
            this.blueprint = blueprint;
            this.origin = origin;
            this.rotation = rotation;
            this.pending = pending;
            this.perPass = perPass;
        }
    }

    /**
     * Starts a building, taking its materials from the settlement's stores.
     *
     * @param seconds how long it should take, already adjusted by the mayor's platform.
     * @return {@code false} when the stores can't cover it, in which case nothing is taken.
     */
    public boolean begin(
            @NotNull Village village,
            @NotNull Blueprint blueprint,
            @NotNull Location origin,
            int rotation,
            double costMultiplier,
            int seconds) {

        Map<Material, Integer> cost = scaledCost(blueprint, costMultiplier);
        VillageStorage storage = village.getStorage();

        // Checked in full before a single item is taken. Removing as we go and discovering the
        // shortfall on the last material would leave the stores raided for a building that never
        // gets built.
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            if (!storage.has(entry.getKey(), entry.getValue())) return false;
        }

        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            storage.remove(entry.getKey(), entry.getValue());
        }

        List<int[]> pending = new ArrayList<>();
        for (int y = 0; y < blueprint.getHeight(); y++) {
            for (int z = 0; z < blueprint.getDepth(rotation); z++) {
                for (int x = 0; x < blueprint.getWidth(rotation); x++) {
                    if (blueprint.blockAt(x, y, z, rotation) == null) continue;
                    pending.add(new int[]{x, y, z});
                }
            }
        }

        if (pending.isEmpty()) return false;

        // Spread over the requested time. At least one block a pass, or a very large building
        // with a very short timer would never finish at all.
        int passes = Math.max(1, (int) (seconds * 20L / TICK_INTERVAL));
        double perPass = Math.max(0.05d, pending.size() / (double) passes);

        sites.add(new Site(village.getId(), blueprint, origin, rotation, pending, perPass));
        start();

        return true;
    }

    /**
     * Whether this building would land on, or too close to, one that is already there.
     * <p>
     * The clearance is the point. Touching walls are not merely ugly: the scanner walks
     * connected columns, so two buildings sharing a wall come back as one, and the map, the
     * settlement's bounds and every later collision check are all working from that merged shape
     * afterwards. Keeping a gap is what stops a village slowly turning into one enormous
     * building on paper.
     * <p>
     * Terrain is deliberately not tested. Cutting a house into a hillside is a normal thing to
     * want, and a check that refused it would make half a village unbuildable.
     */
    public boolean wouldCollide(
            @NotNull VillageManager villages,
            @NotNull Village village,
            @NotNull Blueprint blueprint,
            @NotNull Location origin,
            int rotation) {

        if (!Config.VILLAGE_BUILD_COLLISION.asBool(true)) return false;

        int clearance = Math.max(0, Config.VILLAGE_BUILD_CLEARANCE.asInt(2));

        int minX = origin.getBlockX();
        int minY = origin.getBlockY();
        int minZ = origin.getBlockZ();
        int maxX = minX + blueprint.getWidth(rotation) - 1;
        int maxY = minY + blueprint.getHeight() - 1;
        int maxZ = minZ + blueprint.getDepth(rotation) - 1;

        // Grown sideways by the clearance and by one vertically. Sideways is where the scanner
        // joins things up; one block of headroom is enough to keep a roof from reading as the
        // floor of whatever sits on it.
        VillageBuildings.Footprint candidate = new VillageBuildings.Footprint(
                minX - clearance, minZ - clearance,
                maxX + clearance, maxZ + clearance,
                minY - 1, maxY + 1,
                0, 0, java.util.Set.of());

        for (VillageBuildings.Footprint existing : VillageBuildings.detect(villages, village)) {
            if (candidate.intersects(existing)) return true;
        }

        return false;
    }

    /**
     * Whether this spot lies outside the settlement paying for the building.
     * <p>
     * The stores belong to the village, so what they buy should stand in it. Without this a
     * player can empty a settlement's stores into a private build a hundred blocks away, which
     * is not commissioning a building from a village so much as taking one from it.
     * <p>
     * Only the corners are tested, since a box is inside a box exactly when its corners are.
     */
    public boolean isOutsideVillage(
            @NotNull VillageManager villages,
            @NotNull Village village,
            @NotNull Blueprint blueprint,
            @NotNull Location origin,
            int rotation) {

        if (!Config.VILLAGE_BUILD_INSIDE_ONLY.asBool(true)) return false;

        org.bukkit.World world = origin.getWorld();
        if (world == null) return true;

        int radius = villages.getDefaultRadius();

        int minX = origin.getBlockX();
        int minZ = origin.getBlockZ();
        int maxX = minX + blueprint.getWidth(rotation) - 1;
        int maxZ = minZ + blueprint.getDepth(rotation) - 1;

        for (int x : new int[]{minX, maxX}) {
            for (int z : new int[]{minZ, maxZ}) {
                if (!village.contains(new Location(world, x + 0.5d, origin.getY(), z + 0.5d), radius)) return true;
            }
        }

        return false;
    }

    /**
     * What a building costs under this mayor, in the materials it is actually made of.
     * <p>
     * No substitution happens any more. Swapping a building's materials to suit the local biome
     * produced things nobody designed — the more care had gone into a building, the worse the
     * result — so buildings are sorted by the region they suit instead, and each is raised in
     * exactly what it was built from.
     */
    public static @NotNull Map<Material, Integer> scaledCost(@NotNull Blueprint blueprint, double multiplier) {
        Map<Material, Integer> scaled = new java.util.EnumMap<>(Material.class);
        for (Map.Entry<Material, Integer> entry : blueprint.getCost().entrySet()) {
            scaled.put(entry.getKey(), Math.max(1, (int) Math.round(entry.getValue() * multiplier)));
        }
        return scaled;
    }

    private void start() {
        if (task != null) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    private void tick() {
        sites.removeIf(this::advance);

        if (sites.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Lays this site's next course. Returns whether it is finished (or can't continue). */
    private boolean advance(@NotNull Site site) {
        World world = site.origin.getWorld();
        if (world == null) return true;

        // Building into unloaded chunks would place blocks nobody can see being placed, and on a
        // server where nobody is near, quietly grind through the whole thing. Wait instead.
        if (!world.isChunkLoaded(site.origin.getBlockX() >> 4, site.origin.getBlockZ() >> 4)) return false;

        // Whole blocks out of the running total, so a rate below one a pass still advances —
        // just not on every pass.
        site.carry += site.perPass;

        int allowed = (int) site.carry;
        site.carry -= allowed;

        int laid = 0;
        while (laid < allowed && !site.pending.isEmpty()) {
            int[] at = site.pending.remove(0);

            org.bukkit.block.data.BlockData data = site.blueprint.blockAt(at[0], at[1], at[2], site.rotation);
            if (data == null) continue;

            Block block = world.getBlockAt(
                    site.origin.getBlockX() + at[0],
                    site.origin.getBlockY() + at[1],
                    site.origin.getBlockZ() + at[2]);

            place(block, data);
            laid++;
        }

        if (laid > 0) {
            try {
                world.playSound(site.origin, Sound.BLOCK_WOOD_PLACE, 0.6f, 1.0f);
            } catch (Throwable ignored) {
                // Sound names move between versions; the building still goes up.
            }
        }

        if (!site.pending.isEmpty()) return false;

        settle(site);

        // Finished: the village has a new building, so the scanner should look again rather than
        // serve the cached picture from before it existed.
        Village village = plugin.getVillageManager() == null ? null
                : plugin.getVillageManager().getVillage(site.villageId);

        if (village != null) VillageBuildings.invalidate(village);
        return true;
    }

    /**
     * Nudges every block of the finished building so it takes its neighbours into account.
     * <p>
     * Blocks are laid without neighbour updates, which is the only way to raise a building
     * without gravel falling and water running out of it mid-course. The cost is that anything
     * whose appearance depends on what is next to it never gets to look: stairs stay square
     * instead of forming corners, glass panes stay as isolated posts, and fences and walls stand
     * unjoined. That is the whole of "the stairs don't connect".
     * <p>
     * Rather than reimplementing each of those rules — and getting the corner cases subtly wrong
     * — every block is set to the state it already has, this time <i>with</i> a neighbour update.
     * That makes the game itself run its own shape logic on everything around it, so the
     * building settles into exactly the shape the same blocks would take if a player had placed
     * them by hand.
     */
    private void settle(@NotNull Site site) {
        World world = site.origin.getWorld();
        if (world == null) return;

        for (int[] at : site.all) {
            Block block = world.getBlockAt(
                    site.origin.getBlockX() + at[0],
                    site.origin.getBlockY() + at[1],
                    site.origin.getBlockZ() + at[2]);

            try {
                block.setBlockData(block.getBlockData(), true);
            } catch (Throwable ignored) {
                // A block that refuses the update is left as it was rather than failing the build.
            }
        }
    }

    /**
     * Lays one block.
     * <p>
     * Doors are <b>not</b> special-cased here, and used to be. When buildings were written out by
     * hand a door had to be completed for you, because only its lower half was ever described. A
     * structure file records both halves, so completing it as well placed a second door on top of
     * the first — a door inside a door, all the way up the wall. Whatever the file says is what
     * gets laid, and nothing more.
     * <p>
     * Panes, fences and walls are placed <b>with physics</b> so they join to their neighbours;
     * the settling pass at the end catches the rest.
     */
    private void place(@NotNull Block block, org.bukkit.block.data.@NotNull BlockData data) {
        block.setBlockData(data, data instanceof org.bukkit.block.data.MultipleFacing);
        if (Config.VILLAGE_BUILD_EMPTY_CONTAINERS.asBool(true)) empty(block);
    }

    /**
     * Empties a chest, barrel or furnace the moment it is placed.
     * <p>
     * A saved building carries the contents of its containers, and building it puts them back —
     * so a house recorded with a full chest is an item duplicator that can be run as many times
     * as the settlement can pay for the walls. The walls are the point of a blueprint; what
     * somebody left in a barrel is not.
     * <p>
     * Can be switched off. A build whose contents are part of how it works — a shop, a puzzle, a
     * prepared kit — is a real thing to want, and on a server where players cannot commission
     * buildings for free it is not a duplicator.
     */
    private void empty(@NotNull Block block) {
        try {
            if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                holder.getInventory().clear();
            }
        } catch (Throwable ignored) {
            // A block that won't answer about its inventory is left alone.
        }
    }

    /** Whether anything is being built in this settlement right now. */
    public boolean isBuilding(@NotNull Village village) {
        return sites.stream().anyMatch(site -> site.villageId.equals(village.getId()));
    }

    public void shutdown() {
        // Finish everything outstanding rather than abandoning it. A site left half-raised across
        // a restart is a ruin the plugin has no record of and no way to complete.
        for (Site site : sites) {
            World world = site.origin.getWorld();
            if (world == null) continue;

            for (int[] at : site.pending) {
                org.bukkit.block.data.BlockData data = site.blueprint.blockAt(at[0], at[1], at[2], site.rotation);
                if (data == null) continue;

                place(world.getBlockAt(
                        site.origin.getBlockX() + at[0],
                        site.origin.getBlockY() + at[1],
                        site.origin.getBlockZ() + at[2]), data);
            }

            settle(site);
        }

        sites.clear();

        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
