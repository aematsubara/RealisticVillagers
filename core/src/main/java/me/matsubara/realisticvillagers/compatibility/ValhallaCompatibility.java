package me.matsubara.realisticvillagers.compatibility;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.trading.CustomMerchantManager;
import me.athlaeos.valhallammo.trading.dom.MerchantData;
import me.athlaeos.valhallammo.trading.listeners.MerchantListener;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands a villager's trading over to ValhallaMMO's premium merchant system, for servers running
 * it alongside this plugin.
 * <p>
 * Valhalla exposes no "open your interface for this villager" call. Its own trading lives entirely
 * inside its {@code PlayerInteractEntityEvent} listener — it decides whether the villager is one of
 * its custom merchants, builds the recipe list, and opens a vanilla-shaped {@code MerchantInventory}
 * that its own {@code InventoryOpenEvent}/{@code InventoryClickEvent} hooks then take over. So
 * rather than reimplement any of that, this class hands Valhalla a synthetic copy of the very
 * event it already knows how to handle — the same approach already used for VillagerTradeLimiter
 * in {@link VTLCompatibility}, and for the same reason: it is the only way to get another plugin's
 * closed logic to run correctly without guessing at what it does internally.
 * <p>
 * There is no public getter for the listener instance the way VTL offers one, so it is found the
 * way any plugin's registered listener can be found without one: among the handlers Bukkit already
 * has registered for the event.
 * <p>
 * The window a call here opens is expected to have already been marked with
 * {@link #expect(UUID, UUID)} by the caller <i>before</i> this runs — see that method's note on
 * why marking lives there rather than in here.
 */
public class ValhallaCompatibility implements Compatibility {

    /**
     * Player → villager pairs a deliberate call into trading is about to open a merchant window
     * for.
     * <p>
     * Exists because of one thing found reading Valhalla's own bytecode: its interact handler is
     * registered at {@code EventPriority.HIGHEST} and never checks {@code event.isCancelled()} —
     * only this plugin's own priority and {@code ignoreCancelled} setting decides whether its menu
     * opens, not Valhalla's. So a raw right-click on a villager Valhalla already considers one of
     * its own custom merchants can open Valhalla's trading window on its own, a tick before this
     * plugin's menu would have appeared, with nothing here having asked for it. A guard elsewhere
     * (see {@code VillagerListeners#onValhallaMerchantOpen}) closes any merchant window that opens
     * for a managed villager without this having said it was expected.
     * <p>
     * That guard cannot tell "Valhalla opened this uninvited" apart from "the plugin's own vanilla
     * trading opened this, exactly as intended" by looking at the window alone — both are the same
     * kind of window, a plain {@code MerchantInventory}. So this map covers both outcomes of a
     * deliberate trade attempt, not only the one that goes through Valhalla, which is also why the
     * marking happens once, in the caller that knows about both branches, rather than in here.
     * <p>
     * Keyed by player <i>and</i> villager, and never swept on a timer — see {@link #expect} for
     * why a timeout turned out to be the wrong tool for this.
     */
    private static final Map<UUID, UUID> EXPECTING = new ConcurrentHashMap<>();

    /**
     * Marks a player as about to have a deliberately-opened trade window with this villager,
     * before the attempt that opens it runs.
     * <p>
     * Carries no expiry, on purpose — a fixed number of ticks was tried first and does not work.
     * Reading Valhalla's own bytecode found the actual open does not happen a tick later at all:
     * {@code onVillagerInteract} hands the rest of the work to
     * {@code BukkitScheduler#runTaskAsynchronously}, almost certainly to read the merchant's data
     * off disk without blocking the main thread, and only opens the window once that read answers
     * — on no fixed schedule at all. A five-tick sweep meant to be a generous safety net was, in
     * practice, expiring before Valhalla ever got there, so the guard was closing the very window
     * this plugin had just asked for. Every single trade opened through the "Trade" button was
     * being cancelled by the plugin's own guard.
     * <p>
     * The fix is not a longer number to guess with — any fixed wait is still a guess against a
     * disk read with no upper bound — it is not needing one. One shot, consumed by
     * {@link #isExpecting}, needs no expiry: the only thing a stale, never-consumed mark can do is
     * excuse one later, unrelated raw click on the very same villager by the very same player,
     * which is a narrow enough case that it is not worth trading back to for a coin-flip about
     * whether five seconds was long enough this time.
     */
    public static void expect(@NotNull UUID player, @NotNull UUID villager) {
        EXPECTING.put(player, villager);
    }

    /**
     * Whether an unclaimed {@code MerchantInventory} open for this player and villager should be
     * let through.
     * <p>
     * One-shot: answering the question consumes it, so a second, unrelated open for the same pair
     * right after — Valhalla reacting to some other click — is judged on its own, not waved
     * through on the coat-tails of the first.
     */
    public static boolean isExpecting(@NotNull UUID player, @NotNull UUID villager) {
        return EXPECTING.remove(player, villager);
    }

    @Override
    public boolean shouldTrack(Villager villager) {
        // Valhalla changes what trading looks like, not whether this plugin should be managing
        // the villager at all — her skin, family, schedule and everything else carry on exactly
        // as they would without it installed.
        return true;
    }

    /**
     * Opens Valhalla's own trading interface for this villager, converting her into one of its
     * custom merchants first if she is not one yet.
     * <p>
     * Callers are expected to have already marked the player with {@link #expect}; this method
     * only decides whether Valhalla has anything to offer and, if so, asks it to open — it does
     * not touch the expecting map itself, since the caller also owns the vanilla fallback this
     * declining leads to, and that fallback's own window needs the same marking.
     *
     * @return {@code false} when Valhalla has nothing to offer here — its trading system is
     * switched off server-wide, or nobody has configured a merchant type for this villager's
     * profession — in which case the caller falls back to ordinary trading exactly as if Valhalla
     * were not installed at all.
     */
    public boolean openTrade(
            @NotNull RealisticVillagers plugin,
            @NotNull Plugin valhalla,
            @NotNull Player player,
            @NotNull AbstractVillager villager) {

        if (!ValhallaMMO.isTradingSystemEnabled()) return false;

        if (!CustomMerchantManager.isCustomMerchant(villager)) {
            // Picks a merchant type from whatever is configured for her profession. Returns null,
            // cleanly, when there is nothing configured for it — never throws — so this is a
            // proper "no" rather than a fault to guard against.
            MerchantData created = CustomMerchantManager.convertToRandomMerchant(villager, player);
            if (created == null) return false;
        }

        MerchantListener listener = liveListener(valhalla);
        if (listener == null) return false;

        // The same click Valhalla would have seen had this plugin never intercepted the real one
        // — right hand, nothing more. Everything past this point — happiness, reputation, the
        // discount on the price, the recipes themselves — is Valhalla's own logic, running exactly
        // as it does for anyone who right-clicks one of its merchants directly. It runs the actual
        // open on its own schedule (see the note on expect()), not before this call returns.
        listener.onVillagerInteract(new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND));
        return true;
    }

    /**
     * The listener Valhalla itself registered for this event, found the way any plugin's own
     * listener can be found when it offers no getter for it: among the ones Bukkit already has on
     * file for the event, rather than one built fresh here. A fresh instance would read the same
     * configuration back out — its fields are nothing but config values — but the one Bukkit is
     * already holding is the one actually wired into everything else Valhalla does, and there is
     * no reason to take even that small a risk when the real one is sitting right there.
     */
    private @Nullable MerchantListener liveListener(@NotNull Plugin valhalla) {
        for (RegisteredListener registered : PlayerInteractEntityEvent.getHandlerList().getRegisteredListeners()) {
            if (registered.getPlugin().equals(valhalla) && registered.getListener() instanceof MerchantListener listener) {
                return listener;
            }
        }
        return null;
    }
}
