package me.matsubara.realisticvillagers.entity;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IVillagerNPC {

    UUID getUniqueId();

    String getVillagerName();

    void setVillagerName(String name);

    int getReputation(UUID uuid);

    default int getReputation(@NotNull Player player) {
        return getReputation(player.getUniqueId());
    }

    IVillagerNPC getPartner();

    List<IVillagerNPC> getPartners();

    boolean isPartnerVillager();

    IVillagerNPC getFather();

    boolean isFatherVillager();

    IVillagerNPC getMother();

    @SuppressWarnings("unused")
    boolean isMotherVillager();

    List<IVillagerNPC> getChildrens();

    LivingEntity bukkit();

    void addMinorPositive(UUID uuid, int amount);

    default void addMinorPositive(@NotNull Player player, int amount) {
        addMinorPositive(player.getUniqueId(), amount);
    }

    void addMinorNegative(UUID uuid, int amount);

    default void addMinorNegative(@NotNull Player player, int amount) {
        addMinorNegative(player.getUniqueId(), amount);
    }

    void jumpIfPossible();

    void setProcreatingWith(UUID uuid);

    void setLastProcreation(long lastProcreation);

    boolean canAttack();

    String getSex();

    void setSex(String sex);

    int getSkinTextureId();

    void setSkinTextureId(int skinTextureId);

    int getKidSkinTextureId();

    void setKidSkinTextureId(int skinTextureId);

    boolean isExpectingGift();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isGiftDropped();

    void setGiftDropped(boolean giftDropped);

    void stopExpecting();

    boolean isExpectingBed();

    HandleHomeResult handleBedHome(Block block);

    boolean isTarget(EntityType type);

    boolean isConversating();

    boolean isFemale();

    boolean isMale();

    boolean is(Villager.Profession... professions);

    default boolean isFamily(@NotNull Player player) {
        return isFamily(player.getUniqueId());
    }

    default boolean isFamily(UUID uuid) {
        return isFamily(uuid, false);
    }

    boolean isFamily(UUID uuid, boolean checkPartner);

    default boolean isFamily(@NotNull Player player, boolean checkPartner) {
        return isFamily(player.getUniqueId(), checkPartner);
    }

    boolean isPartner(UUID uuid);

    default boolean isPartner(@NotNull Player player) {
        return isPartner(player.getUniqueId());
    }

    boolean isFather(UUID uuid);

    default boolean isFather(@NotNull Player player) {
        return isFather(player.getUniqueId());
    }

    String getActivityName(String none);

    void addTarget(EntityType type);

    void removeTarget(EntityType type);

    void setInteractType(InteractType interactType);

    void stayInPlace();

    void stopStayingInPlace();

    void startExpectingFrom(ExpectingType type, UUID uuid, int time);

    long getLastProcreation();

    void divorceAndDropRing(@Nullable Player player);

    void drop(ItemStack item);

    void startTrading(Player player);

    void stopInteracting();

    void reactToSeekHorn(Player player);

    boolean isDamageSourceBlocked();

    boolean isInsideRaid();

    boolean isFighting();

    boolean isProcreating();

    boolean isExpectingGiftFrom(UUID uuid);

    @SuppressWarnings("unused")
    boolean isExpectingBedFrom(UUID uuid);

    boolean isExpecting();

    ExpectingType getExpectingType();

    UUID getExpectingFrom();

    boolean isInteracting();

    UUID getInteractingWith();

    boolean isFollowing();

    boolean isStayingInPlace();

    void setInteractingWithAndType(UUID uuid, InteractType type);

    boolean hasPartner();

    void setPartner(@Nullable UUID uuid, boolean isPartnerVillager);

    default void setPartner(@NotNull Player player) {
        setPartner(player.getUniqueId(), false);
    }

    int getFoodLevel();

    boolean isFishing();

    void toggleFishing();

    void sendSpawnPacket();

    void sendDestroyPacket();

    boolean isShakingHead();

    void shakeHead(Player at);

    IVillagerNPC getOffline();

    LastKnownPosition getLastKnownPosition();

    boolean isEquipped();

    void setEquipped(boolean equipped);

    boolean validShoulderEntityLeft();

    Object getShoulderEntityLeft();

    boolean validShoulderEntityRight();

    Object getShoulderEntityRight();

    void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason);

    boolean isWasInfected();

    void stopExchangeables();

    void refreshBrain();

    boolean isReviving();

    Set<UUID> getPlayers();

    byte getHandData();

    int getEffectColor();

    boolean getEffectAmbience();

    int getBeeStingers();

    void attack(LivingEntity entity);

    default void resetNametagsFor(@NotNull RealisticVillagers plugin, Player player) {
        LivingEntity bukkit = bukkit();
        if (bukkit == null || player == null) return;

        plugin.getTracker().getPool()
                .getNPC(bukkit.getEntityId())
                .ifPresent(temp -> temp.refreshNametags(player));
    }
}