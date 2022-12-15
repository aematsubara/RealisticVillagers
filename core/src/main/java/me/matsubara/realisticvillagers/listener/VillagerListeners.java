package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.util.Reflection;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class VillagerListeners implements Listener {

    private final RealisticVillagers plugin;

    private static final MethodHandle MODIFIERS = Reflection.getFieldGetter(EntityDamageEvent.class, "modifiers");

    public VillagerListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(GenericGameEvent event) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        if (plugin.getTracker().isInvalid(villager)) return;

        // Update villager skin when changing job after 1 tick since this event is called before changing job.
        plugin.getServer().getScheduler().runTask(plugin, () -> {

            plugin.getTracker().refreshNPC(villager);

            // Give fishing rod.
            if (event.getProfession() == Villager.Profession.FISHERMAN) {
                EntityEquipment equipment = villager.getEquipment();
                if (equipment == null) return;

                if (ThreadLocalRandom.current().nextFloat() >= Config.FISHING_ROD_CHANCE.asFloat()) return;

                ItemStack previous = equipment.getItemInMainHand();
                if (!previous.getType().isAir()) villager.getInventory().addItem(previous);

                equipment.setItemInMainHand(new ItemStack(Material.FISHING_ROD));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Villager)) return;

        // Prevent iron golem attacking villagers (they might hit them by accident with a bow/crossbow).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
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

    private void addToDrops(List<ItemStack> drops, ItemStack... contents) {
        for (ItemStack item : contents) {
            if (item != null) drops.add(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
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
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Material type = event.getBlock().getType();
        if (!type.isAir() && type != Material.COMPOSTER) return;

        // Play swing hand animation when removing crop or using composter.
        if (event.getEntity() instanceof Villager villager) villager.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        if (Config.DISABLE_INTERACTIONS.asBool()) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }

        // Prevent opening villager inventory.
        event.setCancelled(true);

        Player player = event.getPlayer();
        Messages messages = plugin.getMessages();

        // Don't open GUI if using the whistle.
        ItemStack item;
        ItemMeta meta;
        if ((item = player.getInventory().getItem(event.getHand())) != null
                && (meta = item.getItemMeta()) != null) {
            if (meta.getPersistentDataContainer().has(plugin.getIsWhistleKey(), PersistentDataType.INTEGER)) return;
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

        if (npc.isExpectingGift()) {
            if (npc.isExpectingGiftFrom(player.getUniqueId())) {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_YOU);
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_SOMEONE);
            }
            return;
        }

        if (npc.isExpectingBed()) {
            if (npc.isExpectingBedFrom(player.getUniqueId())) {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_YOU);
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_SOMEONE);
            }
            return;
        }

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
        new MainGUI(plugin, npc, player);

        // Set interacting with id.
        npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

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

        if (villager.getTarget() == null && byEntity.getDamager() instanceof Player player) {
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