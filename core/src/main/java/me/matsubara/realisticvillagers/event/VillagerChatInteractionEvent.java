package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class VillagerChatInteractionEvent extends VillagerEvent {

    private final Player player;
    private final GUIInteractType interactType;
    private boolean success;

    private static final HandlerList handlers = new HandlerList();

    public VillagerChatInteractionEvent(IVillagerNPC npc, Player player, GUIInteractType interactType, boolean success) {
        super(npc);
        this.player = player;
        this.interactType = interactType;
        this.success = success;
    }

    public Player getPlayer() {
        return player;
    }

    public GUIInteractType getType() {
        return interactType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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