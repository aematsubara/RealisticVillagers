package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.work.HarvestFarmland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class SecondaryPoiSensor extends Sensor<Villager> {

    private static final int SCAN_RATE = 40;

    public SecondaryPoiSensor() {
        super(SCAN_RATE);
    }

    @Override
    public void doTick(@NotNull ServerLevel level, @NotNull Villager villager) {
        ResourceKey<Level> dimension = level.dimension();
        BlockPos position = villager.blockPosition();

        List<GlobalPos> positions = Lists.newArrayList();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos offset = position.offset(x, y, z);
                    Block block = level.getBlockState(offset).getBlock();

                    Holder<VillagerProfession> professionHolder = villager.getVillagerData().profession();
                    VillagerProfession profession = professionHolder.value();

                    if (profession.secondaryPoi().contains(block) ||
                            (professionHolder.is(VillagerProfession.FARMER) && ArrayUtils.contains(HarvestFarmland.DIRT, block))) {
                        positions.add(GlobalPos.of(dimension, offset));
                    }
                }
            }
        }

        Brain<Villager> brain = villager.getBrain();
        if (!positions.isEmpty()) {
            brain.setMemory(MemoryModuleType.SECONDARY_JOB_SITE, positions);
        } else {
            brain.eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.SECONDARY_JOB_SITE);
    }
}