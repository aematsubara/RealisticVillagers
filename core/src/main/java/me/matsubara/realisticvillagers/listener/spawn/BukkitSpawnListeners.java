package me.matsubara.realisticvillagers.listener.spawn;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public class BukkitSpawnListeners implements Listener {

    private final RealisticVillagers plugin;

    public BukkitSpawnListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        handleSpawn(
                entity,
                reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG || reason == CreatureSpawnEvent.SpawnReason.CUSTOM);

        if (reason != CreatureSpawnEvent.SpawnReason.INFECTION) return;
        if (event.getEntityType() != EntityType.ZOMBIE_VILLAGER) return;

        String tag = plugin.getTracker().getTransformations().remove(entity.getUniqueId());
        if (tag != null) entity.getPersistentDataContainer().set(
                plugin.getZombieTransformKey(),
                PersistentDataType.STRING,
                tag);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!event.getChunk().isLoaded()) return;
        event.getEntities().forEach(this::handleSpawn);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().getEntitiesByClass(Villager.class).forEach(this::handleSpawn);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            handleSpawn(entity);
        }
    }

    public void handleSpawn(Entity entity) {
        handleSpawn(entity, false);
    }

    public void handleSpawn(Entity entity, boolean createData) {
        if (!(entity instanceof Villager villager)) return;

        INMSConverter converter = plugin.getConverter();
        PersistentDataContainer container = villager.getPersistentDataContainer();

        Optional<IVillagerNPC> npc = converter.getNPC(villager);

        // Isn't custom, ignore since we don't want to track vanilla villagers.
        if (npc.isEmpty()) {
            container.set(plugin.getIgnoreVillagerKey(), PersistentDataType.INTEGER, 1);
            return;
        }

        // Is a custom one, but previously was marked as a vanilla one; we need to convert it back to vanilla.
        if (container.has(plugin.getIgnoreVillagerKey(), PersistentDataType.INTEGER)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> npc.get().convertToVanilla());
            return;
        }

        // Villager#readAdditionalSaveData() isn't called when entity spawn from egg or by a plugin.
        if (createData) {
            converter.loadDataFromTag(villager, "");
        }

        VillagerTracker tracker = plugin.getTracker();
        tracker.add(villager);

        // If the zombie villager wasn't an infected villager, the tag will be empty.
        String tag = tracker.getTransformations().remove(villager.getUniqueId());
        if (tag != null) converter.loadDataFromTag(villager, tag);

        tracker.spawnNPC(villager);
    }
}