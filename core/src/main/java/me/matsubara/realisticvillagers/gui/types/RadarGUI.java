package me.matsubara.realisticvillagers.gui.types;

import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageBuildings;
import me.matsubara.realisticvillagers.village.VillageManager;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The settlement map, for servers running the chest menus instead of the holograms.
 * <p>
 * The hologram radar is built from display entities floating in the world, which needs Minecraft
 * 1.19.4+ and the hologram menu switched on. Neither is a given, and until this existed a server
 * on the chest GUI simply had no map — the mayor's screen was a shell with a comment in it saying
 * the radar would hang off it one day.
 * <p>
 * A chest is a grid, so the map is drawn as one: each slot is a tile of the settlement, coloured
 * by what stands on it. It is coarser than the hologram version by a wide margin and makes no
 * apology for it — the point is that the same information is reachable, not that it looks the
 * same. Clicking a tile reports the building on it exactly as clicking the hologram map does.
 */
@Getter
public final class RadarGUI implements InventoryHolder {

    private final RealisticVillagers plugin;
    private final UUID villageId;
    private final Inventory inventory;

    /** Slot → the building drawn on it, so a click can report it. */
    private final Map<Integer, VillageBuildings.Footprint> buildingSlots = new HashMap<>();

    private static final int COLUMNS = 9;
    private static final int MAP_ROWS = 5;
    private static final int MAP_SLOTS = COLUMNS * MAP_ROWS;
    private static final int SIZE = MAP_SLOTS + COLUMNS;

    /** Control slots on the bottom row. */
    public static final int SLOT_ZOOM_OUT = MAP_SLOTS + 2;
    public static final int SLOT_LEGEND = MAP_SLOTS + 4;
    public static final int SLOT_ZOOM_IN = MAP_SLOTS + 6;
    public static final int SLOT_BACK = MAP_SLOTS + 8;

    /** How much of the settlement is shown, as a fraction of its full size. */
    private final double zoom;

    /** Closest the map goes: a fifth of the settlement fills the grid. */
    public static final double MIN_ZOOM = 0.2d;

    /** Furthest back it goes: twice the settlement, so its edge sits well inside the grid. */
    public static final double MAX_ZOOM = 2.0d;

    public static final double ZOOM_STEP = 0.2d;

    public RadarGUI(
            @NotNull RealisticVillagers plugin,
            @NotNull Village village,
            @NotNull VillageManager villages,
            @NotNull Player viewer,
            double zoom) {

        this.plugin = plugin;
        this.villageId = village.getId();

        // Allowed past 1.0 so the map can be pulled back beyond the settlement's edge. At exactly
        // 1.0 the grid stops where the village does, which leaves the boundary running along the
        // outside of the chest where there is nothing to draw it with — the edge is only visible
        // once there is ground outside it to contrast against.
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

        String title = text("gui.radar.title", "&8Map of %village%")
                .replace("%village%", village.getDisplayName());

        this.inventory = Bukkit.createInventory(this, SIZE, PluginUtils.translate(title));

        build(village, villages, viewer);
    }

    private void build(@NotNull Village village, @NotNull VillageManager villages, @NotNull Player viewer) {
        int defaultRadius = villages.getDefaultRadius();
        double shownX = Math.max(8.0d, village.getExtentX(defaultRadius) * zoom);
        double shownZ = Math.max(8.0d, village.getExtentZ(defaultRadius) * zoom);

        // One tile per slot, sized so the settlement's own rectangle fills the grid. Working the
        // other way — square tiles — would leave a long village drawn down one column with the
        // rest of the chest empty.
        double tileX = shownX * 2.0d / COLUMNS;
        double tileZ = shownZ * 2.0d / MAP_ROWS;

        List<VillageBuildings.Footprint> buildings = VillageBuildings.detect(villages, village);
        List<Villager> residents = villages.getResidents(village);

        for (int row = 0; row < MAP_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = row * COLUMNS + column;

                // Tile bounds in world blocks. North is -Z and the top row is north, so the row
                // runs the same way as Z; the hologram map flips the sign for the same reason.
                double minX = village.getCenterX() - shownX + column * tileX;
                double minZ = village.getCenterZ() - shownZ + row * tileZ;

                inventory.setItem(slot, tile(village, villages, viewer, buildings, residents,
                        minX, minZ, minX + tileX, minZ + tileZ, slot));
            }
        }

