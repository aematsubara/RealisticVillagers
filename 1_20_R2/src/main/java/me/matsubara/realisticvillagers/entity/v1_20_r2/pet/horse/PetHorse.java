package me.matsubara.realisticvillagers.entity.v1_20_r2.pet.horse;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_20_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.nms.v1_20_r2.NMSConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

public class PetHorse extends Horse implements Pet, HorseEating {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    @Getter
    private @Setter boolean tamedByVillager;

    public PetHorse(EntityType<? extends Horse> type, Level level) {
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
        setOwnerUUID(npc.bukkit().getUniqueId());
        setTamedByVillager(true);
        setPersistenceRequired();
        level().broadcastEntityEvent(this, (byte) 7);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        NMSConverter.updateTamedData(plugin, tag, this, tamedByVillager);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tamedByVillager = NMSConverter.getOrCreateBukkitTag(tag).getBoolean(plugin.getTamedByVillagerKey().toString());
    }

    @Override
    public UUID getOwnerUniqueId() {
        return super.getOwnerUUID();
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