package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftVersion;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NPCModifier {

    protected final NPC npc;
    protected final List<LazyPacket> packetContainers = new CopyOnWriteArrayList<>();

    public static final int MINECRAFT_VERSION = MinecraftVersion.getCurrentVersion().getMinor();

    public NPCModifier(NPC npc) {
        this.npc = npc;
    }

    protected void queuePacket(LazyPacket packet) {
        packetContainers.add(packet);
    }

    protected void queueInstantly(@NotNull LazyPacket packet) {
        PacketContainer container = packet.provide(npc, null);
        packetContainers.add((npc, player) -> container);
    }

    public void send() {
        send(Bukkit.getOnlinePlayers());
    }

    public void send(@NotNull Iterable<? extends Player> players) {
        players.forEach(player -> {
            for (LazyPacket packet : packetContainers) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet.provide(npc, player));
            }
        });
        packetContainers.clear();
    }

    public void send(Player... players) {
        send(Arrays.asList(players));
    }

    public interface LazyPacket {

        PacketContainer provide(NPC npc, Player player);
    }
}