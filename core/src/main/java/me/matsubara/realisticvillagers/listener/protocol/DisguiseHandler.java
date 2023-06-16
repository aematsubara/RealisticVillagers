package me.matsubara.realisticvillagers.listener.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.Multimap;
import com.mojang.authlib.properties.Property;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DisguiseHandler extends PacketAdapter {

    private final RealisticVillagers plugin;

    public DisguiseHandler(RealisticVillagers plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(@NotNull PacketEvent event) {
        if (event.isCancelled()) return;

        PacketContainer container = event.getPacket();
        boolean modern = PluginUtils.IS_1_19_3_OR_NEW;

        if (!modern) {
            EnumWrappers.PlayerInfoAction action = container.getPlayerInfoAction().read(0);
            if (action != EnumWrappers.PlayerInfoAction.ADD_PLAYER) return;
        }

        List<PlayerInfoData> datas = new ArrayList<>();
        int infoDataIndex = modern ? 1 : 0;

        for (PlayerInfoData data : container.getPlayerInfoDataLists().read(infoDataIndex)) {
            WrappedGameProfile profile = data.getProfile();

            // Don't cancel for the self player.
            if (profile.getUUID().equals(event.getPlayer().getUniqueId())) {
                return;
            }

            if (!plugin.getTracker().getOldProperties().containsKey(data.getProfileId())) {
                datas.add(data);
                continue;
            }

            Multimap<String, WrappedSignedProperty> properties = profile.getProperties();
            properties.removeAll("textures");

            // Replace textures with the original ones.
            Property property = plugin.getTracker().getOldProperties().get(data.getProfileId()).getSecond().get("textures").iterator().next();
            properties.put("textures", WrappedSignedProperty.fromHandle(property));

            datas.add(modern ?
                    new PlayerInfoData(
                            data.getProfileId(),
                            data.getLatency(),
                            data.isListed(),
                            data.getGameMode(),
                            profile,
                            data.getDisplayName(),
                            data.getRemoteChatSessionData()) :
                    new PlayerInfoData(
                            profile,
                            20,
                            EnumWrappers.NativeGameMode.CREATIVE,
                            null));
        }

        container.getPlayerInfoDataLists().write(infoDataIndex, datas);
        event.setPacket(container);
    }
}