package me.matsubara.realisticvillagers.listener.spawn;

import me.matsubara.realisticvillagers.RealisticVillagers;
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
import org.bukkit.persistence.PersistentDataType;

@SuppressWarnings("ClassCanBeRecord")
public class BukkitSpawnListeners implements Listener {

    private final RealisticVillagers plugin;

    public BukkitSpawnListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        CreatureSpawnEvent.SpawnReason spawnReason = event.getSpawnReason();
        handleSpawn(entity, spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || spawnReason == CreatureSpawnEvent.SpawnReason.CUSTOM);

        if (spawnReason != CreatureSpawnEvent.SpawnReason.INFECTION) return;
        if (event.getEntityType() != EntityType.ZOMBIE_VILLAGER) return;

        String tag = plugin.getVillagerTracker().getTransformations().remove(entity.getUniqueId());
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

        // Villager#readAdditionalSaveData() isn't called when entity spawn from egg or by a plugin.
        if (createData) {
            plugin.getConverter().loadDataFromTag(villager, "");
        }

        VillagerTracker tracker = plugin.getVillagerTracker();
        tracker.add(villager);

        String tag = tracker.getTransformations().remove(villager.getUniqueId());
        if (tag != null) plugin.getConverter().loadDataFromTag(villager, tag);

        tracker.spawnNPC(villager);
    }
}