package me.matsubara.realisticvillagers.listener.spawn;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

public class PaperSpawnListeners implements EventExecutor {

    private final RealisticVillagers plugin;
    private @Getter boolean registered;

    private static final String EVENT_CLASS = "com.destroystokyo.paper.event.entity.EntityAddToWorldEvent";

    public PaperSpawnListeners(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        try {
            @SuppressWarnings("unchecked") Class<? extends Event> clazz = (Class<? extends Event>) Class.forName(EVENT_CLASS);

            PluginManager pluginManager = plugin.getServer().getPluginManager();

            pluginManager.registerEvent(
                    clazz,
                    new Listener() {
                    },
                    EventPriority.HIGHEST,
                    this,
                    plugin,
                    false);

            pluginManager.registerEvents(new Listener() {
                @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                public void onCreatureSpawn(CreatureSpawnEvent event) {
                    plugin.getTracker().getSpawnListeners().onCreatureSpawn(event);
                }
            }, plugin);
            registered = true;
        } catch (ClassNotFoundException ignored) {

        }
    }

    @Override
    public void execute(@NotNull Listener listener, @NotNull Event event) {
        if (!(event instanceof EntityEvent entityEvent)) return;
        plugin.getTracker().getSpawnListeners().handleSpawn(entityEvent.getEntity());
    }
}