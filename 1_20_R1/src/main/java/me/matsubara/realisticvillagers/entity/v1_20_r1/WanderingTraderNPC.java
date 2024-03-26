package me.matsubara.realisticvillagers.entity.v1_20_r1;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Nameable;
import me.matsubara.realisticvillagers.entity.v1_20_r1.villager.OfflineVillagerNPC;
import me.matsubara.realisticvillagers.event.RealisticRemoveEvent;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.nms.v1_20_r1.NMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftWanderingTrader;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class WanderingTraderNPC extends WanderingTrader implements IVillagerNPC, Nameable {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    private String villagerName;
    private String sex;
    private int skinTextureId = -1;

    private TextDisplay nametagEntity;
    private BlockDisplay nametagItemEntity;
    private int currentAmountOfLines;

    private static final @SuppressWarnings("unchecked") EntityDataAccessor<Integer> DATA_EFFECT_COLOR_ID =
            (EntityDataAccessor<Integer>) Reflection.getFieldValue(Reflection.getFieldGetter(net.minecraft.world.entity.LivingEntity.class, "bJ"));
    private static final @SuppressWarnings("unchecked") EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID =
            (EntityDataAccessor<Boolean>) Reflection.getFieldValue(Reflection.getFieldGetter(net.minecraft.world.entity.LivingEntity.class, "bK"));
    private static final @SuppressWarnings("unchecked") EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID =
            (EntityDataAccessor<Integer>) Reflection.getFieldValue(Reflection.getFieldGetter(net.minecraft.world.entity.LivingEntity.class, "bM"));

    public WanderingTraderNPC(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);

        for (EquipmentSlot value : EquipmentSlot.values()) {
            setDropChance(value, 0.0f);
        }

        setDespawnDelay(48000);
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public int getReputation(UUID uuid) {
        return 0;
    }

    @Override
    public IVillagerNPC getPartner() {
        return null;
    }

    @Override
    public List<IVillagerNPC> getPartners() {
        return null;
    }

    @Override
    public boolean isPartnerVillager() {
        return false;
    }

    @Override
    public IVillagerNPC getFather() {
        return null;
    }

    @Override
    public boolean isFatherVillager() {
        return false;
    }

    @Override
    public IVillagerNPC getMother() {
        return null;
    }

    @Override
    public boolean isMotherVillager() {
        return false;
    }

    @Override
    public List<IVillagerNPC> getChildrens() {
        return null;
    }

    @Override
    public CraftWanderingTrader getBukkitEntity() {
        return (CraftWanderingTrader) super.getBukkitEntity();
    }

    @Override
    public LivingEntity bukkit() {
        return getBukkitEntity();
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
        return false;
    }

    @Override
    public boolean isFamily(UUID uuid, boolean checkPartner) {
        return false;
    }

    @Override
    public boolean isPartner(UUID uuid) {
        return false;
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
        return 0;
    }

    @Override
    public void divorceAndDropRing(@Nullable Player player) {

    }

    @Override
    public void drop(ItemStack item) {

    }

    @Override
    public void startTrading(Player player) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        setTradingPlayer(handle);
        openTradingScreen(handle, this.getDisplayName(), 1);
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
        return false;
    }

    @Override
    public void setPartner(@Nullable UUID uuid, boolean isPartnerVillager) {

    }

    @Override
    public int getFoodLevel() {
        return 0;
    }

    @Override
    public boolean isFishing() {
        return false;
    }

    @Override
    public void toggleFishing() {

    }

    @Override
    public boolean startRiding(net.minecraft.world.entity.Entity entity) {
        if (!Config.DISABLE_VILLAGER_RIDING_NEARBY_BOAT.asBool()) return super.startRiding(entity);

        for (StackTraceElement stacktrace : new Throwable().getStackTrace()) {
            String clazz = stacktrace.getClassName();
            if (clazz.equals(Boat.class.getName())) return false;
        }

        return super.startRiding(entity);
    }

    private boolean useVillagerSounds() {
        return Config.USE_VILLAGER_SOUNDS.asBool();
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean success) {
        return useVillagerSounds() ? super.getTradeUpdatedSound(success) : null;
    }

    @Override
    public void playCelebrateSound() {
        if (useVillagerSounds()) super.playCelebrateSound();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return useVillagerSounds() ? super.getNotifyTradeSound() : null;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return useVillagerSounds() ? super.getAmbientSound() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (useVillagerSounds()) return super.getHurtSound(source);
        return switch (source.getMsgId()) {
            case "onFire" -> SoundEvents.PLAYER_HURT_ON_FIRE;
            case "drown" -> SoundEvents.PLAYER_HURT_DROWN;
            case "sweetBerryBush" -> SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH;
            case "freeze" -> SoundEvents.PLAYER_HURT_FREEZE;
            default -> SoundEvents.PLAYER_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return useVillagerSounds() ? super.getDeathSound() : SoundEvents.PLAYER_DEATH;
    }

    @Override
    public SoundEvent getSwimSound() {
        return useVillagerSounds() ? super.getSwimSound() : SoundEvents.PLAYER_SWIM;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return useVillagerSounds() ? super.getSwimSplashSound() : SoundEvents.PLAYER_SPLASH;
    }

    @Override
    public SoundEvent getSwimHighSpeedSplashSound() {
        return useVillagerSounds() ? super.getSwimHighSpeedSplashSound() : SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public SoundSource getSoundSource() {
        return useVillagerSounds() ? super.getSoundSource() : SoundSource.PLAYERS;
    }

    @Override
    public Fallsounds getFallSounds() {
        return useVillagerSounds() ? super.getFallSounds() : new Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    protected SoundEvent getDrinkingSound(net.minecraft.world.item.ItemStack item) {
        return useVillagerSounds() ? super.getDrinkingSound(item) : SoundEvents.GENERIC_DRINK;
    }

    @Override
    public void sendSpawnPacket() {
        sendPacket(new ClientboundAddEntityPacket(this));
        sendPacket(new ClientboundSetEntityDataPacket(getId(), getEntityData().getNonDefaultValues()));
    }

    @Override
    public void sendDestroyPacket() {
        sendPacket(new ClientboundRemoveEntitiesPacket(getId()));
    }

    private void sendPacket(Packet<?> packet) {
        for (ServerPlayer player : ((ServerLevel) level()).players()) {
            player.connection.send(packet);
        }
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
        CompoundTag tag = new CompoundTag();
        savePluginData(tag);

        return OfflineVillagerNPC.from(uuid,
                (CompoundTag) tag.get(plugin.getNpcValuesKey().toString()),
                level().getWorld().getName(),
                getX(),
                getY(),
                getZ());
    }

    @Override
    public @Nullable Component getCustomName() {
        return getCustomName(plugin.getTracker().isInvalid(getBukkitEntity(), true));
    }

    private @Nullable Component getCustomName(boolean vanilla) {
        return vanilla ? super.getCustomName() : villagerName != null ? Component.literal(villagerName) : null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // Save the previous (vanilla) custom name.
        Component customName = getCustomName(true);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName));
        } else {
            tag.remove("CustomName");
        }

        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);
        savePluginData(bukkit);

        tag.put("BukkitValues", bukkit);

        NMSConverter.updateBukkitValues(tag, plugin.getNpcValuesKey().getNamespace(), this);
    }

    public void savePluginData(CompoundTag tag) {
        CompoundTag villagerTag = new CompoundTag();
        villagerTag.putUUID(OfflineVillagerNPC.UUID, uuid);
        if (villagerName != null) villagerTag.putString(OfflineVillagerNPC.NAME, villagerName);
        if (sex != null) villagerTag.putString(OfflineVillagerNPC.SEX, sex);
        villagerTag.putInt(OfflineVillagerNPC.SKIN_TEXTURE_ID, skinTextureId);
        tag.put(plugin.getNpcValuesKey().toString(), villagerTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);

        Tag base = bukkit.get(plugin.getNpcValuesKey().toString());
        loadPluginData(base != null ? (CompoundTag) base : new CompoundTag());

        // Previous versions of this plugin used setCustomName() before.
        Component customName = getCustomName(true);
        if (customName != null && villagerName.equals(customName.getString())) {
            setCustomName(null);
        }
    }

    public void loadPluginData(@NotNull CompoundTag villagerTag) {
        VillagerTracker tracker = plugin.getTracker();

        if (villagerTag.hasUUID(OfflineVillagerNPC.UUID)) setUUID(villagerTag.getUUID(OfflineVillagerNPC.UUID));
        villagerName = villagerTag.getString(OfflineVillagerNPC.NAME);
        sex = villagerTag.getString(OfflineVillagerNPC.SEX);
        if (sex.isEmpty()) sex = PluginUtils.getRandomSex();
        if (tracker.shouldRename(villagerName)) {
            setVillagerName(tracker.getRandomNameBySex(sex));
        }
        skinTextureId = villagerTag.contains(OfflineVillagerNPC.SKIN_TEXTURE_ID) ?
                villagerTag.getInt(OfflineVillagerNPC.SKIN_TEXTURE_ID) :
                -1;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        plugin.getServer().getPluginManager().callEvent(new RealisticRemoveEvent(
                this,
                org.bukkit.entity.EntityType.WANDERING_TRADER,
                RealisticRemoveEvent.RemovalReason.values()[reason.ordinal()]));
    }

    @Override
    public LastKnownPosition getLastKnownPosition() {
        // Only needed for offlines.
        return null;
    }

    @Override
    public boolean isEquipped() {
        return false;
    }

    @Override
    public void setEquipped(boolean equipped) {

    }

    @Override
    public boolean validShoulderEntityLeft() {
        return false;
    }

    @Override
    public Object getShoulderEntityLeft() {
        return null;
    }

    @Override
    public boolean validShoulderEntityRight() {
        return false;
    }

    @Override
    public Object getShoulderEntityRight() {
        return null;
    }

    @Override
    public void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason) {

    }

    @Override
    public boolean isWasInfected() {
        return false;
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
    public Set<UUID> getPlayers() {
        return null;
    }

    @Override
    public byte getHandData() {
        return entityData.get(DATA_LIVING_ENTITY_FLAGS);
    }

    @Override
    public int getEffectColor() {
        return entityData.get(DATA_EFFECT_COLOR_ID);
    }

    @Override
    public boolean getEffectAmbience() {
        return entityData.get(DATA_EFFECT_AMBIENCE_ID);
    }

    @Override
    public int getBeeStingers() {
        return entityData.get(DATA_STINGER_COUNT_ID);
    }

    @Override
    public void attack(LivingEntity entity) {

    }

    @Override
    public int getKidSkinTextureId() {
        return -1;
    }

    @Override
    public void setKidSkinTextureId(int skinTextureId) {

    }
}