package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything about the settlement's mayor: who holds the seat, and what a player must have
 * achieved before the mayor will take a building commission from them.
 * <p>
 * A village has at most one mayor, and the mayor wears the nitwit look so it reads as a
 * distinct figure rather than another tradesman.
 */
public final class MayorManager {

    private final RealisticVillagers plugin;
    private final VillageManager villages;

    /** Stores what a villager did before it became mayor, so standing down restores it exactly. */
    private final org.bukkit.NamespacedKey previousProfessionKey;

    public MayorManager(@NotNull RealisticVillagers plugin, @NotNull VillageManager villages) {
        this.plugin = plugin;
        this.villages = villages;
        this.previousProfessionKey = new org.bukkit.NamespacedKey(plugin, "mayor_previous_profession");
    }

    /** Why a player may not commission a building right now. */
    public enum CommissionResult {
        /** All requirements met. */
        ALLOWED,
        /** The settlement currently has no mayor — an election has to run first. */
        NO_MAYOR,
        /** Standing with the mayor is too low; earn it by dealing with ordinary residents. */
        LOW_REPUTATION,
        /** The player has no partner among the villagers. */
        NO_FAMILY;

        public boolean isAllowed() {
            return this == ALLOWED;
        }
    }

    // ── Seat ──────────────────────────────────────────────────────────────────

    public boolean isMayor(@Nullable Village village, @Nullable Villager villager) {
        return village != null && villager != null && villager.getUniqueId().equals(village.getMayor());
    }

    /** Whether this villager is the mayor of whichever village it belongs to. */
    public boolean isMayor(@Nullable Villager villager) {
        if (villager == null) return false;
        return isMayor(villages.getVillage(villager), villager);
    }

    /** The living mayor entity, or {@code null} when the seat is vacant or its chunk is unloaded. */
    public @Nullable Villager getMayorEntity(@Nullable Village village) {
        if (village == null || !village.hasMayor()) return null;

        // Global lookup, not a resident scan: a mayor that stepped outside the radius is still
        // the mayor, and treating it as missing would open a needless election.
        return villages.findVillagerById(village.getMayor());
    }

    /**
     * Gives a mayor a platform if it took office without one.
     * <p>
     * Only elected mayors arrive with a programme; one seated because the village happened to
     * have a nitwit in it has none, and then every screen that reports how the settlement is
     * governed has nothing to report. A settlement is always governed somehow, so it always has
     * an answer.
     */
    private void ensureProgram(@NotNull Village village) {
        if (village.getMayorProgram() == null) village.setMayorProgram(ElectoralProgram.random());
    }

    /**
     * Installs {@code villager} as mayor, giving it the nitwit look.
     * <p>
     * Any previous mayor is stood down first, so the "one mayor per village" rule holds even
     * if this is called while a mayor is already seated.
     */
    public void appointMayor(@Nullable Village village, @Nullable Villager villager) {
        appointMayor(village, villager, villager == null ? null : villager.getProfession());
    }

    /**
     * Installs {@code villager} as mayor, recording {@code originalProfession} as what to give
     * it back once it stands down, instead of reading its current profession for that.
     * <p>
     * An election winner already wears the campaign's nitwit look by the time it wins — its
     * live profession at this point IS nitwit, not the trade it actually held before running.
     * Reading the live profession here, as the single-argument overload does, would record
     * nitwit as "what to restore", and every elected mayor would be stuck a nitwit forever,
     * even after properly standing down. {@link ElectionManager} passes the candidate's own
     * recorded {@code originalProfession} to route around that; a mayor seated some other way,
     * with nothing campaigned away yet, can go through the single-argument overload instead.
     */
    public void appointMayor(@Nullable Village village, @Nullable Villager villager, @Nullable Villager.Profession originalProfession) {
        if (village == null || villager == null) return;

        // Only ever promote someone who isn't holding down a job. Changing a working villager's
        // profession releases its workstation, and vanilla's ResetProfession behaviour then
        // strips the trade entirely — a working villager must never be turned into a mayor.
        if (!isFreeToPromote(villager)) return;

        if (village.hasMayor() && !villager.getUniqueId().equals(village.getMayor())) {
            standDown(village);
        }

        village.setMayor(villager.getUniqueId());
        ensureProgram(village);
        markPreviousProfession(villager, originalProfession != null ? originalProfession : villager.getProfession());
        setProfessionQuietly(villager, Villager.Profession.NITWIT);

        // Refresh unconditionally. Promoting a villager that is ALREADY a nitwit changes no
        // profession, so setProfessionQuietly returns early and never refreshes — leaving a
        // mayor with no badge block and no visible sign at all. Because the seat is filled,
        // the election loop then skips the village, so it looks like nothing happened: no
        // mayor appeared and no election ran.
        refreshAppearance(villager);
    }

