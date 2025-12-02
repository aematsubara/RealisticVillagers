package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.HandleHomeResult;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFishEvent;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        villagerExpectingCache.entrySet().removeIf(next -> next.getValue().bukkit().equals(villager));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerFish(@NotNull VillagerFishEvent event) {
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
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        // We'll handle gifting on VillagerListeners#isExpecting().
        if (!getGiftModeFromConfig().drop()) return;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            handleItemDissapear(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(@NotNull InventoryPickupItemEvent event) {
        handleItemDissapear(event.getItem());
    }

    private void handleItemDissapear(Item item) {
        if (notOurItem(item)) return;

        // We know that thrower isn't null since isOurItem() is true.
        IVillagerNPC npc = get(item.getThrower());
        npc.setGiftDropped(false);

        removeMetadata(item, plugin.getGiftKey());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (notOurItem(item)) return;

        UUID thrower = item.getThrower();
        IVillagerNPC npc = get(thrower);

        Entity picker = event.getEntity();
        UUID pickerUUID = picker.getUniqueId();

        // We know that thrower isn't null since isOurItem() is true.
        @SuppressWarnings("DataFlowIssue") Player throwerPlayer = Bukkit.getPlayer(thrower);

        // The villager picked up the item.
        if (npc.bukkit().getUniqueId().equals(pickerUUID)) {
            removeMetadata(item, plugin.getGiftKey());
            handleVillagerPickUp(npc, item.getItemStack(), thrower, throwerPlayer, event);
            return;
        }

        // A random player tried to pick up the gift.
        if (!pickerUUID.equals(thrower)) {
            event.setCancelled(true);
            return;
        }

        // The player gifting picked the item.

        npc.setGiftDropped(false);
        removeMetadata(item, plugin.getGiftKey());

        if (throwerPlayer == null) return;

        Inventory open = throwerPlayer.getOpenInventory().getTopInventory();
        if (open.getHolder() instanceof InteractGUI interact) {
            interact.setShouldStopInteracting(true);
            throwerPlayer.closeInventory();
        }
    }

    public void handleVillagerPickUp(@NotNull IVillagerNPC npc, ItemStack item, UUID thrower, Player throwerPlayer, @Nullable Cancellable cancellable) {
        remove(thrower);

        // Stop expecting gift.
        npc.stopExpecting();

        // Cancel event if player is offline.
        if (throwerPlayer == null) {
            if (cancellable != null) cancellable.setCancelled(true);
            return;
        }

        // Call event, handle gift and add to cooldown.
        plugin.getServer().getPluginManager().callEvent(new VillagerPickGiftEvent(
                npc,
                throwerPlayer,
                item));
        handleGift(npc, throwerPlayer, item);
        plugin.getCooldownManager().addCooldown(throwerPlayer, npc.bukkit(), "gift");
    }

    private boolean notOurItem(Item item) {
        removeMetadata(item, plugin.getFishedKey());

        UUID thrower = item.getThrower();
        if (thrower == null) return true;

        IVillagerNPC npc = get(thrower);
        if (npc == null || !npc.isExpectingGift()) {
            // No longer expecting, remove metadata.
            removeMetadata(item, plugin.getGiftKey());
            return true;
        }

        return false;
    }

    private void removeMetadata(@NotNull Item item, NamespacedKey key) {
        ItemStack stack = item.getItemStack();

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) meta.getPersistentDataContainer().remove(key);

        stack.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();

        IVillagerNPC npc = get(player.getUniqueId());
        if (npc == null || !npc.isExpectingBed()) return;

        // Prevent all except physical.
        if (event.getAction() != Action.PHYSICAL) event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        Messages messages = plugin.getMessages();

        if (block == null || !Tag.BEDS.isTagged(block.getType())) {
            messages.send(player, Messages.Message.BED_INVALID);
            return;
        }

        Bed bed = (Bed) block.getBlockData();
        boolean occupied = bed.isOccupied();

        HandleHomeResult result;
        if (occupied || (result = npc.handleBedHome(bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing()))) == HandleHomeResult.OCCUPIED) {
            messages.send(player, Messages.Message.BED_OCCUPIED);
            return;
        } else if (result == HandleHomeResult.INVALID) {
            messages.send(player, Messages.Message.BED_INVALID);
            return;
        } else if (result == HandleHomeResult.SUCCESS) {
            messages.send(player, Messages.Message.BED_ESTABLISHED);
            messages.send(player, npc, Messages.Message.SET_HOME_SUCCESS);
        }

        villagerExpectingCache.remove(player.getUniqueId());
        npc.stopExpecting();
    }

    public void handleGift(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull ItemStack gift) {
        LivingEntity bukkit = npc.bukkit();

        int reputation = npc.getReputation(player);
        int repRequiredToMarry = Config.REPUTATION_REQUIRED_TO_MARRY.asInt();

        boolean isRing = PluginUtils.isItem(gift, plugin.getIsRingKey());
        boolean isCross = PluginUtils.isItem(gift, plugin.getIsCrossKey());

        boolean alreadyMarriedWithPlayer = isRing && npc.isPartner(player);
        boolean alreadyHasCross = isCross && PluginUtils.hasAnyOf((InventoryHolder) bukkit, plugin.getIsCrossKey());
        boolean isAdult = !(bukkit instanceof Villager villager) || villager.isAdult();

        boolean successByRing = isRing
                && isAdult
                && reputation >= repRequiredToMarry
                && !npc.isFamily(player, false)
                && !npc.hasPartner()
                && !plugin.isMarried(player)
                && !alreadyMarriedWithPlayer;
        boolean successByCross = isCross && !alreadyHasCross;

        GiftCategory category = plugin.getGiftManager().getCategory(npc, gift);
        boolean success = successByRing
                || successByCross
                || ((!isRing && !isCross) && category != null)
                || alreadyMarriedWithPlayer
                || alreadyHasCross;

        int amount;
        if (success) {
            if (successByRing) {
                amount = Config.WEDDING_RING_REPUTATION.asInt();
            } else if (successByCross) {
                amount = Config.CROSS_REPUTATION.asInt();
            } else if (alreadyMarriedWithPlayer || alreadyHasCross) {
                amount = 0;
            } else {
                amount = category.reputation();
            }
        } else {
            amount = isRing ? 0 : Config.BAD_GIFT_REPUTATION.asInt();
        }

        if (amount > 1) {
            if (success) {
                npc.addMinorPositive(player, amount);
            } else {
                npc.addMinorNegative(player, amount);
            }
        }

        Messages messages = plugin.getMessages();

        if (successByRing) {
            bukkit.playEffect(EntityEffect.VILLAGER_HEART);
            messages.send(player, npc, Messages.Message.MARRRY_SUCCESS);
            npc.setPartner(player);
            player.getPersistentDataContainer().set(
                    plugin.getMarriedWith(),
                    PersistentDataType.STRING,
                    bukkit.getUniqueId().toString());
            return;
        }

        if (success) {
            bukkit.playEffect(EntityEffect.VILLAGER_HAPPY);
        } else if (isRing && !npc.isFamily(player, false) && isAdult) {
            bukkit.playEffect(EntityEffect.VILLAGER_ANGRY);

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

        // Stop being annoyed after a good gift.
        if (success && Config.ANNOYING_METER_CLEAR_AFTER_SUCCESS_INTERACTION.asBool()) {
            plugin.getAnnoyingManager().stopBeingAnnoyed(player, npc);
        }

        if (successByCross || (success && isCross)) {
            // For cross, use a random category.
            GiftCategory randomCategory = plugin.getGiftManager().getRandomCategory();
            if (randomCategory != null) messages.sendRandomGiftMessage(player, npc, randomCategory);
            return;
        }

        if (success && alreadyMarriedWithPlayer) {
            messages.send(player, npc, Messages.Message.MARRY_FAIL_MARRIED_TO_GIVER);
            return;
        }

        if (!success && isRing && !isAdult) {
            dropRing(npc, gift);
        }

        messages.sendRandomGiftMessage(player, npc, category);

        ItemStackUtils.setBetterWeaponInMaindHand(bukkit, gift);
        ItemStackUtils.setArmorItem(bukkit, gift);
    }

    private void dropRing(@NotNull IVillagerNPC npc, ItemStack gift) {
        npc.drop(gift);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (npc.bukkit() instanceof InventoryHolder holder) {
                        holder.getInventory().removeItem(plugin.getRing().getResult());
                    }
                },
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

    public GiftMode getGiftModeFromConfig() {
        return PluginUtils.getOrDefault(
                GiftMode.class,
                Config.GIFT_MODE.asString("DROP"),
                GiftMode.DROP);
    }

    public enum GiftMode {
        DROP,
        RIGHT_CLICK;

        public boolean drop() {
            return this == DROP;
        }

        public boolean rightClick() {
            return this == RIGHT_CLICK;
        }
    }
}