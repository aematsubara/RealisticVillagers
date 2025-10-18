package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.work;

import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.block.Barrel;
import org.bukkit.craftbukkit.v1_21_R6.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R6.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class WorkAtBarrel extends WorkAtPoi {

    private static final Set<Item> SAVE_ITEMS = ImmutableSet.of(
            Items.COD,
            Items.SALMON,
            Items.TROPICAL_FISH,
            Items.PUFFERFISH);

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        // Don't work at station if fishing.
        return !Config.DISABLE_SKINS.asBool()
                && super.checkExtraStartConditions(level, villager)
                && (!(villager instanceof VillagerNPC npc) || !npc.isFishing());
    }

    @Override
    public void useWorkstation(ServerLevel level, @NotNull Villager villager) {
        Optional<GlobalPos> optional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (optional.isEmpty()) return;

        GlobalPos pos = optional.get();

        BlockState state = level.getBlockState(pos.pos());
        if (!state.is(Blocks.BARREL)) return;

        List<ItemStack> save = new ArrayList<>();
        for (ItemStack item : villager.getInventory().getContents()) {
            if (!SAVE_ITEMS.contains(item.getItem())) continue;
            save.add(item.copy());
            item.shrink(item.getCount());
        }

        if (save.isEmpty()) return;

        Barrel barrel = (Barrel) CraftBlock.at(level, pos.pos()).getState();
        Inventory inventory = barrel.getSnapshotInventory();
        for (ItemStack item : save) {
            inventory.addItem(CraftItemStack.asBukkitCopy(item));
        }
        barrel.setBlockData(barrel.getBlockData());
        barrel.update();
    }
}