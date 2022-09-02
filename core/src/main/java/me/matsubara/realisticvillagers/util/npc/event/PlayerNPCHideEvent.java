package me.matsubara.realisticvillagers.util.npc.event;

import me.matsubara.realisticvillagers.util.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * An event fired when a NPC is hidden for a certain player.
 */
public class PlayerNPCHideEvent extends PlayerNPCEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    /**
     * The reason why the NPC was hidden.
     */
    private final Reason reason;

    /**
     * Constructs a new event instance.
     *
     * @param who    The player who is no longer seeing the NPC.
     * @param npc    The NPC the player is no longer seeing.
     * @param reason The reason why the NPC was hidden.
     */
    public PlayerNPCHideEvent(Player who, NPC npc, Reason reason) {
        super(who, npc);
        this.reason = reason;
    }

    /**
     * Get the handlers for this event.
     *
     * @return the handlers for this event.
     */
    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * @return The reason why the NPC was hidden.
     */
    @NotNull
    public Reason getReason() {
        return this.reason;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /**
     * Represents a reason why a NPC was hidden for a player.
     */
    public enum Reason {
        /**
         * The player has manually been excluded from seeing the NPC.
         */
        EXCLUDED,
        /**
         * The distance from NPC and player is now higher than the configured spawn distance.
         */
        SPAWN_DISTANCE,
        /**
         * NPC was in an unloaded chunk.
         */
        UNLOADED_CHUNK,
        /**
         * The NPC was removed from the pool.
         */
        REMOVED,
        /**
         * The player seeing the NPC respawned.
         */
        RESPAWNED
    }
}