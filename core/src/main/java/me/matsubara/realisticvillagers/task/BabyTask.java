package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class BabyTask extends BukkitRunnable {

    private final RealisticVillagers plugin;
    private final IVillagerNPC villager;
    private final Player player;
    private final boolean isBoy;

    private int count = 0;
    private boolean success = false;

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public BabyTask(@NotNull RealisticVillagers plugin, Villager villager, Player player) {
        this.plugin = plugin;
        // No need to check if it's invalid, the main GUI can only be opened by valid villagers.
        this.villager = plugin.getConverter().getNPC(villager).get();
        this.player = player;
        this.isBoy = RandomUtils.nextBoolean();
    }

    @Override
    public void run() {
        if (++count == 10) {
            openInventory(Config.BABY_TEXT.asStringTranslated());
            cancel();
            return;
        }

        villager.jumpIfPossible();
        player.spawnParticle(Particle.HEART, villager.bukkit().getEyeLocation(), 3, 0.1d, 0.1d, 0.1d);
    }

    private void openInventory(String text) {
        new AnvilGUI.Builder()
                .title(Config.BABY_TITLE.asStringTranslated().replace("%sex%", isBoy ? Config.BOY.asString() : Config.GIRL.asString()))
                .text(text)
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    String result = snapshot.getText();

                    if (result.length() < 3) return RealisticVillagers.CLOSE_RESPONSE;

                    long procreation = System.currentTimeMillis();
                    player.getInventory().addItem(plugin.createBaby(isBoy, result, procreation, villager.bukkit().getUniqueId()));

                    int reputation = Config.BABY_REPUTATION.asInt();
                    if (reputation > 1) villager.addMinorPositive(player.getUniqueId(), reputation);
                    villager.setProcreatingWith(null);
                    villager.setLastProcreation(procreation);

                    success = true;
                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .onClose(opener -> {
                    if (success) return;
                    plugin.getServer().getScheduler().runTask(plugin, () -> openInventory(Config.BABY_INVALID_NAME.asStringTranslated()));
                })
                .plugin(plugin)
                .open(player)
                .getInventory();
    }
}