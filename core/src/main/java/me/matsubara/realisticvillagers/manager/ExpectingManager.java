package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFishEvent;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import me.matsubara.realisticvillagers.files.Messages;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExpectingManager implements Listener {

    private final RealisticVillagers plugin;
    private final Map<UUID, IVillagerNPC> villagerExpectingCache;

    public ExpectingManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.villagerExpectingCache = new HashMap<>();
    }

    @EventHandler()
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        villagerExpectingCache.entrySet().removeIf(next -> next.getValue().bukkit().equals(villager));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerFish(VillagerFishEvent event) {
        if (event.getState() != VillagerFishEvent.State.CAUGHT_FISH) return;

        Entity caught = event.getCaught();
        if (!(caught instanceof Item item)) return;

        ItemMeta meta = item.getItemStack().getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(
                    plugin.getFishedKey(),
                    PersistentDataType.STRING,
                    event.getNPC().bukkit().getUniqueId().toString());
        }
        item.getItemStack().setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        IVillagerNPC npc = villagerExpectingCache.get(uuid);
        if (npc == null || !npc.isExpectingGift()) return;

        npc.setGiftDropped(true);

        ItemMeta meta = event.getItemDrop().getItemStack().getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(plugin.getGiftKey(), PersistentDataType.STRING, uuid.toString());
        }
        event.getItemDrop().getItemStack().setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        removeMetadata(item, plugin.getFishedKey());

        UUID thrower = item.getThrower();
        if (thrower == null) return;

        IVillagerNPC npc = get(thrower);
        if (npc == null || !npc.isExpectingGift()) return;

        Entity picker = event.getEntity();
        UUID pickerUUID = picker.getUniqueId();

        if (npc.bukkit().getUniqueId().equals(pickerUUID)) {
            remove(thrower);
            removeMetadata(item, plugin.getGiftKey());

            // Stop expecting gift.
            npc.stopExpecting();

            // Cancel event if player is offline.
            Player throwerPlayer = Bukkit.getPlayer(thrower);
            if (throwerPlayer == null) {
                event.setCancelled(true);
                return;
            }

            plugin.getServer().getPluginManager().callEvent(new VillagerPickGiftEvent(
                    npc,
                    throwerPlayer,
                    item.getItemStack()));
            return;
        }

        if (!pickerUUID.equals(thrower)) {
            event.setCancelled(true);
        } else {
            npc.setGiftDropped(false);
            removeMetadata(item, plugin.getGiftKey());
        }
    }

    private void removeMetadata(Item item, NamespacedKey key) {
        ItemStack stack = item.getItemStack();

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) meta.getPersistentDataContainer().remove(key);

        stack.setItemMeta(meta);
        item.setItemStack(stack);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getItem() != null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        IVillagerNPC npc = get(player.getUniqueId());
        if (npc == null || !npc.isExpectingBed()) return;

        Block block = event.getClickedBlock();
        if (block == null || !Tag.BEDS.isTagged(block.getType())) return;

        Bed bed = (Bed) block.getBlockData();
        boolean occupied = bed.isOccupied();
        if (occupied || !npc.handleBedHome(bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing()))) {
            if (occupied) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.BED_OCCUPIED));
            }
            plugin.getMessages().send(npc, player, Messages.Message.SET_HOME_FAIL);
        } else {
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.BED_ESTABLISHED));
            plugin.getMessages().send(npc, player, Messages.Message.SET_HOME_SUCCESS);
        }
        getVillagerExpectingCache().remove(player.getUniqueId());
        npc.stopExpecting();

        event.setCancelled(true);
    }

    public IVillagerNPC get(UUID uuid) {
        return villagerExpectingCache.get(uuid);
    }

    public void expect(UUID uuid, IVillagerNPC npc) {
        villagerExpectingCache.put(uuid, npc);
    }

    public void remove(UUID uuid) {
        villagerExpectingCache.remove(uuid);
    }

    public Map<UUID, IVillagerNPC> getVillagerExpectingCache() {
        return villagerExpectingCache;
    }
}