package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class VillagerExhaustionEvent extends VillagerEvent implements Cancellable {

    private final ExhaustionReason reason;

    private float exhaustion;
    private boolean cancelled;

    private static final HandlerList handlers = new HandlerList();

    public VillagerExhaustionEvent(IVillagerNPC npc, ExhaustionReason reason, float exhaustion) {
        super(npc);
        this.reason = reason;
        this.exhaustion = exhaustion;
    }

    @NotNull
    public ExhaustionReason getReason() {
        return reason;
    }

    public float getExhaustion() {
        return exhaustion;
    }

    public void setExhaustion(float exhaustion) {
        this.exhaustion = exhaustion;
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

    public enum ExhaustionReason {
        BLOCK_MINED,
        HUNGER_EFFECT,
        DAMAGED,
        ATTACK,
        JUMP_SPRINT,
        JUMP,
        SWIM,
        WALK_UNDERWATER,
        WALK_ON_WATER,
        SPRINT,
        CROUCH,
        WALK,
        REGEN,
        UNKNOWN
    }
}