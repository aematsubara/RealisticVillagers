package me.matsubara.realisticvillagers.entity;

import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public interface IVillagerNPC {

    String getVillagerName();

    void setVillagerName(String name);

    int getReputation(UUID uuid);

    UUID getPartner();

    boolean isPartnerVillager();

    UUID getFather();

    boolean isFatherVillager();

    UUID getMother();

    boolean isMotherVillager();

    List<UUID> getChildrens();

    Villager bukkit();

    void addMinorPositive(UUID uuid, int amount);

    void addMinorNegative(UUID uuid, int amount);

    void jumpIfPossible();

    void setProcreatingWith(UUID uuid);

    void setLastProcreation(long lastProcreation);

    boolean canAttack();

    void spawnParticle(Particle particle);

    String getSex();

    void setSex(String sex);

    int getSkinTextureId();

    void setSkinTextureId(int skinTextureId);

    boolean isExpectingGift();

    void setGiftDropped(boolean giftDropped);

    void stopExpecting();

    boolean isExpectingBed();

    boolean handleBedHome(Block block);

    boolean isTarget(EntityType type);

    boolean isConversating();

    boolean isMale();

    boolean is(Villager.Profession... professions);

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

    void divorceAndDropRing(Player player);

    void drop(ItemStack item);

    void startTrading(Player player);

    void stopInteracting();

    void reactToSeekHorn(Player player);

    boolean isDamageSourceBlocked();

    boolean isInsideRaid();

    boolean isFighting();

    boolean isProcreating();

    boolean isExpectingGiftFrom(UUID uuid);

    boolean isExpectingBedFrom(UUID uuid);

    boolean isInteracting();

    UUID getInteractingWith();

    boolean isFollowing();

    boolean isStayingInPlace();

    void setInteractingWithAndType(UUID uuid, InteractType type);

    boolean hasPartner();

    void setPartner(UUID uuid, boolean isPartnerVillager);
}