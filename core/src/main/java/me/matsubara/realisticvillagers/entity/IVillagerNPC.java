package me.matsubara.realisticvillagers.entity;

import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IVillagerNPC {

    UUID getUniqueId();

    String getVillagerName();

    void setVillagerName(String name);

    int getReputation(UUID uuid);

    IVillagerNPC getPartner();

    List<IVillagerNPC> getPartners();

    boolean isPartnerVillager();

    IVillagerNPC getFather();

    boolean isFatherVillager();

    IVillagerNPC getMother();

    @SuppressWarnings("unused")
    boolean isMotherVillager();

    List<IVillagerNPC> getChildrens();

    Villager bukkit();

    void addMinorPositive(UUID uuid, int amount);

    void addMinorNegative(UUID uuid, int amount);

    void jumpIfPossible();

    void setProcreatingWith(UUID uuid);

    void setLastProcreation(long lastProcreation);

    boolean canAttack();

    void spawnEntityEventParticle(Particle particle);

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

    @SuppressWarnings("unused")
    boolean isFamily(UUID uuid);

    boolean isFamily(UUID uuid, boolean checkPartner);

    boolean isPartner(UUID uuid);

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

    Object getShoulderEntityLeft();

    Object getShoulderEntityRight();

    void causeFoodExhaustion(float exhaustion, VillagerExhaustionEvent.ExhaustionReason reason);

    boolean isWasInfected();

    void stopExchangeables();

    void refreshBrain();

    boolean isReviving();

    Set<UUID> getPlayers();
}