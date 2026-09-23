package me.matsubara.realisticvillagers.hologram;

import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The countdown floating over a village bell while its election runs.
 * <p>
 * Purely cosmetic and fully disposable: if the display can't be spawned (unloaded world, or
 * a server too old for text displays) the election still runs, players just don't get the
 * on-world timer.
 */
/**
 * Lives with the other holograms rather than with the elections it counts down.
 * <p>
 * The folders in this project name a kind of class, not a feature: a hologram belongs with the
 * holograms wherever it is used from, the same way its GUIs sit with the GUIs and its timers with
 * the tasks. Made public for the move — it was package-private only because it used to sit beside
 * the one class that calls it.
 */
public final class ElectionHologram {

    private @Nullable TextDisplay display;

    /** Spawns the countdown above {@code bell}. Safe to call when one already exists. */
    public void spawn(@Nullable Location bell, double heightOffset) {
        if (display != null && !display.isDead()) return;
        if (bell == null) return;

        World world = bell.getWorld();
        if (world == null || !world.isChunkLoaded(bell.getBlockX() >> 4, bell.getBlockZ() >> 4)) return;

        Location at = findClearSpot(bell, heightOffset);

        try {
            display = world.spawn(at, TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                // Cull by a box big enough to cover the text, not by its anchor point.
                spawned.setDisplayWidth(4.0f);
                spawned.setDisplayHeight(4.0f);
                spawned.setDefaultBackground(false);
                spawned.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setSeeThrough(false);
                // Never persist: a leftover display after a crash would be an orphan nobody
                // owns and nothing would clean up.
                spawned.setPersistent(false);
            });
        } catch (Throwable ignored) {
            display = null;
        }
    }

    /**
     * Picks somewhere the countdown is actually readable.
     * <p>
     * A bell can hang from a ceiling, in which case the space straight above it is solid stone
     * or planks and the text ends up buried inside the building. Search upwards for open air
     * first, and if the whole column is blocked, drop below the bell instead.
     */
    private static @NotNull Location findClearSpot(@NotNull Location bell, double heightOffset) {
        World world = bell.getWorld();
        if (world == null) return bell.clone().add(0.0d, heightOffset, 0.0d);

        int start = (int) Math.ceil(heightOffset);
        for (int up = start; up <= start + 3; up++) {
            Location candidate = bell.clone().add(0.0d, up, 0.0d);
            if (candidate.getBlock().isPassable()) return candidate;
        }

        // Ceiling-mounted bell with no room above: hang the text underneath it.
        for (int down = 1; down <= 3; down++) {
            Location candidate = bell.clone().subtract(0.0d, down, 0.0d);
            if (candidate.getBlock().isPassable()) return candidate;
        }

        return bell.clone().add(0.0d, heightOffset, 0.0d);
    }

    /** The candidates' faces, spawned once and then left alone. */
    private final java.util.List<org.bukkit.entity.ItemDisplay> faces = new java.util.ArrayList<>();

    /** How far apart the faces sit, in blocks. */
    private static final double FACE_SPACING = 0.45d;

    /** How far below the countdown the row of faces hangs, in blocks. */
    private static final double FACE_DROP = 0.55d;

    private static final float FACE_SCALE = 0.4f;

    /**
     * Puts the candidates' heads in a row under the countdown.
     * <p>
     * Spawned once and then left in place: the field doesn't change during an election, and
     * respawning them every tick alongside the timer would make the whole row strobe.
     */
    public void showCandidates(@Nullable Location bell, @NotNull java.util.List<org.bukkit.inventory.ItemStack> heads) {
        if (!faces.isEmpty() || heads.isEmpty()) return;
        if (display == null || display.isDead() || bell == null) return;

        World world = bell.getWorld();
        if (world == null) return;

        Location anchor = display.getLocation().clone().subtract(0.0d, FACE_DROP, 0.0d);

        // Centred on the countdown, so the row grows outwards rather than off to one side.
        double start = -(heads.size() - 1) * FACE_SPACING / 2.0d;

        for (int i = 0; i < heads.size(); i++) {
            Location at = anchor.clone().add(start + i * FACE_SPACING, 0.0d, 0.0d);
            org.bukkit.inventory.ItemStack head = heads.get(i);

            try {
                faces.add(world.spawn(at, org.bukkit.entity.ItemDisplay.class, spawned -> {
                    spawned.setItemStack(head);
                    // CENTER so the row turns to face whoever is reading it, like the timer.
                    spawned.setBillboard(Display.Billboard.CENTER);
                    // FIXED renders the head flat, like a picture in an item frame, so what the
                    // reader sees is the candidate's face. Left at the default the head is drawn
                    // the way a dropped item is — a tilted cube showing whichever side happens to
                    // point at you, which is a plain coloured square from most angles.
                    spawned.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                    spawned.setDisplayWidth(2.0f);
                    spawned.setDisplayHeight(2.0f);
                    spawned.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(FACE_SCALE, FACE_SCALE, FACE_SCALE),
                            new org.joml.Quaternionf()));
                    spawned.setPersistent(false);
                }));
            } catch (Throwable ignored) {
                // Older servers have no item displays; the countdown alone still works.
                return;
            }
        }
    }

    public void update(@NotNull String text) {
        if (display == null || display.isDead()) return;

        try {
            display.setText(PluginUtils.translate(text));
        } catch (Throwable ignored) {
            // A display that can't be written to is not worth failing an election over.
        }
    }

    public void remove() {
        for (org.bukkit.entity.ItemDisplay face : faces) {
            try {
                if (!face.isDead()) face.remove();
            } catch (Throwable ignored) {
                // Already gone with its chunk.
            }
        }
        faces.clear();

        if (display == null) return;

        try {
            if (!display.isDead()) display.remove();
        } catch (Throwable ignored) {
            // Already gone with its chunk.
        } finally {
            display = null;
        }
    }

    public boolean isAlive() {
        return display != null && !display.isDead();
    }
}
