package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.village.ElectoralProgram;
import me.matsubara.realisticvillagers.village.SecondaryProfession;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
import me.matsubara.realisticvillagers.village.VillageStorage;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns the settlement's workers into a working settlement.
 * <p>
 * Every villager with a trade has a {@link SecondaryProfession} — what that trade means to the
 * village rather than to a player buying from it — and this is what makes that mapping matter:
 * on each round, workers put what they produce into their village's {@link VillageStorage}, which
 * is the pool builders and the mayor draw from. Until this existed the mapping was a table nobody
 * read and the storage was a ledger nobody wrote to, so a village of forty settlers produced
 * exactly nothing.
 * <p>
 * <b>Deliberately not a simulation of the work itself.</b> A villager is credited for its trade,
 * not watched swinging an axe: the goods are the point, and pathing every settler to a tree would
 * cost far more than the result is worth. What it does insist on is that the villager is actually
 * at work — it has a workstation and it is near it — so a settlement asleep or scattered by a
 * raid stops producing, and the pool tracks what the village is really doing.
 */
public final class VillageEconomy {

    private final RealisticVillagers plugin;
    private final VillageManager villages;

    private @Nullable BukkitTask task;

    /**
     * How close to its workstation a villager has to be to count as working, in blocks.
     * <p>
     * Generous on purpose. Villagers wander around their station rather than standing on it, and
     * a radius tight enough to look precise would mostly measure how lucky the timing was.
     */
    private static final double AT_WORK_DISTANCE = 12.0d;

    public VillageEconomy(@NotNull RealisticVillagers plugin, @NotNull VillageManager villages) {
        this.plugin = plugin;
        this.villages = villages;
    }

    public void start() {
        stop();

        long interval = Math.max(20L, Config.VILLAGE_ECONOMY_INTERVAL.asInt(60) * 20L);

        // Say out loud how many trades resolved on this server.
        //
        // The profession mapping swallows anything this Minecraft version doesn't recognise, so a
        // version that named its professions differently would leave the table empty and the
        // whole economy would do nothing at all — quietly, and looking exactly like it was
        // working. One line at startup is the difference between that and a bug that can be seen.
        int mapped = SecondaryProfession.getMappedProfessionCount();
        if (mapped == 0) {
            plugin.getLogger().warning("No villager profession could be mapped to a settlement role; "
                    + "workers will not produce anything.");
        } else {
            plugin.getLogger().info("Settlement economy ready: " + mapped + " villager professions produce goods.");
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void tick() {
        if (!villages.isEnabled() || !Config.VILLAGE_ECONOMY_ENABLED.asBool(true)) return;

        for (Village village : villages.getVillages()) {
            World world = village.getWorld();
            if (world == null || plugin.isDisabledIn(world)) continue;

            // Only while the settlement is awake. Crediting a sleeping village would mean goods
            // appearing overnight from nobody, which is exactly the sort of thing that makes an
            // economy feel bolted on.
            if (!isWorkingHours(world)) continue;

            produce(village);
        }
    }

    private void produce(@NotNull Village village) {
        double chance = Config.VILLAGE_ECONOMY_CHANCE.asDouble(0.5d);
        int min = Math.max(1, Config.VILLAGE_ECONOMY_MIN_AMOUNT.asInt(1));
        int max = Math.max(min, Config.VILLAGE_ECONOMY_MAX_AMOUNT.asInt(3));

        // The sitting mayor's platform decides how hard the settlement works. This is what makes
        // an election visible without commissioning anything: under an industrious mayor the
        // stores fill noticeably faster, under a leisurely one they crawl.
        ElectoralProgram program = village.getMayorProgram();
        double harvest = program != null ? program.getHarvestMultiplier() : 1.0d;

        VillageStorage storage = village.getStorage();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Which trades actually had somebody at their post. Refining is gated on this rather than
        // on who lives here, so a settlement whose lumberjack is asleep saws no planks tonight.
        java.util.Set<SecondaryProfession> working = java.util.EnumSet.noneOf(SecondaryProfession.class);

        for (Villager resident : villages.getResidents(village)) {
            // The mayor governs; it does not also work a trade.
            if (villages.isMayor(village, resident.getUniqueId())) continue;

            SecondaryProfession role = SecondaryProfession.of(resident);
            if (role == null) continue;

            if (!isAtWork(resident)) continue;
            working.add(role);

            if (random.nextDouble() > chance * harvest) continue;

            List<Material> goods = role.getYield();
            if (goods.isEmpty()) continue;

            Material produced = goods.get(random.nextInt(goods.size()));

            // The platform scales the haul as well as the odds, so a mayor elected on filling the
            // stores does both more often and by more.
            int amount = Math.max(1, (int) Math.round(random.nextInt(min, max + 1) * harvest));
            storage.add(produced, amount);
        }

        // Refining runs after gathering, so what came in this round can be worked the next.
        if (plugin.getVillageCrafting() != null) plugin.getVillageCrafting().refine(village, working);
    }

    /**
     * Whether this villager is at its post.
     * <p>
     * A villager with no workstation has a trade in name only — it is between jobs, and vanilla
     * will strip the profession from it before long — so it contributes nothing.
     */
    private boolean isAtWork(@NotNull Villager villager) {
        Location job = jobSite(villager);
        if (job == null) return false;

        World world = job.getWorld();
        if (world == null || !world.equals(villager.getWorld())) return false;

        return job.distanceSquared(villager.getLocation()) <= AT_WORK_DISTANCE * AT_WORK_DISTANCE;
    }

    private @Nullable Location jobSite(@NotNull Villager villager) {
        try {
            return villager.getMemory(MemoryKey.JOB_SITE);
        } catch (Throwable ignored) {
            // Memory keys have moved between versions; that village simply produces nothing
            // rather than the whole economy failing.
            return null;
        }
    }

    /** Daytime, when villagers are out at their stations rather than in bed. */
    private boolean isWorkingHours(@NotNull World world) {
        long time = world.getTime();
        return time < 12000L;
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
