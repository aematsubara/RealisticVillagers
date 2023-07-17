package me.matsubara.realisticvillagers.nms;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.Raid;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public interface INMSConverter {

    Optional<IVillagerNPC> getNPC(Villager villager);

    void registerEntities();

    String getNPCTag(LivingEntity entity, boolean isInfection);

    boolean isSeekGoatHorn(ItemStack item);

    void createBaby(Location location, String name, String sex, UUID mother, Player father);

    void loadDataFromTag(Villager villager, String tag);

    UUID getPartnerUUIDFromPlayerNBT(File file);

    void removePartnerFromPlayerNBT(File file);

    void loadData();

    ItemStack randomVanillaEnchantments(Location location, ItemStack item);

    Raid getRaidAt(Location location);

    PropertyMap changePlayerSkin(Player player, String texture, String signature);

    GameProfile getPlayerProfile(Player player);

    boolean isBeingTracked(Player player, int villagerId);

    void refreshSchedules();

    IVillagerNPC getNPCFromTag(String tag);

    void spawnFromTag(Location location, String tag);
}