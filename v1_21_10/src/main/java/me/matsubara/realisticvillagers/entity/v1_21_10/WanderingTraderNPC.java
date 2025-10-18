package me.matsubara.realisticvillagers.entity.v1_21_10;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.data.serialization.OfflineDataWrapper;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Nameable;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.OfflineVillagerNPC;
import me.matsubara.realisticvillagers.event.RealisticRemoveEvent;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftWanderingTrader;
import org.bukkit.craftbukkit.v1_21_R6.persistence.CraftPersistentDataContainer;
import org.bukkit.entity.Boat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
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

    private int nametagEntity = -1;
    private int nametagItemEntity = -1;

    private static final @SuppressWarnings("unchecked") EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID =
            (EntityDataAccessor<Boolean>) Reflection.getFieldValue(Reflection.getField(net.minecraft.world.entity.LivingEntity.class, EntityDataAccessor.class, "bM", true, "DATA_EFFECT_AMBIENCE_ID"));
    private static final @SuppressWarnings("unchecked") EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID =
            (EntityDataAccessor<Integer>) Reflection.getFieldValue(Reflection.getField(net.minecraft.world.entity.LivingEntity.class, EntityDataAccessor.class, "bO", true, "DATA_STINGER_COUNT_ID"));

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
    public boolean startRiding(Entity entity, boolean force, boolean emitEvent) {
        if (!Config.DISABLE_VILLAGER_RIDING_NEARBY_BOAT.asBool()) return super.startRiding(entity, force, emitEvent);

        for (StackTraceElement stacktrace : new Throwable().getStackTrace()) {
            String clazz = stacktrace.getClassName();
            if (clazz.equals(Boat.class.getName())) return false;
        }

        return super.startRiding(entity, force, emitEvent);
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
    public void sendSpawnPacket() {
        sendPacket(new ClientboundAddEntityPacket(this, 0, blockPosition()));
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
        return new OfflineVillagerNPC(
                uuid,
                villagerName,
                sex,
                null,
                false,
                0L,
                skinTextureId,
                -1,
                null,
                null,
                false,
                getLastKnownPosition(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                false,
                false,
                0,
                0,
                0.0f,
                0.0f);
    }

    @Override
    public @Nullable Component getCustomName() {
        return getCustomName(plugin.getTracker().isInvalid(getBukkitEntity(), true));
    }

    private @Nullable Component getCustomName(boolean vanilla) {
        return vanilla ? super.getCustomName() : villagerName != null ? Component.literal(villagerName) : null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        // After addAdditionalSaveData() is called, CraftEntity#storeBukkitValues() will save the values inside the container.
        if (getOffline() instanceof OfflineVillagerNPC offline) {
            CraftPersistentDataContainer container = getBukkitEntity().getPersistentDataContainer();
            container.remove(plugin.getNpcValuesKey()); // Remove previous data.
            container.set(plugin.getNpcValuesKey(), RealisticVillagers.VILLAGER_DATA, offline.toOfflineDataWrapper()); // Use the new system.
        }

        // Save the previous (vanilla) custom name.
        output.storeNullable("CustomName", ComponentSerialization.CODEC, getCustomName(true));
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);

        // We use load() instead of readAdditionalSaveData() because CraftEntity#readBukkitValues is called AFTER readAdditionalSaveData(),
        // so our data won't be present at that time.

        OfflineDataWrapper wrapper = getBukkitEntity().getPersistentDataContainer().get(plugin.getNpcValuesKey(), RealisticVillagers.VILLAGER_DATA);
        OfflineVillagerNPC offline = OfflineVillagerNPC.fromOfflineDataWrapper(wrapper) instanceof OfflineVillagerNPC temp ? temp : null;
        loadFromOffline(offline);

        // Previous versions of this plugin used setCustomName() before.
        Component customName = getCustomName(true);
        if (customName != null && villagerName.equals(customName.getString())) {
            setCustomName(null);
        }
    }

    public void loadFromOffline(@Nullable OfflineVillagerNPC offline) {
        VillagerTracker tracker = plugin.getTracker();

        OfflineVillagerNPC.getAndSet(offline, OfflineVillagerNPC::getUniqueId, this::setUUID);
        OfflineVillagerNPC.getAndSet(offline, OfflineVillagerNPC::getVillagerName, this::setVillagerName, "");
        OfflineVillagerNPC.getAndSet(offline, OfflineVillagerNPC::getSex, this::setSex, "");
        if (sex.isEmpty()) sex = PluginUtils.getRandomSex();
        if (tracker.shouldRename(villagerName)) {
            setVillagerName(tracker.getRandomNameBySex(sex));
        }
        skinTextureId = OfflineVillagerNPC.get(offline, OfflineVillagerNPC::getSkinTextureId, -1);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);

        plugin.getServer().getPluginManager().callEvent(new RealisticRemoveEvent(
                this,
                org.bukkit.entity.EntityType.WANDERING_TRADER,
                RealisticRemoveEvent.RemovalReason.values()[reason.ordinal()]));
    }

    @Override
    public LastKnownPosition getLastKnownPosition() {
        return new LastKnownPosition(level().getWorld().getName(), getX(), getY(), getZ());
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
        return 0;
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