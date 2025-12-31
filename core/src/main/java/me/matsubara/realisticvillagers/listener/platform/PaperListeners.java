package me.matsubara.realisticvillagers.listener.platform;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.util.Reflection;
import org.apache.commons.lang3.BooleanUtils;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;

public class PaperListeners implements Listener {

    private final RealisticVillagers plugin;
    private final @Getter boolean spawnRegistered;
    private final @Getter boolean ruleRegistered;

    private static final String ADD_EVENT_CLASS = "com.destroystokyo.paper.event.entity.EntityAddToWorldEvent";
    private static final String RULE_EVENT_CLASS = "io.papermc.paper.event.world.WorldGameRuleChangeEvent";

    private static final MethodHandle GET_GAME_RULE;
    private static final MethodHandle GET_VALUE;

    static {
        Class<?> clazz = Reflection.getClazzSilently(RULE_EVENT_CLASS);
        GET_GAME_RULE = clazz != null ? Reflection.getMethod(clazz, "getGameRule") : null;
        GET_VALUE = clazz != null ? Reflection.getMethod(clazz, "getValue") : null;
    }

    public PaperListeners(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Easiest way to load custom entities.
        spawnRegistered = register(ADD_EVENT_CLASS, this::handleSpawn);

        // We are (most probably) using Spigot.
        if (GET_GAME_RULE == null || GET_VALUE == null) {
            ruleRegistered = false;
            return;
        }

        // This is ugly, but it's the only way to support both Spigot and Paper.
        ruleRegistered = register(RULE_EVENT_CLASS, this::handleRule);
    }

    private void handleSpawn(@NotNull Listener listener, @NotNull Event event) {
        if (!(event instanceof EntityEvent entityEvent)) return;
        plugin.getTracker().getSpawnListeners().handleSpawn(entityEvent.getEntity());
    }

    private void handleRule(@NotNull Listener listener, @NotNull Event event) {
        if (!(event instanceof WorldEvent worldEvent)) return;

        World world = worldEvent.getWorld();

        GameRule<?> rule;
        try {
            rule = (GameRule<?>) GET_GAME_RULE.invoke(event);
        } catch (Throwable ignored) {
            return;
        }

        String value;
        try {
            value = (String) GET_VALUE.invoke(event);
        } catch (Throwable ignored) {
            return;
        }

        if (GameRule.MOB_GRIEFING.equals(rule)
                && !BooleanUtils.toBoolean(value)
                && world.getGameRuleValue(rule) instanceof Boolean current
                && current) {
            INMSConverter.printRuleWarning(plugin, world, rule);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        plugin.getTracker().getSpawnListeners().onCreatureSpawn(event);
    }

    @SuppressWarnings("unchecked")
    private boolean register(String clazzName, EventExecutor executor) {
        Class<?> clazz = Reflection.getClazzSilently(clazzName);
        if (clazz == null) return false;

        plugin.getServer().getPluginManager().registerEvent(
                (Class<? extends Event>) clazz,
                new Listener() {
                },
                EventPriority.HIGHEST,
                executor,
                plugin,
                false);

        return true;
    }
}