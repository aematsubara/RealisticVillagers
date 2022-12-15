package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class VillagerTameEvent extends VillagerEvent implements Cancellable {

    private final LivingEntity entity;
    private boolean cancelled;

    private static final HandlerList handlers = new HandlerList();

    public VillagerTameEvent(LivingEntity entity, IVillagerNPC npc) {
        super(npc);
        this.entity = entity;
    }

    public @NotNull LivingEntity getEntity() {
        return entity;
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

    @SuppressWarnings("unused")
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}