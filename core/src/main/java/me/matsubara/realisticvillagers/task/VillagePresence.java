package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells players when they cross into or out of a settlement.
 * <p>
 * A village has no visible edge, so without this a player has no way of knowing whether the rules
 * of a settlement — its mayor, its election, its reputation — currently apply to them. The bar is
 * deliberately temporary: it answers "where am I" at the moment that changes, then gets out of
 * the way rather than sitting on screen for the rest of the session.
 */
public final class VillagePresence {

    private final RealisticVillagers plugin;

    /** The village each player was last seen in, so only the changes are announced. */
    private final Map<UUID, UUID> lastVillage = new HashMap<>();

    /** The bar each player currently has, so a second crossing replaces the first. */
    private final Map<UUID, Shown> shown = new HashMap<>();

    private record Shown(BossBar bar, BukkitTask expiry) {}

    private @Nullable BukkitTask task;

    /**
     * How often presence is checked, in ticks.
     * <p>
     * A second is far below how long it takes to walk anywhere, and the check is a distance
     * comparison against a handful of centres — cheap enough not to warrant anything cleverer
     * than polling, and free of the move-event storm that tracking every step would bring.
     */
    private static final long CHECK_TICKS = 20L;

    public VillagePresence(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, CHECK_TICKS, CHECK_TICKS);
    }

    private void tick() {
        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isDisabledIn(player.getWorld())) continue;

            Village village = villages.getVillageAt(player.getLocation());
            UUID now = village != null ? village.getId() : null;
            UUID before = lastVillage.get(player.getUniqueId());

            if (java.util.Objects.equals(now, before)) continue;

            if (now == null) {
                lastVillage.remove(player.getUniqueId());
                announce(player, message("village.left", "&7Leaving &f%village%", before(villages, before)),
                        BarColor.WHITE);
            } else {
                lastVillage.put(player.getUniqueId(), now);
                announce(player, message("village.entered", "&aEntering &f%village%", village.getDisplayName()),
                        BarColor.GREEN);

                // Walking in is the moment of discovery, and the only moment the plugin can say
                // whose it was — the scan itself runs on a timer with nobody attached to it.
                var discovery = plugin.getVillageDiscovery();
                if (discovery != null) discovery.discover(player, village);
            }
        }

        // A player who logged out inside a village would otherwise be remembered for ever.
        lastVillage.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private @NotNull String before(@NotNull VillageManager villages, @Nullable UUID id) {
        Village village = villages.getVillage(id);
        return village != null ? village.getDisplayName() : "";
    }

    /** Announces that the settlement's mayor has died, to everyone standing in it. */
    public void announceMayorDeath(@Nullable Village village) {
        broadcast(village, message("village.mayor-died", "&cThe mayor of &f%village% &chas died.",
                village == null ? "" : village.getDisplayName()), BarColor.RED);
    }

    /** Announces that voting has opened, to everyone standing in the settlement. */
    public void announceElectionStarted(@Nullable Village village) {
        broadcast(village, message("village.election-started",
                "&e&lELECTION &fopened in &e%village%&f — click the bell to vote.",
                village == null ? "" : village.getDisplayName()), BarColor.YELLOW);
    }

    private void broadcast(@Nullable Village village, @NotNull String text, @NotNull BarColor color) {
        if (village == null) return;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!village.contains(player.getLocation(), villages.getDefaultRadius())) continue;
            announce(player, text, color);
        }
    }

    /**
     * Puts a bar on screen for a few seconds, replacing whatever was there.
     * <p>
     * Replacing rather than queueing: crossing two boundaries in quick succession should leave
     * the player looking at where they are now, not waiting out a message about where they were.
     */
    private void announce(@NotNull Player player, @NotNull String text, @NotNull BarColor color) {
        if (text.isEmpty()) return;

        clear(player.getUniqueId());

        BossBar bar;
        try {
            bar = Bukkit.createBossBar(PluginUtils.translate(text), color, BarStyle.SOLID);
        } catch (Throwable ignored) {
            // Boss bars are optional scenery; a server that can't make one loses nothing else.
            return;
        }

        bar.addPlayer(player);

        UUID id = player.getUniqueId();
        BukkitTask expiry = new BukkitRunnable() {
            @Override
            public void run() {
                clear(id);
            }
        }.runTaskLater(plugin, SHOW_TICKS);

        shown.put(id, new Shown(bar, expiry));
    }

    /** How long a crossing stays on screen, in ticks. */
    private static final long SHOW_TICKS = 60L;

    private void clear(@NotNull UUID id) {
        Shown previous = shown.remove(id);
        if (previous == null) return;

        previous.bar().removeAll();
        previous.expiry().cancel();
    }

    private @NotNull String message(@NotNull String path, @NotNull String fallback, @NotNull String village) {
        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String text = config != null ? config.getString(path) : null;
        if (text == null) text = fallback;

        return text.replace("%village%", village);
    }

    public void shutdown() {
        stop();
        for (UUID id : Set.copyOf(shown.keySet())) clear(id);
        lastVillage.clear();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
