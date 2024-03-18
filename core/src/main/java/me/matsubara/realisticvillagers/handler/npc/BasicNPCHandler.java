package me.matsubara.realisticvillagers.handler.npc;

import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.npc.SpawnCustomizer;
import me.matsubara.realisticvillagers.npc.modifier.MetadataModifier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BasicNPCHandler implements SpawnCustomizer {

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        Location location = npc.getLocation();

        npc.rotation().queueRotate(location.getYaw(), location.getPitch()).send(player);

        MetadataModifier metadata = npc.metadata();
        metadata.queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);
    }
}
