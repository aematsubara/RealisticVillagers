package me.matsubara.realisticvillagers.gui.types;

import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@Getter
public final class EquipmentGUI extends InteractGUI {

    private final static EquipmentSlot[] ARMOR_SLOTS_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND};

    private final static ItemStack EMPTY = new ItemStack(Material.AIR);

    private final ItemStack close;
    private final ItemStack border;
    private final ItemStack head;

    public EquipmentGUI(RealisticVillagers plugin, Player player, IVillagerNPC npc) {
        super("equipment", plugin, npc, npc.bukkit().getInventory().getSize() + 18, null);
        this.close = getGUIItem("close");
        this.border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        WrappedSignedProperty textures = plugin.getVillagerTracker().getTextures(npc.bukkit());
        this.head = textures == null ? border.clone() : new ItemBuilder(PluginUtils.createHead(textures.getValue()))
                .setDisplayName("&7")
                .build();

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::fillInventory);
    }

    private void fillInventory() {
        for (ItemStack item : npc.bukkit().getInventory().getContents()) {
            inventory.addItem(item == null || item.getType().isAir() ? EMPTY : item);
        }

        int size = npc.bukkit().getInventory().getSize();

        int borderEnd = size + 9;
        for (int i = size; i < borderEnd; i++) {
            inventory.setItem(i, border);
        }

        inventory.setItem(borderEnd, head);

        int armorStart = borderEnd + 1;
        int armorEnd = armorStart + 6;

        EntityEquipment equipment = npc.bukkit().getEquipment();
        Preconditions.checkNotNull(equipment);

        for (int i = armorStart; i < armorEnd; i++) {
            EquipmentSlot slot = ARMOR_SLOTS_ORDER[i - armorStart];
            inventory.setItem(i, equipment.getItem(slot));
        }

        inventory.setItem(armorEnd, border);
        inventory.setItem(size + 17, close);
    }
}