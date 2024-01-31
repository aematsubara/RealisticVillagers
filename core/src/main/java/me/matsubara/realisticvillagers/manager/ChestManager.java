package me.matsubara.realisticvillagers.manager;

import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.handler.protocol.ChestHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ChestManager implements Listener {

    private final Map<Vector, UUID> villagerChests = new ConcurrentHashMap<>();
    private final Map<Vector, Set<UUID>> playerChests = new ConcurrentHashMap<>();

    public ChestManager(@NotNull RealisticVillagers plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        ProtocolLibrary.getProtocolManager().addPacketListener(new ChestHandler(plugin));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Vector vector = block.getLocation().toVector();

        Set<UUID> viewers = playerChests.getOrDefault(vector, Sets.newHashSet());
        viewers.add(event.getPlayer().getUniqueId());

        playerChests.put(vector, viewers);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        Location location = event.getInventory().getLocation();
        if (location == null || location.getBlock().getType() != Material.CHEST) return;

        Vector vector = location.toVector();

        Set<UUID> viewers = playerChests.getOrDefault(vector, Sets.newHashSet());
        viewers.remove(event.getPlayer().getUniqueId());

        if (viewers.isEmpty()) {
            playerChests.remove(vector);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        Location location = event.getInventory().getLocation();
        try {
            World world;
            if (location == null
                    || ((world = location.getWorld()) == null || !world.isChunkLoaded(location.getBlockX(), location.getBlockZ()))
                    || location.getBlock().getType() != Material.CHEST) return;
        } catch (IllegalStateException exception) {
            return;
        }

        Vector vector = location.toVector();

        Set<UUID> viewers = playerChests.getOrDefault(vector, Sets.newHashSet());
        viewers.add(event.getPlayer().getUniqueId());

        playerChests.put(vector, viewers);
    }
}