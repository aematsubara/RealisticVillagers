package me.matsubara.realisticvillagers.npc;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class NPCPool implements Listener, Runnable {

    private final @Getter RealisticVillagers plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();
    private int tick = 0;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public NPCPool(RealisticVillagers plugin) {
        this.plugin = plugin;
        Server server = this.plugin.getServer();
        server.getPluginManager().registerEvents(this, plugin);
        server.getScheduler().runTaskTimerAsynchronously(plugin, this, 1L, 1L); // We can do this on every tick (async).
    }

    @Override
    public void run() {
        for (NPC npc : npcMap.values()) {
            LivingEntity bukkit = npc.getNpc().bukkit();
            if (bukkit == null) continue;

            Location location = bukkit.getLocation();

            World world = location.getWorld();
            if (world == null) continue;

            for (Player player : List.copyOf(world.getPlayers())) {
                if (!player.isValid()) continue;
                handleVisibility(player, player.getLocation(), npc);
            }
        }
        tick++;
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
            LivingEntity bukkit = npc.getNpc().bukkit();
            if (bukkit != null) {
                List.copyOf(bukkit.getWorld().getPlayers()).forEach(npc::hide);
            } else {
                npc.getSeeingPlayers().forEach(npc::hide);
            }
        });
    }

    public void handleVisibility(@NotNull Player player, Location playerLocation, @NotNull NPC npc) {
        LivingEntity bukkit = npc.getNpc().bukkit();
        if (bukkit == null) return;

        Location npcLocation = bukkit.getLocation();

        World world = npcLocation.getWorld();
        if (world == null) return;

        if (!world.equals(playerLocation.getWorld())
                || !world.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
            // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
            if (npc.isShownFor(player)) npc.hide(player);
            return;
        }

        double renderDistance = Math.min(Math.pow(Config.RENDER_DISTANCE.asInt(), 2), BUKKIT_VIEW_DISTANCE);
        boolean npcRange = npcLocation.distanceSquared(playerLocation) <= renderDistance;

        if (!npcRange && npc.isShownFor(player)) {
            npc.hide(player);
        } else if (npcRange && !npc.isShownFor(player)) {
            npc.show(player, npcLocation);
        }

        // Send passengers again to prevent ghost-nametags.
        if (tick % 50 == 0 && npc.isShownFor(player)) {
            npc.sendPassengers(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        // If the player switched worlds, then we'll handle the visibility on onPlayerChangedWorld().
        Location to = event.getTo();
        if (to == null || !event.getPlayer().getWorld().equals(to.getWorld())) return;

        handleEventVisibility(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        handleEventVisibility(event);
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        handleEventVisibility(event);
    }

    private void handleEventVisibility(@NotNull PlayerEvent event) {
        Player player = event.getPlayer();

        Location location = Objects.requireNonNullElse(
                event instanceof PlayerTeleportEvent teleport ? teleport.getTo() : null,
                player.getLocation());

        for (NPC npc : npcMap.values()) {
            handleVisibility(player, location, npc);
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        handleRemoval(event.getEntity(), NPC::hide);
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        // No need to send remove packets, the player left.
        handleRemoval(event.getPlayer(), NPC::removeSeeingPlayer);
    }

    private void handleRemoval(Player player, BiConsumer<NPC, Player> action) {
        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> action.accept(npc, player));
    }
}