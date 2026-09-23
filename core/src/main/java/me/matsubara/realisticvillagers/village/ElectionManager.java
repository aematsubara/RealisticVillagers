package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.hologram.ElectionHologram;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs mayoral elections: opens one whenever a settlement is left without a mayor, collects
 * weighted votes for its duration, then installs the winner.
 * <p>
 * Candidates are drawn only from unemployed residents, so holding an election never costs
 * the village a working tradesman.
 */
public final class ElectionManager {

    private final RealisticVillagers plugin;
    private final VillageManager villages;
    private final MayorManager mayors;

    private final Map<UUID, Election> elections = new ConcurrentHashMap<>();
    private final Map<UUID, ElectionHologram> holograms = new ConcurrentHashMap<>();

    /** Villages that had no candidates, and the earliest time worth trying them again. */
    private final Map<UUID, Long> nextStartAttempt = new ConcurrentHashMap<>();

    /** Candidates still owed their old profession back, keyed by villager id. */
    private final Map<UUID, Villager.Profession> pendingRestores = new ConcurrentHashMap<>();

    /**
     * Villagers this system put into nitwit robes, tracked in memory.
     * <p>
     * The authority on "is this nitwit one of ours". The entity mark is only a backup for
     * surviving restarts — reading it can fail, and treating a failed read as "natural nitwit"
     * is what let elections and the natural-nitwit path chew through the same villagers.
     */
    private final Set<UUID> dressedByElection = ConcurrentHashMap.newKeySet();

    /**
     * Profession changes allowed in a single pass.
     * <p>
     * A safety net, not a feature: seating one mayor touches a handful of villagers at most, so
     * anything beyond this means a bug is loose. Refusing the change turns what would be a
     * village wiped to unemployment into a log line.
     */
    private static final int MAX_PROFESSION_CHANGES_PER_PASS = 8;

    private int professionChangesThisPass;

    /** Consecutive checks a mayor must be missing before the seat is vacated. */
    private final Map<UUID, Integer> missingMayorChecks = new ConcurrentHashMap<>();

    private static final int MISSING_MAYOR_TOLERANCE = 3;

    private static final long NO_CANDIDATE_BACKOFF_MILLIS = 60_000L;
    private @Nullable BukkitTask task;

    /** Separate from the main check so the countdown ticks smoothly regardless of it. */
    private @Nullable BukkitTask hologramTask;

    private static final Random RANDOM = new Random();

    /**
     * Marks a villager as standing for office, storing what it was before.
     * <p>
     * Written to the entity itself so it survives a restart: an election interrupted by a
     * shutdown would otherwise leave its candidates as nitwits with nothing left in memory to
     * say what they used to be.
     */
    private final NamespacedKey candidateKey;

    public ElectionManager(@NotNull RealisticVillagers plugin, @NotNull VillageManager villages, @NotNull MayorManager mayors) {
        this.plugin = plugin;
        this.villages = villages;
        this.mayors = mayors;
        this.candidateKey = new NamespacedKey(plugin, "election_candidate");
        startTask();
    }

    private void markCandidate(@NotNull Villager villager, Villager.@NotNull Profession original) {
        try {
            villager.getPersistentDataContainer().set(candidateKey, PersistentDataType.STRING, original.toString());
        } catch (Throwable ignored) {
            // Without the mark the in-memory restore still covers the normal case.
        }
    }

