package me.matsubara.realisticvillagers.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Nameable;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.types.SkinGUI;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.BrewingStand;
import org.bukkit.block.data.type.Grindstone;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class NametagManager implements Listener {

    private final RealisticVillagers plugin;

    private static final org.bukkit.Color NAMETAG_BACKGROUND_COLOR = org.bukkit.Color.fromARGB((int) (0.35 * 255), 0, 0, 0);
    private static final Set<Material> POTIONS = Set.of(Material.POTION, Material.LINGERING_POTION, Material.SPLASH_POTION);

    public NametagManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void remove(IVillagerNPC npc) {
        if (!(npc instanceof Nameable nameable)) return;
        invalidate(nameable, nameable.getNametagEntity(), temp -> temp.setNametagEntity(null));
        invalidate(nameable, nameable.getNametagItemEntity(), temp -> temp.setNametagItemEntity(null));
    }

    public void showNametag(IVillagerNPC npc, Player player) {
        if (!(npc instanceof Nameable nameable)) return;
        spawnIfNeeded(npc, player.getWorld());

        TextDisplay nametagEntity = nameable.getNametagEntity();
        if (nametagEntity != null && !player.canSee(nametagEntity)) {
            player.showEntity(plugin, nametagEntity);
        }

        BlockDisplay nametagItemEntity = nameable.getNametagItemEntity();
        if (nametagItemEntity != null && !player.canSee(nametagItemEntity))
            player.showEntity(plugin, nametagItemEntity);

        LivingEntity bukkit = npc.bukkit();

        List<Entity> passengers = bukkit.getPassengers();
        if (passengers.isEmpty()) return;

        int[] ids = passengers.stream().mapToInt(Entity::getEntityId).toArray();

        PacketContainer mount = new PacketContainer(PacketType.Play.Server.MOUNT);
        mount.getIntegers().write(0, bukkit.getEntityId());
        mount.getIntegerArrays().write(0, ids);

        ProtocolLibrary.getProtocolManager().sendServerPacket(player, mount);
    }

    public List<String> getLines(@NotNull LivingEntity entity) {
        EntityType type = entity.getType();
        if (type == EntityType.VILLAGER) return Config.CUSTOM_NAME_VILLAGER_LINES.asStringList();
        if (type == EntityType.WANDERING_TRADER) return Config.CUSTOM_NAME_TRADER_LINES.asStringList();
        return Collections.emptyList();
    }

    private void spawnIfNeeded(IVillagerNPC npc, World world) {
        if (!(npc instanceof Nameable nameable) || world == null) return;
        if (Config.DISABLE_NAMETAGS.asBool()) return;

        LivingEntity bukkit = npc.bukkit();
        if (bukkit == null) return;

        Location at = bukkit.getLocation();
        at.setPitch(0.0f);

        TextDisplay nametagEntity = nameable.getNametagEntity();
        if (!getLines(bukkit).isEmpty()
                && !npc.bukkit().hasPotionEffect(PotionEffectType.INVISIBILITY)
                && (nametagEntity == null || !nametagEntity.isValid())) {
            if (nametagEntity != null) invalidate(nametagEntity);
            nameable.setNametagEntity(nametagEntity = world.spawn(at, TextDisplay.class, display -> handleNametag(npc, display, true)));
            bukkit.addPassenger(nametagEntity);
        }

        BlockDisplay nametagItemEntity = nameable.getNametagItemEntity();
        if (bukkit.getType() == EntityType.VILLAGER
                && nametagEntity != null && Config.CUSTOM_NAME_SHOW_JOB_BLOCK.asBool()
                && (nametagItemEntity == null || !nametagItemEntity.isValid())) {
            if (nametagItemEntity != null) invalidate(nametagItemEntity);
            nameable.setNametagItemEntity(nametagItemEntity = world.spawn(at, BlockDisplay.class, display -> handleNametagItem(npc, display, true, true, null)));
            bukkit.addPassenger(nametagItemEntity);
        }
    }

    public void hideNametag(IVillagerNPC npc, Player player) {
        if (!(npc instanceof Nameable nameable)) return;

        TextDisplay nametagEntity = nameable.getNametagEntity();
        if (nametagEntity != null && player.canSee(nametagEntity)) player.hideEntity(plugin, nametagEntity);

        BlockDisplay nametagItemEntity = nameable.getNametagItemEntity();
        if (nametagItemEntity != null && player.canSee(nametagItemEntity)) player.hideEntity(plugin, nametagItemEntity);
    }

    public void resetNametag(IVillagerNPC npc, @Nullable Consumer<BlockData> dataFunction, boolean reset) {
        if (!(npc instanceof Nameable nameable)) return;
        spawnIfNeeded(npc, npc.bukkit().getWorld());

        TextDisplay nametagEntity = nameable.getNametagEntity();

        boolean nametagInvalidated = nametagEntity != null && getLines(npc.bukkit()).isEmpty();
        if (nametagInvalidated) {
            invalidate(nameable, nametagEntity, temp -> temp.setNametagEntity(null));
        }
        handleNametag(npc, nametagEntity, false);

        BlockDisplay nametagItemEntity = nameable.getNametagItemEntity();
        if (nametagInvalidated || (nametagItemEntity != null && !Config.CUSTOM_NAME_SHOW_JOB_BLOCK.asBool())) {
            invalidate(nameable, nametagItemEntity, temp -> temp.setNametagItemEntity(null));
        }
        handleNametagItem(npc, nametagItemEntity, false, reset, dataFunction);
    }

    private <T extends Display> void invalidate(T entity) {
        invalidate(null, entity, null);
    }

    private <T extends Display> void invalidate(@Nullable Nameable nameable, T entity, @Nullable Consumer<Nameable> invalidateFunction) {
        if (entity != null) {
            entity.leaveVehicle();
            entity.remove();
        }

        if (nameable != null && invalidateFunction != null) {
            invalidateFunction.accept(nameable);
        }
    }

    @SuppressWarnings("deprecation")
    private void handleNametag(IVillagerNPC npc, TextDisplay display, boolean transform) {
        if (display == null) return;

        LivingEntity bukkit = npc.bukkit();
        StringBuilder builder = new StringBuilder();
        List<String> lines = getLines(bukkit);

        for (int i = 0; i < lines.size(); i++) {
            String line = PluginUtils.translate(lines.get(i).replace("%villager-name%", npc.getVillagerName()));

            builder.append(bukkit instanceof Villager villager ? line
                    .replace("%villager-name%", npc.getVillagerName())
                    .replace("%level%", String.valueOf(villager.getVillagerLevel()))
                    .replace("%profession%", plugin.getProfessionFormatted(villager.getProfession().name().toLowerCase())) : line);

            if (i != lines.size() - 1) builder.append("\n");
        }

        display.setText(builder.toString());
        display.setDefaultBackground(false);
        display.setBackgroundColor(NAMETAG_BACKGROUND_COLOR);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(false);
        display.setSeeThrough(true);

        display.setBillboard(Display.Billboard.CENTER);

        if (transform) {
            Transformation transformation = display.getTransformation();
            transformation.getTranslation().add(0.0f, 0.25f, 0.0f);

            display.setTransformation(transformation);
        }

        display.setPersistent(false);
    }

    private void handleNametagItem(IVillagerNPC npc, BlockDisplay display, boolean transform, boolean reset, @Nullable Consumer<BlockData> dataFunction) {
        if (display == null || !(npc instanceof Nameable nameable)) return;

        LivingEntity living = npc.bukkit();

        display.setBillboard(Display.Billboard.VERTICAL);

        if (!npc.is(Villager.Profession.NONE, Villager.Profession.NITWIT)) {
            BlockData previousData = display.getBlock();
            boolean previousValid = !previousData.getMaterial().isAir() && !reset;

            Material material = SkinGUI.PROFESSION_ICON.get(((Villager) living).getProfession().name());
            BlockData data = previousValid ? previousData : createBlockData(living, material);

            if (dataFunction != null && previousValid) {
                dataFunction.accept(data);
            }

            if (data instanceof Grindstone grindstone) {
                grindstone.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            }

            if (data instanceof Directional directional) {
                directional.setFacing(BlockFace.SOUTH);
            }

            display.setBlock(data);
        } else {
            display.setBlock(Material.AIR.createBlockData());
        }

        int amountOfLines = getLines(npc.bukkit()).size();
        if (transform || nameable.getCurrentAmountOfLines() != amountOfLines) {
            nameable.setCurrentAmountOfLines(amountOfLines);

            Transformation transformation = display.getTransformation();

            float y = amountOfLines * 0.275f + 0.275f;
            transformation.getTranslation().set(0.0f).add(-0.1f, y, -0.1f);

            if (transform) transformation.getScale().mul(0.25f);

            display.setTransformation(transformation);
        }

        display.setPersistent(false);
    }

    private @NotNull BlockData createBlockData(LivingEntity living, Material material) {
        BlockData data = getJobBlockData(living);
        return data != null ? data : material.createBlockData();
    }

    private @Nullable BlockData getJobBlockData(@NotNull LivingEntity living) {
        if (!(living instanceof Villager)) return null;

        Location pos = living.getMemory(MemoryKey.JOB_SITE);
        if (pos == null) return null;

        World world = pos.getWorld();
        if (world == null) return null;

        return world.getBlockData(pos);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        handleBarrel(event, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        handleBarrel(event, false);
    }

    private void handleBarrel(@NotNull InventoryEvent event, boolean open) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.BARREL) return;

        Location location = inventory.getLocation();
        if (location == null) return;

        resetVillagerNametags(location.getBlock(), data -> {
            if (!(data instanceof Openable openable)) return;
            openable.setOpen(open || event.getViewers().size() != 1);
        }, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceBurn(@NotNull FurnaceBurnEvent event) { // Reset furnace icon.
        handleFurnace(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(@NotNull FurnaceSmeltEvent event) { // Reset furnace icon.
        Block block = event.getBlock();

        Material type = block.getType();
        if (type != Material.BLAST_FURNACE && type != Material.SMOKER) return;

        if (!(block.getState() instanceof Furnace furnace)) return;

        int fuelLeft = furnace.getBurnTime() / 20;
        if (fuelLeft != 0) return;

        ItemStack fuel = furnace.getInventory().getFuel();
        if (fuel != null) return;

        handleFurnace(block, false);
    }

    private void handleFurnace(@NotNull Block block, boolean lit) {
        Material type = block.getType();
        if (type != Material.BLAST_FURNACE && type != Material.SMOKER) return;

        resetVillagerNametags(block, data -> {
            if (!(data instanceof Lightable lightable)) return;
            lightable.setLit(lit);
        }, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCauldronLevelChange(@NotNull CauldronLevelChangeEvent event) { // Reset cauldron icon.
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> resetVillagerNametags(block, null, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) { // Reset composter icon.
        Block block = event.getBlock();
        if (block.getType() != Material.COMPOSTER) return;

        if (!(event.getBlockData() instanceof Levelled levelled)) return;

        handleComposter(block, levelled.getLevel());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COMPOSTER) return;

        if (!(block.getBlockData() instanceof Levelled levelled)) return;
        if (levelled.getLevel() != levelled.getMaximumLevel()) return;

        handleComposter(block, 0);
    }

    private void handleComposter(Block block, int level) {
        resetVillagerNametags(block, data -> {
            if (!(data instanceof Levelled temp)) return;
            temp.setLevel(level);
        }, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(@NotNull InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        if (event.getSource().getType() != InventoryType.HOPPER
                || destination.getType() != InventoryType.BREWING) return;

        if (!POTIONS.contains(event.getItem().getType())) return;

        if (!(destination instanceof BrewerInventory brewer)) return;

        Location location = destination.getLocation();
        if (location == null) return;

        handleBrewingSlot(brewer, location.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.BREWING) return;

        Set<Integer> rawSlots = event.getRawSlots();
        if (rawSlots.size() != 1) return;

        // We only care about the bottles slots.
        int slot = rawSlots.iterator().next();
        if (slot < 0 || slot > 2) return;

        Location location = inventory.getLocation();
        if (location == null) return;

        ItemStack item = event.getNewItems().get(slot);
        if (item == null) return;

        handleHasBrewing(location.getBlock(), slot);
    }

    private void handleHasBrewing(Block block, int slot) {
        resetVillagerNametags(block, data -> {
            if (!(data instanceof BrewingStand brewing)) return;
            brewing.setBottle(slot, true);
        }, false);
    }

    private void handlePlayerToBrewingShifting(@NotNull InventoryClickEvent event) {
        if (!event.getClick().isShiftClick()) return;

        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.BREWING) return;

        Location location = top.getLocation();
        if (location == null) return;

        ItemStack current = event.getCurrentItem();
        if (current == null
                || !POTIONS.contains(current.getType())
                || !(top instanceof BrewerInventory brewer)) return;

        if (current.getAmount() != 1) return;

        handleBrewingSlot(brewer, location.getBlock());
    }

    private void handleBrewingSlot(BrewerInventory brewer, Block block) {
        for (int slot = 0; slot < 3; slot++) {
            ItemStack item = brewer.getItem(slot);
            if (item != null && !item.getType().isAir()) continue;

            handleHasBrewing(block, slot);
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) { // Reset brewing stand icon.
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;
        if (inventory.getType() != InventoryType.BREWING) {
            handlePlayerToBrewingShifting(event);
            return;
        }

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        if (click == ClickType.MIDDLE) return;
        if (click == ClickType.DOUBLE_CLICK) return;
        if (action == InventoryAction.SWAP_WITH_CURSOR) return;

        // We only care about the bottles slots.
        int slot = event.getRawSlot();
        if (slot < 0 || slot > 2) return;

        // Real brewing only.
        Location location = inventory.getLocation();
        if (location == null) return;

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        ItemStack hotbarItem;
        if (click == ClickType.NUMBER_KEY
                && action == InventoryAction.HOTBAR_MOVE_AND_READD
                && (currentItem != null && POTIONS.contains(currentItem.getType()))
                && (hotbarItem = player.getInventory().getItem(event.getHotbarButton())) != null
                && !hotbarItem.getType().isAir()) {
            return;
        }

        // Ignore empty clicks.
        if ((currentItem == null || currentItem.getType().isAir())
                && (cursor == null || cursor.getType().isAir())) {

            int hotbar = event.getHotbarButton();
            if (hotbar != -1) {
                ItemStack item = player.getInventory().getItem(hotbar);
                if (item != null && POTIONS.contains(item.getType())) {
                    handleHasBrewing(location.getBlock(), slot);
                }
            }

            return;
        }

        if ((cursor == null || cursor.getType().isAir())
                && click.isShiftClick()
                && action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && player.getInventory().firstEmpty() == -1) {
            return;
        }

        Block block = location.getBlock();

        resetVillagerNametags(block, data -> {
            if (!(data instanceof BrewingStand brewing)) return;

            if (currentItem == null || currentItem.getType().isAir()) {
                brewing.setBottle(slot, true);
                return;
            }

            if (cursor == null || cursor.getType().isAir()) {
                brewing.setBottle(slot, false);
            }
        }, false);
    }

    private void resetVillagerNametags(Block block, @Nullable Consumer<BlockData> dataFunction, boolean reset) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
                if (jobSite == null || !jobSite.equals(block.getLocation())) continue;

                plugin.getConverter().getNPC(villager).ifPresent(npc -> resetNametag(npc, dataFunction, reset));
            }
        }
    }
}