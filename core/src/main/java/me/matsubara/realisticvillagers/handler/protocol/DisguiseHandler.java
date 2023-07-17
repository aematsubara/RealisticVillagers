package me.matsubara.realisticvillagers.handler.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.*;
import com.google.common.collect.Multimap;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DisguiseHandler extends PacketAdapter {

    private final RealisticVillagers plugin;

    public DisguiseHandler(RealisticVillagers plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(@NotNull PacketEvent event) {
        if (event.isCancelled()) return;

        Map<UUID, Pair<Integer, PropertyMap>> oldProperties = plugin.getTracker().getOldProperties();
        if (oldProperties.isEmpty()) return;

        PacketContainer container = event.getPacket();
        boolean modern = PluginUtils.IS_1_19_3_OR_NEW;

        if (!modern) {
            EnumWrappers.PlayerInfoAction action = container.getPlayerInfoAction().read(0);
            if (action != EnumWrappers.PlayerInfoAction.ADD_PLAYER) return;
        }

        List<PlayerInfoData> datas = new ArrayList<>();
        int infoDataIndex = modern ? 1 : 0;

        StructureModifier<List<PlayerInfoData>> dataLists = container.getPlayerInfoDataLists();
        for (PlayerInfoData data : dataLists.read(infoDataIndex)) {
            WrappedGameProfile profile = data.getProfile();
            UUID profileId = data.getProfileId();

            // Don't cancel for the player being disguised.
            if (profileId.equals(event.getPlayer().getUniqueId()) || !oldProperties.containsKey(profileId)) {
                datas.add(data);
                continue;
            }

            Multimap<String, WrappedSignedProperty> properties = profile.getProperties();
            properties.removeAll("textures");

            // Replace textures with the original ones.
            Property property = oldProperties.get(profileId).getSecond().get("textures").iterator().next();
            properties.put("textures", WrappedSignedProperty.fromHandle(property));

            int latency = data.getLatency();
            EnumWrappers.NativeGameMode gameMode = data.getGameMode();
            WrappedChatComponent displayName = data.getDisplayName();

            datas.add(modern ?
                    new PlayerInfoData(
                            profileId,
                            latency,
                            data.isListed(),
                            gameMode,
                            profile,
                            displayName,
                            data.getRemoteChatSessionData()) :
                    new PlayerInfoData(
                            profile,
                            latency,
                            gameMode,
                            displayName));
        }

        dataLists.write(infoDataIndex, datas);
        event.setPacket(container);
    }
}