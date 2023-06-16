package me.matsubara.realisticvillagers.gui.types;

import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public final class EquipmentGUI extends InteractGUI {

    private final Player player;
    private final ItemStack close;
    private final ItemStack head;

    private static final ItemStack EMPTY = new ItemStack(Material.AIR);
    public static final EquipmentSlot[] ARMOR_SLOTS_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND};

    public EquipmentGUI(RealisticVillagers plugin, IVillagerNPC npc, @NotNull Player player) {
        super(plugin, npc, "equipment", npc.bukkit().getInventory().getSize() + 18, null);
        this.player = player;

        this.close = getGUIItem("close");

        WrappedSignedProperty textures = Config.DISABLE_SKINS.asBool() ? null : plugin.getTracker().getTextures(npc.bukkit());
        this.head = new ItemBuilder(PluginUtils.createHead(textures == null ? VILLAGER_HEAD_TEXTURE : textures.getValue()))
                .setDisplayName("&7")
                .build();

        fillInventory();
        player.openInventory(inventory);
    }

    private void fillInventory() {
        inventory.setContents(npc.bukkit().getInventory().getContents());

        int size = npc.bukkit().getInventory().getSize();
        int borderEnd = size + 9;

        inventory.setItem(borderEnd, head);

        int armorStart = borderEnd + 1;
        int armorEnd = armorStart + 6;

        EntityEquipment equipment = npc.bukkit().getEquipment();
        Preconditions.checkNotNull(equipment);

        for (int i = armorStart; i < armorEnd; i++) {
            EquipmentSlot slot = ARMOR_SLOTS_ORDER[i - armorStart];
            inventory.setItem(i, equipment.getItem(slot));
        }

        inventory.setItem(size + 17, close);
    }
}