    /**
     * Whether this villager can be made mayor without costing the village a tradesman.
     * <p>
     * Adults only, and only ones with no trade to lose: children shouldn't hold office, and a
     * working villager would lose its job to the promotion.
     */
    public boolean isFreeToPromote(@Nullable Villager villager) {
        if (villager == null || !villager.isAdult()) return false;

        Villager.Profession profession = villager.getProfession();
        return profession == Villager.Profession.NONE || profession == Villager.Profession.NITWIT;
    }

    /**
     * Vacates the seat, returning the outgoing mayor to the unemployed pool so it can take a
     * job again (and stand in a future election).
     */
    public void standDown(@Nullable Village village) {
        if (village == null || !village.hasMayor()) return;

        Villager mayor = getMayorEntity(village);
        if (mayor != null) {
            // Put them back to whatever they were before taking office rather than assuming
            // unemployment — guessing here is how villagers quietly lose their trade.
            setProfessionQuietly(mayor, previousProfession(mayor));
            clearPreviousProfession(mayor);
        }

        village.setMayor(null);
    }

    /** Remembers what a villager was before being made mayor, on the entity itself. */
    private void markPreviousProfession(@NotNull Villager villager, Villager.@NotNull Profession profession) {
        try {
            villager.getPersistentDataContainer().set(previousProfessionKey, PersistentDataType.STRING, profession.toString());
        } catch (Throwable ignored) {
            // Without the mark, standing down falls back to leaving them as a nitwit.
        }
    }

    private void clearPreviousProfession(@NotNull Villager villager) {
        try {
            villager.getPersistentDataContainer().remove(previousProfessionKey);
        } catch (Throwable ignored) {
            // Nothing to clean up.
        }
    }

