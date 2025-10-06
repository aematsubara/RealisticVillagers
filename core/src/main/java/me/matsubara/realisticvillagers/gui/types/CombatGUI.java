package me.matsubara.realisticvillagers.gui.types;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.EntityCategory;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.util.EntityHead;
import me.matsubara.realisticvillagers.util.InventoryUpdate;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public final class CombatGUI extends InteractGUI {

    private final Player player;
    private final List<EntityHead> heads;
    private final String keyword;
    private final boolean isAnimal;
    private final boolean useSpawnEgg;

    private int current;
    private int pages;

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] STATUS_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] HOTBAR = {28, 29, 30, 31, 32, 33, 34};

    // We'll keep these here, so we can swap them when clicking on them.
    private final ItemStack enabled;
    private final ItemStack disabled;

    // Villagers can't reach water entities when they're underwater.
    private static final Set<EntityType> IGNORE_ENTITIES = Stream.of(
                    "AXOLOTL",
                    "COD",
                    "DOLPHIN",
                    "ELDER_GUARDIAN",
                    "GLOW_SQUID",
                    "GUARDIAN",
                    "PUFFERFISH",
                    "SALMON",
                    "SQUID",
                    "TADPOLE",
                    "TROPICAL_FISH")
            .map(string -> PluginUtils.getOrNull(EntityType.class, string))
            .collect(Collectors.toSet());

    public CombatGUI(RealisticVillagers plugin, IVillagerNPC npc, Player player, @Nullable Integer page, @Nullable String keyword, boolean isAnimal) {
        super(plugin, npc, "combat", 45, null, false);
        this.player = player;
        this.isAnimal = isAnimal;
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        this.useSpawnEgg = plugin.getConfig().getBoolean("gui.combat.use-spawn-egg")
                && version.isNewerThanOrEquals(ServerVersion.V_1_20_5);
        this.current = page != null ? page : 0;

        enabled = getGUIItem("enabled");
        disabled = getGUIItem("disabled");

        this.heads = new ArrayList<>();
        for (EntityHead skull : EntityHead.values()) {
            EntityType type = skull.getType();
            if (type == null || IGNORE_ENTITIES.contains(type)) continue;

            EntityCategory category = skull.getCategory();
            if (isAnimal && category == EntityCategory.MONSTER) continue;
            if (!isAnimal && category == EntityCategory.ANIMAL) continue;

            heads.add(skull);
        }

        this.keyword = keyword;

        if (keyword != null && !keyword.isEmpty()) {
            this.heads.removeIf(head -> !head.name().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
        }

        updateInventory();
        player.openInventory(inventory);
        InventoryUpdate.updateInventory(player, getTitle());
    }

    public void updateInventory() {
        clear(SLOTS, STATUS_SLOTS, HOTBAR);

        pages = (int) (Math.ceil((double) heads.size() / SLOTS.length));

        if (current > 0) inventory.setItem(28, getGUIItem("previous"));
        inventory.setItem(31, getSearchItem(keyword));
        inventory.setItem(44, getGUIItem("back"));
        if (current < pages - 1) inventory.setItem(34, getGUIItem("next"));

        InventoryUpdate.updateInventory(player, getTitle());

        if (heads.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = current * SLOTS.length;
        boolean isLastPage = current == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < heads.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            EntityHead skull = heads.get(aux);

            String defaultName = skull.name().toLowerCase(Locale.ROOT);
            @SuppressWarnings("deprecation") String name = plugin.getConfig().getString("variable-text.entity." + defaultName.replace("-", "_"),
                    WordUtils.capitalizeFully(defaultName.toLowerCase(Locale.ROOT).replace("_", " ")));

            ItemBuilder builder = new ItemBuilder(getGUIItem("entity"));

            if (useSpawnEgg) {
                Material egg = skull.getSpawnEgg();
                if (egg != null) builder.setType(egg);
            } else if (skull.getUrl() == null) {
                builder.setType(skull.getHead().getType());
            } else {
                builder.setHead(skull.getUrl(), true);
            }

            // Set item in the respective slot.
            inventory.setItem(slotIndex.get(index), builder
                    .setData(plugin.getEntityTypeKey(), PersistentDataType.STRING, skull.name())
                    .replace("%entity-type%", name)
                    .build());

            boolean enabled = npc.isTarget(skull.getType());

            inventory.setItem(slotIndex.get(index) + 9, new ItemBuilder(enabled ? this.enabled : this.disabled)
                    .setData(plugin.getEntityTypeKey(), PersistentDataType.STRING, skull.name())
                    .build());
        }
    }

    @Override
    protected @NotNull String getTitle() {
        return super.getTitle()
                .replace("%page%", String.valueOf(current + 1))
                .replace("%max-page%", String.valueOf(pages == 0 ? 1 : pages));
    }

    public void previousPage(boolean isShiftClick) {
        // If shift clicking, go to the first page; otherwise, go to the previous page.
        current = isShiftClick ? 0 : current - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        // If shift clicking, go to the last page; otherwise, go to the next page.
        current = isShiftClick ? pages - 1 : current + 1;
        updateInventory();
    }
}