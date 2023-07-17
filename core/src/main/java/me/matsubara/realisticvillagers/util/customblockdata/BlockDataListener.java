/*
 * Copyright (c) 2022 Alexander Majka (mfnalex) / JEFF Media GbR
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * If you need help or have any suggestions, feel free to join my Discord and head to #programming-help:
 *
 * Discord: https://discord.jeff-media.com/
 *
 * If you find this library helpful or if you're using it one of your paid plugins, please consider leaving a donation
 * to support the further development of this project :)
 *
 * Donations: https://paypal.me/mfnalex
 */

package me.matsubara.realisticvillagers.util.customblockdata;

import me.matsubara.realisticvillagers.util.customblockdata.events.CustomBlockDataMoveEvent;
import me.matsubara.realisticvillagers.util.customblockdata.events.CustomBlockDataRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

final class BlockDataListener implements Listener {

    private final Plugin plugin;
    private final Predicate<Block> customDataPredicate;

    public BlockDataListener(Plugin plugin) {
        this.plugin = plugin;
        this.customDataPredicate = block -> CustomBlockData.hasCustomBlockData(block, plugin);
    }

    @Contract("_ -> new")
    private @NotNull CustomBlockData getCbd(@NotNull BlockEvent event) {
        return getCbd(event.getBlock());
    }

    @Contract("_ -> new")
    private @NotNull CustomBlockData getCbd(Block block) {
        return new CustomBlockData(block, plugin);
    }

    private void callAndRemove(BlockEvent blockEvent) {
        if (callEvent(blockEvent)) {
            getCbd(blockEvent).clear();
        }
    }

    private boolean callEvent(BlockEvent blockEvent) {
        return callEvent(blockEvent.getBlock(), blockEvent);
    }

    private boolean callEvent(Block block, Event bukkitEvent) {
        if (!CustomBlockData.hasCustomBlockData(block, plugin) || CustomBlockData.isProtected(block, plugin)) {
            return false;
        }

        CustomBlockDataRemoveEvent cbdEvent = new CustomBlockDataRemoveEvent(plugin, block, bukkitEvent);
        Bukkit.getPluginManager().callEvent(cbdEvent);

        return !cbdEvent.isCancelled();
    }

    private void callAndRemoveBlockStateList(@NotNull List<BlockState> blockStates, Event bukkitEvent) {
        blockStates.stream()
                .map(BlockState::getBlock)
                .filter(customDataPredicate)
                .forEach(block -> callAndRemove(block, bukkitEvent));
    }

    private void callAndRemoveBlockList(@NotNull List<Block> blocks, Event bukkitEvent) {
        blocks.stream()
                .filter(customDataPredicate)
                .forEach(block -> callAndRemove(block, bukkitEvent));
    }

    private void callAndRemove(Block block, Event bukkitEvent) {
        if (callEvent(block, bukkitEvent)) {
            getCbd(block).clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        callAndRemove(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        if (!CustomBlockData.isDirty(event.getBlock())) {
            callAndRemove(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntity(@NotNull EntityChangeBlockEvent event) {
        if (event.getTo() != event.getBlock().getType()) {
            callAndRemove(event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        callAndRemoveBlockList(event.blockList(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        callAndRemoveBlockList(event.blockList(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        callAndRemove(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(@NotNull BlockPistonExtendEvent event) {
        handlePistonHead(event);
        onPiston(event.getBlocks(), event);
    }

    private void handlePistonHead(@NotNull BlockPistonExtendEvent event) {
        Block pistonHead = event.getBlock().getRelative(event.getDirection().getOppositeFace());
        if (!CustomBlockData.hasCustomBlockData(pistonHead, plugin)) return;

        Block destinationBlock = pistonHead.getRelative(event.getDirection());

        CustomBlockDataMoveEvent moveEvent = new CustomBlockDataMoveEvent(plugin, pistonHead, destinationBlock, event);
        Bukkit.getPluginManager().callEvent(moveEvent);
        if (moveEvent.isCancelled()) return;

        CustomBlockData cbd = getCbd(pistonHead);
        cbd.copyTo(pistonHead.getRelative(event.getDirection()), plugin);
        cbd.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        onPiston(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(@NotNull BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FIRE) return;
        if (event.getNewState().getType() != event.getBlock().getType()) {
            callAndRemove(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        callAndRemove(event.getToBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructure(StructureGrowEvent event) {
        callAndRemoveBlockStateList(event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        callAndRemoveBlockStateList(event.getBlocks(), event);
    }

    private void onPiston(@NotNull List<Block> blocks, @NotNull BlockPistonEvent bukkitEvent) {
        Map<Block, CustomBlockData> map = new LinkedHashMap<>();
        BlockFace direction = bukkitEvent.getDirection();
        blocks.stream().filter(customDataPredicate).forEach(block -> {
            CustomBlockData cbd = new CustomBlockData(block, plugin);
            if (cbd.isEmpty() || cbd.isProtected()) return;
            Block destinationBlock = block.getRelative(direction);
            CustomBlockDataMoveEvent moveEvent = new CustomBlockDataMoveEvent(plugin, block, destinationBlock, bukkitEvent);
            Bukkit.getPluginManager().callEvent(moveEvent);
            if (moveEvent.isCancelled()) return;
            map.put(destinationBlock, cbd);
        });
        reverse(map).forEach((block, cbd) -> {
            cbd.copyTo(block, plugin);
            cbd.clear();
        });
    }

    private <K, V> @NotNull Map<K, V> reverse(@NotNull Map<K, V> map) {
        LinkedHashMap<K, V> reversed = new LinkedHashMap<>();
        List<K> keys = new ArrayList<>(map.keySet());
        Collections.reverse(keys);
        keys.forEach((key) -> reversed.put(key, map.get(key)));
        return reversed;
    }
}