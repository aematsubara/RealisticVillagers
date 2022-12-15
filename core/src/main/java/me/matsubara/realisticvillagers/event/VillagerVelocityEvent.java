package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class VillagerVelocityEvent extends VillagerEvent implements Cancellable {

    private Vector velocity;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    public VillagerVelocityEvent(IVillagerNPC npc, Vector velocity) {
        super(npc);
        this.velocity = velocity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }


    public @NotNull Vector getVelocity() {
        return velocity;
    }


    public void setVelocity(@NotNull Vector velocity) {
        this.velocity = velocity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
