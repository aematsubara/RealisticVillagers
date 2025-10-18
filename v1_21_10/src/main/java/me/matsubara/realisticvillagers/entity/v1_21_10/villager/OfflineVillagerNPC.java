package me.matsubara.realisticvillagers.entity.v1_21_10.villager;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.data.serialization.GossipEntryWrapper;
import me.matsubara.realisticvillagers.data.serialization.OfflineDataWrapper;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.nms.v1_21_10.CustomGossipContainer;
import me.matsubara.realisticvillagers.nms.v1_21_10.NMSConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public class OfflineVillagerNPC implements IVillagerNPC {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);
    private final UUID uuid;
    private final String villagerName;
    private final String sex;
    private final IVillagerNPC partner;
    private final List<IVillagerNPC> partners = new ArrayList<>();
    private final boolean isPartnerVillager;
    private final long lastProcreation;
    private final int skinTextureId;
    private final int kidSkinTextureId;
    private final IVillagerNPC father;
    private final IVillagerNPC mother;
    private final boolean isFatherVillager;
    private final List<IVillagerNPC> childrens = new ArrayList<>();
    private final Set<EntityType<?>> targetEntities = new HashSet<>();
    private final List<CustomGossipContainer.GossipEntry> entries = new ArrayList<>();
    private final CustomGossipContainer gosssips;
    private final Set<UUID> players = new HashSet<>();
    private @Setter LastKnownPosition lastKnownPosition;
    private final @Getter(AccessLevel.NONE) CompoundTag shoulderEntityLeft;
    private final @Getter(AccessLevel.NONE) CompoundTag shoulderEntityRight;
    private final UUID bedHomeWorld;
    private final BlockPos bedHome;
    private final boolean wasInfected;
    private final boolean equipped;
    private final int foodLevel;
    private final int tickTimer;
    private final float saturationLevel;
    private final float exhaustionLevel;

    public static final OfflineVillagerNPC DUMMY_OFFLINE = dummy(null, null);

    public OfflineVillagerNPC(UUID uuid,
                              String villagerName,
                              String sex,
                              IVillagerNPC partner,
                              boolean isPartnerVillager,
                              long lastProcreation,
                              int skinTextureId,
                              int kidSkinTextureId,
                              IVillagerNPC father,
                              IVillagerNPC mother,
                              boolean isFatherVillager,
                              LastKnownPosition lastKnownPosition,
                              List<IVillagerNPC> partners,
                              List<IVillagerNPC> childrens,
                              Set<EntityType<?>> targetEntities,
                              Set<UUID> players,
                              List<CustomGossipContainer.GossipEntry> entries,
                              CompoundTag shoulderEntityLeft,
                              CompoundTag shoulderEntityRight,
                              UUID bedHomeWorld,
                              BlockPos bedHome,
                              boolean wasInfected,
                              boolean equipped,
                              int foodLevel,
                              int tickTimer,
                              float saturationLevel,
                              float exhaustionLevel) {
        this.uuid = uuid;
        this.villagerName = villagerName;
        this.sex = sex;
        this.partner = partner;
        this.isPartnerVillager = isPartnerVillager;
        this.lastProcreation = lastProcreation;
        this.skinTextureId = skinTextureId;
        this.kidSkinTextureId = kidSkinTextureId;
        this.father = father;
        this.mother = mother;
        this.isFatherVillager = isFatherVillager;
        this.lastKnownPosition = lastKnownPosition;
        this.shoulderEntityLeft = shoulderEntityLeft;
        this.shoulderEntityRight = shoulderEntityRight;
        this.bedHomeWorld = bedHomeWorld;
        this.bedHome = bedHome;
        this.wasInfected = wasInfected;
        this.equipped = equipped;
        this.foodLevel = foodLevel;
        this.tickTimer = tickTimer;
        this.saturationLevel = saturationLevel;
        this.exhaustionLevel = exhaustionLevel;
        this.entries.addAll(entries);
        this.partners.addAll(partners);
        this.childrens.addAll(childrens);
        this.targetEntities.addAll(targetEntities);
        this.players.addAll(players);
        this.gosssips = new CustomGossipContainer(entries);
    }

    public static <T> T get(@Nullable OfflineVillagerNPC offline, Function<OfflineVillagerNPC, T> getter) {
        return get(offline, getter, null);
    }

    public static <T> T get(@Nullable OfflineVillagerNPC offline, Function<OfflineVillagerNPC, T> getter, @Nullable T defaultValue) {
        return offline != null ? getter.apply(offline) : defaultValue;
    }

    public static <T> void getAndSet(@Nullable OfflineVillagerNPC offline, Function<OfflineVillagerNPC, T> getter, @NotNull Consumer<T> setter) {
        getAndSet(offline, getter, setter, null);
    }

    public static <T> void getAndSet(@Nullable OfflineVillagerNPC offline, Function<OfflineVillagerNPC, T> getter, @NotNull Consumer<T> setter, @Nullable T defaultValue) {
        T value = get(offline, getter, defaultValue);
        if (value != null) setter.accept(value);
        else if (defaultValue != null) setter.accept(defaultValue);
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public void setVillagerName(String name) {
    }

    @Override
    public int getReputation(UUID uuid) {
        return gosssips.getReputation(uuid, (type) -> true);
    }

    @Override
    public boolean isMotherVillager() {
        return true;
    }

    @Override
    public LivingEntity bukkit() {
        return Bukkit.getEntity(uuid) instanceof Villager villager ? villager : null;
    }

    @Override
    public void addMinorPositive(UUID uuid, int amount) {

    }

    @Override
    public void addMinorNegative(UUID uuid, int amount) {

    }

    @Override
    public void jumpIfPossible() {

    }

    @Override
    public void setProcreatingWith(UUID uuid) {

    }

    @Override
    public void setLastProcreation(long lastProcreation) {

    }

    @Override
    public boolean canAttack() {
        return false;
    }

    @Override
    public void setSex(String sex) {

    }

    @Override
    public void setSkinTextureId(int skinTextureId) {

    }

    @Override
    public void setKidSkinTextureId(int skinTextureId) {

    }

    @Override
    public boolean isExpectingGift() {
        return false;
    }

    @Override
    public boolean isGiftDropped() {
        return false;
    }

    @Override
    public void setGiftDropped(boolean giftDropped) {

    }

    @Override
    public void stopExpecting() {

    }

    @Override
    public boolean isExpectingBed() {
        return false;
    }

    @Override
    public HandleHomeResult handleBedHome(Block block) {
        return null;
    }

    @Override
    public boolean isTarget(org.bukkit.entity.EntityType type) {
        for (EntityType<?> entityType : targetEntities) {
            if (entityType.toShortString().equalsIgnoreCase(type.name())) return true;
        }
        return false;
    }

    @Override
    public boolean isConversating() {
        return false;
    }

    @Override
    public boolean isFemale() {
        return sex.equalsIgnoreCase("female");
    }

    @Override
    public boolean isMale() {
        return sex.equalsIgnoreCase("male");
    }

    @Override
    public boolean is(Villager.Profession... professions) {
        return false;
    }

    @Override
    public boolean isFamily(UUID uuid) {
        return isFamily(uuid, false);
    }

    @Override
    public boolean isFamily(UUID uuid, boolean checkPartner) {
        IVillagerNPC father, mother;
        return (checkPartner && isPartner(uuid))
                || isChildren(uuid)
                || ((father = getFather()) != null && father.getUniqueId().equals(uuid))
                || ((mother = getMother()) != null && mother.getUniqueId().equals(uuid));
    }

    private boolean isChildren(UUID uuid) {
        for (IVillagerNPC npc : childrens) {
            if (npc.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    @Override
    public boolean isPartner(UUID uuid) {
        IVillagerNPC partner = getPartner();
        return partner != null && partner.getUniqueId().equals(uuid);
    }

    @Override
    public String getActivityName(String none) {
        return null;
    }

    @Override
    public void addTarget(org.bukkit.entity.EntityType type) {

    }

    @Override
    public void removeTarget(org.bukkit.entity.EntityType type) {

    }

    @Override
    public void setInteractType(InteractType interactType) {

    }

    @Override
    public void stayInPlace() {

    }

    @Override
    public void stopStayingInPlace() {

    }

    @Override
    public void startExpectingFrom(ExpectingType type, UUID uuid, int time) {

    }

    @Override
    public void divorceAndDropRing(@Nullable Player player) {

    }

    @Override
    public void drop(ItemStack item) {

    }

    @Override
    public void startTrading(Player player) {

    }

    @Override
    public void stopInteracting() {

    }

    @Override
    public void reactToSeekHorn(Player player) {

    }

    @Override
    public boolean isDamageSourceBlocked() {
        return false;
    }

    @Override
    public boolean isInsideRaid() {
        return false;
    }

    @Override
    public boolean isFighting() {
        return false;
    }

    @Override
    public boolean isProcreating() {
        return false;
    }

    @Override
    public boolean isExpectingGiftFrom(UUID uuid) {
        return false;
    }

    @Override
    public boolean isExpectingBedFrom(UUID uuid) {
        return false;
    }

    @Override
    public boolean isExpecting() {
        return false;
    }

    @Override
    public ExpectingType getExpectingType() {
        return null;
    }

    @Override
    public UUID getExpectingFrom() {
        return null;
    }

    @Override
    public boolean isInteracting() {
        return false;
    }

    @Override
    public UUID getInteractingWith() {
        return null;
    }

    @Override
    public boolean isFollowing() {
        return false;
    }

    @Override
    public boolean isStayingInPlace() {
        return false;
    }

    @Override
    public void setInteractingWithAndType(UUID uuid, InteractType type) {

    }

    @Override
    public boolean hasPartner() {
        return getPartner() != null;
    }

    @Override
    public void setPartner(@Nullable UUID uuid, boolean isPartnerVillager) {

    }

    @Override
    public boolean isFishing() {
        return false;
    }

    @Override
    public void toggleFishing() {

    }

    @Override
    public void sendSpawnPacket() {

    }

    @Override
    public void sendDestroyPacket() {

    }

    @Override
    public boolean isShakingHead() {
        return false;
    }

    @Override
    public void shakeHead(Player at) {

    }

    @Override
    public IVillagerNPC getOffline() {
        return this;
    }

    @Override
    public void setEquipped(boolean equipped) {

    }

    @Override
    public boolean validShoulderEntityLeft() {
        return getShoulderEntityLeft() != null;
    }

    @Override
    public Object getShoulderEntityLeft() {
        return NMSConverter.extractParrotVariant(shoulderEntityLeft);
    }

    @Override
    public boolean validShoulderEntityRight() {
        return getShoulderEntityRight() != null;
    }

    @Override
    public Object getShoulderEntityRight() {
        return NMSConverter.extractParrotVariant(shoulderEntityRight);
    }

    @Override
    public void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason) {

    }

    @Override
    public void stopExchangeables() {

    }

    @Override
    public void refreshBrain() {

    }

    @Override
    public boolean isReviving() {
        return false;
    }

    @Override
    public byte getHandData() {
        return 0;
    }

    @Override
    public int getEffectColor() {
        return 0;
    }

    @Override
    public boolean getEffectAmbience() {
        return false;
    }

    @Override
    public int getBeeStingers() {
        return 0;
    }

    @Override
    public void attack(LivingEntity entity) {

    }

    public static IVillagerNPC fromOfflineDataWrapper(OfflineDataWrapper wrapper) {
        if (wrapper == null) return null;

        return new OfflineVillagerNPC(
                wrapper.getUuid(),
                wrapper.getVillagerName(),
                wrapper.getSex(),
                fromOfflineDataWrapper(wrapper.getPartner()),
                wrapper.isPartnerVillager(),
                wrapper.getLastProcreation(),
                wrapper.getSkinTextureId(),
                wrapper.getKidSkinTextureId(),
                fromOfflineDataWrapper(wrapper.getFather()),
                fromOfflineDataWrapper(wrapper.getMother()),
                wrapper.isFatherVillager(),
                wrapper.getLastKnownPosition(),
                wrapper.getPartners().stream().map(OfflineVillagerNPC::fromOfflineDataWrapper).toList(),
                wrapper.getChildrens().stream().map(OfflineVillagerNPC::fromOfflineDataWrapper).toList(),
                wrapper.getTargetEntities().stream()
                        .map(string -> EntityType.byString(string).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()),
                new HashSet<>(wrapper.getPlayers()),
                wrapper.getEntries().stream()
                        .map(entry -> new CustomGossipContainer.GossipEntry(
                                entry.target(),
                                getTypeBySerializedName(entry.typeSerializedName()),
                                entry.value()))
                        .toList(),
                wrapper.getShoulderEntityLeft() != null ? getShoulderEntity(wrapper.getShoulderEntityLeft()) : null,
                wrapper.getShoulderEntityRight() != null ? getShoulderEntity(wrapper.getShoulderEntityRight()) : null,
                wrapper.getBedHomeWorld(),
                wrapper.getBedHome() != null ? new BlockPos(
                        wrapper.getBedHome().getBlockX(),
                        wrapper.getBedHome().getBlockY(),
                        wrapper.getBedHome().getBlockZ()) : null,
                wrapper.isWasInfected(),
                wrapper.isEquipped(),
                wrapper.getFoodLevel(),
                wrapper.getTickTimer(),
                wrapper.getSaturationLevel(),
                wrapper.getExhaustionLevel());
    }

    private static @Nullable GossipType getTypeBySerializedName(String name) {
        for (GossipType temp : GossipType.values()) {
            if (temp.getSerializedName().equals(name)) return temp;
        }
        return null;
    }

    public OfflineDataWrapper toOfflineDataWrapper() {
        return new OfflineDataWrapper(
                uuid,
                villagerName,
                sex,
                toOfflineDataWrapper(partner),
                isPartnerVillager,
                lastProcreation,
                skinTextureId,
                kidSkinTextureId,
                toOfflineDataWrapper(father),
                toOfflineDataWrapper(mother),
                isFatherVillager,
                lastKnownPosition != null ? lastKnownPosition : LastKnownPosition.ZERO,
                toOfflineDataWrapper(partners),
                toOfflineDataWrapper(childrens),
                targetEntities.stream().map(EntityType::toShortString).toList(),
                players,
                entries.stream().map(entry -> new GossipEntryWrapper(entry.target(), entry.type().getSerializedName(), entry.value())).toList(),
                validShoulderEntityLeft() ? shoulderEntityLeft.toString() : null,
                validShoulderEntityRight() ? shoulderEntityRight.toString() : null,
                bedHomeWorld,
                bedHome != null ? new Vector(bedHome.getX(), bedHome.getY(), bedHome.getZ()) : null,
                wasInfected,
                equipped,
                foodLevel,
                tickTimer,
                saturationLevel,
                exhaustionLevel);
    }

    private List<OfflineDataWrapper> toOfflineDataWrapper(@NotNull List<IVillagerNPC> npcs) {
        return npcs.stream().map(this::toOfflineDataWrapper)
                .filter(Objects::nonNull)
                .toList();
    }

    private OfflineDataWrapper toOfflineDataWrapper(IVillagerNPC npc) {
        return npc instanceof OfflineVillagerNPC offline ? offline.toOfflineDataWrapper() : null;
    }

    private static CompoundTag getShoulderEntity(String string) {
        try {
            return TagParser.parseCompoundFully(string);
        } catch (CommandSyntaxException ignored) {
            // Shouldn't happen.
            return new CompoundTag();
        }
    }

    @Contract("_, _ -> new")
    public static @NotNull OfflineVillagerNPC dummy(UUID uuid, String villagerName) {
        return new OfflineVillagerNPC(
                uuid, villagerName,
                null, null,
                false,
                0L,
                -1, -1,
                null, null,
                false,
                LastKnownPosition.ZERO,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptySet(), Collections.emptyList(),
                null, null,
                null, null,
                false,
                false,
                20, 0, 5.0f, 0.0f);
    }
}