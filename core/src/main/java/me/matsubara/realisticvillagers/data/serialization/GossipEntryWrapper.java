package me.matsubara.realisticvillagers.data.serialization;

import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record GossipEntryWrapper(
        UUID target,
        String typeSerializedName,
        int value) implements ConfigurationSerializable {

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("Target", target.toString());
        result.put("Type", typeSerializedName);
        result.put("Value", value);

        return result;
    }

    @SuppressWarnings({"unused"})
    public static @NotNull GossipEntryWrapper deserialize(@NotNull Map<String, Object> args) {
        UUID target = PluginUtils.getOrDefault(args, "Target", String.class, UUID::fromString, null);
        String type = PluginUtils.getOrDefault(args, "Type", String.class);
        Integer value = PluginUtils.getOrDefault(args, "Value", Integer.class);
        return new GossipEntryWrapper(target, type, value);
    }
}