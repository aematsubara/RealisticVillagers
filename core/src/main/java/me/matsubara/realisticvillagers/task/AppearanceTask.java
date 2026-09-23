package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.appearance.Traits;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.VersionMatcher;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gives every villager the height she was born with.
 * <p>
 * Height is an attribute: set once, remembered, free from then on. Her figure
 * is not placed here at
 * all — it is worn, so the game draws it on her body and moves it with her. All
 * this does is send it
 * again when what she has on has changed, since armour picks a different model.
 */
public final class AppearanceTask {

    private final RealisticVillagers plugin;

    private @Nullable BukkitTask task;

    /**
     * How often figures are checked and re-sent, in ticks.
     * <p>
     * Two ticks (100 ms) rather than a full second. The figure is sent in the HEAD
     * slot; when the player's real helmet changes the server fires its own
     * equipment
     * packet immediately, which overwrites the figure. Spigot does not expose an
     * armor-change event, so the only way to notice and put the figure back is on
     * the
     * next tick of this loop.
     * <p>
     * Every tick, and it used to be every other. The reason is the lever: upright her figure sits
     * seven hundredths of a block off the line she turns about, and being a tick late with that is
     * nothing anyone could see. Flat — swimming, crawling, gliding — it sits a body's length out
     * in front, and being late with <em>that</em> swings it. Turning at thirty degrees between two
     * passes, a figure a block and a quarter out lands six tenths of a block from where it belongs,
     * which is not a figure that looks a little behind: it is a figure that has left.
     * <p>
     * The rates that used to be written per two ticks are written per tick now, so nothing about
     * how she settles or how her turn eases has changed in real time — only how often it is said.
     * <p>
     * Height checks, which this loop also handles, are unaffected: an attribute
     * that
     * is already at the right value is not set again, so the extra passes cost only
     * the check itself.
     */
    private static final long TICKS = 1L;

    /**
     * Past this many blocks nobody can tell one villager's height from another's.
     */
    private static final double RANGE = 48.0d;

    public AppearanceTask(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether this server can draw the shapes at all.
     * <p>
     * They rest on the item model system that arrived in 1.21.4 — a model whose
     * faces are coloured
     * from the item rather than from a texture. Height needs none of that, but the
     * two are kept
     * together so the feature is either on or off rather than half of each.
     */
    public static boolean supported() {
        VersionMatcher version = VersionMatcher.getByMinecraftVersion();
        return version != null && version.higherOrEqualThan(VersionMatcher.v1_21_8);
    }

    public void start() {
        stop();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, TICKS, TICKS);
    }

    /**
     * How many passes between sweeps for figures that are riding nobody.
     * <p>
     * Every tenth second. Rare because it walks every loaded entity in every world, and often
     * enough that a figure left behind by something nobody anticipated is gone before anyone has
     * time to walk over and look at it.
     */
    private static final int SWEEP_EVERY = 100;

    /** How many passes between hunts for villagers, which is the rate this loop used to run at. */
    private static final int VILLAGERS_EVERY = 2;

    private int passes;

    private void tick() {
        if (++passes >= SWEEP_EVERY) {
            passes = 0;
            if (plugin.getPlayerAppearanceManager() != null) {
                plugin.getPlayerAppearanceManager().sweep();
            }
        }

        // Villagers are looked for on the old rhythm, players on the new one.
        //
        // What made this loop worth quickening is a body's length of lever between a flying
        // player's own position and where her figure hangs, and villagers do not glide, swim or
        // crawl — they walk, and a walking figure sits seven hundredths of a block off the line she
        // turns about. So they gain nothing from being asked twice as often, while asking costs a
        // sweep of every entity within forty-eight blocks of every player. The cost stays where it
        // was and only the part that needed it got quicker.
        boolean scanForVillagers = passes % VILLAGERS_EVERY == 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerAppearanceManager() != null) {
                plugin.getPlayerAppearanceManager().update(player);
            }

            if (!scanForVillagers || plugin.isDisabledIn(player.getWorld()))
                continue;

            for (var entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
                if (!(entity instanceof Villager villager))
                    continue;
                if (plugin.getTracker().isInvalid(villager))
                    continue;

                IVillagerNPC npc = plugin.getConverter().getNPC(villager).orElse(null);
                if (npc == null)
                    continue;

                applyHeight(villager, npc);

                // Her figure rides her, exactly as a player's does. It used to be worn in her
                // head slot, where it turned with her head and disappeared under a helmet; there
                // was never a reason for her to keep the worse of the two once the better one
                // worked.
                if (plugin.getPlayerAppearanceManager() != null) {
                    plugin.getPlayerAppearanceManager().update(villager, npc);
                }
            }
        }
    }

    /**
     * Makes her as tall as she is meant to be.
     * <p>
     * Scale is an attribute the client already knows how to draw, so this costs
     * nothing to keep up
     * and carries her hitbox and stride along with it. It is also why height needed
     * no model: the
     * game could always do this, nobody had thought to ask it per villager.
     * <p>
     * Set only when it differs from what is there, or every pass would send a
     * packet.
     */
    private void applyHeight(@NotNull Villager villager, @NotNull IVillagerNPC npc) {
        // Children are drawn at half size by the game itself; scaling them again would
        // stack.
        if (!villager.isAdult())
            return;

        AttributeInstance scale = villager.getAttribute(Attribute.SCALE);
        if (scale == null)
            return;

        double wanted = Traits.of(villager.getUniqueId(), npc.isFemale()).height();
        if (Math.abs(scale.getBaseValue() - wanted) > 0.001d)
            scale.setBaseValue(wanted);
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
