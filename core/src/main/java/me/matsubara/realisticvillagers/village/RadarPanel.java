package me.matsubara.realisticvillagers.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The settlement map, drawn in the world as a flat panel facing the player.
 * <p>
 * Built from <b>shapes, not pixels</b>: each building is an outlined rectangle sized to its
 * real footprint. Outlines rather than solid blocks because a filled square reads as a blob at
 * this scale, while an outline still reads as a building when it's only centimetres wide.
 * <p>
 * Once drawn, the panel is <b>moved, never rebuilt</b>. Every part remembers where it sits in
 * panel-local space, so following the player is a teleport per tick — the same thing the
 * interaction menu does. Respawning the entities instead would make the whole map blink.
 * <p>
 * Requires Minecraft 1.19.4+ for display entities.
 */
public final class RadarPanel {

    /** One drawn element, and where it lives on the panel. */
    private static final class Part {

        private final Entity entity;
        private final double layer;
        private final double width;
        private final double height;

        /** The part's own scale, so showing it again restores exactly what it was. */
        private final Vector3f scale;

        private double x;
        private double y;

        /** Set when this part tracks a villager, so its spot is recomputed as they walk. */
        private final @Nullable Villager source;

        /** True for the marker that tracks the player looking at the map. */
        private final boolean tracksViewer;

        /** Where this part sits relative to the thing it tracks, in panel-local metres. */
        private double trackOffsetX;
        private double trackOffsetY;

        /** Set when this part is one of a building's outline bars, so it can be recoloured. */
        private @Nullable VillageBuildings.Footprint building;

        private boolean hidden;

