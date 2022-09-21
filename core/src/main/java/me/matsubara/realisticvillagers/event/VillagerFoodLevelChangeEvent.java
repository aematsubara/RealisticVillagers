package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class VillagerFoodLevelChangeEvent extends VillagerEvent implements Cancellable {

    private final ItemStack item;

    private int level;
    private boolean cancelled;

    private static final HandlerList handlers = new HandlerList();

    public VillagerFoodLevelChangeEvent(IVillagerNPC npc, int level) {
        this(npc, level, null);
    }

    public VillagerFoodLevelChangeEvent(IVillagerNPC npc, int level, @Nullable ItemStack item) {
        super(npc);
        this.level = level;
        this.item = item;
    }


    public @Nullable ItemStack getItem() {
        return (item == null) ? null : item.clone();
    }

    public int getFoodLevel() {
        return level;
    }

    public void setFoodLevel(int level) {
        this.level = Math.max(level, 0);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}