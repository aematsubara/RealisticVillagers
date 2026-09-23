package me.matsubara.realisticvillagers.hologram;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HologramManager {

    private final RealisticVillagers plugin;
    private final Map<UUID, HologramMenu> playerMenus   = new HashMap<>();
    private final Map<UUID, SpeechBubble> activeSpeech  = new HashMap<>();

    public HologramManager(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        var cfg = plugin.getHologramConfig();
        return cfg == null || cfg.getBoolean("hologram.enabled", true);
    }

    // Lets servers keep the head display / info panel / speech bubbles while sending
    // right-click interactions to the old chest-GUI instead of the hologram menu.
    public boolean isMenuEnabled() {
        if (!isEnabled()) return false;
        var cfg = plugin.getHologramConfig();
        return cfg == null || cfg.getBoolean("hologram.menu.enabled", true);
    }

    public void openMenu(Player player, IVillagerNPC npc) {
        openMenu(player, npc, false);
    }

    /**
     * @param mayorMode opens directly on the settlement screen instead of the villager menu.
     */
    public void openMenu(Player player, IVillagerNPC npc, boolean mayorMode) {
        if (!isMenuEnabled()) return;

        // Close any existing menu for this player before opening a new one.
        HologramMenu existing = playerMenus.remove(player.getUniqueId());
        if (existing != null) existing.close(false);

        HologramMenu menu = new HologramMenu(plugin, player, npc, mayorMode);
        playerMenus.put(player.getUniqueId(), menu);
        menu.open();
    }

    public boolean hasMenu(UUID playerUUID) {
        return playerMenus.containsKey(playerUUID);
    }

    /**
     * Hides every currently open menu's entities from {@code joined}.
     * <p>
     * A menu hides its entities from whoever is online the moment it spawns them, but a player
     * who joins afterwards would otherwise still see someone else's private interaction panel
     * floating in the world.
     */
    public void hideAllFrom(Player joined) {
        for (HologramMenu menu : playerMenus.values()) {
            menu.hideFrom(joined);
        }
    }

    public @Nullable HologramMenu getMenuForPlayer(UUID playerUUID) {
        return playerMenus.get(playerUUID);
    }

    public void unregisterMenu(UUID playerUUID) {
        playerMenus.remove(playerUUID);
    }

    public void closeMenu(UUID playerUUID) {
        HologramMenu menu = playerMenus.remove(playerUUID);
        // preserveOrder=true: user explicitly closed the menu — FOLLOW/STAY orders must persist.
        if (menu != null) menu.close(false, true);
    }

    public void closeMenuForVillager(UUID villagerUUID) {
        playerMenus.entrySet().removeIf(entry -> {
            HologramMenu menu = entry.getValue();
            if (menu.getNPC().getUniqueId().equals(villagerUUID)) {
                menu.close(false);
                return true;
            }
            return false;
        });
    }

    public void showSpeech(LivingEntity entity, String rawText) {
        if (!isEnabled()) return;

        var cfg = plugin.getHologramConfig();
        if (cfg != null && !cfg.getBoolean("hologram.speech-bubble.enabled", true)) return;

        UUID id = entity.getUniqueId();
        SpeechBubble old = activeSpeech.remove(id);
        if (old != null) old.remove();

        SpeechBubble bubble = new SpeechBubble(plugin, entity, rawText);
        activeSpeech.put(id, bubble);
        bubble.start();
    }

    public void removeSpeech(UUID villagerUUID) {
        SpeechBubble bubble = activeSpeech.remove(villagerUUID);
        if (bubble != null) bubble.remove();
    }

    public void closeAll() {
        List<HologramMenu> menus = new ArrayList<>(playerMenus.values());
        playerMenus.clear();
        for (HologramMenu menu : menus) {
            menu.close(false);
        }
        for (SpeechBubble bubble : activeSpeech.values()) bubble.remove();
        activeSpeech.clear();
    }
}