        private Part(Entity entity, double layer, double x, double y, double width, double height,
                     @NotNull Vector3f scale, @Nullable Villager source, boolean tracksViewer) {
            this.entity = entity;
            this.scale = scale;
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.source = source;
            this.tracksViewer = tracksViewer;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();

    // The plane the panel currently occupies, kept for hit testing and repositioning.
    private @Nullable Location planeAnchor;
    private @Nullable Vector planeRight;
    private @Nullable Vector planeUp;
    private @Nullable Vector planeLook;
    private double planeSize;

    // Map projection, kept so markers can be re-projected as their villagers move.
    private @Nullable Location villageCenter;
    private double blocksToPanel;

    /** A building as drawn: its footprint plus its rectangle in panel-local metres. */
    public record Marker(VillageBuildings.Footprint footprint, double x, double y, double width, double height) {

        boolean contains(double u, double v) {
            return Math.abs(u - x) <= width / 2.0d && Math.abs(v - y) <= height / 2.0d;
        }
    }

    /**
     * Depth between layers.
     * <p>
     * Must be larger than {@link #THICKNESS}: at 0.006 against plates 0.02 thick, every layer
     * sat physically inside the one behind it, so which surface won depended on the viewing
     * angle — buildings vanished into the background the moment the player crouched or moved.
     */
    private static final double LAYER_STEP = 0.035d;

    private static final float THICKNESS = 0.02f;
    private static final int MAX_MARKERS = 60;

    /**
     * Fraction of a layer the uprights of an outline sit in front of its crossbars.
     * <p>
     * Just enough that the overlap at each corner has a clear winner, and far short of a whole
     * layer so nothing crosses into the one in front.
     */
    private static final double CORNER_NUDGE = 0.35d;

    /**
     * How much bigger a head is drawn than the frame around it.
     * <p>
     * An item display's model does not fill the box it is scaled into — there is padding around
     * it — so a head and a frame given the same size come out with the frame visibly larger.
     * Oversizing the head by this much makes it meet the frame's edges.
     */
    private static final double HEAD_FILL = 1.18d;

    public record Colors(
            Material border,
            Material boundary,
            Material background,
            Material building,
            Material residential,
            Material workplace,
            Material mixed,
            Material buildingHighlight,
            Material buildingSelected,
            Material resident,
            Material mayor,
            Material viewer) {

        public static final Colors DEFAULT = new Colors(
                // Blue frame, so the map's own edge is never mistaken for the village boundary
                // drawn just inside it.
                Material.BLUE_CONCRETE,
                Material.YELLOW_CONCRETE,
                Material.BLACK_CONCRETE,
                // Unknown buildings are grey so they read differently from homes.
                Material.GRAY_CONCRETE,
                Material.WHITE_CONCRETE,
                Material.ORANGE_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE,
                Material.YELLOW_CONCRETE,
                Material.LIME_CONCRETE,
                Material.LIGHT_GRAY_CONCRETE,
                Material.LIME_CONCRETE,
                Material.MAGENTA_CONCRETE);
    }

    /**
     * Builds the panel from scratch. Only call this when the map's <i>content</i> changes —
     * zoom, filters or selection. For movement use {@link #reposition}.
     */
    public void draw(
            @NotNull Player viewer,
            @NotNull Location anchor,
            @NotNull Village village,
            @NotNull VillageManager villages,
            boolean showResidents,
            boolean showMayor,
            double panelSize,
            double zoom,
            @NotNull Colors colors,
            VillageBuildings.@Nullable Footprint highlighted,
            @Nullable HeadFactory heads) {

        remove();
        this.heads = heads;

        World world = anchor.getWorld();
        Location center = village.getCenter();
        if (world == null || center == null) return;

        updatePlane(viewer, anchor, panelSize);

        int extentX = village.getExtentX(villages.getDefaultRadius());
        int extentZ = village.getExtentZ(villages.getDefaultRadius());

        // The map is square, so it is scaled by whichever side of the village is longer; the
        // boundary below is then drawn at each side's own size, which is what makes an oblong
        // settlement read as an oblong instead of being squashed into the panel.
        int radius = Math.max(extentX, extentZ);

        // Zoom shrinks the area covered rather than the panel, so zooming in magnifies the
        // middle of the village instead of making the map smaller.
        double shown = Math.max(8.0d, radius * Math.max(0.1d, zoom));

        villageCenter = center.clone();
        blocksToPanel = panelSize / (shown * 2.0d);

        double frame = panelSize * 0.02d;
        double outline = Math.max(panelSize * 0.006d, 0.012d);

        // Layer 0: frame, a slightly larger rectangle peeking out behind the map.
        rect(world, 0.0d, 0.0d, panelSize + frame * 2.0d, panelSize + frame * 2.0d, colors.border(), 0, null, false);

        // Layer 1: the map itself.
        rect(world, 0.0d, 0.0d, panelSize, panelSize, colors.background(), 1, null, false);

        // Layer 1 also carries the settlement boundary. When zoomed out the village no longer
        // fills the panel, so the frame stops standing in for its edge and the border has to be
        // drawn where it actually falls.
        // Always drawn; clipping keeps it inside the map when zoomed in past the village edge,
        // which is tidier than the old "only draw it when it fits" rule that made the boundary
        // pop in and out as the zoom changed.
        // Its own layer: sharing layer 1 with the background put both at the same depth and the
        // outline flickered as the panel swung around.
        outlineRect(world, 0.0d, 0.0d,
                extentX * blocksToPanel * 2.0d,
                extentZ * blocksToPanel * 2.0d,
                outline, colors.boundary(), 2);

        // Layer 2: buildings, each outlined at its true size and place.
        for (VillageBuildings.Footprint building : VillageBuildings.detect(villages, village)) {
            double x = (building.centerX() - center.getX()) * blocksToPanel;
            // Panel "up" is north, and north is -Z, so the sign flips here.
            double y = -(building.centerZ() - center.getZ()) * blocksToPanel;

            double width = Math.max(panelSize * 0.03d, building.width() * blocksToPanel);
            double height = Math.max(panelSize * 0.03d, building.depth() * blocksToPanel);

            // Keep anything that overlaps the map at all, not just what's centred on it —
            // clipping trims the overhang, so a building at the edge still shows its near side.
            if (!overlapsPanel(x, y, width, height)) continue;

            int before = parts.size();
            outlineRect(world, x, y, width, height, outline, colorFor(building, colors), 3);

            // Tag the bars that were just added so hovering can recolour them in place.
            for (int i = before; i < parts.size(); i++) {
                parts.get(i).building = building;
            }

            markers.add(new Marker(building, x, y, width, height));
        }

        double dot = panelSize * 0.03d;

        // Layer 3: people, drawn as their own heads so you can tell who is who at a glance.
        // The mayor and the player get a coloured ring behind the head to stand out.
        if (showResidents || showMayor) {
            int drawn = 0;
            for (Villager resident : villages.getResidents(village)) {
                if (drawn >= MAX_MARKERS) break;

                boolean isMayor = villages.isMayor(village, resident.getUniqueId());
                if (isMayor && !showMayor) continue;
                if (!isMayor && !showResidents) continue;

                double[] spot = project(resident.getLocation());

                // Heads read as icons, so they need to be a little bigger than a plain dot, and
                // the ring only a little larger than the head — a wide ring around a tiny head
                // just looks like a coloured square with something lost in the middle.
                //
                // The mayor is barely larger than anyone else: the green ring is what marks the
                // office, so size on top of it only crowded the map without saying anything new.
                double size = isMayor ? dot * 2.0d : dot * 1.8d;

                if (isMayor) {
                    ring(world, spot[0], spot[1], size, colors.mayor(), 4, resident);
                }
                head(world, spot[0], spot[1], size * HEAD_FILL, resolveHead(resident), 5, resident, false);
                drawn++;
            }
        }

        // Layer 5: the bell, as the real item so the centre of the village is unmistakable.
        // The bell is drawn where it actually stands, not at the village centre — the two are
        // no longer the same thing, and a bell icon pinned to the middle told a lie about it.
        double[] bell = project(new Location(world, village.getBellX(), village.getBellY(), village.getBellZ()));
        if (!outside(bell[0], bell[1])) {
            item(world, bell[0], bell[1], panelSize * 0.06d, Material.BELL, 6);
        }

        // Layer 6: the player, on top so nothing can hide it, ringed in its own colour.
        double[] spot = project(viewer.getLocation());
        ring(world, spot[0], spot[1], dot * 2.0d, colors.viewer(), 7, null);
        head(world, spot[0], spot[1], dot * 2.0d * HEAD_FILL, playerHead(viewer), 8, null, true);
    }

    /**
     * Moves the existing panel to follow the player, without rebuilding it.
     * <p>
     * Re-aims the plane, then teleports every part. Markers are re-projected first so villagers
     * glide across the map instead of jumping when the panel is next rebuilt.
     */
    public void reposition(@NotNull Player viewer, @NotNull Location anchor, double panelSize) {
        if (parts.isEmpty()) return;

        updatePlane(viewer, anchor, panelSize);

        for (Part part : parts) {
            if (part.entity.isDead()) continue;

            if (part.source != null) {
                if (!part.source.isValid()) {
                    hide(part, true);
                    continue;
                }
                double[] spot = project(part.source.getLocation());
                part.x = spot[0] + part.trackOffsetX;
                part.y = spot[1] + part.trackOffsetY;
            } else if (part.tracksViewer) {
                double[] spot = project(viewer.getLocation());
                part.x = spot[0] + part.trackOffsetX;
                part.y = spot[1] + part.trackOffsetY;
            }

            boolean shouldHide = (part.source != null || part.tracksViewer) && outside(part.x, part.y);
            hide(part, shouldHide);
            if (shouldHide) continue;

            try {
                part.entity.teleport(placed(part.x, part.y, part.layer));
            } catch (Throwable ignored) {
                // A part that can't be moved simply stays where it was.
            }
        }
    }

    /**
     * Hides or shows a part by collapsing its scale.
     * <p>
     * Display entities have no visibility flag, and removing the entity would mean respawning
     * it later — which is exactly the blink this class avoids.
     */
    private void hide(@NotNull Part part, boolean hidden) {
        if (part.hidden == hidden) return;
        part.hidden = hidden;

        try {
            Transformation current = ((Display) part.entity).getTransformation();
            ((Display) part.entity).setTransformation(new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    hidden ? new Vector3f(0.0f, 0.0f, 0.0f) : new Vector3f(part.scale),
                    current.getRightRotation()));
        } catch (Throwable ignored) {
            // Not fatal; the marker just keeps its previous look.
        }
    }

