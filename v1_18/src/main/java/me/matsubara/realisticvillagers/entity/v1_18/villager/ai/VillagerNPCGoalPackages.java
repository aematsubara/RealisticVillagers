package me.matsubara.realisticvillagers.entity.v1_18.villager.ai;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_18.pet.PetCat;
import me.matsubara.realisticvillagers.entity.v1_18.pet.PetParrot;
import me.matsubara.realisticvillagers.entity.v1_18.pet.PetWolf;
import me.matsubara.realisticvillagers.entity.v1_18.pet.horse.HorseEating;
import me.matsubara.realisticvillagers.entity.v1_18.villager.VillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.*;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.GiveGiftToHero;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.SetEntityLookTarget;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.ShowTradesToPlayer;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.TradeWithVillager;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.*;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.GoToPotentialJobSite;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.GoToWantedItem;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.LookAtTargetSink;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.MoveToTargetSink;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.ReactToBell;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.SetRaidStatus;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.VillagerPanicTrigger;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.core.YieldJobSite;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.fight.*;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.fight.BackUpIfTooClose;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.fight.MeleeAttack;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.fight.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.fight.StopAttackingIfTargetInvalid;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.idle.InteractWithBreed;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.idle.VillagerMakeLove;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.meet.SocializeAtBell;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.rest.SleepInBed;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.stay.ResetStayStatus;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.stay.StrollAroundStayPoint;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.work.StartFishing;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.work.UseBonemeal;
import me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.work.WorkAtBarrel;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.GateBehavior.OrderPolicy;
import net.minecraft.world.entity.ai.behavior.GateBehavior.RunningPolicy;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class VillagerNPCGoalPackages {

    private static final int MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW = 5;
    private static final int GO_TO_WANTED_ITEM_DISTANCE = 10;

    private static final ImmutableSet<Item> HORSE_FOOD = ImmutableSet.of(Items.WHEAT, Items.SUGAR, Blocks.HAY_BLOCK.asItem(), Items.APPLE, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
    private static final Predicate<LivingEntity> IS_DOING_NOTHING = living -> !(living instanceof VillagerNPC npc) || npc.isDoingNothing(true);
    private static final Predicate<LivingEntity> DUMMY = living -> true;
    private static final Predicate<Villager> SHOULD_HIDE = villager -> villager instanceof VillagerNPC npc && !npc.canAttack();
    private static final Function<VillagerNPC, Integer> BACK_UP_FUNCTION = npc -> npc.isHoldingMeleeWeapon() ?
            (npc.isAttackingWithTrident() ? TridentAttack.TRIDENT_DISTANCE_ATTACK : (int) npc.getMeleeAttackRangeSqr(null)) :
            MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW;

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getCorePackage(@NotNull VillagerProfession profession) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8f)),
                Pair.of(0, new InteractWithDoor()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(0, new WakeUp()),
                Pair.of(0, new ReactToBell()),
                Pair.of(0, new SetRaidStatus()),
                Pair.of(0, new ValidateNearbyPoi(profession.getJobPoiType(), MemoryModuleType.JOB_SITE)),
                Pair.of(0, new ValidateNearbyPoi(profession.getJobPoiType(), MemoryModuleType.POTENTIAL_JOB_SITE)),
                Pair.of(0, new Consume()),
                Pair.of(1, new EatCake()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(2, new PoiCompetitorScan(profession)),
                Pair.of(3, new LookAndFollowPlayerSink()),
                Pair.of(5, new GoToWantedItem(VillagerNPC.WALK_SPEED.get(), GO_TO_WANTED_ITEM_DISTANCE)),
                Pair.of(5, new LootChest()),
                Pair.of(6, new AcquirePoi(
                        profession.getJobPoiType(),
                        MemoryModuleType.JOB_SITE,
                        MemoryModuleType.POTENTIAL_JOB_SITE,
                        true,
                        Optional.empty())),
                Pair.of(7, new GoToPotentialJobSite(VillagerNPC.WALK_SPEED.get())),
                Pair.of(8, new YieldJobSite(VillagerNPC.WALK_SPEED.get())),
                Pair.of(10, new AcquirePoi(PoiType.HOME, MemoryModuleType.HOME, false, Optional.of((byte) 14))),
                Pair.of(10, new AcquirePoi(PoiType.MEETING, MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
                Pair.of(10, new AssignProfessionFromJobSite()),
                Pair.of(10, new ResetProfession()),
                Pair.of(1, runIf(villager -> villager.getVehicle() instanceof HorseEating,
                        new VillageBoundRandomStroll(VillagerNPC.SPRINT_SPEED.get()))),
                Pair.of(10, new BackToStay()),
                Pair.of(10, new CheckInventory()),
                Pair.of(10, new EquipTotem()),
                Pair.of(10, new RideHorse(100, VillagerNPC.WALK_SPEED.get())),
                Pair.of(10, new StopRiding()),
                Pair.of(10, createTameOrFeedPet(
                        3,
                        (npc, living) -> Config.TAME_HORSES.asBool()
                                && living instanceof Pet
                                && living instanceof AbstractHorse horse
                                && !horse.isVehicle()
                                && !horse.isBaby()
                                && !horse.isTamed()
                                && npc.getInventory().hasAnyOf(Set.of(Items.SADDLE)),
                        HORSE_FOOD)),
                Pair.of(10, createTameOrFeedPet(
                        3,
                        (npc, living) -> living instanceof PetCat cat && !cat.isTame(),
                        ImmutableSet.of(Items.COD, Items.SALMON))),
                Pair.of(10, createTameOrFeedPet(
                        10,
                        (npc, living) -> living instanceof PetParrot parrot && !parrot.isTame(),
                        ImmutableSet.of(Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS))),
                Pair.of(10, createTameOrFeedPet(
                        3,
                        (npc, living) -> living instanceof PetWolf wolf && !wolf.isTame() && !wolf.isAngry(),
                        ImmutableSet.of(Items.BONE))),
                Pair.of(10, new HealGolem(100, VillagerNPC.WALK_SPEED.get())),
                Pair.of(10, new HelpFamily(100, VillagerNPC.WALK_SPEED.get())));
    }

    @Contract("_, _, _ -> new")
    private static @NotNull TameOrFeedPet createTameOrFeedPet(int tameChance, BiPredicate<VillagerNPC, LivingEntity> tameFilter, Set<Item> tameItems) {
        return new TameOrFeedPet(2, tameChance, VillagerNPC.WALK_SPEED.get(), tameFilter, tameItems);
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getWorkPackage(VillagerProfession profession) {
        Behavior<Villager> behavior;
        if (profession == VillagerProfession.FARMER) {
            behavior = new WorkAtComposter();
        } else if (profession == VillagerProfession.FISHERMAN) {
            behavior = new WorkAtBarrel();
        } else {
            behavior = new WorkAtPoi();
        }

        return ImmutableList.of(
                getMinimalLookBehavior(),
                Pair.of(5, new RunOne<>(ImmutableList.of(
                        Pair.of(behavior, 7),
                        Pair.of(new StrollAroundPoi(MemoryModuleType.JOB_SITE, VillagerNPC.WALK_SPEED.get(), 4), 2),
                        Pair.of(new StrollToPoi(MemoryModuleType.JOB_SITE, VillagerNPC.WALK_SPEED.get(), 1, 10), 5),
                        Pair.of(new StrollToPoiList(MemoryModuleType.SECONDARY_JOB_SITE, VillagerNPC.WALK_SPEED.get(), 1, 6, MemoryModuleType.JOB_SITE), 5),
                        Pair.of(new StartFishing(), profession == VillagerProfession.FISHERMAN ? 2 : 5),
                        Pair.of(new UseBonemeal(), profession == VillagerProfession.FARMER ? 4 : 7)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, new SetLookAndInteractPlayer(4)),
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.JOB_SITE, VillagerNPC.WALK_SPEED.get(), 9, 100, 1200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getRestPackage() {
        return ImmutableList.of(
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.HOME, VillagerNPC.WALK_SPEED.get(), 1, 150, 1200)),
                Pair.of(3, new ValidateNearbyPoi(PoiType.HOME, MemoryModuleType.HOME)),
                Pair.of(3, new SleepInBed()),
                Pair.of(5, new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(new SetClosestHomeAsWalkTarget(VillagerNPC.WALK_SPEED.get()), 1),
                                Pair.of(new InsideBrownianWalk(VillagerNPC.WALK_SPEED.get()), 4),
                                Pair.of(new GoToClosestVillage(VillagerNPC.WALK_SPEED.get(), 4), 2),
                                Pair.of(new DoNothing(20, 40), 2)))),
                getMinimalLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getMeetPackage() {
        return ImmutableList.of(
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(new StrollAroundPoi(MemoryModuleType.MEETING_POINT, VillagerNPC.WALK_SPEED.get(), 40), 2),
                        Pair.of(new SocializeAtBell(), 2)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, new SetLookAndInteractPlayer(4)),
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.MEETING_POINT, VillagerNPC.WALK_SPEED.get(), 6, 100, 200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, new ValidateNearbyPoi(PoiType.MEETING, MemoryModuleType.MEETING_POINT)),
                Pair.of(3, new GateBehavior<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        OrderPolicy.ORDERED,
                        RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new TradeWithVillager(), 1)))),
                getFullLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getIdlePackage() {
        return ImmutableList.of(
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(createInteractWith(EntityType.VILLAGER), 2),
                        Pair.of(new InteractWithBreed(8, VillagerNPC.WALK_SPEED.get(), 2), 1),
                        Pair.of(createInteractWith(EntityType.CAT), 1),
                        Pair.of(createInteractWith(EntityType.PARROT), 1),
                        Pair.of(createInteractWith(EntityType.WOLF), 1),
                        Pair.of(new VillageBoundRandomStroll(VillagerNPC.WALK_SPEED.get()), 1),
                        Pair.of(new SetWalkTargetFromLookTarget(VillagerNPC.WALK_SPEED.get(), 2), 1),
                        Pair.of(new JumpOnBed(VillagerNPC.WALK_SPEED.get()), 1),
                        Pair.of(new DoNothing(30, 60), 1)))),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, new SetLookAndInteractPlayer(4)),
                Pair.of(3, new ShowTradesToPlayer(400, 1600)),
                Pair.of(3, new GateBehavior<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        OrderPolicy.ORDERED,
                        RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new TradeWithVillager(), 1)))),
                Pair.of(3, new GateBehavior<>(ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.BREED_TARGET),
                        OrderPolicy.ORDERED, RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new VillagerMakeLove(), 1)))),
                getFullLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    @Contract("_ -> new")
    private static @NotNull InteractWith<LivingEntity, LivingEntity> createInteractWith(EntityType<? extends LivingEntity> type) {
        return new InteractWith<>(
                type,
                8,
                IS_DOING_NOTHING,
                DUMMY,
                MemoryModuleType.INTERACTION_TARGET,
                VillagerNPC.WALK_SPEED.get(),
                2);
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getRaidPackage() {
        return ImmutableList.of(
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(new GoOutsideToCelebrate(VillagerNPC.WALK_SPEED.get()), 5),
                        Pair.of(new VictoryStroll(VillagerNPC.WALK_SPEED.get()), 2)))),
                Pair.of(0, new CelebrateVillagersSurvivedRaid(600, 600)),
                Pair.of(0, runIfShouldNotHide(new VillageBoundRandomStroll(VillagerNPC.SPRINT_SPEED.get()))),
                Pair.of(1, runIfShouldHide(new LocateHidingPlaceDuringRaid(24, VillagerNPC.SPRINT_SPEED.get()))),
                getMinimalLookBehavior(),
                Pair.of(99, new ResetRaidStatus()));
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getFightPackage() {
        return ImmutableList.of(
                Pair.of(0, new MeleeAttack()),
                Pair.of(0, new RangeWeaponAttack()),
                Pair.of(0, new TridentAttack()),
                Pair.of(0, new BlockAttackWithShield()),
                Pair.of(1, runIf(
                        villager -> BlockAttackWithShield.notUsingShield(villager)
                                && (!(villager instanceof VillagerNPC npc) || !npc.isAttackingWithTrident()),
                        new SetWalkTargetFromAttackTargetIfTargetOutOfReach(living -> VillagerNPC.SPRINT_SPEED.get()))),
                Pair.of(1, new BackUpIfTooClose(BACK_UP_FUNCTION, VillagerNPC.SPRINT_SPEED.get())),
                Pair.of(2, new StopAttackingIfTargetInvalid()));
    }

    public static @NotNull ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getStayPackage() {
        Preconditions.checkArgument(VillagerNPC.STAY_PLACE != null);
        return ImmutableList.of(
                getFullLookBehavior(),
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(new StrollAroundStayPoint(VillagerNPC.STAY_PLACE, VillagerNPC.WALK_SPEED.get(), 3), 2),
                        Pair.of(new StrollToPoi(VillagerNPC.STAY_PLACE, VillagerNPC.WALK_SPEED.get(), 1, 4), 5)))),
                Pair.of(2, new SetWalkTargetFromBlockMemory(VillagerNPC.STAY_PLACE, VillagerNPC.WALK_SPEED.get(), 5, 100, 1200)),
                Pair.of(99, new ResetStayStatus()));
    }

    @Contract(" -> new")
    private static @NotNull Pair<Integer, Behavior<LivingEntity>> getFullLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(new SetEntityLookTarget(EntityType.CAT, 8.0f), 8),
                Pair.of(new SetEntityLookTarget(EntityType.PARROT, 8.0f), 8),
                Pair.of(new SetEntityLookTarget(EntityType.WOLF, 8.0f), 8),
                Pair.of(new SetEntityLookTarget(EntityType.VILLAGER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(MobCategory.CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.WATER_CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.AXOLOTLS, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.WATER_AMBIENT, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.MONSTER, 8.0f), 1),
                Pair.of(new DoNothing(30, 60), 2))));
    }

    @Contract(" -> new")
    private static @NotNull Pair<Integer, Behavior<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(new SetEntityLookTarget(EntityType.VILLAGER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 8.0f), 2),
                Pair.of(new DoNothing(30, 60), 8))));
    }

    @Contract("_, _ -> new")
    private static @NotNull Behavior<? super Villager> runIf(Predicate<Villager> predicate, Behavior<? super Villager> behavior) {
        return new RunIf<>(predicate, behavior);
    }

    @Contract("_ -> new")
    private static @NotNull Behavior<? super Villager> runIfShouldHide(Behavior<? super Villager> behavior) {
        return runIf(SHOULD_HIDE, behavior);
    }

    @Contract("_ -> new")
    private static @NotNull Behavior<? super Villager> runIfShouldNotHide(Behavior<? super Villager> behavior) {
        return runIf(SHOULD_HIDE.negate(), behavior);
    }
}