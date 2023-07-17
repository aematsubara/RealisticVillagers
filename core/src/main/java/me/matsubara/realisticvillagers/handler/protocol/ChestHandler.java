package me.matsubara.realisticvillagers.handler.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.manager.ChestManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChestHandler extends PacketAdapter {

    private final RealisticVillagers plugin;

    public ChestHandler(RealisticVillagers plugin) {
        super(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.NAMED_SOUND_EFFECT,
                PacketType.Play.Server.BLOCK_ACTION);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(@NotNull PacketEvent event) {
        PacketContainer packet = event.getPacket();
        World world = event.getPlayer().getWorld();

        if (packet.getType() == PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            Sound sound;
            try {
                sound = packet.getSoundEffects().readSafely(0);
            } catch (IllegalStateException | NullPointerException exception) {
                // Should "fix" IllegalStateException: Unable to invoke method public static org.bukkit.Sound org.bukkit.craftbukkit.v1_19_R2.CraftSound.getBukkit(net.minecraft.sounds.SoundEffect);
                return;
            }
            if (sound != Sound.BLOCK_CHEST_OPEN && sound != Sound.BLOCK_CHEST_CLOSE) return;

            StructureModifier<Integer> integers = packet.getIntegers();
            double x = (float) integers.readSafely(0) / 8.0f;
            double y = (float) integers.readSafely(1) / 8.0f;
            double z = (float) integers.readSafely(2) / 8.0f;
            Location location = new Location(world, x, y, z);

            Block block = location.getBlock();
            if (block.getType() != Material.CHEST) return;

            if (shouldCancel(block)) {
                event.setCancelled(true);
            }
            return;
        }

        Material material = packet.getBlocks().readSafely(0);
        if (material != Material.CHEST) return;

        // We only want to cancel the close animation if a villager has the inventory open.
        boolean open = packet.getIntegers().readSafely(1) == 1;
        if (open) return;

        BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);
        Location location = pos.toLocation(world);

        Block block = world.getBlockAt(location);
        if (block.getType() != Material.CHEST) return;

        Vector vector = pos.toVector();

        ChestManager chestManager = plugin.getChestManager();
        if (chestManager.getVillagerChests().containsKey(vector)) {
            event.setCancelled(true);
        }

        Map<Vector, Set<UUID>> playerChests = plugin.getChestManager().getPlayerChests();

        Set<UUID> viewers = playerChests.get(vector);
        if (viewers == null) return;

        Chest chest = (Chest) block.getState();
        viewers.removeIf(uuid -> !isViewer(chest, uuid));

        if (viewers.isEmpty()) {
            playerChests.remove(vector);
        }
    }

    private boolean isViewer(@NotNull Chest chest, UUID uuid) {
        for (HumanEntity viewer : chest.getBlockInventory().getViewers()) {
            if (viewer.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    private boolean shouldCancel(@NotNull Block block) {
        if (block.getType() != Material.CHEST) return false;

        Chest chest = (Chest) block.getState();
        if (!(chest.getInventory() instanceof DoubleChestInventory doubleChest)) return test(block);

        return (test(doubleChest.getLeftSide())) || test(doubleChest.getRightSide());
    }

    private boolean test(@NotNull Inventory inventory) {
        Location location = inventory.getLocation();
        return location != null && test(location.getBlock());
    }

    private boolean test(@NotNull Block block) {
        ChestManager chestManager = plugin.getChestManager();
        Vector vector = block.getLocation().toVector();

        boolean isBeingLooted = chestManager.getVillagerChests().containsKey(vector);
        boolean isOpen = !((Chest) block.getState()).getInventory().getViewers().isEmpty();
        boolean isAboutToBeOpenOrClosed = chestManager.getPlayerChests().containsKey(vector);

        return isBeingLooted && (isOpen || isAboutToBeOpenOrClosed);
    }
}