    private void updatePlane(@NotNull Player viewer, @NotNull Location anchor, double panelSize) {
        Location facing = anchor.clone();
        facing.setDirection(viewer.getEyeLocation().toVector().subtract(anchor.toVector()));

        Vector look = facing.getDirection().normalize();

        Vector right = look.clone().crossProduct(new Vector(0.0d, 1.0d, 0.0d));
        if (right.lengthSquared() < 1.0e-6d) {
            right = new Vector(-Math.cos(Math.toRadians(facing.getYaw())), 0.0d, -Math.sin(Math.toRadians(facing.getYaw())));
        }
        right.normalize();

        planeAnchor = anchor.clone();
        planeAnchor.setYaw(facing.getYaw());
        planeAnchor.setPitch(facing.getPitch());
        planeRight = right;
        planeUp = right.clone().crossProduct(look).normalize();
        planeLook = look;
        planeSize = panelSize;
    }

    /** Maps a world location onto panel-local metres. */
    private double[] project(@NotNull Location at) {
        if (villageCenter == null) return new double[]{0.0d, 0.0d};

        return new double[]{
                (at.getX() - villageCenter.getX()) * blocksToPanel,
                -(at.getZ() - villageCenter.getZ()) * blocksToPanel};
    }

    /** Base colour for a building, by what it is used for. */
    private static @NotNull Material colorFor(VillageBuildings.@NotNull Footprint building, @NotNull Colors colors) {
        return switch (building.type()) {
            case RESIDENTIAL -> colors.residential();
            case WORKPLACE -> colors.workplace();
            case MIXED -> colors.mixed();
            case UNKNOWN -> colors.building();
        };
    }

