package me.matsubara.realisticvillagers.util.anvilgui.version;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
// VersionWrapper and its nested AnvilContainerWrapper are NOT imported on purpose.
//
// This class already sits in me.matsubara.realisticvillagers.util.anvilgui.version, which is
// where core's shade plugin relocates net.wesjd.anvilgui to — so both types resolve from this
// very package and an import would be redundant. Importing the original net.wesjd names instead
// compiles cleanly, because that library is on the compile classpath, and then fails at runtime
// with NoClassDefFoundError: the relocation means those names do not exist in the shipped jar.
// That took out every anvil screen on this version, not just the one being worked on at the time.
import me.matsubara.realisticvillagers.util.anvilgui.version.VersionWrapper.AnvilContainerWrapper;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftChatMessage;

public final class Wrapper26_R1 implements VersionWrapper {

    private ServerPlayer toNMS(org.bukkit.entity.Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private int getRealNextContainerId(org.bukkit.entity.Player player) {
        return toNMS(player).nextContainerCounter();
    }

    @Override
    public int getNextContainerId(org.bukkit.entity.Player player, AnvilContainerWrapper container) {
        return ((AnvilContainer) container).getContainerId();
    }

    @Override
    public void handleInventoryCloseEvent(org.bukkit.entity.Player player) {
        ServerPlayer nmsPlayer = toNMS(player);
        try {
            CraftEventFactory.handleInventoryCloseEvent(nmsPlayer);
        } catch (NoSuchMethodError e) {
            try {
                Class<?> reasonClass = Class.forName("org.bukkit.event.inventory.InventoryCloseEvent$Reason");
                CraftEventFactory.class
                        .getMethod("handleInventoryCloseEvent",
                                net.minecraft.world.entity.player.Player.class, reasonClass)
                        .invoke(null, nmsPlayer, reasonClass.getField("UNKNOWN").get(null));
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        }
        nmsPlayer.doCloseContainer();
    }

    @Override
    public void sendPacketOpenWindow(org.bukkit.entity.Player player, int id, Object title) {
        toNMS(player).connection.send(
                new ClientboundOpenScreenPacket(id, MenuType.ANVIL, (Component) title));
    }

    @Override
    public void sendPacketCloseWindow(org.bukkit.entity.Player player, int id) {
        toNMS(player).connection.send(new ClientboundContainerClosePacket(id));
    }

    @Override
    public void sendPacketExperienceChange(org.bukkit.entity.Player player, int xp) {
        toNMS(player).connection.send(new ClientboundSetExperiencePacket(0, 0, xp));
    }

    @Override
    public void setActiveContainerDefault(org.bukkit.entity.Player player) {
        ServerPlayer nmsPlayer = toNMS(player);
        nmsPlayer.containerMenu = nmsPlayer.inventoryMenu;
    }

    @Override
    public void setActiveContainer(org.bukkit.entity.Player player, AnvilContainerWrapper container) {
        toNMS(player).containerMenu = (AbstractContainerMenu) container;
    }

    @Override
    public void setActiveContainerId(AnvilContainerWrapper container, int id) {
        // no-op in Purpur 26.x
    }

    @Override
    public void addActiveContainerSlotListener(AnvilContainerWrapper container, org.bukkit.entity.Player player) {
        toNMS(player).initMenu((AbstractContainerMenu) container);
    }

    @Override
    public AnvilContainerWrapper newContainerAnvil(org.bukkit.entity.Player player, Object title) {
        return new AnvilContainer(player, getRealNextContainerId(player), (Component) title);
    }

    @Override
    public Object literalChatComponent(String text) {
        return Component.literal(text);
    }

    @Override
    public Object jsonChatComponent(String text) {
        return CraftChatMessage.fromJSON(text);
    }

    static class AnvilContainer extends AnvilMenu implements AnvilContainerWrapper {

        AnvilContainer(org.bukkit.entity.Player player, int id, Component title) {
            super(id,
                    ((CraftPlayer) player).getHandle().getInventory(),
                    ContainerLevelAccess.create(
                            ((CraftWorld) player.getWorld()).getHandle(),
                            new BlockPos(0, 0, 0)));
            this.checkReachable = false;
            setTitle(title);
        }

        @Override
        public void createResult() {
            Slot outputSlot = getSlot(2);
            if (!outputSlot.hasItem()) {
                outputSlot.set(getSlot(0).getItem().copy());
            }
            cost.set(0);
            sendAllDataToRemote();
            broadcastChanges();
        }

        @Override
        public void removed(net.minecraft.world.entity.player.Player player) {
            // intentional no-op: prevent items being returned on close
        }

        @Override
        protected void clearContainer(net.minecraft.world.entity.player.Player player,
                                      net.minecraft.world.Container container) {
            // intentional no-op
        }

        public int getContainerId() {
            return containerId;
        }

        @Override
        public String getRenameText() {
            return itemName;
        }

        @Override
        public void setRenameText(String text) {
            Slot slot = getSlot(0);
            if (slot.hasItem()) {
                slot.getItem().set(DataComponents.CUSTOM_NAME, Component.literal(text));
            }
        }

        @Override
        public org.bukkit.inventory.Inventory getBukkitInventory() {
            return getBukkitView().getTopInventory();
        }
    }
}
