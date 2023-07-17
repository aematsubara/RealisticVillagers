package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.customblockdata.CustomBlockData;
import me.matsubara.realisticvillagers.util.customblockdata.events.CustomBlockDataEvent;
import me.matsubara.realisticvillagers.util.customblockdata.events.CustomBlockDataMoveEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlockListeners implements Listener {

    private final RealisticVillagers plugin;

    public BlockListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null) return;

        String tag = meta.getPersistentDataContainer().get(plugin.getNpcValuesKey(), PersistentDataType.STRING);
        if (tag == null) return;

        PersistentDataContainer customBlockData = new CustomBlockData(event.getBlock(), plugin);
        customBlockData.set(plugin.getNpcValuesKey(), PersistentDataType.STRING, tag);
    }

    @EventHandler
    public void onCustomBlockData(@NotNull CustomBlockDataEvent event) {
        Block block = event.getBlock();
        CustomBlockData data = event.getCustomBlockData();

        String tag = data.get(plugin.getNpcValuesKey(), PersistentDataType.STRING);
        if (tag == null) return;

        IVillagerNPC npc = plugin.getConverter().getNPCFromTag(tag);
        if (npc == null) return;

        Event bukkitEvent = event.getBukkitEvent();

        // Prevent moving block.
        if (event instanceof CustomBlockDataMoveEvent move) {
            cancel(move, bukkitEvent);
            return;
        }

        // Prevent explode events dropping vanilla block and remove it manually.
        if (bukkitEvent instanceof BlockExplodeEvent blockExplode) {
            handleExplode(blockExplode.blockList(), block);
        } else if (bukkitEvent instanceof EntityExplodeEvent entityExplode) {
            handleExplode(entityExplode.blockList(), block);
        }

        // Prevent liquid events dropping vanilla block and remove it manually.
        if (bukkitEvent instanceof BlockFromToEvent fromTo) {
            cancel(fromTo);
            fromTo.getToBlock().setType(Material.AIR);
        }

        block.getWorld().dropItemNaturally(block.getLocation().add(0.5d, 0.5d, 0.5d), plugin.getReviveManager().createHeadItem(npc, tag));
    }

    private void cancel(Event @NotNull ... events) {
        for (Event event : events) {
            if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
        }
    }

    private void handleExplode(@NotNull List<Block> blocks, Block block) {
        blocks.remove(block);
        block.setType(Material.AIR);
    }
}
