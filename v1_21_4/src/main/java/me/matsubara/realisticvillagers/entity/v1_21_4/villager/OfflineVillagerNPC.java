package me.matsubara.realisticvillagers.entity.v1_21_4.villager;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.nms.v1_21_4.CustomGossipContainer;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
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

@Getter
public class OfflineVillagerNPC implements IVillagerNPC, ConfigurationSerializable {

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

    public static final String UUID = "UUID";
    public static final String NAME = "Name";
    public static final String SEX = "Sex";
    public static final String PARTNER = "Partner";
    public static final String PARTNERS = "Partners";
    public static final String IS_PARTNER_VILLAGER = "IsPartnerVillager";
    public static final String LAST_PROCREATION = "LastProcreation";
    public static final String SKIN_TEXTURE_ID = "SkinTextureId";
    public static final String KID_SKIN_TEXTURE_ID = "KidSkinTextureId";
    public static final String FATHER = "Father";
    public static final String MOTHER = "Mother";
    public static final String IS_FATHER_VILLAGER = "IsFatherVillager";
    public static final String WAS_INFECTED = "WasInfected";
    public static final String CHILDRENS = "Childrens";
    public static final String TARGET_ENTITIES = "TargetEntities";
    public static final String BED_HOME_WORLD = "BedHomeWorld";
    public static final String BED_HOME_POS = "BedHomePos";
    public static final String EQUIPPED = "Equipped";
    public static final String SHOULDER_ENTITY_LEFT = "ShoulderEntityLeft";
    public static final String SHOULDER_ENTITY_RIGHT = "ShoulderEntityRight";
    public static final String GOSSIP_ENTRIES = "GossipsEntries";
    public static final String PLAYERS = "Players";
    public static final String LAST_WORLD = "LastWorld";
    public static final String LAST_POS = "LastPos";
    public static final String FOOD_LEVEL = "FoodLevel";
    public static final String FOOD_TICK_TIMER = "FoodTickTimer";
    public static final String FOOD_SATURATION_LEVEL = "FoodSaturationLevel";
    public static final String FOOD_EXHAUSTION_LEVEL = "FoodExhaustionLevel";

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
        return shoulderEntityLeft != null && !shoulderEntityLeft.isEmpty();
    }

    @Override
    public Object getShoulderEntityLeft() {
        return shoulderEntityLeft;
    }

    @Override
    public boolean validShoulderEntityRight() {
        return shoulderEntityRight != null && !shoulderEntityRight.isEmpty();
    }

    @Override
    public Object getShoulderEntityRight() {
        return shoulderEntityRight;
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

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(UUID, uuid.toString());
        if (lastKnownPosition != null) {
            result.put(LAST_WORLD, lastKnownPosition.world());
            result.put(LAST_POS, new Vector(lastKnownPosition.x(), lastKnownPosition.y(), lastKnownPosition.z()));
        }
        if (villagerName != null) result.put(NAME, villagerName);
        if (sex != null) result.put(SEX, sex);
        if (partner != null) result.put(PARTNER, partner);
        result.put(PARTNERS, partners);
        result.put(IS_PARTNER_VILLAGER, isPartnerVillager);
        result.put(LAST_PROCREATION, lastProcreation);
        result.put(SKIN_TEXTURE_ID, skinTextureId);
        result.put(KID_SKIN_TEXTURE_ID, kidSkinTextureId);
        if (father != null) result.put(FATHER, father);
        if (mother != null) result.put(MOTHER, mother);
        result.put(IS_FATHER_VILLAGER, isFatherVillager);
        result.put(CHILDRENS, childrens);
        result.put(TARGET_ENTITIES, targetEntities.stream().map(EntityType::toShortString).toList());
        result.put(GOSSIP_ENTRIES, entries);
        result.put(PLAYERS, players.stream().map(java.util.UUID::toString).toList());
        if (shoulderEntityLeft != null) result.put(SHOULDER_ENTITY_LEFT, shoulderEntityLeft.toString());
        if (shoulderEntityRight != null) result.put(SHOULDER_ENTITY_RIGHT, shoulderEntityRight.toString());
        if (bedHomeWorld != null) result.put(BED_HOME_WORLD, bedHomeWorld.toString());
        if (bedHome != null) result.put(BED_HOME_POS, new Vector(bedHome.getX(), bedHome.getY(), bedHome.getZ()));
        result.put(WAS_INFECTED, wasInfected);
        result.put(EQUIPPED, equipped);
        result.put(FOOD_LEVEL, foodLevel);
        result.put(FOOD_TICK_TIMER, tickTimer);
        result.put(FOOD_SATURATION_LEVEL, saturationLevel);
        result.put(FOOD_EXHAUSTION_LEVEL, exhaustionLevel);
        return result;
    }

    @SuppressWarnings({"unused"})
    public static @NotNull OfflineVillagerNPC deserialize(@NotNull Map<String, Object> args) {
        UUID uuid = PluginUtils.getOrDefault(args, UUID, String.class, java.util.UUID::fromString, null);
        String world = PluginUtils.getOrDefault(args, LAST_WORLD, String.class);
        Vector vector = PluginUtils.getOrDefault(args, LAST_POS, Vector.class, new Vector(0, 0, 0));
        LastKnownPosition lastKnownPosition = new LastKnownPosition(world, vector.getX(), vector.getY(), vector.getZ());
        String villagerName = PluginUtils.getOrDefault(args, NAME, String.class);
        String sex = PluginUtils.getOrDefault(args, SEX, String.class);
        IVillagerNPC partner = PluginUtils.getOrDefault(args, PARTNER, IVillagerNPC.class);
        @SuppressWarnings("unchecked") List<IVillagerNPC> partners = PluginUtils.getOrDefault(
                args,
                PARTNERS,
                List.class,
                Collections.emptyList());
        boolean isPartnerVillager = PluginUtils.getOrDefault(args, IS_PARTNER_VILLAGER, Boolean.class);
        long lastProcreation = PluginUtils.getOrDefault(args, LAST_PROCREATION, Long.class);
        int skinTextureId = PluginUtils.getOrDefault(args, SKIN_TEXTURE_ID, Integer.class, -1);
        int kidSkinTextureId = PluginUtils.getOrDefault(args, KID_SKIN_TEXTURE_ID, Integer.class, -1);
        IVillagerNPC father = PluginUtils.getOrDefault(args, FATHER, IVillagerNPC.class);
        IVillagerNPC mother = PluginUtils.getOrDefault(args, MOTHER, IVillagerNPC.class);
        boolean isFatherVillager = PluginUtils.getOrDefault(args, IS_FATHER_VILLAGER, Boolean.class);
        @SuppressWarnings("unchecked") List<IVillagerNPC> childrens = PluginUtils.getOrDefault(
                args,
                CHILDRENS,
                List.class,
                Collections.emptyList());
        Set<EntityType<?>> targetEntities = new HashSet<>();
        for (Object object : PluginUtils.getOrDefault(args, TARGET_ENTITIES, List.class, Collections.emptyList())) {
            if (!(object instanceof String string)) continue;
            EntityType.byString(string).ifPresent(targetEntities::add);
        }
        Set<UUID> players = new HashSet<>();
        for (Object object : PluginUtils.getOrDefault(args, PLAYERS, List.class, Collections.emptyList())) {
            if (!(object instanceof String string)) continue;
            players.add(java.util.UUID.fromString(string));
        }
        @SuppressWarnings("unchecked") List<CustomGossipContainer.GossipEntry> entries = PluginUtils.getOrDefault(
                args,
                GOSSIP_ENTRIES,
                List.class,
                Collections.emptyList());
        CompoundTag shoulderEntityLeft = getShoulderEntity(args, SHOULDER_ENTITY_LEFT);
        CompoundTag shoulderEntityRight = getShoulderEntity(args, SHOULDER_ENTITY_RIGHT);
        UUID bedHomeWorld = PluginUtils.getOrDefault(args, BED_HOME_WORLD, String.class, java.util.UUID::fromString, null);
        Vector bedHome = PluginUtils.getOrDefault(args, BED_HOME_POS, Vector.class, new Vector(0, 0, 0));
        boolean wasInfected = PluginUtils.getOrDefault(args, WAS_INFECTED, Boolean.class);
        boolean equipped = PluginUtils.getOrDefault(args, EQUIPPED, Boolean.class);
        int foodLevel = PluginUtils.getOrDefault(args, FOOD_LEVEL, Integer.class, 20);
        int tickTimer = PluginUtils.getOrDefault(args, FOOD_TICK_TIMER, Integer.class);
        float saturationLevel = PluginUtils.getOrDefault(args, FOOD_SATURATION_LEVEL, Float.class, 5.0f);
        float exhaustionLevel = PluginUtils.getOrDefault(args, FOOD_EXHAUSTION_LEVEL, Float.class);

        return new OfflineVillagerNPC(
                uuid,
                villagerName,
                sex,
                partner,
                isPartnerVillager,
                lastProcreation,
                skinTextureId,
                kidSkinTextureId,
                father,
                mother,
                isFatherVillager,
                lastKnownPosition,
                partners,
                childrens,
                targetEntities,
                players,
                entries,
                shoulderEntityLeft,
                shoulderEntityRight,
                bedHomeWorld,
                new BlockPos(bedHome.getBlockX(), bedHome.getBlockY(), bedHome.getBlockZ()),
                wasInfected,
                equipped,
                foodLevel,
                tickTimer,
                saturationLevel,
                exhaustionLevel);
    }

    private static CompoundTag getShoulderEntity(Map<String, Object> args, String name) {
        try {
            return TagParser.parseCompoundFully(PluginUtils.getOrDefault(args, name, String.class, ""));
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