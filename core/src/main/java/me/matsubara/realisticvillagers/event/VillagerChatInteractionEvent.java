package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class VillagerChatInteractionEvent extends VillagerEvent {

    private final Player player;
    private final ChatType type;
    private boolean success;

    private static final HandlerList handlers = new HandlerList();

    public VillagerChatInteractionEvent(IVillagerNPC npc, Player player, ChatType type, boolean success) {
        super(npc);
        this.player = player;
        this.type = type;
        this.success = success;
    }

    public Player getPlayer() {
        return player;
    }

    public ChatType getType() {
        return type;
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

    public enum ChatType {
        CHAT,
        GREET,
        STORY,
        JOKE,
        FLIRT,
        BE_PROUD_OF,
        INSULT;

        public boolean isChat() {
            return this == CHAT;
        }

        public boolean isGreet() {
            return this == GREET;
        }

        public boolean isStory() {
            return this == STORY;
        }

        public boolean isJoke() {
            return this == JOKE;
        }

        public boolean isFlirt() {
            return this == FLIRT;
        }

        public boolean isBeProudOf() {
            return this == BE_PROUD_OF;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isInsult() {
            return this == INSULT;
        }

        public String getName() {
            if (isBeProudOf()) return "proud-of";
            return name().toLowerCase();
        }
    }
}