package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFishEvent;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
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
import org.bukkit.inventory.Inventory;
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
        if (!(caught instanceof Item)) return;

        ItemStack item = ((Item) caught).getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(
                    plugin.getFishedKey(),
                    PersistentDataType.STRING,
                    event.getNPC().bukkit().getUniqueId().toString());
        }
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        IVillagerNPC npc = villagerExpectingCache.get(uuid);
        if (npc == null || !npc.isExpectingGift()) return;

        npc.setGiftDropped(true);

        ItemStack item = event.getItemDrop().getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(plugin.getGiftKey(), PersistentDataType.STRING, uuid.toString());
        }
        item.setItemMeta(meta);
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

        Player throwerPlayer = Bukkit.getPlayer(thrower);

        if (npc.bukkit().getUniqueId().equals(pickerUUID)) {
            remove(thrower);
            removeMetadata(item, plugin.getGiftKey());

            // Stop expecting gift.
            npc.stopExpecting();

            // Cancel event if player is offline.
            if (throwerPlayer == null) {
                event.setCancelled(true);
                return;
            }

            ItemStack stack = item.getItemStack();

            // Call event, handle gift & add to cooldown.
            plugin.getServer().getPluginManager().callEvent(new VillagerPickGiftEvent(
                    npc,
                    throwerPlayer,
                    stack));
            handleGift(npc, throwerPlayer, stack);
            plugin.getCooldownManager().addCooldown(throwerPlayer, npc.bukkit(), "gift");
            return;
        }

        if (!pickerUUID.equals(thrower)) {
            event.setCancelled(true);
        } else {
            npc.setGiftDropped(false);
            removeMetadata(item, plugin.getGiftKey());

            if (throwerPlayer == null) return;

            Inventory openInventory = throwerPlayer.getOpenInventory().getTopInventory();
            if (openInventory.getHolder() instanceof InteractGUI interact) {
                interact.setShouldStopInteracting(true);
                throwerPlayer.closeInventory();
            }
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

        Messages messages = plugin.getMessages();

        Bed bed = (Bed) block.getBlockData();
        boolean occupied = bed.isOccupied();
        if (occupied || !npc.handleBedHome(bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing()))) {
            if (occupied) {
                messages.send(player, Messages.Message.BED_OCCUPIED);
            }
            messages.send(player, npc, Messages.Message.SET_HOME_FAIL);
        } else {
            messages.send(player, Messages.Message.BED_ESTABLISHED);
            messages.send(player, npc, Messages.Message.SET_HOME_SUCCESS);
        }
        getVillagerExpectingCache().remove(player.getUniqueId());
        npc.stopExpecting();

        event.setCancelled(true);
    }

    private void handleGift(IVillagerNPC npc, Player player, ItemStack gift) {
        UUID playerUUID = player.getUniqueId();

        int reputation = npc.getReputation(playerUUID);
        int repRequiredToMarry = Config.REPUTATION_REQUIRED_TO_MARRY.asInt();

        boolean isRing = gift.isSimilar(plugin.getRing().getRecipe().getResult());

        boolean alreadyMarriedWithPlayer = isRing && npc.isPartner(playerUUID);

        boolean successByRing = isRing
                && npc.bukkit().isAdult()
                && reputation >= repRequiredToMarry
                && !npc.isFamily(playerUUID, false)
                && !npc.hasPartner()
                && !plugin.isMarried(player)
                && !alreadyMarriedWithPlayer;

        GiftCategory category = plugin.getGiftManager().getCategory(npc, gift);
        boolean success = successByRing || (!isRing && category != null) || alreadyMarriedWithPlayer;

        int amount;
        if (success) {
            amount = successByRing ? Config.WEDDING_RING_REPUTATION.asInt() : alreadyMarriedWithPlayer ? 0 : category.reputation();
        } else {
            amount = isRing ? 0 : Config.BAD_GIFT_REPUTATION.asInt();
        }

        if (amount != 0) {
            if (success) {
                npc.addMinorPositive(playerUUID, amount);
            } else {
                npc.addMinorNegative(playerUUID, amount);
            }
        }

        Messages messages = plugin.getMessages();

        if (successByRing) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_HEART);
            messages.send(player, npc, Messages.Message.MARRRY_SUCCESS);
            npc.setPartner(playerUUID, false);
            player.getPersistentDataContainer().set(
                    plugin.getMarriedWith(),
                    PersistentDataType.STRING,
                    npc.bukkit().getUniqueId().toString());
            return;
        }

        if (success) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_HAPPY);
        } else if (isRing && !npc.isFamily(playerUUID, false) && npc.bukkit().isAdult()) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_ANGRY);

            Messages.Message message;
            if (npc.hasPartner()) {
                message = Messages.Message.MARRY_FAIL_MARRIED_TO_OTHER;
            } else if (plugin.isMarried(player)) {
                message = Messages.Message.MARRY_FAIL_PLAYER_MARRIED;
            } else {
                message = Messages.Message.MARRY_FAIL_LOW_REPUTATION;
            }

            messages.send(player, npc, message);
            dropRing(npc, gift);
            return;
        }

        if (success && alreadyMarriedWithPlayer) {
            messages.send(player, npc, Messages.Message.MARRY_FAIL_MARRIED_TO_GIVER);
            return;
        }

        if (!success && isRing && !npc.bukkit().isAdult()) {
            dropRing(npc, gift);
        }

        messages.sendRandomGiftMessage(player, npc, category);

        ItemStackUtils.setBetterWeaponInMaindHand(npc.bukkit(), gift);
        ItemStackUtils.setArmorItem(npc.bukkit(), gift);
    }

    private void dropRing(IVillagerNPC npc, ItemStack gift) {
        npc.drop(gift);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> npc.bukkit().getInventory().removeItem(plugin.getRing().getRecipe().getResult()),
                2L);
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