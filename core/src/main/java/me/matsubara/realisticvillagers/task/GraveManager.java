package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.village.Grave;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Marks where villagers are buried, and keeps those markers standing.
 * <p>
 * Built from displays rather than blocks. A headstone made of real blocks would be something
 * players break, build over and lose track of, and every grave would permanently take a piece of
 * the world; displays are ours to remove, never in the way, and cost the settlement nothing.
 * <p>
 * Rendered fresh whenever the plugin starts, from the record on disk rather than from whatever
 * happens to still be standing in the world — displays do not survive a crash, and a grave that
 * quietly disappeared would be worse than none at all.
 */
public final class GraveManager {

    private final RealisticVillagers plugin;

    private final List<Grave> graves = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();

    /** Every display that belongs to a grave, so hitting one is enough to find it. */
    private final java.util.Map<java.util.UUID, Grave> graveEntities = new java.util.HashMap<>();


    /** How high above the ground the text sits, in blocks. */
    private static final double TEXT_HEIGHT = 1.15d;

    public GraveManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /**
     * Where a world keeps its own graves.
     * <p>
     * Inside the world, not the plugin folder. A grave is a thing that happened at a place, and
     * deleting the world should take its graveyard with it — kept centrally, a fresh world would
     * open with headstones for villagers who died somewhere that no longer exists.
     */
    private @NotNull File fileFor(@NotNull World world) {
        return new File(new File(world.getWorldFolder(), "realisticvillagers"), "graves.yml");
    }

    public void start() {
        load();
        render();
    }

    /**
     * Buries a villager where it fell.
     * <p>
     * The details are read from the villager <b>before</b> it is gone: once the entity is removed
     * its family is no longer reachable, and a grave that says "child of unknown and unknown" is
     * a grave that arrived a tick too late.
     */
    public void bury(@NotNull Villager villager, @Nullable IVillagerNPC npc, @NotNull String cause) {
        if (!Config.VILLAGE_GRAVES_ENABLED.asBool(true)) return;

        Location at = villager.getLocation();
        World world = at.getWorld();
        if (world == null) return;

        String name = npc != null && npc.getVillagerName() != null && !npc.getVillagerName().isEmpty()
                ? npc.getVillagerName()
                : "Villager";

        Grave grave = new Grave(name, parentsOf(npc), prettify(cause), System.currentTimeMillis(),
                world.getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());

        graves.add(grave);
        spawn(grave);
        save();
    }

    /**
     * "Child of X and Y", or as much of it as is known.
     * <p>
     * Villagers born in the world have parents recorded; ones that spawned with the village do
     * not, and saying so plainly is better than inventing a lineage or leaving a blank line.
     */
    private @NotNull String parentsOf(@Nullable IVillagerNPC npc) {
        if (npc == null) return "";

        String father = nameOf(npc.getFather());
        String mother = nameOf(npc.getMother());

        if (father.isEmpty() && mother.isEmpty()) return "";
        if (father.isEmpty()) return mother;
        if (mother.isEmpty()) return father;

        return father + " & " + mother;
    }

    private @NotNull String nameOf(@Nullable IVillagerNPC parent) {
        if (parent == null) return "";

        String name = parent.getVillagerName();
        return name == null ? "" : name;
    }

