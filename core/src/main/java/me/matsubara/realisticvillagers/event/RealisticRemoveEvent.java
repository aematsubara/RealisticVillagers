package me.matsubara.realisticvillagers.event;

import lombok.Getter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class RealisticRemoveEvent extends VillagerEvent {

    private final EntityType type;
    private final RemovalReason reason;

    private static final HandlerList handlers = new HandlerList();

    public RealisticRemoveEvent(IVillagerNPC npc, RemovalReason reason) {
        this(npc, EntityType.VILLAGER, reason);
    }

    public RealisticRemoveEvent(IVillagerNPC npc, EntityType type, RemovalReason reason) {
        super(npc);
        this.type = type;
        this.reason = reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    public enum RemovalReason {
        KILLED(true, false),
        DISCARDED(true, false),
        UNLOADED_TO_CHUNK(false, true),
        UNLOADED_WITH_PLAYER(false, false),
        CHANGED_DIMENSION(false, false);

        private final @Getter boolean destroy;
        private final @Getter boolean save;

        RemovalReason(boolean destroy, boolean save) {
            this.destroy = destroy;
            this.save = save;
        }
    }
}