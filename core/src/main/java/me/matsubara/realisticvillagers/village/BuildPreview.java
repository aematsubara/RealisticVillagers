package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ghost of a building, shown where it would stand before anyone pays for it.
 * <p>
 * Commissioning without this is asking a player to spend the settlement's stores on a guess:
 * where a building lands, which way it faces, and whether it will sit in a hillside are things
 * you can only really judge by looking at it. So the ghost follows where they look, turns on
 * command, and is only charged for once they say yes.
 * <p>
 * Built from block displays, which are see-through-able, collide with nothing and can be thrown
 * away without touching the world — no blocks are placed until the commission is confirmed, so a
 * cancelled preview leaves not a trace behind.
 */
public final class BuildPreview {

    private final RealisticVillagers plugin;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private @Nullable BukkitTask task;

    /** How far out in front of the player the ghost starts, in blocks. */
    private static final double DEFAULT_RANGE = 12.0d;

    private static final double MIN_RANGE = 3.0d;
    private static final double MAX_RANGE = 48.0d;

    /**
     * Most displays one ghost uses.
     * <p>
     * A large blueprint is thousands of blocks and one entity each would be unplayable. Past this
     * the preview shows the shell only — the outside faces — which is what tells you where the
     * building sits and which way it faces anyway.
     */
    private static final int MAX_DISPLAYS = 500;

    private static final float GHOST_SCALE = 0.92f;

    public BuildPreview(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /** One player lining up one building. */
    private static final class Session {

        private final UUID villageId;
        private final Blueprint blueprint;
        private int rotation;

        private final List<BlockDisplay> ghost = new ArrayList<>();
        private @Nullable Location placedAt;

        /** Whether the spot it currently sits on is refused. */
        private boolean blocked;

        /**
         * Set once the player has picked a spot, after which the ghost stops following them.
         * <p>
         * Placing and confirming are separate on purpose. While the ghost tracks your view the
         * building moves with every twitch of the mouse, so the click that commits it is aimed at
         * a target that is still moving — pinning it first lets you walk round the thing and look
         * at it before spending the settlement's stores.
         */
        private boolean pinned;

        /** How far out the ghost is held, changed by scrolling. */
        private double distance = DEFAULT_RANGE;

        private Session(UUID villageId, Blueprint blueprint) {
            this.villageId = villageId;
            this.blueprint = blueprint;
        }
    }

    public boolean isPreviewing(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Starts lining up {@code blueprint}; replaces whatever the player was previewing. */
    public void start(@NotNull Player player, @NotNull Village village, @NotNull Blueprint blueprint) {
        cancel(player);

        sessions.put(player.getUniqueId(), new Session(village.getId(), blueprint));
        instruct(player, blueprint);

        if (task == null) {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    tick();
                }
            }.runTaskTimer(plugin, 1L, 5L);
        }
    }