    /**
     * Recolours buildings for the current hover and selection.
     * <p>
     * Changes the block on the existing displays rather than redrawing, so highlighting on
     * hover costs nothing and never makes the map blink.
     */
    public void applyHighlight(
            VillageBuildings.@Nullable Footprint hovered,
            VillageBuildings.@Nullable Footprint selected,
            @NotNull Colors colors) {

        for (Part part : parts) {
            if (part.building == null || !(part.entity instanceof BlockDisplay display)) continue;

            Material wanted;
            if (part.building.equals(selected)) {
                wanted = colors.buildingSelected();
            } else if (part.building.equals(hovered)) {
                wanted = colors.buildingHighlight();
            } else {
                wanted = colorFor(part.building, colors);
            }

            try {
                if (!display.getBlock().getMaterial().equals(wanted)) {
                    display.setBlock(wanted.createBlockData());
                }
            } catch (Throwable ignored) {
                // Colour is cosmetic; never let it break the map.
            }
        }
    }

    /**
     * Converts a point on the panel back into world coordinates.
     * <p>
     * The inverse of the map projection — this is what a future "place a building here" mode
     * needs to turn a click on the map into a spot in the settlement.
     *
     * @return the world location on the village's ground plane, or {@code null} if the panel
     * isn't drawn.
     */
    public @Nullable Location panelToWorld(double u, double v) {
        if (villageCenter == null || blocksToPanel <= 0.0d) return null;

        Location world = villageCenter.clone();
        world.setX(villageCenter.getX() + u / blocksToPanel);
        // Panel "up" is north, and north is -Z, so the sign flips back here.
        world.setZ(villageCenter.getZ() - v / blocksToPanel);
        return world;
    }

    /**
     * Where the player's crosshair meets the panel, in panel-local metres, or {@code null}.
     * <p>
     * Split out from {@link #hitTest} so a placement mode can use the raw point even when it
     * doesn't land on a building.
     */
    public double @Nullable [] aimPoint(@NotNull Player viewer) {
        if (planeAnchor == null || planeRight == null || planeUp == null || planeLook == null) return null;

        Location eye = viewer.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().equals(planeAnchor.getWorld())) return null;

        Vector direction = eye.getDirection().normalize();

        double denominator = direction.dot(planeLook);
        // Looking along the panel's own plane: there is no meaningful intersection.
        if (Math.abs(denominator) < 1.0e-6d) return null;

        double distance = planeAnchor.toVector().subtract(eye.toVector()).dot(planeLook) / denominator;
        if (distance <= 0.0d) return null;

        Vector local = eye.toVector().add(direction.multiply(distance)).subtract(planeAnchor.toVector());