    /**
     * Whether this villager is a nitwit because this system made it one.
     * <p>
     * Checks the in-memory record first, since that can't fail, and falls back to the entity
     * mark for villagers dressed before a restart. A read error counts as "ours" — mistaking a
     * candidate for a natural nitwit is what let the two pools chew through each other.
     */
    private boolean isDressedByElection(@NotNull Villager villager) {
        if (dressedByElection.contains(villager.getUniqueId())) return true;

        try {
            return villager.getPersistentDataContainer().has(candidateKey, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void clearCandidateMark(@NotNull Villager villager) {
        try {
            villager.getPersistentDataContainer().remove(candidateKey);
        } catch (Throwable ignored) {
            // Nothing to do; the villager is already restored.
        }
    }

    /**
     * Frees villagers still marked as candidates for elections that are no longer running —
     * the ones a restart or a crash would otherwise strand in campaign robes forever.
     */
    private void releaseStrandedCandidates() {
        for (Village village : villages.getVillages()) {
            Election election = getElection(village);

            for (Villager resident : villages.getResidents(village)) {
                String stored;
                try {
                    stored = resident.getPersistentDataContainer().get(candidateKey, PersistentDataType.STRING);
                } catch (Throwable ignored) {
                    continue;
                }
                if (stored == null) continue;

                UUID id = resident.getUniqueId();
                // Still legitimately standing, or currently serving as mayor: leave it alone.
                if (election != null && election.isCandidate(id)) continue;
                if (villages.isMayor(village, id)) continue;

                Villager.Profession original = professionFromKey(stored);
                if (original == null) {
                    // Unreadable mark: drop the mark rather than guessing a profession, so this
                    // villager is never quietly turned unemployed on a bad read.
                    clearCandidateMark(resident);
                    dressedByElection.remove(id);
                    pendingRestores.remove(id);
                    continue;
                }

                setProfessionQuietly(resident, original);
                clearCandidateMark(resident);
                dressedByElection.remove(id);
                pendingRestores.remove(id);
            }
        }
    }

    /**
     * Resolves a stored profession key back to a profession.
     * <p>
     * Matches on the bare name as well as the full key, because the profession's
     * {@code toString()} is namespaced on some versions ({@code minecraft:farmer}) and not on
     * others. Falling back to unemployed on a near-miss is how a whole village ends up jobless,
     * so an unrecognised key returns {@code null} and the caller leaves the villager alone.
     */
    private Villager.@Nullable Profession professionFromKey(@NotNull String stored) {
        String wanted = bareName(stored);

        for (Villager.Profession profession : Villager.Profession.values()) {
            if (bareName(profession.toString()).equals(wanted)) return profession;
        }
        return null;
    }

    private static @NotNull String bareName(@NotNull String key) {
        int colon = key.lastIndexOf(':');
        return (colon >= 0 ? key.substring(colon + 1) : key).toLowerCase(java.util.Locale.ROOT);
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public @Nullable Election getElection(@Nullable Village village) {
        return village == null ? null : elections.get(village.getId());
    }

    public boolean hasElection(@Nullable Village village) {
        return getElection(village) != null;
    }

    public int getPlayerVoteWeight() {
        return Math.max(1, Config.VILLAGE_ELECTION_PLAYER_VOTE_WEIGHT.asInt(5));
    }

    public int getVillagerVoteWeight() {
        return Math.max(1, Config.VILLAGE_ELECTION_VILLAGER_VOTE_WEIGHT.asInt(1));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private void startTask() {
        stopTask();

        long seconds = Math.max(5L, Config.VILLAGE_ELECTION_CHECK_INTERVAL.asInt(10));
        long ticks = seconds * 20L;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, ticks, ticks);

        hologramTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateHolograms();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
    }

    /** Keeps a countdown floating over each contested bell. */
    private void updateHolograms() {
        // The master hologram switch governs this too.
        //
        // It is documented as turning off every hologram, and a countdown floating over a bell is
        // one — a server that switched them off to run the chest menus would otherwise still get
        // text hanging in the world, from the one place that never asked.
        //
        // Elections themselves are untouched: they run, the bell still opens the ballot, and the
        // ballot carries its own timer. What is lost is the sign visible from a distance, and the
        // boss bar shown when voting opens still covers "something is happening here".
        if (plugin.getHologramManager() == null || !plugin.getHologramManager().isEnabled()) {
            // Clear any that were already up, so switching it off during a reload takes effect
            // rather than stranding whatever happened to be floating at the time.
            if (!holograms.isEmpty()) {
                for (ElectionHologram hologram : holograms.values()) hologram.remove();
                holograms.clear();
            }
            return;
        }

        if (elections.isEmpty()) return;

        // The countdown is a hologram, so its text lives with the other hologram settings.
        String template = plugin.getHologramConfig() == null
                ? "&e&lELECTION\n&fTime left: &e%time%"
                : plugin.getHologramConfig().getString(
                        "hologram.election.countdown",
                        "&e&lELECTION\n&fTime left: &e%time%");

        for (Map.Entry<UUID, Election> entry : elections.entrySet()) {
            Village village = villages.getVillage(entry.getKey());
            if (village == null) continue;

            // Over the bell, not the village centre. The two stopped being the same thing when
            // the bell became movable, and the countdown belongs at the place people walk up to
            // in order to vote.
            World world = village.getWorld();
            Location bell = world == null ? null : new Location(
                    world, village.getBellX() + 0.5d, village.getBellY(), village.getBellZ() + 0.5d);

            ElectionHologram hologram = holograms.computeIfAbsent(entry.getKey(), id -> new ElectionHologram());
            if (!hologram.isAlive()) hologram.spawn(bell, 2.0d);

            hologram.update(template.replace("%time%", formatTime(entry.getValue().getRemainingSeconds())));

            // The candidates' faces in a row beneath the countdown, the same faces the ballot
            // shows — so the hologram tells you who is standing, not just how long is left.
            hologram.showCandidates(bell, candidateHeads(entry.getValue()));
        }
    }

    /**
     * The candidates' heads, in ballot order.
     * <p>
     * Same skin the ballot and the map use, so one candidate is one recognisable face wherever
     * they turn up. A candidate whose skin can't be resolved still gets a blank head rather than
     * being dropped, which would silently shorten the row and misrepresent the field.
     */
    private @NotNull List<org.bukkit.inventory.ItemStack> candidateHeads(@NotNull Election election) {
        List<org.bukkit.inventory.ItemStack> heads = new ArrayList<>();

        for (Election.Candidate candidate : election.getCandidates()) {
            org.bukkit.inventory.ItemStack head = null;
            try {
                Villager villager = villages.findVillagerById(candidate.getVillagerId());
                IVillagerNPC npc = villager != null ? plugin.getConverter().getNPC(villager).orElse(null) : null;

                String texture = plugin.getNPCTextureURL(npc);
                if (texture != null && !texture.isEmpty()) {
                    head = new me.matsubara.realisticvillagers.util.ItemBuilder(Material.PLAYER_HEAD)
                            .setHead(texture, true)
                            .build();
                }
            } catch (Throwable ignored) {
                // Falls through to the plain head below.
            }

            heads.add(head != null ? head : new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD));
        }

        return heads;
    }

    private void removeHologram(@NotNull UUID villageId) {
        ElectionHologram hologram = holograms.remove(villageId);
        if (hologram != null) hologram.remove();
    }

    private static @NotNull String formatTime(long seconds) {
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    private void tick() {
        if (!villages.isEnabled() || !isEnabled()) return;

        professionChangesThisPass = 0;

        // Register any settlement we haven't seen yet. Everything below iterates the known
        // villages, so without this pass a server where nobody had clicked a villager had no
        // villages at all — and therefore no elections and nothing in the log.
        villages.detectVillages();

        // Straight after detection, so a village found this pass is sized to its buildings before
        // anything asks who lives in it.
        villages.fitVillageBounds();


        // Before anything else, put back anyone still stuck in campaign robes.
        retryPendingRestores();
        releaseStrandedCandidates();
        mayors.releaseStrandedMayors();

        // Resolve anything that has run its course before opening new races, so a village
        // never has a finished election lingering while a fresh one starts.
        for (Election election : new ArrayList<>(elections.values())) {
            if (election.isFinished()) finish(election);
        }

        long now = System.currentTimeMillis();

        for (Village village : villages.getVillages()) {
            // Deliberately NOT re-centring here. Moving the centre moves the circle that defines
            // who counts as a resident, and a centre that drifted onto the middle of the
            // buildings could leave the villagers themselves outside it — at which point there
            // are no residents, therefore no unemployed, therefore no election, ever. The centre
            // is fixed where the village was first found and simply stays put.

            // A seat held by a villager that no longer exists would block this village forever:
            // hasMayor() only checks that an id is stored, so a dead mayor left the settlement
            // with no mayor in the world AND no election to replace them.
            releaseSeatIfMayorGone(village);

            if (village.hasMayor() || hasElection(village)) continue;

            // A village with nobody free to stand would otherwise retry the (entity-querying)
            // candidate search on every single check, forever. Back off instead.
            Long retryAt = nextStartAttempt.get(village.getId());
            if (retryAt != null && now < retryAt) continue;

            // Natural nitwits settle the seat among themselves — checked BEFORE start(), because
            // start() dresses unemployed villagers as nitwits and they would otherwise be
            // mistaken for the village's own idle folk on the next pass.
            if (seatMayorFromNaturalNitwits(village)) {
                nextStartAttempt.remove(village.getId());
                continue;
            }

            if (start(village) == null) {
                nextStartAttempt.put(village.getId(), now + NO_CANDIDATE_BACKOFF_MILLIS);
            } else {
                nextStartAttempt.remove(village.getId());
            }
        }
    }

    /**
     * Vacates the seat when the villager holding it is gone for good.
     * <p>
     * Only judges a settlement whose residents are actually loaded. An unloaded village reports
     * no residents and no findable mayor, which says nothing about whether the mayor still
     * exists — clearing on that would depose mayors simply for being far from any player.
     */
    private void releaseSeatIfMayorGone(@NotNull Village village) {
        if (!village.hasMayor()) return;

        // No loaded residents means the village isn't loaded; we can't tell anything yet.
        if (villages.getResidents(village).isEmpty()) {
            missingMayorChecks.remove(village.getId());
            return;
        }

        if (mayors.getMayorEntity(village) != null) {
            missingMayorChecks.remove(village.getId());
            return;
        }

        // Require several consecutive misses before deposing anyone. A single miss means very
        // little — the mayor can be mid-respawn while the tracker swaps entities, or briefly
        // unreachable while chunks settle after a bell is broken — and acting on one reading is
        // how a mayor got removed just for having its bell knocked down.
        int misses = missingMayorChecks.merge(village.getId(), 1, Integer::sum);
        if (misses < MISSING_MAYOR_TOLERANCE) return;

        missingMayorChecks.remove(village.getId());
        village.setMayor(null);
        village.setMayorProgram(null);
    }

    /**
     * Seats a mayor from the village's own nitwits and frees the others.
     * <p>
     * "Its own" means nitwits Minecraft generated — never the ones an election dressed up.
     * Those carry a candidate mark, and telling the two apart is the whole point: without it,
     * {@link #start} turns unemployed villagers into nitwits and the next pass reads them back
     * as natural ones, which is exactly how a village ended up entirely unemployed.
     * <p>
     * <b>Strictly per village</b> — it only reads {@code getResidents(village)}, bounded by that
     * settlement's radius. Never widen this scan; doing so once left one mayor for the world.
     *
     * @return {@code true} if a mayor was seated.
     */
    private boolean seatMayorFromNaturalNitwits(@NotNull Village village) {
        List<Villager> nitwits = new ArrayList<>();

        for (Villager resident : villages.getResidents(village)) {
            if (!SecondaryProfession.isNitwit(resident)) continue;

            // Anything the election system put in robes is off limits here.
            if (isDressedByElection(resident)) continue;
            if (isAwaitingRestore(resident.getUniqueId())) continue;

            if (!mayors.isFreeToPromote(resident)) continue;
            nitwits.add(resident);
        }

        if (nitwits.isEmpty()) return false;

        Villager chosen = nitwits.get(RANDOM.nextInt(nitwits.size()));
        mayors.appointMayor(village, chosen);

        // The rest join the reserve pool, so the village isn't left with a crowd of idle nitwits
        // alongside its mayor.
        for (Villager other : nitwits) {
            if (other.getUniqueId().equals(chosen.getUniqueId())) continue;
            setProfessionQuietly(other, Villager.Profession.NONE);
        }

        return true;
    }

    public boolean isEnabled() {
        return Config.VILLAGE_ELECTION_ENABLED.asBool();
    }

    /**
     * Opens an election in this village.
     *
     * @return the new election, or {@code null} when there's nobody to stand — a village with
     * no unemployed residents simply stays without a mayor until someone is free.
     */
    public @Nullable Election start(@Nullable Village village) {
        if (village == null || hasElection(village)) return null;

        List<Villager> pool = villages.getUnemployedResidents(village);
        if (pool.isEmpty()) return null;

        Collections.shuffle(pool, RANDOM);

        int wanted = Math.max(1, Config.VILLAGE_ELECTION_CANDIDATES.asInt(3));
        int count = Math.min(wanted, pool.size());

        List<Election.Candidate> candidates = new ArrayList<>();
        Set<ElectoralProgram> taken = EnumSet.noneOf(ElectoralProgram.class);

        for (int i = 0; i < count; i++) {
            Villager villager = pool.get(i);

            ElectoralProgram program = ElectoralProgram.randomExcluding(taken);
            taken.add(program);

            candidates.add(new Election.Candidate(villager.getUniqueId(), program, villager.getProfession()));

            // Candidates campaign in the nitwit's green robes, as the spec describes.
            markCandidate(villager, villager.getProfession());
            dressedByElection.add(villager.getUniqueId());
            setProfessionQuietly(villager, Villager.Profession.NITWIT);
        }

        long duration = Math.max(10L, Config.VILLAGE_ELECTION_DURATION.asInt(300)) * 1000L;
        Election election = new Election(village.getId(), candidates, duration);
        elections.put(village.getId(), election);

        plugin.getLogger().info("Election opened in " + village.getDisplayName()
                + " with " + candidates.size() + " candidate(s); voting closes in "
                + (duration / 1000L) + "s.");

        // Tell whoever is standing in the village. An election changes what the settlement does
        // next — the bell locks, candidates are dressed, a vote is waiting to be cast — and
        // without this the only sign of it is noticing the countdown by chance.
        var presence = plugin.getVillagePresence();
        if (presence != null) presence.announceElectionStarted(village);

        castVillagerVotes(village, election);
        return election;
    }

    /**
     * Every resident that isn't standing casts a vote up front.
     * <p>
     * This gives the ballot a baseline so an unattended server still elects someone, while
     * leaving players free to outweigh it — their votes count for much more.
     */
    private void castVillagerVotes(@NotNull Village village, @NotNull Election election) {
        List<Election.Candidate> candidates = election.getCandidates();
        if (candidates.isEmpty()) return;

        for (Villager resident : villages.getResidents(village)) {
            UUID id = resident.getUniqueId();
            if (election.isCandidate(id)) continue;

            Election.Candidate pick = candidates.get(RANDOM.nextInt(candidates.size()));
            election.vote(id, pick.getVillagerId(), false);
        }
    }

    /** Records a player's vote. Returns {@code false} if they already voted or it's over. */
    public boolean vote(@Nullable Village village, @Nullable Player player, @Nullable UUID candidateId) {
        Election election = getElection(village);
        if (election == null || player == null) return false;

        return election.vote(player.getUniqueId(), candidateId, true);
    }

    /** Closes the election, installs the winner and returns the losers to the reserve pool. */
    public void finish(@Nullable Election election) {
        if (election == null) return;

        elections.remove(election.getVillageId());
        removeHologram(election.getVillageId());

        Village village = villages.getVillage(election.getVillageId());
        if (village == null) return;

        Election.Candidate winner = election.getWinner(getPlayerVoteWeight(), getVillagerVoteWeight());

        // Reset every loser first: appointing the winner afterwards keeps the nitwit look on
        // exactly one villager even if a loser and the winner are processed in the same pass.
        for (Election.Candidate candidate : election.getCandidates()) {
            if (winner != null && candidate.getVillagerId().equals(winner.getVillagerId())) continue;
            restore(candidate);
        }

        if (winner == null) return;

        Villager elected = findResident(village, winner.getVillagerId());
        if (elected == null) {
            plugin.getLogger().warning("Election in " + village.getDisplayName()
                    + " ended but the winner could not be found; the seat stays vacant and a new "
                    + "election will open.");
            // The winner wandered off or died before the count; leave the seat vacant and the
            // next tick will simply open a fresh election.
            return;
        }

        // The winner was skipped by the restore loop above — on purpose, so it doesn't flip back
        // to its old trade for an instant before being made mayor — which means it still wears
        // the campaign's nitwit look right now, and still carries this system's own candidate
        // mark. Passing its recorded originalProfession explicitly is what stops MayorManager
        // from reading that live nitwit look back as "what to restore" (see the two-argument
        // appointMayor overload); clearing the mark here is what stops releaseStrandedCandidates
        // from later mistaking the new mayor for a candidate whose election never finished.
        mayors.appointMayor(village, elected, winner.getOriginalProfession());
        clearCandidateMark(elected);
        dressedByElection.remove(winner.getVillagerId());
        pendingRestores.remove(winner.getVillagerId());

        village.setMayorProgram(winner.getProgram());

        plugin.getLogger().info("Election in " + village.getDisplayName()
                + " finished; the seat went to " + elected.getUniqueId() + ".");
    }

    private @Nullable Villager findResident(@NotNull Village village, @NotNull UUID id) {
        // Look the entity up globally rather than among current residents: candidates often
        // drift outside the village radius while campaigning, and a loser that wandered off
        // must still be taken out of its nitwit robes.
        return villages.findVillagerById(id);
    }

    /**
     * Puts a losing candidate back exactly as it was.
     * <p>
     * If the villager can't be reached right now (unloaded chunk, or it wandered far off) the
     * restore is queued and retried. Dropping it instead is what stranded villagers as nitwits
     * permanently, and since a nitwit is no longer "unemployed", every later election recruited
     * fresh victims until the whole reserve pool was wearing the robes.
     */
    private void restore(Election.@NotNull Candidate candidate) {
        Villager villager = villages.findVillagerById(candidate.getVillagerId());

        if (villager == null || !villager.isValid()) {
            pendingRestores.put(candidate.getVillagerId(), candidate.getOriginalProfession());
            return;
        }

        setProfessionQuietly(villager, candidate.getOriginalProfession());
        clearCandidateMark(villager);
        dressedByElection.remove(candidate.getVillagerId());
        pendingRestores.remove(candidate.getVillagerId());
    }

    /** Retries restores that couldn't be applied when their election ended. */
    private void retryPendingRestores() {
        if (pendingRestores.isEmpty()) return;

        for (Map.Entry<UUID, Villager.Profession> entry : new ArrayList<>(pendingRestores.entrySet())) {
            Villager villager = villages.findVillagerById(entry.getKey());
            if (villager == null || !villager.isValid()) continue;

            setProfessionQuietly(villager, entry.getValue());
            pendingRestores.remove(entry.getKey());
        }
    }

    /** Whether this villager is still owed a restore, and so must not be recruited again. */
    public boolean isAwaitingRestore(@Nullable UUID villagerId) {
        return villagerId != null && pendingRestores.containsKey(villagerId);
    }

    private void setProfessionQuietly(@NotNull Villager villager, Villager.Profession profession) {
        try {
            // Bail out when nothing changes. The refresh below can reach out to the MineSkin
            // API to generate a missing skin, and this used to run on every pass of the
            // stranded-candidate sweep — which hammered that API until skins stopped resolving
            // at all and every villager fell back to a default one.
            if (villager.getProfession() == profession) return;

            if (++professionChangesThisPass > MAX_PROFESSION_CHANGES_PER_PASS) {
                plugin.getLogger().warning("Refusing to change more than "
                        + MAX_PROFESSION_CHANGES_PER_PASS + " villager professions in one pass — "
                        + "something in the settlement system is misbehaving, so the rest of the "
                        + "village has been left alone.");
                return;
            }

            villager.setProfession(profession);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Couldn't change a candidate's profession: " + throwable.getMessage());
            return;
        }

        // The Bukkit setter doesn't fire the career-change event the tracker refreshes on, so
        // candidates would otherwise keep their old skin for the whole campaign.
        try {
            plugin.getTracker().refreshNPCSkin(villager, false);
        } catch (Throwable ignored) {
            // Cosmetic only — never let a failed refresh break the election.
        }
    }

    public void shutdown() {
        stopTask();
        elections.clear();

        // Displays aren't persistent, but removing them explicitly avoids leaving one behind
        // on a reload where the world stays loaded.
        for (ElectionHologram hologram : holograms.values()) {
            hologram.remove();
        }
        holograms.clear();
    }
}