    private @NotNull String prettify(@NotNull String cause) {
        String text = cause.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.isEmpty() ? "" : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void render() {
        for (Grave grave : graves) spawn(grave);
    }

    private void spawn(@NotNull Grave grave) {
        World world = plugin.getServer().getWorld(grave.world());
        if (world == null) return;

        Location base = new Location(world, grave.x() + 0.5d, grave.y(), grave.z() + 0.5d);
        if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;

        try {
            // A stone, a base it stands on, and a rounded top — three thin slabs rather than one,
            // because a single upright plate reads as a floating tile and not as a headstone.
            BlockDisplay stone = world.spawn(base, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.POLISHED_ANDESITE.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-0.28f, 0.1f, -0.06f),
                        new Quaternionf(),
                        new Vector3f(0.56f, 0.75f, 0.12f),
                        new Quaternionf()));
                spawned.setPersistent(false);
            });
            spawned.add(stone);
            graveEntities.put(stone.getUniqueId(), grave);

            BlockDisplay cap = world.spawn(base, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.ANDESITE_SLAB.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-0.34f, 0.82f, -0.09f),
                        new Quaternionf(),
                        new Vector3f(0.68f, 0.14f, 0.18f),
                        new Quaternionf()));
                spawned.setPersistent(false);
            });
            spawned.add(cap);
            graveEntities.put(cap.getUniqueId(), grave);

            BlockDisplay plinth = world.spawn(base, BlockDisplay.class, spawned -> {
                spawned.setBlock(Material.ANDESITE_SLAB.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(-0.4f, 0.0f, -0.22f),
                        new Quaternionf(),
                        new Vector3f(0.8f, 0.12f, 0.44f),
                        new Quaternionf()));
                spawned.setPersistent(false);
            });
            spawned.add(plinth);
            graveEntities.put(plinth.getUniqueId(), grave);

            // Something that can actually be clicked.
            //
            // Block and text displays have no interaction box at all — a right-click passes
            // straight through them and no event is ever raised, which is why sneaking and
            // clicking a headstone did nothing whatsoever. An interaction entity exists for
            // exactly this: an invisible hitbox that answers clicks on behalf of the scenery.
            org.bukkit.entity.Interaction hitbox = world.spawn(base, org.bukkit.entity.Interaction.class, spawned -> {
                spawned.setInteractionWidth(0.9f);
                spawned.setInteractionHeight(1.4f);
                spawned.setResponsive(true);
                spawned.setPersistent(false);
            });
            spawned.add(hitbox);
            graveEntities.put(hitbox.getUniqueId(), grave);

            TextDisplay text = world.spawn(base.clone().add(0.0d, TEXT_HEIGHT, 0.0d), TextDisplay.class, spawned -> {
                spawned.setText(PluginUtils.translate(epitaph(grave)));
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setDefaultBackground(false);
                spawned.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);

                // Small, and only readable close up. At full size a headstone's text is taller
                // than the stone it belongs to and legible across the village, which makes a
                // graveyard a wall of writing rather than something you walk up to and read.
                spawned.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(0.45f, 0.45f, 0.45f),
                        new Quaternionf()));
                spawned.setViewRange(0.25f);
                spawned.setSeeThrough(false);
                spawned.setPersistent(false);
            });
            spawned.add(text);
            graveEntities.put(text.getUniqueId(), grave);
        } catch (Throwable ignored) {
            // Older servers have no displays; the record is kept either way, and shows up if the
            // server is ever moved to a version that can draw it.
        }
    }

    private @NotNull String epitaph(@NotNull Grave grave) {
        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String template = config != null ? config.getString("village.grave") : null;
        if (template == null) {
            template = "&f%name%\n&7%parents%\n&8%cause%";
        }

        // Lines whose only content is a placeholder that came out empty are dropped, so a
        // villager with no known parents gets a two-line stone rather than a blank middle.
        StringBuilder out = new StringBuilder();
        for (String line : template.split("\n")) {
            String filled = line
                    .replace("%name%", grave.name())
                    .replace("%parents%", grave.parents().isEmpty() ? "" : "Child of " + grave.parents())
                    .replace("%cause%", grave.cause());

            if (PluginUtils.translate(filled).replaceAll("§.", "").trim().isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(filled);
        }

        return out.toString();
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    private void load() {
        graves.clear();

        for (World world : plugin.getServer().getWorlds()) {
            File file = fileFor(world);
            if (!file.isFile()) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            var section = config.getConfigurationSection("graves");
            if (section == null) continue;

            for (String key : section.getKeys(false)) {
                var entry = section.getConfigurationSection(key);
                if (entry == null) continue;

                Grave grave = Grave.load(entry);
                if (grave != null) graves.add(grave);
            }
        }
    }

    /** Writes each world's graves back into that world. */
    private void save() {
        for (World world : plugin.getServer().getWorlds()) {
            YamlConfiguration config = new YamlConfiguration();

            int index = 0;
            for (Grave grave : graves) {
                if (!grave.world().equals(world.getName())) continue;
                grave.save(config.createSection("graves." + index++));
            }

            File file = fileFor(world);

            // A world with no graves gets no file rather than an empty one left lying about.
            if (index == 0) {
                if (file.isFile() && !file.delete()) file.deleteOnExit();
                continue;
            }

            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();

                config.save(file);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not save graves for " + world.getName() + ": "
                        + throwable.getMessage());
            }
        }
    }

    /**
     * Removes the grave a display belongs to.
     * <p>
     * Graves are permanent unless somebody clears them, and a settlement that has been through a
     * few raids ends up with a field of headstones nobody wants. Any part of a stone answers for
     * the whole of it, so there is no hunting for the one clickable piece.
     *
     * @return whether something was removed.
     */
    public boolean removeAt(@NotNull Entity display) {
        Grave grave = graveEntities.get(display.getUniqueId());
        if (grave == null) return false;

        graves.remove(grave);

        // Every display of that grave, not just the one that was hit.
        var owned = new ArrayList<java.util.UUID>();
        for (var entry : graveEntities.entrySet()) {
            if (entry.getValue().equals(grave)) owned.add(entry.getKey());
        }

        for (java.util.UUID id : owned) {
            graveEntities.remove(id);

            Entity entity = plugin.getServer().getEntity(id);
            if (entity == null) continue;

            spawned.remove(entity);
            try {
                entity.remove();
            } catch (Throwable ignored) {
                // Already gone with its chunk.
            }
        }

        save();
        return true;
    }

    /** The grave a display belongs to, or {@code null}. */
    public @Nullable Grave graveOf(@NotNull Entity display) {
        return graveEntities.get(display.getUniqueId());
    }

    public void shutdown() {
        for (Entity entity : spawned) {
            try {
                if (!entity.isDead()) entity.remove();
            } catch (Throwable ignored) {
                // Already gone with its chunk.
            }
        }
        spawned.clear();
        graveEntities.clear();
        save();
    }
}
