package me.matsubara.realisticvillagers.listener;

import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.Reflection;
import org.apache.commons.lang3.Validate;
import org.bukkit.ChatColor;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VillagerListeners implements Listener {

    private final RealisticVillagers plugin;

    private static final MethodHandle MODIFIERS = Reflection.getFieldGetter(EntityDamageEvent.class, "modifiers");

    public VillagerListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(@NotNull GenericGameEvent event) {
        GameEvent gameEvent = event.getEvent();

        // RING_BELL is deprecated and shouldn't be used, but due to having the same key as BLOCK_CHANCE,
        // RING BELL is called because of the map replacing the duplicated key.
        if (gameEvent != GameEvent.BLOCK_CHANGE && gameEvent != GameEvent.RING_BELL) return;

        if (event.getLocation().getBlock().getType() != Material.BELL) return;

        // Play swing hand animation when ringing bell.
        if (event.getEntity() instanceof Villager villager) {
            villager.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!Config.ARROWS_PASS_THROUGH_OTHER_VILLAGERS.asBool()) return;

        // It's a custom villager since vanilla ones can't shoot arrows.
        if (!(event.getEntity().getShooter() instanceof Villager)) return;
        if (!(event.getHitEntity() instanceof Villager)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(@NotNull VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();

        VillagerTracker tracker = plugin.getTracker();
        if (tracker.isInvalid(villager)) return;

        // Update villager skin when changing job after 1 tick since this event is called before changing job.
        // Respawn NPC with the new profession texture.
        plugin.getServer().getScheduler().runTask(plugin, () -> tracker.refreshNPCSkin(villager, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(@NotNull EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Villager)) return;

        // Prevent iron golem attacking villagers (they might hit them by accident with a bow/crossbow).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (!(open.getHolder() instanceof InteractGUI interact)) continue;

            IVillagerNPC npc = interact.getNPC();
            if (npc != null && npc.bukkit().equals(villager)) player.closeInventory();
        }

        if (!Config.DROP_WHOLE_INVENTORY.asBool()) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear();

        addToDrops(drops, villager.getInventory().getContents());

        EntityEquipment equipment = villager.getEquipment();
        if (equipment != null) {
            addToDrops(drops, equipment.getItemInMainHand(), equipment.getItemInOffHand());
            addToDrops(drops, equipment.getArmorContents());
        }
    }

    private void addToDrops(List<ItemStack> drops, ItemStack @NotNull ... contents) {
        for (ItemStack item : contents) {
            if (item != null) drops.add(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(@NotNull EntityInteractEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;

        Villager villager = (Villager) event.getEntity();
        if (villager.getProfession() != Villager.Profession.FARMER) return;

        // Prevent farmer villager trampling farmlands.
        if (!plugin.getTracker().isInvalid(villager, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        Material type = event.getBlock().getType();
        if (!type.isAir() && type != Material.COMPOSTER) return;

        // Play swing hand animation when removing crop or using composter.
        if (event.getEntity() instanceof Villager villager) villager.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        preventChangeSkinItemUse(event, event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        preventChangeSkinItemUse(event, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        preventChangeSkinItemUse(event, event.getPlayerItem());
    }

    // Changed the priority to LOW to support VTL.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        ItemStack handItem = player.getInventory().getItem(event.getHand());
        preventChangeSkinItemUse(event, handItem);

        if (!(event.getRightClicked() instanceof Villager villager)) return;

        VillagerTracker tracker = plugin.getTracker();

        if (Config.DISABLE_INTERACTIONS.asBool()) return;
        if (tracker.isInvalid(villager, true)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }

        // Prevent opening villager inventory.
        event.setCancelled(true);

        Messages messages = plugin.getMessages();

        // Don't open GUI if using the whistle.
        ItemMeta meta;
        if (handItem != null && (meta = handItem.getItemMeta()) != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(plugin.getIsWhistleKey(), PersistentDataType.INTEGER)) return;
            if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
                handleChangeSkinItem(player, npc, handItem);
                return;
            }
            if (handItem.getType() == Material.NAME_TAG && meta.hasDisplayName()) {
                handleRename(event);
                return;
            }
        }

        // Prevent interacting with villager if it's fighting.
        if (npc.isFighting() || npc.isInsideRaid()) {
            messages.send(player, Messages.Message.INTERACT_FAIL_FIGHTING_OR_RAID);
            return;
        }

        if (npc.isProcreating()) {
            messages.send(player, Messages.Message.INTERACT_FAIL_PROCREATING);
            return;
        }


        if (isExpecting(player, npc, ExpectingType.GIFT)) return;
        if (isExpecting(player, npc, ExpectingType.BED)) return;

        if (npc.isInteracting()) {
            if (!npc.getInteractingWith().equals(player.getUniqueId())) {
                messages.send(player, Messages.Message.INTERACT_FAIL_INTERACTING);
            } else if (npc.isFollowing()) {
                messages.send(player, npc, Messages.Message.FOLLOW_ME_STOP);
                npc.stopInteracting();
            } else if (npc.isStayingInPlace()) {
                messages.send(player, npc, Messages.Message.STAY_HERE_STOP);
                npc.stopInteracting();
                npc.stopStayingInPlace();
            }
            // Otherwise, is in GUI so, do nothing.
            return;
        }

        if (villager.isTrading()) {
            messages.send(player, Messages.Message.INTERACT_FAIL_TRADING);
            return;
        }

        // Open custom GUI.
        plugin.getServer().getScheduler().runTask(plugin, () -> new MainGUI(plugin, npc, player));

        // Set interacting with id.
        npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
    }

    // All this is checked in the invoker method.
    @SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
    private void handleRename(@NotNull PlayerInteractEntityEvent event) {
        Villager villager = (Villager) event.getRightClicked();

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());

        IVillagerNPC npc = plugin.getConverter().getNPC(villager).get();

        // Prevent renaming villager, we'll do it ourself.
        event.setCancelled(true);

        if (plugin.getInventoryListeners().notAllowedToModifyInventoryOrName(player, npc, Config.WHO_CAN_MODIFY_VILLAGER_NAME)) {
            plugin.getMessages().send(player, Messages.Message.INTERACT_FAIL_RENAME_NOT_ALLOWED);
            return;
        }

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.length() < 3) return;

        npc.setVillagerName(name);
        plugin.getTracker().refreshNPCSkin(villager, false);

        player.getInventory().removeItem(new ItemBuilder(item.clone())
                .setAmount(1)
                .build());
    }

    private boolean isExpecting(Player player, @NotNull IVillagerNPC npc, ExpectingType checkType) {
        if (!npc.isExpecting()) return false;

        ExpectingType expecting = npc.getExpectingType();
        if (expecting != checkType) return false;

        Messages messages = plugin.getMessages();

        if (!npc.getExpectingFrom().equals(player.getUniqueId())) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_SOMEONE"));
            return true;
        }

        if (!player.isSneaking()) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_YOU"));
            return true;
        }

        messages.send(player, npc, Messages.Message.valueOf((expecting.isGift() ? "GIFT_EXPECTING" : "SET_HOME") + "_FAIL"));
        npc.stopExpecting();
        plugin.getCooldownManager().removeCooldown(player, checkType.name().toLowerCase());
        return true;
    }

    private void preventChangeSkinItemUse(Cancellable event, ItemStack item) {
        ItemMeta meta;
        if (item == null || (meta = item.getItemMeta()) == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private void handleChangeSkinItem(Player player, @NotNull IVillagerNPC npc, @NotNull ItemStack handItem) {
        ItemMeta meta = handItem.getItemMeta();
        Validate.notNull(meta);

        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        Villager villager = npc.bukkit();

        VillagerTracker.SkinRelatedData relatedData = tracker.getRelatedData(villager, "none");
        WrappedSignedProperty property = relatedData.property();

        if (property != null && property.getName().equals("error")) {
            messages.send(player, Messages.Message.SKIN_ERROR);
            plugin.getLogger().severe(property.getValue());
            return;
        }

        String skinData = meta.getPersistentDataContainer().get(plugin.getSkinDataKey(), PersistentDataType.STRING);
        if (skinData == null || skinData.isEmpty()) return;

        String[] data = skinData.split(":");
        if (data.length != 2) return;

        String sex = data[0];
        if (!sex.equalsIgnoreCase(npc.getSex())) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_SEX, string -> string.replace("%sex%", (sex.equals("male") ? Config.MALE : Config.FEMALE).asString()));
            return;
        }

        int id = Integer.parseInt(data[1]);
        if (id == npc.getSkinTextureId()) {
            messages.send(player, Messages.Message.SKIN_VILLAGER_SAME_SKIN);
            return;
        }

        boolean isAdult = villager.isAdult(), forBabies = relatedData.storage().getBoolean("none." + id + ".for-babies");
        if ((isAdult && forBabies) || (!isAdult && !forBabies)) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_AGE_STAGE, string -> string.replace("%age-stage%", (forBabies ? Config.KID : Config.ADULT).asString()));
            return;
        }

        // Here, we change the id of the villager, so then we can check if the skin exists.
        npc.setSkinTextureId(id);

        int skinId = tracker.getRelatedData(villager, "none", false).id();
        if (skinId == -1) {
            messages.send(player, Messages.Message.SKIN_TEXTURE_NOT_FOUND);
            return;
        }

        messages.send(player, Messages.Message.SKIN_DISGUISED, string -> string
                .replace("%id%", String.valueOf(skinId))
                .replace("%sex%", sex.equals("male") ? Config.MALE.asString() : Config.FEMALE.asString())
                .replace("%profession%", plugin.getProfessionFormatted(villager.getProfession()))
                .replace("%age-stage%", isAdult ? Config.ADULT.asString() : Config.KID.asString()));

        tracker.refreshNPCSkin(villager, false);

        player.getInventory().removeItem(new ItemBuilder(handItem.clone())
                .setAmount(1)
                .build());
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;
        if (npc.isFishing()) npc.toggleFishing();

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;

        if (byEntity.getDamager() instanceof Firework firework
                && firework.getShooter() instanceof Villager
                && !Config.VILLAGER_CROSSBOW_FIREWORK_DAMAGES_OTHER_VILLAGERS.asBool()) {
            event.setCancelled(true);
            return;
        }

        // Don't send messages if villager died.
        if (villager.getTarget() == null
                && byEntity.getDamager() instanceof Player player
                && villager.getHealth() - event.getFinalDamage() > 0.0d) {
            plugin.getMessages().send(player, npc, Messages.Message.ON_HIT);
        }

        if (!npc.isDamageSourceBlocked()) return;

        try {
            EntityDamageEvent.DamageModifier modifier = EntityDamageEvent.DamageModifier.BLOCKING;
            double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            ((Map<EntityDamageEvent.DamageModifier, Double>) MODIFIERS.invoke(event)).put(modifier, base);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}