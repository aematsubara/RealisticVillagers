package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a villager's name only to the player looking at it.
 * <p>
 * A settlement of forty villagers is forty name tags hanging in the air at once, and they are
 * drawn through walls and through each other — so the more alive a village is, the less of it you
 * can see. Showing one at a time answers the question a name tag exists to answer ("who is this")
 * at the moment it is actually being asked.
 * <p>
 * Off by default. Name tags being always visible is what most servers expect, and this changes
 * something a player sees constantly.
 */
public final class HoverNametagTask {

    private final RealisticVillagers plugin;

    /** The villager each player is currently being shown, so only changes are sent. */
    private final Map<UUID, UUID> showing = new HashMap<>();

    /** Counts passes, so the sweep runs on its own slower beat than the look check. */
    private int passes;

    private @Nullable BukkitTask task;

    /**
     * How often the look is checked, in ticks.
     * <p>
     * Fast enough to feel immediate — a name appearing a fifth of a second after you look at
     * someone reads as instant — and slow enough that it is a handful of ray traces a second
     * rather than one every tick.
     */
    private static final long CHECK_TICKS = 4L;

    public HoverNametagTask(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        announce();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, CHECK_TICKS, CHECK_TICKS);
    }

    /**
     * Says which way name tags are currently set, in the log.
     * <p>
     * "The names are still showing" has two causes that look identical from in game: the setting
     * being off, and the setting being on but not working. Said at start-up and again on reload,
     * because editing config.yml on its own changes nothing until one of those happens — which is
     * its own third cause that also looks identical.
     */
    public void announce() {
        boolean on = Config.NAMETAGS_ONLY_WHEN_LOOKING.asBool(false);
        plugin.getLogger().info("Villager name tags: " + (on
                ? "shown only to the player looking at them (within "
                        + (int) Config.NAMETAGS_LOOK_RANGE.asDouble(12.0d) + " blocks)."
                : "always visible. Set nametags.only-when-looking to true in config.yml to change that."));
    }

    /**
     * Whether this villager's name should stay hidden from this player.
     * <p>
     * Asked by the spawn path itself, so a name is never drawn in the first place instead of
     * being drawn and then taken away — which is both a packet nobody needed and a name that
     * flickers into view every time a villager is re-tracked.
     */
    public boolean isHidingBy(@Nullable Player player, @Nullable me.matsubara.realisticvillagers.entity.IVillagerNPC npc) {
        if (player == null || npc == null) return false;
        if (!Config.NAMETAGS_ONLY_WHEN_LOOKING.asBool(false)) return false;

        return !npc.getUniqueId().equals(showing.get(player.getUniqueId()));
    }

    private void tick() {
        if (!Config.NAMETAGS_ONLY_WHEN_LOOKING.asBool(false)) {
            // Switched off while running: give everyone their name tags back rather than leaving
            // whoever was hidden at the time hidden for good.
            if (!showing.isEmpty()) restoreAll();
            return;
        }

        double range = Math.max(1.0d, Config.NAMETAGS_LOOK_RANGE.asDouble(12.0d));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isDisabledIn(player.getWorld())) continue;

            Villager looked = lookedAt(player, range);
            UUID wanted = looked == null ? null : looked.getUniqueId();
            UUID current = showing.get(player.getUniqueId());

            if (java.util.Objects.equals(wanted, current)) continue;

            // The map is updated first, because the gate above reads it: showing a name means
            // recording it as shown and then asking the NPC to draw itself.
            if (wanted == null) {
                showing.remove(player.getUniqueId());
            } else {
                showing.put(player.getUniqueId(), wanted);
            }

            if (current != null) {
                apply(player, current, false);
            }

            if (wanted != null) apply(player, wanted, true);
        }

        // Every fifth pass: once a second, against the look check's five times a second.
        if (++passes % 5 == 0) sweep(range);

        showing.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /**
     * Takes back the name of every nearby villager the player isn't looking at.
     * <p>
     * Repeated rather than done once. Remembering which had already been dealt with looked like
     * the thrifty version and was the whole bug: a villager is re-tracked whenever it leaves and
     * re-enters a player's view — walking away, a chunk reloading, a trade level changing — and
     * each of those hands it a fresh name tag, while the record said it had been seen to. The
     * name came back and nothing ever took it away again.
     * <p>
     * Run on a slower beat than the look check, so it costs one destroy packet per nearby
     * villager a second — sent to a client that mostly has nothing to destroy, which is cheap —
     * while the name of whoever you are actually looking at still appears immediately.
     */
    private void sweep(double range) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isDisabledIn(player.getWorld())) continue;

            UUID looking = showing.get(player.getUniqueId());

            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (!(entity instanceof Villager villager)) continue;
                if (entity.getUniqueId().equals(looking)) continue;
                if (plugin.getTracker().isInvalid(villager)) continue;

                apply(player, villager, false);
            }
        }
    }

    private @Nullable Villager lookedAt(@NotNull Player player, double range) {
        try {
            // A ray with a little width to it, so a name appears when you look at a villager
            // rather than only when the line through your eye passes exactly through its middle.
            var hit = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    range,
                    0.4d,
                    entity -> entity instanceof Villager && !entity.equals(player));

            if (hit == null || !(hit.getHitEntity() instanceof Villager villager)) return null;
            return plugin.getTracker().isInvalid(villager) ? null : villager;
        } catch (Throwable ignored) {
            // A ray trace that the server refuses simply leaves every name as it was.
            return null;
        }
    }

    private void apply(@NotNull Player player, @NotNull UUID villagerId, boolean show) {
        Entity entity = Bukkit.getEntity(villagerId);
        if (entity instanceof Villager villager) apply(player, villager, show);
    }

    private void apply(@NotNull Player player, @NotNull Villager villager, boolean show) {
        plugin.getTracker().getNPC(villager.getEntityId()).ifPresent(npc -> {
            if (show) {
                npc.refreshNametags(player);
            } else {
                npc.hideNametags(player);
            }
        });
    }

    /** Puts every hidden name tag back, for a shutdown or a setting turned off. */
    private void restoreAll() {
        for (Map.Entry<UUID, UUID> entry : showing.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) apply(player, entry.getValue(), true);
        }
        showing.clear();

        // Then hand back every other name that was taken away.
        //
        // Waiting for each villager to be re-tracked would get there eventually, but "eventually"
        // means standing still in a village with no names above anybody until you walk far enough
        // away and back — which reads as the setting having broken, not as having been turned off.
        // One pass over what is nearby costs a single refresh per villager, once.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isDisabledIn(player.getWorld())) continue;

            double range = Math.max(1.0d, Config.NAMETAGS_LOOK_RANGE.asDouble(12.0d));
            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof Villager villager && !plugin.getTracker().isInvalid(villager)) {
                    apply(player, villager, true);
                }
            }
        }
    }

    public void shutdown() {
        stop();
        if (!showing.isEmpty()) restoreAll();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