        double u = local.dot(planeRight);
        double v = local.dot(planeUp);

        if (Math.abs(u) > planeSize / 2.0d || Math.abs(v) > planeSize / 2.0d) return null;

        return new double[]{u, v};
    }

    /**
     * The building under the player's crosshair, or {@code null}.
     * <p>
     * Resolved by intersecting the look ray with the panel's plane and testing the hit point
     * against the drawn rectangles — no per-tile hitboxes, and it stays exact at any angle.
     */
    public VillageBuildings.@Nullable Footprint hitTest(@NotNull Player viewer) {
        double[] aim = aimPoint(viewer);
        if (aim == null) return null;

        for (Marker marker : markers) {
            if (marker.contains(aim[0], aim[1])) return marker.footprint();
        }
        return null;
    }

    private boolean outside(double x, double y) {
        double half = planeSize / 2.0d;
        return Math.abs(x) > half || Math.abs(y) > half;
    }

    /** Whether a rectangle touches the map at all, even if its centre is off it. */
    private boolean overlapsPanel(double x, double y, double width, double height) {
        double half = planeSize / 2.0d;
        return Math.abs(x) - width / 2.0d < half && Math.abs(y) - height / 2.0d < half;
    }

    /** Draws a hollow rectangle as four thin bars. */
    private void outlineRect(
            @NotNull World world,
            double x,
            double y,
            double width,
            double height,
            double thickness,
            @Nullable Material material,
            double layer) {

        double halfW = width / 2.0d;
        double halfH = height / 2.0d;

        clippedRect(world, x, y + halfH - thickness / 2.0d, width, thickness, material, layer);
        clippedRect(world, x, y - halfH + thickness / 2.0d, width, thickness, material, layer);

        // The uprights run the full height and sit a hair in front of the crossbars.
        //
        // Both halves of this matter. Full height means the corners are covered, where cutting
        // the uprights short to avoid the overlap left the two ends merely touching — and a join
        // with no overlap opens into a visible notch at a shallow viewing angle. The nudge
        // forward is what makes the overlap safe: two plates at the same depth fight over the
        // same pixels, which is what made the outline look ragged in the first place.
        double front = layer + CORNER_NUDGE;

        clippedRect(world, x - halfW + thickness / 2.0d, y, thickness, height, material, front);
        clippedRect(world, x + halfW - thickness / 2.0d, y, thickness, height, material, front);
    }

    /**
     * Draws a rectangle clipped to the panel.
     * <p>
     * Only the part that falls on the map is drawn. Testing an element's centre isn't enough:
     * a building sitting near the edge is centred inside the panel while half its outline hangs
     * off it, which is what made buildings and the boundary spill past the frame.
     */
    private void clippedRect(
            @NotNull World world,
            double x,
            double y,
            double width,
            double height,
            @Nullable Material material,
            double layer) {

        double half = planeSize / 2.0d;

        double left = Math.max(x - width / 2.0d, -half);
        double right = Math.min(x + width / 2.0d, half);
        double bottom = Math.max(y - height / 2.0d, -half);
        double top = Math.min(y + height / 2.0d, half);

        // Nothing of it lands on the map.
        if (right <= left || top <= bottom) return;

        rect(world,
                (left + right) / 2.0d,
                (bottom + top) / 2.0d,
                right - left,
                top - bottom,
                material,
                layer,
                null,
                false);
    }

