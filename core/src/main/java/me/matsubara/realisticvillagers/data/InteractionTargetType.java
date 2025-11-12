package me.matsubara.realisticvillagers.data;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum InteractionTargetType {
    ADULT,
    CHILD,
    CHILD_OFFSPRING,
    PARTNER;

    public @NotNull String getName() {
        return name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    public static InteractionTargetType getInteractionTarget(@NotNull IVillagerNPC npc, @NotNull Player player) {
        if (npc.isPartner(player)) return PARTNER;
        else if (npc.isFather(player)) return CHILD_OFFSPRING;
        else if (!(npc.bukkit() instanceof Villager villager) || villager.isAdult()) return ADULT;
        return CHILD;
    }
}