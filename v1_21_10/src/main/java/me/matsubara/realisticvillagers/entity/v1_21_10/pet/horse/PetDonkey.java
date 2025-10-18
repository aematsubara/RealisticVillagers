package me.matsubara.realisticvillagers.entity.v1_21_10.pet.horse;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.nms.v1_21_10.NMSConverter;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftLivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

public class PetDonkey extends Donkey implements Pet, HorseEating {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    @Getter
    private @Setter boolean tamedByVillager;

    public PetDonkey(EntityType<? extends Donkey> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, VillagerNPC.class, 6.0f));
    }

    @Override
    public void tameByVillager(@NotNull IVillagerNPC npc) {
        setTamed(true);
        setOwner(((CraftLivingEntity) npc.bukkit()).getHandle());
        setTamedByVillager(true);
        setPersistenceRequired();
        this.persist = true;
        level().broadcastEntityEvent(this, (byte) 7);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        NMSConverter.updateTamedData(plugin, this, tamedByVillager);
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);

        // We use load() instead of readAdditionalSaveData() because CraftEntity#readBukkitValues is called AFTER readAdditionalSaveData(),
        // so our data won't be present at that time.

        tamedByVillager = getBukkitEntity().getPersistentDataContainer().getOrDefault(plugin.getTamedByVillagerKey(), PersistentDataType.BOOLEAN, false);
    }

    @Override
    public UUID getOwnerUniqueId() {
        EntityReference<LivingEntity> reference = getOwnerReference();
        if (reference != null) return reference.getUUID();
        return null;
    }

    @Override
    public void setInLove(@Nullable Player player) {
        if (player == null) return;
        super.setInLove(player);
    }

    @Override
    public void handleEating(ItemStack item) {
        handleEating(null, item);
    }
}