package me.matsubara.realisticvillagers.nms;

import com.mojang.authlib.GameProfile;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.serialization.OfflineDataWrapper;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public interface INMSConverter {

    Optional<IVillagerNPC> getNPC(LivingEntity living);

    void registerEntities();

    String getNPCTag(LivingEntity entity, boolean isInfection);

    boolean isSeekGoatHorn(ItemStack item);

    void createBaby(Location location, String name, String sex, UUID mother, Player father);

    void loadDataFromTag(LivingEntity living, String tag);

    UUID getPartnerUUIDFromPlayerNBT(File file);

    void removePartnerFromPlayerNBT(File file);

    void loadData();

    Raid getRaidAt(Location location);

    GameProfile getPlayerProfile(Player player);

    void refreshSchedules();

    IVillagerNPC getNPCFromTag(String tag);

    void spawnFromTag(Location location, String tag);

    void addGameRuleListener(World world);

    /**
     * Attempts to read an {@link OfflineDataWrapper} directly from a PDC container using the given key.
     * Used for migrating data saved in legacy format (OfflineVillagerNPC stored directly by 1.21.8).
     */
    @Nullable OfflineDataWrapper getNPCFromPDC(PersistentDataContainer container, NamespacedKey key);

    static void printRuleWarning(@NotNull RealisticVillagers plugin,
                                 @NotNull World world,
                                 @NotNull GameRule<?> rule) {
        String warning = "The rule {" + rule.getName() + "} has been disabled in the world {" + world.getName()
                + "}, this will not allow villagers to pick up items.";
        plugin.getLogger().warning(warning);
    }
}