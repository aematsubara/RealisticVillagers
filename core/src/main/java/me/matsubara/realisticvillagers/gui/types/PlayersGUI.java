package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.PaginatedGUI;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Getter
public class PlayersGUI extends PaginatedGUI {

    private final String keyword;
    private final ItemStack close;
    private final ItemStack previous;
    private final ItemStack next;
    private final ItemStack search;
    private final ItemStack clearSearch;
    private final ItemStack addNewPlayer;

    private static final int CLOSE_SLOT = 35;
    private static final int PREVIOUS_SLOT = 19;
    private static final int SEARCH_SLOT = 22;
    private static final int ADD_NEW_PLAYER_SLOT = 23;
    private static final int NEXT_SLOT = 25;

    public PlayersGUI(RealisticVillagers plugin, IVillagerNPC npc, Player player, @NotNull Set<OfflinePlayer> players, @Nullable String keyword) {
        super(plugin, npc, "players", getValidSize(plugin, "players", 36), player, players
                .stream()
                .filter(offline -> keyword == null || (offline.getName() != null && offline.getName().contains(keyword.toLowerCase())))
                .map(offline -> {
                    String name = offline.getName();
                    return new ItemBuilder(plugin.getItem("gui.players.items.player").build())
                            .setOwningPlayer(offline)
                            .setData(plugin.getPlayerUUIDKey(), PersistentDataType.STRING, offline.getUniqueId().toString())
                            .replace("%name%", name != null ? name : "???")
                            .build();
                })
                .toList());

        this.keyword = keyword;
        close = getGUIItem("close");
        previous = getGUIItem("previous");
        next = getGUIItem("next");
        search = getGUIItem("search");
        clearSearch = keyword != null ? getGUIItem("clear-search", string -> string.replace("%keyword%", keyword)) : null;
        addNewPlayer = getGUIItem("add-new-player");
    }

    @Override
    public void addButtons() {
        int extra = 9 * (size == 36 ? 0 : size == 45 ? 1 : 2);
        inventory.setItem(CLOSE_SLOT + extra, close);
        if (current > 0) inventory.setItem(PREVIOUS_SLOT + extra, previous);
        if (current < pages - 1) inventory.setItem(NEXT_SLOT + extra, next);

        boolean centerSearch = pages > 1;
        inventory.setItem(SEARCH_SLOT + extra, centerSearch ? (keyword != null ? clearSearch : search) : addNewPlayer);

        if (centerSearch) inventory.setItem(ADD_NEW_PLAYER_SLOT + extra, addNewPlayer);
    }
}