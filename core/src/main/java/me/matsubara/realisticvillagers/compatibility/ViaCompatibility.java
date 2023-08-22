package me.matsubara.realisticvillagers.compatibility;

import com.viaversion.viaversion.api.Via;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

public class ViaCompatibility implements Compatibility {

    public boolean cancelMetadata(@NotNull Player player) {
        // 48 is the oficial protocol version of 1.8; we want to cancel the metadata packet for players using any version lower than 1.8.
        return Via.getAPI().getPlayerVersion(player.getUniqueId()) < 47;
    }

    @Override
    public boolean shouldTrack(Villager villager) {
        return true;
    }
}