    private void rect(
            @NotNull World world,
            double x,
            double y,
            double width,
            double height,
            @Nullable Material material,
            double layer,
            @Nullable Villager source,
            boolean tracksViewer) {

        if (material == null || !material.isBlock()) return;

        try {
            BlockDisplay display = world.spawn(placed(x, y, layer), BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setBillboard(Display.Billboard.FIXED);
                // Translation is in the entity's own (already rotated) space, so this genuinely
                // centres the rectangle on its position.
                spawned.setTransformation(new Transformation(
                        new Vector3f((float) (-width / 2.0d), (float) (-height / 2.0d), 0.0f),
                        new Quaternionf(),
                        new Vector3f((float) width, (float) height, THICKNESS),
                        new Quaternionf()));
                applyCommon(spawned);
            });
            parts.add(new Part(display, layer, x, y, width, height,
                    new Vector3f((float) width, (float) height, THICKNESS), source, tracksViewer));
        } catch (Throwable ignored) {
            // A missing rectangle leaves a gap; the rest of the map still draws.
        }
    }

    /**
     * A coloured frame drawn around a head.
     * <p>
     * Four bars rather than a filled square behind the head: a block display and an item display
     * anchor differently, so a backing square never lined up with the head sitting on it and the
     * marker always looked knocked off-centre. A frame has nothing to line up with.
     */
    private void ring(
            @NotNull World world,
            double x,
            double y,
            double size,
            @Nullable Material material,
            double layer,
            @Nullable Villager source) {

        if (material == null || !material.isBlock()) return;

        double bar = Math.max(size * 0.12d, 0.012d);
        double half = size / 2.0d;
        double inset = half - bar / 2.0d;

        bar(world, x, y, 0.0d, inset, size, bar, material, layer, source);
        bar(world, x, y, 0.0d, -inset, size, bar, material, layer, source);
        bar(world, x, y, -inset, 0.0d, bar, size, material, layer, source);
        bar(world, x, y, inset, 0.0d, bar, size, material, layer, source);
    }

    /** One bar of a frame, remembering how far it sits from the marker's centre. */
    private void bar(
            @NotNull World world,
            double x,
            double y,
            double offsetX,
            double offsetY,
            double width,
            double height,
            @Nullable Material material,
            double layer,
            @Nullable Villager source) {

        int before = parts.size();
        rect(world, x + offsetX, y + offsetY, width, height, material, layer, source, source == null);

        for (int i = before; i < parts.size(); i++) {
            parts.get(i).trackOffsetX = offsetX;
            parts.get(i).trackOffsetY = offsetY;
        }
    }

    /** The head of a villager, as an item icon on the map. */
    private void head(
            @NotNull World world,
            double x,
            double y,
            double size,
            @Nullable ItemStack item,
            double layer,
            @Nullable Villager source,
            boolean tracksViewer) {

        if (item == null) return;

        try {
            ItemDisplay display = world.spawn(placed(x, y, layer), ItemDisplay.class, spawned -> {
                spawned.setItemStack(item);
                spawned.setBillboard(Display.Billboard.FIXED);
                // FIXED renders the item flat, like an item frame. GUI renders it tilted the way
                // it looks in an inventory slot, which on a flat map reads as a crooked icon.
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf(),
                        new Vector3f((float) size, (float) size, (float) size),
                        new Quaternionf()));
                applyCommon(spawned);
            });
            parts.add(new Part(display, layer, x, y, size, size,
                    new Vector3f((float) size, (float) size, (float) size), source, tracksViewer));
        } catch (Throwable ignored) {
            // Falling back to no marker is better than failing the whole map.
        }
    }

    /**
     * Builds the head icon for a villager.
     * <p>
     * Injected rather than resolved here: the skin lives in the plugin's tracker, and taking a
     * dependency on that from a rendering class would drag the whole plugin in behind it.
     */
    public interface HeadFactory {
        @Nullable ItemStack headOf(@NotNull Villager villager);
    }

    private @Nullable HeadFactory heads;

    /** The villager's own head, falling back to a plain one if the skin can't be resolved. */
    private @Nullable ItemStack resolveHead(@NotNull Villager villager) {
        if (heads != null) {
            try {
                ItemStack head = heads.headOf(villager);
                if (head != null) return head;
            } catch (Throwable ignored) {
                // Fall through to the plain head below.
            }
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static @Nullable ItemStack playerHead(@NotNull Player player) {
        try {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta meta) {
                meta.setOwningPlayer(player);
                item.setItemMeta(meta);
            }
            return item;
        } catch (Throwable ignored) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    /** Spawns an item icon on the panel, used for the bell at the village centre. */
    private void item(@NotNull World world, double x, double y, double size, @NotNull Material material, double layer) {
        try {
            ItemDisplay display = world.spawn(placed(x, y, layer), ItemDisplay.class, spawned -> {
                spawned.setItemStack(new ItemStack(material));
                spawned.setBillboard(Display.Billboard.FIXED);
                // FIXED renders the item flat, like an item frame. GUI renders it tilted the way
                // it looks in an inventory slot, which on a flat map reads as a crooked icon.
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf(),
                        new Vector3f((float) size, (float) size, (float) size),
                        new Quaternionf()));
                applyCommon(spawned);
            });
            parts.add(new Part(display, layer, x, y, size, size,
                    new Vector3f((float) size, (float) size, (float) size), null, false));
        } catch (Throwable ignored) {
            // Item displays are newer than block displays on some forks; the map still works.
        }
    }

    private void applyCommon(@NotNull Display display) {
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(0.8f);

        // Culling box. A display's own bounding box is effectively a point, so the client drops
        // the whole thing the moment that point leaves the view frustum — which is why parts of
        // the map vanished as it swung around with the player. Sizing the box to the panel keeps
        // every tile rendered as long as any of the map is on screen.
        float cull = (float) Math.max(planeSize, 1.0d) * 2.0f;
        display.setDisplayWidth(cull);
        display.setDisplayHeight(cull);
        // Never persist: a panel left behind by a crash would be an orphan nothing owns.
        display.setPersistent(false);
        // Move smoothly between ticks instead of snapping, which is what makes following the
        // player read as gliding rather than teleporting.
        display.setInterpolationDuration(2);
        display.setTeleportDuration(2);
    }

    private @NotNull Location placed(double x, double y, double layer) {
        Location anchor = planeAnchor;
        if (anchor == null || planeRight == null || planeUp == null || planeLook == null) {
            return new Location(null, 0.0d, 0.0d, 0.0d);
        }

        // `look` runs from the panel towards the viewer, so higher layers are ADDED along it.
        Location at = anchor.clone()
                .add(planeRight.clone().multiply(x))
                .add(planeUp.clone().multiply(y))
                .add(planeLook.clone().multiply(layer * LAYER_STEP));

        at.setYaw(anchor.getYaw());
        at.setPitch(anchor.getPitch());
        return at;
    }

    public void remove() {
        for (Part part : parts) {
            try {
                if (part.entity != null && !part.entity.isDead()) part.entity.remove();
            } catch (Throwable ignored) {
                // Already gone with its chunk.
            }
        }
        parts.clear();
        markers.clear();
        planeAnchor = null;
        villageCenter = null;
    }

    public boolean isVisible() {
        return !parts.isEmpty();
    }

    /**
     * Hides every drawn part of this map from {@code viewer}.
     * <p>
     * The map is real, world-spawned display entities, so left alone it is visible to anyone
     * standing nearby — this is what keeps it private to whoever opened it, and what catches a
     * player who joins the server after the map was already drawn.
     */
    public void hideFrom(@NotNull Player viewer, @NotNull Plugin plugin) {
        for (Part part : parts) {
            if (part.entity != null && !part.entity.isDead()) viewer.hideEntity(plugin, part.entity);
        }
    }

    public static @NotNull Colors readColors(@Nullable ConfigurationSection section) {
        if (section == null) return Colors.DEFAULT;

        return new Colors(
                material(section, "border", Colors.DEFAULT.border()),
                material(section, "boundary", Colors.DEFAULT.boundary()),
                material(section, "background", Colors.DEFAULT.background()),
                material(section, "building", Colors.DEFAULT.building()),
                material(section, "residential", Colors.DEFAULT.residential()),
                material(section, "workplace", Colors.DEFAULT.workplace()),
                material(section, "mixed", Colors.DEFAULT.mixed()),
                material(section, "building-highlight", Colors.DEFAULT.buildingHighlight()),
                material(section, "building-selected", Colors.DEFAULT.buildingSelected()),
                material(section, "resident", Colors.DEFAULT.resident()),
                material(section, "mayor", Colors.DEFAULT.mayor()),
                material(section, "viewer", Colors.DEFAULT.viewer()));
    }

    private static @NotNull Material material(@NotNull ConfigurationSection section, String key, @NotNull Material fallback) {
        String name = section.getString(key);
        if (name == null || name.isEmpty()) return fallback;

        Material material = Material.matchMaterial(name);
        return material != null && material.isBlock() ? material : fallback;
    }
}
