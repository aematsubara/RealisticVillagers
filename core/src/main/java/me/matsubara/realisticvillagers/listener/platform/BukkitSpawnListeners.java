package me.matsubara.realisticvillagers.listener.platform;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitSpawnListeners implements Listener {

    private final RealisticVillagers plugin;

    // Tracks villagers that already had their family auto-assigned this session.
    // Prevents re-scanning on every chunk reload.
    private final Set<UUID> familyAssigned = ConcurrentHashMap.newKeySet();

    public BukkitSpawnListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        handleSpawn(entity, reason);

        if (reason != CreatureSpawnEvent.SpawnReason.INFECTION)
            return;
        if (event.getEntityType() != EntityType.ZOMBIE_VILLAGER)
            return;

        String tag = plugin.getTracker().getTransformations().remove(entity.getUniqueId());
        if (tag != null)
            entity.getPersistentDataContainer().set(
                    plugin.getZombieTransformKey(),
                    PersistentDataType.STRING,
                    tag);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitiesLoad(@NotNull EntitiesLoadEvent event) {
        if (!event.getChunk().isLoaded())
            return;
        event.getEntities().forEach(this::handleSpawn);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        event.getWorld().getEntitiesByClass(AbstractVillager.class).forEach(this::handleSpawn);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            handleSpawn(entity);
        }
    }

    public void handleSpawn(Entity entity) {
        handleSpawn(entity, null);
    }

    @SuppressWarnings({ "OptionalGetWithoutIsPresent" })
    public void handleSpawn(Entity entity, @Nullable CreatureSpawnEvent.SpawnReason reason) {
        // Is invalid, ignore since we don't want to track those villagers.
        if (!(entity instanceof AbstractVillager villager))
            return;
        if (plugin.getTracker().isInvalid(villager, true))
            return;
        if (villager instanceof Villager temp && handleVillagerMarket(temp))
            return;

        boolean createData = reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || (villager.getType() == EntityType.WANDERING_TRADER
                        && reason == CreatureSpawnEvent.SpawnReason.NATURAL);

        INMSConverter converter = plugin.getConverter();
        PersistentDataContainer container = villager.getPersistentDataContainer();

        // Removed the previous "ignore" key, not used anymore.
        container.remove(plugin.getIgnoreVillagerKey());

        // Villager#readAdditionalSaveData() isn't called when an entity spawns from an
        // egg or by a plugin.
        if (createData) {
            converter.loadDataFromTag(villager, "");
        }

        VillagerTracker tracker = plugin.getTracker();

        // If the zombie villager wasn't an infected villager, the tag will be empty.
        String tag = tracker.getTransformations().remove(villager.getUniqueId());
        if (tag != null)
            converter.loadDataFromTag(villager, tag);

        // Equip armor (if possible).
        if (!converter.getNPC(villager).get().isWasInfected()
                && villager.isAdult()
                && reason != CreatureSpawnEvent.SpawnReason.BREEDING) {
            plugin.equipVillager(villager, Config.SPAWN_LOOT_FORCE_EQUIP.asBool());
        }

        // Spawn NPC & cache data in the next tick to prevent disguising invalid
        // entities after checking their new metadata.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            tracker.spawnNPC(villager);
            tracker.updateData(villager);
            // Only assign family for fresh spawns (reason != null) OR for chunk-loaded
            // villagers that have never been scanned this session.
            // Either way, each villager is only scanned once per session via
            // familyAssigned.
            if (villager instanceof Villager realVillager
                    && villager.isAdult()
                    && reason != CreatureSpawnEvent.SpawnReason.BREEDING
                    && Config.AUTO_ASSIGN_FAMILY.asBool()
                    && familyAssigned.add(realVillager.getUniqueId())) {
                autoAssignFamily(converter, realVillager);
            }
        });
    }

    private void autoAssignFamily(@NotNull INMSConverter converter, @NotNull Villager villager) {
        IVillagerNPC npc = converter.getNPC(villager).orElse(null);
        if (npc == null || npc.getFather() != null || npc.getMother() != null)
            return;

        IVillagerNPC fatherNPC = null;
        IVillagerNPC motherNPC = null;
        for (Entity nearby : villager.getNearbyEntities(50, 10, 50)) {
            if (!(nearby instanceof Villager nearbyVillager) || !nearbyVillager.isAdult())
                continue;

            IVillagerNPC nearbyNPC = converter.getNPC(nearbyVillager).orElse(null);
            if (nearbyNPC == null)
                continue;

            if (fatherNPC == null && nearbyNPC.isMale()) {
                if (motherNPC != null && !Config.ALLOW_PROCREATION_BETWEEN_FAMILY_MEMBERS.asBool()
                        && motherNPC.isFamily(nearbyNPC.getUniqueId(), false))
                    continue;
                // Skip if nearbyNPC already has npc as a child or if npc is nearbyNPC's own
                // parent (circular guard).
                boolean hasNpcAsChild = nearbyNPC.getChildrens().stream()
                        .anyMatch(c -> c.getUniqueId().equals(npc.getUniqueId()));
                boolean npcIsNearbyParent = (nearbyNPC.getFather() != null
                        && nearbyNPC.getFather().getUniqueId().equals(npc.getUniqueId()))
                        || (nearbyNPC.getMother() != null
                                && nearbyNPC.getMother().getUniqueId().equals(npc.getUniqueId()));
                if (!hasNpcAsChild && !npcIsNearbyParent)
                    fatherNPC = nearbyNPC;
            } else if (motherNPC == null && nearbyNPC.isFemale()) {
                if (fatherNPC != null && !Config.ALLOW_PROCREATION_BETWEEN_FAMILY_MEMBERS.asBool()
                        && fatherNPC.isFamily(nearbyNPC.getUniqueId(), false))
                    continue;
                boolean hasNpcAsChild = nearbyNPC.getChildrens().stream()
                        .anyMatch(c -> c.getUniqueId().equals(npc.getUniqueId()));
                boolean npcIsNearbyParent = (nearbyNPC.getFather() != null
                        && nearbyNPC.getFather().getUniqueId().equals(npc.getUniqueId()))
                        || (nearbyNPC.getMother() != null
                                && nearbyNPC.getMother().getUniqueId().equals(npc.getUniqueId()));
                if (!hasNpcAsChild && !npcIsNearbyParent)
                    motherNPC = nearbyNPC;
            }
            if (fatherNPC != null && motherNPC != null)
                break;
        }

        if (fatherNPC != null)
            npc.setParent(fatherNPC);
        if (motherNPC != null)
            npc.setParent(motherNPC);
    }

    private boolean handleVillagerMarket(Villager villager) {
        // Plugins that disable the AI - AFTER the villager spawned will be considered
        // as valid, such as VillagerMarket.
        if (plugin.getServer().getPluginManager().getPlugin("VillagerMarket") == null)
            return false;

        for (StackTraceElement stacktrace : new Throwable().getStackTrace()) {
            String method = stacktrace.getMethodName(), clazz = stacktrace.getClassName();
            if (method.equals("spawnShop") && clazz.equals("net.bestemor.villagermarket.shop.ShopManager")) {
                plugin.getTracker().getHandler().getAllowSpawn().add(villager.getUniqueId());
                return true;
            }
        }

        return false;
    }
}