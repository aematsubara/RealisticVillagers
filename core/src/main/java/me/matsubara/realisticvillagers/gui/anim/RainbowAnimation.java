package me.matsubara.realisticvillagers.gui.anim;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.*;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

public class RainbowAnimation extends BukkitRunnable {

    private final InteractGUI gui;
    private final boolean frameEnabled;
    private final @Getter ItemStack defaultItem;
    private final @Getter boolean guiAnim;
    private final int guiAnimType;
    private final long delay;
    private int count;

    private int previous = -1;

    private static final EnumMap<Material, ItemStack> CACHED_ITEMS = new EnumMap<>(Material.class);
    private static final Material[] PANES = {
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE};

    public RainbowAnimation(@NotNull InteractGUI gui) {
        this.gui = gui;

        RealisticVillagers plugin = gui.getPlugin();
        this.frameEnabled = plugin.getConfig().getBoolean("gui.main.frame.enabled");
        this.defaultItem = plugin.getItem("gui.main.frame").build();
        this.guiAnim = plugin.getConfig().getBoolean("gui.rainbow-animation.enabled");
        this.guiAnimType = plugin.getConfig().getInt("gui.rainbow-animation.type");
        this.delay = plugin.getConfig().getLong("gui.rainbow-animation.delay", 10L);
    }

    @Override
    public void run() {
        // Here we update the lore!
        if (count > 0 && count % 20 == 0 && gui instanceof MainGUI main) {
            main.updateRequiredItems();
        }

        // Don't play frame animation.
        if (!guiAnim && previous != -1) {
            count++;
            return;
        }

        // Only apply at delay!
        if (count % delay != 0) {
            count++;
            return;
        }

        if (!guiAnim && !frameEnabled) {
            // At this point, we just use this runnable to update the main items.
            count++;
            return;
        }

        int next;
        do {
            next = RandomUtils.nextInt(0, PANES.length);
        } while (previous == next);

        // No rainbow? default item. Type 1 ? random pane. Type 2 ? null (selected in createFrame()).
        ItemStack background = !guiAnim ? defaultItem : guiAnimType == 1 ? getCachedFrame(PANES[next]) : null;

        int size = gui.getSize();
        String name = gui.getName();

        if (name.equals("equipment")) {
            size = gui.getNPC().bukkit().getInventory().getSize();

            int borderEnd = size + 9;
            int armorStart = borderEnd + 1;
            int armorEnd = armorStart + 6;

            createFrame(background, size, size + 9, i -> i + 1);
            createFrame(background, armorEnd, armorEnd + 1, i -> i + 1);
        } else {
            createFrame(background, 0, 9, i -> i + 1);
            createFrame(background, size - 9, size, i -> i + 1);
            createFrame(background, 0, size - 1, i -> i + 9);
            createFrame(background, 8, size, i -> i + 9);
        }

        previous = next;

        count++;
    }

    public void createFrame(@Nullable ItemStack item, int start, int limit, IntUnaryOperator operator) {
        for (int i = start; i < limit; i = operator.applyAsInt(i)) {
            List<Integer> ignoreIndexes = new ArrayList<>();
            if (gui instanceof WhistleGUI whistle) {
                // Buttons are added once AFTHER this frame is created.
                whistle.addButtons();
                ignoreIndexes.add(gui.getInventory().first(whistle.getClose()));
            } else if (gui instanceof SkinGUI skin) {
                skin.addButtons();
                ignoreIndexes.add(gui.getInventory().first(skin.getClose()));
                handleSkinGUI(skin.getProfessionItems(), ignoreIndexes);
            } else if (gui instanceof CombatGUI combat) {
                ignoreIndexes.add(gui.getInventory().first(combat.getClose()));
            } else if (gui instanceof PlayersGUI players) {
                players.addButtons();
            }
            if (ignoreIndexes.contains(i)) continue;

            gui.getInventory().setItem(i, item != null ? item : getCachedFrame(PANES[RandomUtils.nextInt(0, PANES.length)]));
        }
    }

    private void handleSkinGUI(Map<?, ItemStack> skin, List<Integer> ignoreIndexes) {
        if (skin == null) return;

        for (ItemStack value : skin.values()) {
            int first = gui.getInventory().first(value);
            if (first != -1) ignoreIndexes.add(first);
        }
    }

    private static ItemStack getCachedFrame(Material material) {
        return CACHED_ITEMS.computeIfAbsent(material, mat -> new ItemBuilder(mat).setDisplayName("&7").build());
    }

    public static boolean isCachedBackground(RainbowAnimation animation, @Nullable ItemStack item) {
        return item != null && (item.isSimilar(getCachedFrame(item.getType())) || item.isSimilar(animation.getDefaultItem()));
    }
}
