package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.idle;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.OfflineVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.bukkit.craftbukkit.v1_21_R5.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class VillagerMakeLove extends Behavior<Villager> {

    private static final int INTERACT_DIST_SQR = 5;
    private static final float SPEED_MODIFIER = 0.5f;

    private long birthTimestamp;

    public VillagerMakeLove() {
        super(ImmutableMap.of(
                        MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT,
                        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT),
                350,
                350);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return isBreedingPossible(villager);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return time <= birthTimestamp && isBreedingPossible(villager);
    }

    @Override
    public void start(@NotNull ServerLevel level, @NotNull Villager villager, long time) {
        AgeableMob breedWith = villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(villager, breedWith, SPEED_MODIFIER, 2);
        level.broadcastEntityEvent(breedWith, (byte) 18);
        level.broadcastEntityEvent(villager, (byte) 18);
        int nextBirth = 275 + villager.getRandom().nextInt(50);
        birthTimestamp = time + (long) nextBirth;
    }

    @Override
    public void tick(ServerLevel level, @NotNull Villager villager, long time) {
        Villager breedWith = (Villager) villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        if (villager.distanceToSqr(breedWith) > INTERACT_DIST_SQR) return;

        if (time % 20 == 0) {
            if (breedWith instanceof VillagerNPC npc && npc.isFemale()) {
                if (npc.onGround()) npc.jumpIfPossible();
            } else {
                if (villager.onGround()) villager.getJumpControl().jump();
            }
        }

        BehaviorUtils.lockGazeAndWalkToEachOther(villager, breedWith, SPEED_MODIFIER, 2);
        if (time >= birthTimestamp) {
            villager.eatAndDigestFood();
            breedWith.eatAndDigestFood();
            tryToGiveBirth(level, villager, breedWith);
        } else if (villager.getRandom().nextInt(35) == 0) {
            level.broadcastEntityEvent(breedWith, (byte) 12);
            level.broadcastEntityEvent(villager, (byte) 12);
        }
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
    }

    private void tryToGiveBirth(ServerLevel level, Villager villager, Villager breedWith) {
        Optional<BlockPos> optional = takeVacantBed(level, villager);
        if (optional.isEmpty()) {
            level.broadcastEntityEvent(breedWith, (byte) 13);
            level.broadcastEntityEvent(villager, (byte) 13);
        } else {
            Optional<Villager> optional1 = breed(level, villager, breedWith);
            if (optional1.isPresent()) {
                giveBedToChild(level, optional1.get(), optional.get());
            } else {
                level.getPoiManager().release(optional.get());
                DebugPackets.sendPoiTicketCountPacket(level, optional.get());
            }
        }

    }

    private boolean isBreedingPossible(@NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        Optional<AgeableMob> optional = brain.getMemory(MemoryModuleType.BREED_TARGET)
                .filter((breedWith) -> breedWith.getType() == EntityType.VILLAGER);

        return optional.isPresent()
                && BehaviorUtils.targetIsValid(brain, MemoryModuleType.BREED_TARGET, EntityType.VILLAGER)
                && villager.canBreed()
                && optional.get().canBreed();
    }

    private Optional<BlockPos> takeVacantBed(@NotNull ServerLevel level, @NotNull Villager villager) {
        return level.getPoiManager().take(
                (holder) -> holder.is(PoiTypes.HOME),
                (holder, position) -> canReach(villager, position, holder),
                villager.blockPosition(),
                48);
    }

    private boolean canReach(@NotNull Villager villager, BlockPos blockposition, @NotNull Holder<PoiType> holder) {
        Path path = villager.getNavigation().createPath(blockposition, holder.value().validRange());
        return path != null && path.canReach();
    }

    private Optional<Villager> breed(ServerLevel level, Villager villager, Villager breedWith) {
        if (!(villager instanceof VillagerNPC npc) || !(breedWith instanceof VillagerNPC breed)) {
            return Optional.empty();
        }

        VillagerNPC baby = npc.getBreedOffspring(level, breedWith);
        if (baby == null) return Optional.empty();

        if (CraftEventFactory.callEntityBreedEvent(baby, villager, breedWith, null, null, 0).isCancelled()) {
            return Optional.empty();
        }

        villager.setAge(6000);
        breedWith.setAge(6000);

        // To initialize name and sex.
        baby.loadFromOffline(OfflineVillagerNPC.DUMMY_OFFLINE);

        // Add children to parents' list.
        breed.getChildrens().add(baby.getOffline());
        npc.getChildrens().add(baby.getOffline());

        baby.setAge(-24000);
        baby.setPos(villager.getX(), villager.getY(), villager.getZ());
        baby.setYRot(0.0f);
        baby.setXRot(0.0f);

        baby.setMother(npc.isFemale() ? npc.getOffline() : breed.getOffline());
        baby.setFather(npc.isFemale() ? breed.getOffline() : npc.getOffline());
        baby.setFatherVillager(true);

        level.addFreshEntityWithPassengers(baby, SpawnReason.BREEDING);
        level.broadcastEntityEvent(baby, (byte) 12);

        // Only marry the villagers if they don't have a partner.
        if (!npc.hasPartner() && !breed.hasPartner()) {
            npc.setPartner(breedWith.getUUID(), true);
            breed.setPartner(villager.getUUID(), true);
        }

        return Optional.of(baby);
    }

    private void giveBedToChild(@NotNull ServerLevel level, @NotNull Villager villager, BlockPos position) {
        GlobalPos globalpos = GlobalPos.of(level.dimension(), position);
        villager.getBrain().setMemory(MemoryModuleType.HOME, globalpos);
    }
}