package me.matsubara.realisticvillagers.npc;

import com.google.common.base.Preconditions;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NPCPool implements Listener {

    private final RealisticVillagers plugin;
    private final long tabListRemoveTicks;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();

    private NPCPool(RealisticVillagers plugin, long tabListRemoveTicks) {
        this.plugin = plugin;
        this.tabListRemoveTicks = tabListRemoveTicks;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        tick();
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Builder builder(RealisticVillagers plugin) {
        return new Builder(plugin);
    }

    protected void tick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (NPC npc : npcMap.values()) {
                    Location npcLocation = npc.getLocation();
                    Location playerLocation = player.getLocation();

                    World npcWorld = npcLocation.getWorld();
                    if (npcWorld == null) continue;

                    if (!npcWorld.equals(playerLocation.getWorld())
                            || !npcWorld.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
                        // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
                        if (npc.isShownFor(player)) npc.hide(player);
                        continue;
                    }

                    // Only considered in range if the real entity is being tracked by the player.
                    boolean tracked = plugin.getConverter().isBeingTracked(player, npc.getEntityId());

                    if (!tracked && npc.isShownFor(player)) {
                        npc.hide(player);
                    } else if (tracked && !npc.isShownFor(player)) {
                        npc.show(player, plugin, tabListRemoveTicks);
                    }
                }
            }
        }, 30L, 30L);
    }

    protected void takeCareOf(NPC npc) {
        npcMap.put(npc.getEntityId(), npc);
    }

    public Optional<NPC> getNPC(int entityId) {
        return Optional.ofNullable(npcMap.get(entityId));
    }

    public Optional<NPC> getNPC(UUID uniqueId) {
        return npcMap.values().stream().filter(npc -> npc.getProfile().getUUID().equals(uniqueId)).findFirst();
    }

    public void removeNPC(int entityId) {
        getNPC(entityId).ifPresent(npc -> {
            npcMap.remove(entityId);
            npc.getSeeingPlayers().forEach(npc::hide);
        });
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.hide(player));
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.removeSeeingPlayer(player));
    }

    public static class Builder {

        private final RealisticVillagers plugin;
        private long tabListRemoveTicks = 30;

        private Builder(RealisticVillagers plugin) {
            this.plugin = Preconditions.checkNotNull(plugin, "Plugin can't be null!");
        }

        public Builder tabListRemoveTicks(long tabListRemoveTicks) {
            this.tabListRemoveTicks = tabListRemoveTicks;
            return this;
        }

        public NPCPool build() {
            return new NPCPool(plugin, tabListRemoveTicks);
        }
    }
}