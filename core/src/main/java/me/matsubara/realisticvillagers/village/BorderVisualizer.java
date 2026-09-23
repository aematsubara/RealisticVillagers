package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a settlement's edge in the world, so a player can see where it actually ends.
 * <p>
 * Particles rather than entities: the ring is a few hundred points around a 64-block radius,
 * and spawning display entities for that would cost far more than it's worth for something
 * meant to be glanced at and switched off again.
 * <p>
 * Only the arc near the player is drawn each pass — the far side of a large village is behind
 * them and out of render distance anyway, and skipping it keeps the cost flat regardless of how
 * big the settlement grows.
 */
public final class BorderVisualizer {

    private final RealisticVillagers plugin;

    /** Players currently showing a border, and which village they asked about. */
    private final Map<UUID, UUID> watching = new ConcurrentHashMap<>();

    private @Nullable BukkitTask task;

    /** Points around the full circle. Spacing works out at roughly one every two blocks. */
    private static final int STEPS = 180;

    /** Only draw points within this distance of the player. */
    private static final double DRAW_DISTANCE = 48.0d;

    private static final double DRAW_DISTANCE_SQ = DRAW_DISTANCE * DRAW_DISTANCE;

    public BorderVisualizer(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /** Whether this player is currently shown a border. */
    public boolean isShowing(@NotNull Player player) {
        return watching.containsKey(player.getUniqueId());
    }

    /**
     * Turns the border on or off for a player.
     *
     * @return {@code true} if it is now showing.
     */
    public boolean toggle(@NotNull Player player, @Nullable Village village) {
        if (isShowing(player) || village == null) {
            watching.remove(player.getUniqueId());
            stopIfIdle();
            return false;
        }

        watching.put(player.getUniqueId(), village.getId());
        start();
        return true;
    }

    public void hide(@NotNull UUID playerId) {
        watching.remove(playerId);
        stopIfIdle();
    }

    private void start() {
        if (task != null) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                draw();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void stopIfIdle() {
        if (!watching.isEmpty() || task == null) return;
        task.cancel();
        task = null;
    }

    private void draw() {
        if (watching.isEmpty()) {
            stopIfIdle();
            return;
        }

        VillageManager villages = plugin.getVillageManager();
        if (villages == null) return;

        for (Map.Entry<UUID, UUID> entry : watching.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                watching.remove(entry.getKey());
                continue;
            }

            Village village = villages.getVillage(entry.getValue());
            if (village == null) {
                watching.remove(entry.getKey());
                continue;
            }

            drawFor(player, village, villages);
        }

        stopIfIdle();
    }

    private void drawFor(@NotNull Player player, @NotNull Village village, @NotNull VillageManager villages) {
        Location center = village.getCenter();
        if (center == null) return;

        World world = center.getWorld();
        if (world == null || !world.equals(player.getWorld())) return;

        // The settlement's real rectangle. Drawing a square around an oblong village put the
        // border through the middle of it on one axis and far out in open ground on the other.
        int extentX = village.getExtentX(villages.getDefaultRadius());
        int extentZ = village.getExtentZ(villages.getDefaultRadius());
        Location eye = player.getLocation();

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 220, 60), 1.6f);

        int steps = Math.max(STEPS, (extentX + extentZ) / 2);
        double perimeter = 4.0d * (extentX + extentZ);

        for (int i = 0; i < steps; i++) {
            double t = perimeter * i / steps;

            double x;
            double z;

            double sideX = 2.0d * extentX;
            double sideZ = 2.0d * extentZ;

            if (t < sideX) {                          // north edge, west → east
                x = center.getX() - extentX + t;
                z = center.getZ() - extentZ;
            } else if (t < sideX + sideZ) {           // east edge, north → south
                x = center.getX() + extentX;
                z = center.getZ() - extentZ + (t - sideX);
            } else if (t < sideX * 2.0d + sideZ) {    // south edge, east → west
                x = center.getX() + extentX - (t - sideX - sideZ);
                z = center.getZ() + extentZ;
            } else {                                  // west edge, south → north
                x = center.getX() - extentX;
                z = center.getZ() + extentZ - (t - sideX * 2.0d - sideZ);
            }

            double dx = x - eye.getX();
            double dz = z - eye.getZ();
            if (dx * dx + dz * dz > DRAW_DISTANCE_SQ) continue;

            // Sit the marker on the ground so the border follows the terrain rather than
            // hanging in the air at the bell's altitude.
            int groundY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));

            try {
                // A wall rather than a line on the floor. A single row of dust at ankle height
                // disappears behind the first hillock and is invisible from anywhere but directly
                // above it — the one place a player normally isn't. Stacking it makes the edge
                // something you can see across the settlement and walk up to.
                for (int up = 0; up < WALL_HEIGHT; up++) {
                    Location at = new Location(world, x, groundY + 1.0d + up * WALL_STEP, z);
                    player.spawnParticle(Particle.DUST, at, 1, 0.0d, 0.0d, 0.0d, 0.0d, dust);
                }
            } catch (Throwable ignored) {
                // Particle names have moved between versions; the border simply doesn't show.
                return;
            }
        }
    }

    /** How many dust markers are stacked at each point along the border. */
    private static final int WALL_HEIGHT = 4;

    /** Vertical gap between them, in blocks. */
    private static final double WALL_STEP = 0.9d;

    /**
     * Outlines a single building on the ground for a few seconds.
     * <p>
     * A one-shot burst rather than a toggle: it exists to answer "is the scanner actually right
     * about this building?", and for that you want to look once and compare, not manage another
     * thing that stays switched on.
     */
    public void outlineBuilding(@NotNull Player player, VillageBuildings.@NotNull Footprint building, @NotNull World world) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.4f);

        new BukkitRunnable() {
            int passes = 0;

            @Override
            public void run() {
                if (++passes > 12 || !player.isOnline() || !player.getWorld().equals(world)) {
                    cancel();
                    return;
                }

                // The real box, floor and roof both. Tracing it at ground level only showed where
                // a building stands but never how tall it is, which is half of what you are
                // checking when you ask the scanner to prove itself.
                for (int y : new int[]{building.minY(), building.maxY()}) {
                    for (int x = building.minX(); x <= building.maxX(); x++) {
                        mark(player, world, dust, x, y, building.minZ());
                        mark(player, world, dust, x, y, building.maxZ());
                    }
                    for (int z = building.minZ(); z <= building.maxZ(); z++) {
                        mark(player, world, dust, building.minX(), y, z);
                        mark(player, world, dust, building.maxX(), y, z);
                    }
                }

                // The four uprights, so the two rings read as one solid box.
                for (int y = building.minY(); y <= building.maxY(); y++) {
                    mark(player, world, dust, building.minX(), y, building.minZ());
                    mark(player, world, dust, building.minX(), y, building.maxZ());
                    mark(player, world, dust, building.maxX(), y, building.minZ());
                    mark(player, world, dust, building.maxX(), y, building.maxZ());
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private static void mark(@NotNull Player player, @NotNull World world, Particle.@NotNull DustOptions dust, int x, int y, int z) {
        try {
            player.spawnParticle(Particle.DUST, new Location(world, x + 0.5d, y + 0.5d, z + 0.5d), 1, 0.0d, 0.0d, 0.0d, 0.0d, dust);
        } catch (Throwable ignored) {
            // Particle names shift between versions; the outline simply doesn't show.
        }
    }

    public void shutdown() {
        watching.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
