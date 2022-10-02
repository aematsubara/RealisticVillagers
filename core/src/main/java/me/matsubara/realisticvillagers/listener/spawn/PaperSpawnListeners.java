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
import org.jetbrains.annotations.NotNull;

public class PaperSpawnListeners implements EventExecutor {

    private final RealisticVillagers plugin;
    private @Getter boolean registered;

    private final static String EVENT_CLASS = "com.destroystokyo.paper.event.entity.EntityAddToWorldEvent";

    public PaperSpawnListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
        try {
            @SuppressWarnings("unchecked") Class<? extends Event> clazz = (Class<? extends Event>) Class.forName(EVENT_CLASS);

            plugin.getServer().getPluginManager().registerEvent(
                    clazz,
                    new Listener() {
                    },
                    EventPriority.NORMAL,
                    this,
                    plugin,
                    false);

            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler(ignoreCancelled = true)
                public void onCreatureSpawn(CreatureSpawnEvent event) {
                    plugin.getVillagerTracker().getSpawnListeners().onCreatureSpawn(event);
                }
            }, plugin);
            registered = true;
        } catch (ClassNotFoundException ignored) {

        }
    }

    @Override
    public void execute(@NotNull Listener listener, @NotNull Event event) {
        if (event instanceof EntityEvent entityEvent) {
            plugin.getVillagerTracker().getSpawnListeners().handleSpawn(entityEvent.getEntity());
        }
    }
}