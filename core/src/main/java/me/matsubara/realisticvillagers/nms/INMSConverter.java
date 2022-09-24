package me.matsubara.realisticvillagers.nms;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public interface INMSConverter {

    Optional<IVillagerNPC> getNPC(Villager villager);

    void registerEntity();

    String getNPCTag(LivingEntity entity);

    boolean isSeekGoatHorn(ItemStack item);

    void createBaby(Location location, String name, String sex, UUID mother, Player father);

    void loadDataFromTag(Villager villager, String tag);
}