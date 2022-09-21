package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class VillagerFishEvent extends VillagerEvent implements Cancellable {

    private final Entity entity;
    private final State state;
    private final FishHook hook;

    private boolean cancel;
    //private int exp;

    private static final HandlerList handlers = new HandlerList();

    public VillagerFishEvent(IVillagerNPC npc, @Nullable Entity entity, FishHook hook, State state) {
        super(npc);
        this.entity = entity;
        this.hook = hook;
        this.state = state;
    }

    public @Nullable Entity getCaught() {
        return entity;
    }

    public @NotNull FishHook getHook() {
        return hook;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    /*
    public int getExpToDrop() {
        return exp;
    }

    public void setExpToDrop(int amount) {
        exp = amount;
    }*/

    public @NotNull State getState() {
        return state;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    public enum State {
        FISHING,
        CAUGHT_FISH,
        CAUGHT_ENTITY,
        IN_GROUND,
        FAILED_ATTEMPT,
        REEL_IN,
        BITE
    }
}