    /**
     * What this villager was before taking office.
     * <p>
     * Falls back to NITWIT rather than NONE: leaving an ex-mayor as a nitwit changes nothing
     * about the village, while wrongly marking them unemployed sends them hunting for a job.
     */
    private Villager.@NotNull Profession previousProfession(@NotNull Villager villager) {
        String stored;
        try {
            stored = villager.getPersistentDataContainer().get(previousProfessionKey, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return Villager.Profession.NITWIT;
        }
        if (stored == null) return Villager.Profession.NITWIT;

        String wanted = bareName(stored);
        for (Villager.Profession profession : Villager.Profession.values()) {
            if (bareName(profession.toString()).equals(wanted)) return profession;
        }
        return Villager.Profession.NITWIT;
    }

    private static @NotNull String bareName(@NotNull String key) {
        int colon = key.lastIndexOf(':');
        return (colon >= 0 ? key.substring(colon + 1) : key).toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasPreviousProfession(@NotNull Villager villager) {
        try {
            return villager.getPersistentDataContainer().has(previousProfessionKey, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Restores anyone still marked with a previous profession but no longer holding the seat.
     * <p>
     * {@link #standDown} clears the mark itself on the way out, so this only ever finds
     * something when the seat was vacated some other way — namely {@code ElectionManager}
     * giving up on a mayor it couldn't find for several checks in a row and clearing the seat
     * directly. That path has no entity in hand to restore at the moment it gives up, so if the
     * villager was only temporarily untrackable (an unloaded chunk, a tracker hiccup) rather
     * than actually gone, it would otherwise sit there alive and marked, but never actually
     * given its job back — a nitwit forever, indistinguishable from one Minecraft grew on its
     * own. Called every election tick so a villager that turns up again gets caught here as
     * soon as it does. Restoring only what was genuinely marked (never anyone without a mark)
     * keeps this from touching a villager that never held office.
     */
    public void releaseStrandedMayors() {
        for (Village village : villages.getVillages()) {
            for (Villager resident : villages.getResidents(village)) {
                if (isMayor(village, resident)) continue;
                if (!hasPreviousProfession(resident)) continue;

                setProfessionQuietly(resident, previousProfession(resident));
                clearPreviousProfession(resident);
            }
        }
    }

    private void setProfessionQuietly(@NotNull Villager villager, Villager.Profession profession) {
        try {
            // Nothing to do — and crucially, no refresh either. The refresh can reach out to
            // the MineSkin API to generate a missing skin, so firing it on unchanged villagers
            // floods that API and ends with every villager falling back to a default skin.
            if (villager.getProfession() == profession) return;

            villager.setProfession(profession);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Couldn't change the mayor's profession: " + throwable.getMessage());
            return;
        }

        refreshAppearance(villager);
    }

    /**
     * Re-sends the NPC's skin and nametag.
     * <p>
     * Changing the profession through the Bukkit API doesn't fire the career-change event the
     * tracker listens to, so without this the mayor keeps its old skin and shows no badge
     * block until something else happens to respawn it for the player.
     */
    private void refreshAppearance(@NotNull Villager villager) {
        try {
            plugin.getTracker().refreshNPCSkin(villager, false);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Couldn't refresh the mayor's appearance: " + throwable.getMessage());
        }
    }

    // ── Commission requirements ───────────────────────────────────────────────

    /**
     * Whether {@code player} may commission a new building from this village's mayor.
     * <p>
     * Both gates come from the spec: enough standing with the mayor (which is itself derived
     * from the player's reputation with ordinary residents) and having a family.
     */
    /**
     * Lets staff commission buildings without earning standing first.
     * <p>
     * Reputation is a progression gate, and an administrator setting a server up, testing a
     * blueprint or fixing someone else's village has no progression to make — they would have to
     * grind a settlement they may not even play in. Granted to operators, and to the permission
     * so it can be handed out without handing out everything else.
     */
    public static final String BYPASS_PERMISSION = "realisticvillagers.village.bypass";

    public boolean bypassesRequirements(@Nullable Player player) {
        return player != null && (player.isOp() || player.hasPermission(BYPASS_PERMISSION));
    }

    public @NotNull CommissionResult canCommission(@Nullable Village village, @Nullable Player player) {
        if (village == null || player == null) return CommissionResult.NO_MAYOR;

        // Still needs a mayor: without one there is nobody to take the plans, and that is a fact
        // about the settlement rather than a requirement placed on the player.
        if (!village.hasMayor()) return CommissionResult.NO_MAYOR;

        if (bypassesRequirements(player)) return CommissionResult.ALLOWED;

        if (villages.getMayorReputation(village, player) < getRequiredReputation()) {
            return CommissionResult.LOW_REPUTATION;
        }

        if (isFamilyRequired() && !hasFamily(village, player)) {
            return CommissionResult.NO_FAMILY;
        }

        return CommissionResult.ALLOWED;
    }

    /**
     * Whether the player is partnered to one of this village's residents.
     * <p>
     * Only the settlement's own residents count — being married into a village on the other
     * side of the world shouldn't unlock building rights here.
     */
    public boolean hasFamily(@Nullable Village village, @Nullable Player player) {
        if (village == null || player == null) return false;

        for (IVillagerNPC npc : villages.getResidentNPCs(village)) {
            if (npc.isPartner(player.getUniqueId())) return true;
        }
        return false;
    }

    public int getRequiredReputation() {
        return Config.VILLAGE_MAYOR_REPUTATION_TO_COMMISSION.asInt(50);
    }

    public boolean isFamilyRequired() {
        return Config.VILLAGE_MAYOR_REQUIRE_FAMILY.asBool();
    }
}
