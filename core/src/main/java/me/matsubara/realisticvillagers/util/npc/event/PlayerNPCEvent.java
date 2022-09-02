package me.matsubara.realisticvillagers.util.npc.event;

import me.matsubara.realisticvillagers.util.npc.NPC;
import me.matsubara.realisticvillagers.util.npc.modifier.NPCModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Represents an event fired when an action between a player and a NPC occurs.
 */
public abstract class PlayerNPCEvent extends PlayerEvent {

    /**
     * The NPC involved in this event.
     */
    private final NPC npc;

    /**
     * Constructs a new event instance.
     *
     * @param who the player involved in this event.
     * @param npc the NPC involved in this event.
     */
    public PlayerNPCEvent(Player who, NPC npc) {
        super(who);
        this.npc = npc;
    }

    /**
     * Sends the queued data in the provided {@link NPCModifier}s to the player involved in this
     * event.
     *
     * @param npcModifiers The {@link NPCModifier}s whose data should be sent.
     */
    public void send(NPCModifier... npcModifiers) {
        for (NPCModifier npcModifier : npcModifiers) {
            npcModifier.send(super.getPlayer());
        }
    }

    /**
     * @return The NPC involved in this event.
     */
    public NPC getNPC() {
        return npc;
    }
}