package me.matsubara.realisticvillagers.entity.v1_18_r2.villager;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

@Getter
public class OfflineVillagerNPC implements IVillagerNPC {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    private final UUID uuid;
    private @Setter CompoundTag tag;
    private final List<IVillagerNPC> partners = new ArrayList<>();
    private final List<IVillagerNPC> childrens = new ArrayList<>();
    private final Set<EntityType<?>> targetEntities;
    private final LastKnownPosition lastKnownPosition;

    public static final String UUID = "UUID";
    public static final String INVENTORY = "Inventory";
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
    public static final String BED_HOME = "BedHome";
    public static final String EQUIPPED = "Equipped";
    public static final String SHOULDER_ENTITY_LEFT = "ShoulderEntityLeft";
    public static final String SHOULDER_ENTITY_RIGHT = "ShoulderEntityRight";
    public static final String GOSSIPS = "Gossips";
    public static final BiFunction<VillagerTracker, Tag, IVillagerNPC> OFFLINE_MAPPER = (tracker, input) -> input instanceof CompoundTag compound ?
            OfflineVillagerNPC.from(compound) :
            tracker.getOffline(NbtUtils.loadUUID(input));

    public OfflineVillagerNPC(UUID uuid, CompoundTag tag, LastKnownPosition lastKnownPosition) {
        this.uuid = uuid;
        this.tag = tag;
        this.lastKnownPosition = lastKnownPosition;
        VillagerNPC.fillCollection(
                partners,
                input -> OfflineVillagerNPC.OFFLINE_MAPPER.apply(plugin.getTracker(), input),
                PARTNERS,
                tag);
        VillagerNPC.fillCollection(
                childrens,
                input -> OfflineVillagerNPC.OFFLINE_MAPPER.apply(plugin.getTracker(), input),
                CHILDRENS,
                tag);
        this.targetEntities = new HashSet<>();
        VillagerNPC.fillCollection(targetEntities, input -> EntityType.byString(input.getAsString()).orElse(null), TARGET_ENTITIES, tag);
    }

    @Contract("_, _, _, _, _, _ -> new")
    public static @NotNull OfflineVillagerNPC from(UUID uuid, CompoundTag tag, String world, double x, double y, double z) {
        return new OfflineVillagerNPC(uuid, tag, new LastKnownPosition(world, x, y, z));
    }

    public static @NotNull OfflineVillagerNPC from(CompoundTag tag) {
        LastKnownPosition position = lastPositionFrom(tag);
        return new OfflineVillagerNPC(tag.getUUID(UUID), tag, position);
    }

    public CompoundTag getTag() {
        CompoundTag tag = this.tag.copy();
        tag.putUUID(UUID, uuid);
        tag.putString("World", lastKnownPosition.world());
        tag.put("Pos", newDoubleList(lastKnownPosition.x(), lastKnownPosition.y(), lastKnownPosition.z()));
        return tag;
    }

    private @NotNull ListTag newDoubleList(double @NotNull ... nums) {
        ListTag list = new ListTag();
        for (double num : nums) {
            list.add(DoubleTag.valueOf(num));
        }
        return list;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public String getVillagerName() {
        return tag.getString(NAME);
    }

    @Override
    public void setVillagerName(String name) {
    }

    @Override
    public int getReputation(UUID uuid) {
        return -1;
    }

    @Override
    public IVillagerNPC getPartner() {
        return getFamily(tag, PARTNER);
    }

    @Override
    public List<IVillagerNPC> getPartners() {
        return partners;
    }

    @Override
    public boolean isPartnerVillager() {
        return tag.getBoolean(IS_PARTNER_VILLAGER);
    }

    @Override
    public IVillagerNPC getFather() {
        return getFamily(tag, FATHER);
    }

    @Override
    public boolean isFatherVillager() {
        return tag.getBoolean(IS_FATHER_VILLAGER);
    }

    @Override
    public IVillagerNPC getMother() {
        return getFamily(tag, MOTHER);
    }

    private @Nullable IVillagerNPC getFamily(@NotNull CompoundTag tag, String who) {
        if (!tag.contains(who)) return null;

        if (tag.hasUUID(who)) {
            UUID partnerUUID = tag.getUUID(who);
            return plugin.getTracker().getOffline(partnerUUID);
        } else {
            return OfflineVillagerNPC.from(tag.getCompound(who));
        }
    }

    @Override
    public boolean isMotherVillager() {
        return true;
    }

    @Override
    public List<IVillagerNPC> getChildrens() {
        return childrens;
    }

    @Override
    public Villager bukkit() {
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
    public void spawnEntityEventParticle(Particle particle) {

    }

    @Override
    public String getSex() {
        return tag.getString(SEX);
    }

    @Override
    public void setSex(String sex) {

    }

    @Override
    public int getSkinTextureId() {
        return tag.getInt(SKIN_TEXTURE_ID);
    }

    @Override
    public void setSkinTextureId(int skinTextureId) {

    }

    @Override
    public int getKidSkinTextureId() {
        return tag.getInt(KID_SKIN_TEXTURE_ID);
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
        return getSex().equalsIgnoreCase("female");
    }

    @Override
    public boolean isMale() {
        return getSex().equalsIgnoreCase("male");
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
    public long getLastProcreation() {
        return tag.getLong(LAST_PROCREATION);
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
    public int getFoodLevel() {
        return tag.getInt("foodLevel");
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
    public boolean isEquipped() {
        return tag.getBoolean(EQUIPPED);
    }

    @Override
    public void setEquipped(boolean equipped) {

    }

    @Override
    public Object getShoulderEntityLeft() {
        return tag.getCompound(OfflineVillagerNPC.SHOULDER_ENTITY_LEFT);
    }

    @Override
    public Object getShoulderEntityRight() {
        return tag.getCompound(OfflineVillagerNPC.SHOULDER_ENTITY_RIGHT);
    }

    @Override
    public void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason) {

    }

    @Override
    public boolean isWasInfected() {
        return tag.getBoolean(OfflineVillagerNPC.WAS_INFECTED);
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

    private static @NotNull LastKnownPosition lastPositionFrom(@NotNull CompoundTag tag) {
        String world = tag.getString("World");
        ListTag list = tag.getList("Pos", 6);
        double x = list.getDouble(0);
        double y = list.getDouble(1);
        double z = list.getDouble(2);
        return new LastKnownPosition(world, x, y, z);
    }
}