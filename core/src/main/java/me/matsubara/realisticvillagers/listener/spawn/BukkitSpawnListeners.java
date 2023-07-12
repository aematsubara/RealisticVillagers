package me.matsubara.realisticvillagers.listener.spawn;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BukkitSpawnListeners implements Listener {

    private final RealisticVillagers plugin;

    public BukkitSpawnListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        handleSpawn(entity, reason);

        if (reason != CreatureSpawnEvent.SpawnReason.INFECTION) return;
        if (event.getEntityType() != EntityType.ZOMBIE_VILLAGER) return;

        String tag = plugin.getTracker().getTransformations().remove(entity.getUniqueId());
        if (tag != null) entity.getPersistentDataContainer().set(
                plugin.getZombieTransformKey(),
                PersistentDataType.STRING,
                tag);
    }

    @EventHandler
    public void onEntitiesLoad(@NotNull EntitiesLoadEvent event) {
        if (!event.getChunk().isLoaded()) return;
        event.getEntities().forEach(this::handleSpawn);
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        event.getWorld().getEntitiesByClass(Villager.class).forEach(this::handleSpawn);
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            handleSpawn(entity);
        }
    }

    public void handleSpawn(Entity entity) {
        handleSpawn(entity, null);
    }

    @SuppressWarnings({"deprecation", "OptionalGetWithoutIsPresent"})
    public void handleSpawn(Entity entity, @Nullable CreatureSpawnEvent.SpawnReason reason) {
        // Is invalid, ignore since we don't want to track those villagers.
        if (!(entity instanceof Villager villager)) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;
        if (handleVillagerMarket(villager)) return;

        boolean createData = reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM;

        INMSConverter converter = plugin.getConverter();
        PersistentDataContainer container = villager.getPersistentDataContainer();

        // Removed previous ignore key, not used anymore.
        container.remove(plugin.getIgnoreVillagerKey());

        // Villager#readAdditionalSaveData() isn't called when entity spawn from egg or by a plugin.
        if (createData) {
            converter.loadDataFromTag(villager, "");
        }

        VillagerTracker tracker = plugin.getTracker();

        // If the zombie villager wasn't an infected villager, the tag will be empty.
        String tag = tracker.getTransformations().remove(villager.getUniqueId());
        if (tag != null) converter.loadDataFromTag(villager, tag);

        // Equip armor (if possible).
        if (!converter.getNPC(villager).get().isWasInfected()
                && villager.isAdult()
                && reason != CreatureSpawnEvent.SpawnReason.BREEDING) {
            plugin.equipVillager(villager, Config.SPAWN_LOOT_FORCE_EQUIP.asBool());
        }

        // Spawn NPC & cache data in the next tick to prevent disguising invalid entities after checking their new metadata.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            tracker.spawnNPC(villager);
            tracker.updateData(villager);
        });
    }

    private boolean handleVillagerMarket(Villager villager) {
        // Plugins that disable the AI - AFTER the villager spawned will be considered as valid, such as VillagerMarket.
        if (plugin.getServer().getPluginManager().getPlugin("VillagerMarket") == null) return false;

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