        controls(village, villages);
    }

    /**
     * What one tile of the map shows.
     * <p>
     * People win over buildings: a tile with the mayor standing on it says so, because who is
     * where changes minute to minute while a house does not.
     */
    private @NotNull ItemStack tile(
            @NotNull Village village,
            @NotNull VillageManager villages,
            @NotNull Player viewer,
            @NotNull List<VillageBuildings.Footprint> buildings,
            @NotNull List<Villager> residents,
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            int slot) {

        // Worked out first and registered whatever ends up drawn on top.
        //
        // Registering it only when the building is the thing displayed meant a house with anyone
        // standing in it could not be clicked at all — and a villager standing in a house is the
        // normal case, not the exception.
        VillageBuildings.Footprint building = null;
        for (VillageBuildings.Footprint candidate : buildings) {
            // Any overlap at all, not just a building centred on the tile: at this scale one tile
            // is several blocks across and a house rarely lines up with the grid.
            if (candidate.maxX() < minX || candidate.minX() >= maxX) continue;
            if (candidate.maxZ() < minZ || candidate.minZ() >= maxZ) continue;

            building = candidate;
            buildingSlots.put(slot, candidate);
            break;
        }

        Location eye = viewer.getLocation();
        if (eye.getX() >= minX && eye.getX() < maxX && eye.getZ() >= minZ && eye.getZ() < maxZ) {
            return new ItemBuilder(Material.MAGENTA_STAINED_GLASS_PANE)
                    .setDisplayName(text("gui.radar.you", "&dYou are here"))
                    .build();
        }

        for (Villager resident : residents) {
            Location at = resident.getLocation();
            if (at.getX() < minX || at.getX() >= maxX || at.getZ() < minZ || at.getZ() >= maxZ) continue;

            boolean isMayor = villages.isMayor(village, resident.getUniqueId());
            return residentHead(resident, isMayor, building);
        }

        if (building != null) return buildingTile(building);

        // Outside the settlement. Drawn differently from empty ground inside it, which is what
        // puts the village's edge on the map at all — the grid itself can't show a boundary that
        // runs along the outside of the chest.
        int defaultRadius = villages.getDefaultRadius();
        boolean inside = Math.abs((minX + maxX) / 2.0d - (village.getCenterX() + 0.5d)) <= village.getExtentX(defaultRadius)
                && Math.abs((minZ + maxZ) / 2.0d - (village.getCenterZ() + 0.5d)) <= village.getExtentZ(defaultRadius);

        return inside
                ? new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(" ").build()
                : new ItemBuilder(Material.BROWN_STAINED_GLASS_PANE)
                        .setDisplayName(text("gui.radar.outside", "&8Outside the settlement"))
                        .build();
    }

    private @NotNull ItemStack residentHead(
            @NotNull Villager resident,
            boolean isMayor,
            VillageBuildings.@Nullable Footprint standingIn) {

        String name = null;
        String texture = null;

        try {
            IVillagerNPC npc = plugin.getConverter().getNPC(resident).orElse(null);
            if (npc != null) name = npc.getVillagerName();
            texture = plugin.getNPCTextureURL(npc);
        } catch (Throwable ignored) {
            // A head with no skin still marks the spot.
        }

        ItemBuilder builder = texture != null && !texture.isEmpty()
                ? new ItemBuilder(Material.PLAYER_HEAD).setHead(texture, true)
                : new ItemBuilder(Material.PLAYER_HEAD);

        String label = isMayor
                ? text("gui.radar.mayor", "&a&lMAYOR &f%name%")
                : text("gui.radar.resident", "&f%name%");

        builder.setDisplayName(label.replace("%name%", name != null ? name : "Villager"));

        // Say what they are standing in, and that the tile is still the building's. Otherwise a
        // head looks like it replaced the house rather than being inside it.
        if (standingIn != null) {
            List<String> lore = new ArrayList<>();
            lore.add(text("gui.radar.inside-building", "&7Inside a building"));
            lore.add("");
            lore.add(text("gui.radar.building-outline", "&bClick to outline it in the world"));
            builder.setLore(lore);
        }

        return builder.build();
    }

    private @NotNull ItemStack buildingTile(VillageBuildings.@NotNull Footprint building) {
        Material material = switch (building.type()) {
            case RESIDENTIAL -> Material.WHITE_STAINED_GLASS_PANE;
            case WORKPLACE -> Material.ORANGE_STAINED_GLASS_PANE;
            case MIXED -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case UNKNOWN -> Material.GRAY_STAINED_GLASS_PANE;
        };

        String kind = switch (building.type()) {
            case RESIDENTIAL -> text("gui.radar.type-residential", "&aResidential");
            case WORKPLACE -> text("gui.radar.type-workplace", "&6Workplace");
            case MIXED -> text("gui.radar.type-mixed", "&bHome & workshop");
            case UNKNOWN -> text("gui.radar.type-unknown", "&7Unknown");
        };

        List<String> lore = new ArrayList<>();
        lore.add(text("gui.radar.building-kind", "&7Type: &f%kind%").replace("%kind%", kind));
        lore.add(text("gui.radar.building-box", "&7Size: &f%width%x%depth%x%height% &7blocks at &f%x%, %y%, %z%")
                .replace("%width%", String.valueOf(building.width()))
                .replace("%depth%", String.valueOf(building.depth()))
                .replace("%height%", String.valueOf(building.height()))
                .replace("%x%", String.valueOf((int) building.centerX()))
                .replace("%y%", String.valueOf(building.minY()))
                .replace("%z%", String.valueOf((int) building.centerZ())));

        if (building.beds() > 0) {
            lore.add(text("gui.radar.building-beds", "&7Sleeps: &f%count%")
                    .replace("%count%", String.valueOf(building.beds())));
        }
        if (!building.professions().isEmpty()) {
            lore.add(text("gui.radar.building-work", "&7Work: &f%trades%")
                    .replace("%trades%", String.join(", ", building.professions())));
        }

        lore.add("");
        lore.add(text("gui.radar.building-outline", "&bClick to outline it in the world"));

        return new ItemBuilder(material)
                .setDisplayName(text("gui.radar.building-name", "&eBuilding"))
                .setLore(lore)
                .build();
    }

    private void controls(@NotNull Village village, @NotNull VillageManager villages) {
        inventory.setItem(SLOT_ZOOM_OUT, new ItemBuilder(Material.SPYGLASS)
                .setDisplayName(text("gui.radar.zoom-out", "&e« Zoom out"))
                .build());

        inventory.setItem(SLOT_ZOOM_IN, new ItemBuilder(Material.SPYGLASS)
                .setDisplayName(text("gui.radar.zoom-in", "&eZoom in »"))
                .build());

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .setDisplayName(text("gui.radar.back", "&c« Back"))
                .build());

        int defaultRadius = villages.getDefaultRadius();

        List<String> legend = new ArrayList<>();
        legend.add(text("gui.radar.legend-size", "&7Settlement: &f%x% x %z% &7blocks")
                .replace("%x%", String.valueOf(village.getExtentX(defaultRadius) * 2))
                .replace("%z%", String.valueOf(village.getExtentZ(defaultRadius) * 2)));
        legend.add(text("gui.radar.legend-zoom", "&7Showing: &f%percent%% &7of the settlement")
                .replace("%percent%", String.valueOf((int) Math.round(zoom * 100.0d))));
        legend.add("");
        legend.add(text("gui.radar.legend-outside", "&8Brown: outside the settlement"));
        legend.add("");
        legend.add(text("gui.radar.type-residential", "&aResidential"));
        legend.add(text("gui.radar.type-workplace", "&6Workplace"));
        legend.add(text("gui.radar.type-mixed", "&bHome & workshop"));
        legend.add(text("gui.radar.type-unknown", "&7Unknown"));

        inventory.setItem(SLOT_LEGEND, new ItemBuilder(Material.FILLED_MAP)
                .setDisplayName(text("gui.radar.legend", "&eMap key"))
                .setLore(legend)
                .build());
    }

    /** The building drawn on this slot, or {@code null} for anything else. */
    public VillageBuildings.@Nullable Footprint getBuildingAt(int slot) {
        return buildingSlots.get(slot);
    }

    private @NotNull String text(String path, String fallback) {
        String value = plugin.getGuiConfig() == null ? null : plugin.getGuiConfig().getString(path);
        return PluginUtils.translate(value != null && !value.isEmpty() ? value : fallback);
    }
}