    /** Pushes the ghost further out or pulls it in. Ignored once it is pinned. */
    public void push(@NotNull Player player, double amount) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.pinned) return;

        session.distance = Math.max(MIN_RANGE, Math.min(MAX_RANGE, session.distance + amount));
    }

    /**
     * Pins the ghost where it stands.
     *
     * @return {@code false} if it was already pinned, which is the caller's cue to build.
     */
    public boolean pin(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.pinned || session.placedAt == null) return false;

        session.pinned = true;
        return true;
    }

    /**
     * Lets the ghost follow the player again.
     *
     * @return whether it was pinned, so the caller can tell "unpinned" from "nothing to undo".
     */
    public boolean unpin(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.pinned) return false;

        session.pinned = false;
        return true;
    }

    public boolean isPinned(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.pinned;
    }

    public void rotate(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        session.rotation = (session.rotation + 1) % 4;

        // Turning a pinned ghost has to redraw it here: the update loop leaves pinned ghosts
        // alone, so nothing else would ever put it back.
        if (session.pinned && session.placedAt != null) {
            clearGhost(session);
            draw(session, session.placedAt);
            return;
        }

        // The whole ghost has to be rebuilt: turning it changes which block sits where, not just
        // the angle it is drawn at.
        clearGhost(session);
    }

    /** Whether the ghost is currently sitting somewhere it cannot be built. */
    public boolean isBlocked(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.blocked;
    }

    /**
     * Confirms the build.
     *
     * @return the spot it was placed at, or {@code null} if there was nothing to confirm or the
     * spot is refused.
     */
    public @Nullable Placement confirm(@NotNull Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.placedAt == null) return null;

        // Refused rather than quietly allowed. The ghost has been red for as long as it has been
        // in a bad spot, so this is confirming what the player is already looking at.
        if (session.blocked) return null;

        Placement placement = new Placement(session.villageId, session.blueprint,
                session.placedAt.clone(), session.rotation);

        cancel(player);
        return placement;
    }

    /** Where a confirmed building goes, and which way round. */
    public record Placement(UUID villageId, Blueprint blueprint, Location origin, int rotation) {}

    public void cancel(@NotNull Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) clearGhost(session);

        if (sessions.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        sessions.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                clearGhost(entry.getValue());
                return true;
            }

            update(player, entry.getValue());
            return false;
        });
    }

    private void update(@NotNull Player player, @NotNull Session session) {
        // Pinned means the player has chosen; nothing about the spot changes after that.
        if (session.pinned) return;

        Location target = aim(player, session);
        if (target == null) return;

        // Only redraw when it has actually moved a block. Rebuilding several hundred displays
        // every pass is what would turn a preview into a slideshow.
        if (session.placedAt != null && session.placedAt.getBlockX() == target.getBlockX()
                && session.placedAt.getBlockY() == target.getBlockY()
                && session.placedAt.getBlockZ() == target.getBlockZ()
                && !session.ghost.isEmpty()) {
            return;
        }

        session.placedAt = target;

        Village village = plugin.getVillageManager() == null ? null
                : plugin.getVillageManager().getVillage(session.villageId);

        session.blocked = village != null
                && plugin.getConstructionManager() != null
                && (plugin.getConstructionManager().wouldCollide(
                        plugin.getVillageManager(), village, session.blueprint, target, session.rotation)
                || plugin.getConstructionManager().isOutsideVillage(
                        plugin.getVillageManager(), village, session.blueprint, target, session.rotation));

        clearGhost(session);
        draw(session, target);
    }

    /**
     * Where the player is pointing, snapped so the building sits on the ground.
     * <p>
     * Centred on their aim rather than cornered on it: a building lines up by its middle, and
     * having it grow off to one side of the cursor makes it far harder to place.
     * <p>
     * Landed on the block being pointed at, or on top of it, according to how the building stood
     * in its own ground — see {@link Blueprint#lift()}. A house whose floor was laid on the turf
     * goes back on top of the turf; one whose floor replaced the turf replaces it again.
     * <p>
     * Asked of the building rather than fixed, because both kinds are out there in the same
     * village and neither can be told from the other in the menu. Laying them all the same way
     * buried most of them a block deep with their floors below the grass line, and the only way
     * to find out was to build one and look at it.
     */
    private @Nullable Location aim(@NotNull Player player, @NotNull Session session) {
        World world = player.getWorld();

        // Out along the player's view by however far they have scrolled, then dropped onto
        // whatever is underneath — so pushing the ghost across a valley lands it on the far side
        // rather than leaving it hanging in the air.
        Location eye = player.getEyeLocation();
        Location base = eye.clone().add(eye.getDirection().multiply(session.distance));

        // Zero for a building that was cut into the ground, one for a building that stood on it.
        int lift = session.blueprint.lift();

        var hit = player.rayTraceBlocks(session.distance);
        if (hit != null && hit.getHitBlock() != null) {
            base = hit.getHitBlock().getLocation().add(0.0d, lift, 0.0d);
        } else {
            base.setY(world.getHighestBlockYAt(base.getBlockX(), base.getBlockZ()) + lift);
        }

        int width = session.blueprint.getWidth(session.rotation);
        int depth = session.blueprint.getDepth(session.rotation);

        return new Location(world,
                base.getBlockX() - width / 2,
                base.getBlockY(),
                base.getBlockZ() - depth / 2);
    }

    private void draw(@NotNull Session session, @NotNull Location origin) {
        World world = origin.getWorld();
        if (world == null) return;

        Blueprint blueprint = session.blueprint;
        int rotation = session.rotation;

        int width = blueprint.getWidth(rotation);
        int depth = blueprint.getDepth(rotation);
        int height = blueprint.getHeight();

        boolean shellOnly = blueprint.getBlockCount() > MAX_DISPLAYS;

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    org.bukkit.block.data.BlockData data = blueprint.blockAt(x, y, z, rotation);
                    if (data == null || data.getMaterial().isAir()) continue;

                    if (shellOnly && x > 0 && x < width - 1 && z > 0 && z < depth - 1
                            && y > 0 && y < height - 1) {
                        continue;
                    }

                    if (session.ghost.size() >= MAX_DISPLAYS) return;

                    // Red the moment the spot is refused, so the answer is on the ghost itself
                    // rather than in a message that only arrives after a wasted click.
                    spawnGhost(world, origin, x, y, z, session.blocked
                            ? Material.RED_STAINED_GLASS.createBlockData()
                            : data, session);
                }
            }
        }
    }

    private void spawnGhost(
            @NotNull World world,
            @NotNull Location origin,
            int x,
            int y,
            int z,
            org.bukkit.block.data.@NotNull BlockData data,
            @NotNull Session session) {

        Location at = origin.clone().add(x, y, z);

        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                // Shrunk a little so the ghost reads as a lattice of separate blocks rather than
                // a solid mass — you can see the shape of the inside through it.
                spawned.setTransformation(new Transformation(
                        new Vector3f((1.0f - GHOST_SCALE) / 2.0f, (1.0f - GHOST_SCALE) / 2.0f, (1.0f - GHOST_SCALE) / 2.0f),
                        new Quaternionf(),
                        new Vector3f(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE),
                        new Quaternionf()));
                spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                spawned.setViewRange(1.0f);
                // Never persist: a ghost surviving a crash would be scenery nobody owns.
                spawned.setPersistent(false);
            });

            session.ghost.add(display);
        } catch (Throwable ignored) {
            // Older servers have no block displays; the commission still works without a preview.
        }
    }

    private void clearGhost(@NotNull Session session) {
        for (BlockDisplay display : session.ghost) {
            try {
                if (!display.isDead()) display.remove();
            } catch (Throwable ignored) {
                // Already gone with its chunk.
            }
        }
        session.ghost.clear();
    }

    private void instruct(@NotNull Player player, @NotNull Blueprint blueprint) {
        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String text = config != null ? config.getString("village.preview-help") : null;
        if (text == null) {
            text = "&ePlacing &f%building%&e — &aright-click&e to build, "
                    + "&asneak + right-click&e to turn, &cleft-click&e to cancel.";
        }

        player.sendMessage(PluginUtils.translate(text.replace("%building%", blueprint.getName())));
    }

    public void shutdown() {
        for (Session session : sessions.values()) clearGhost(session);
        sessions.clear();

        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
