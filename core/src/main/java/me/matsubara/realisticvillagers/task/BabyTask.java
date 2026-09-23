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
import java.util.List;

public class BabyTask extends BukkitRunnable {

    private final RealisticVillagers plugin;
    private final IVillagerNPC villager;
    private final Player player;
    private final boolean isBoy;

    private int count = 0;

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public BabyTask(@NotNull RealisticVillagers plugin, Villager villager, Player player) {
        this.plugin = plugin;
        this.villager = plugin.getConverter().getNPC(villager).get();
        this.player = player;
        this.isBoy = RandomUtils.nextBoolean();
    }

    @Override
    public void run() {
        if (++count == 10) {
            openNamingGUI(Config.BABY_TEXT.asStringTranslated());
            cancel();
            return;
        }
        villager.jumpIfPossible();
        player.spawnParticle(Particle.HEART, villager.bukkit().getEyeLocation(), 3, 0.1d, 0.1d, 0.1d);
    }

    private void openNamingGUI(final String inputHint) {
        String title = Config.BABY_TITLE.asStringTranslated()
                .replace("%sex%", isBoy ? Config.BOY.asString() : Config.GIRL.asString());

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(title)
                .text(inputHint)
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(state.getText()));
                    }
                    String result = state.getText().trim();
                    if (result.length() < 3 || result.equals(inputHint)) {
                        return List.of(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(() ->
                                        plugin.getServer().getScheduler().runTask(plugin,
                                                () -> openNamingGUI(Config.BABY_INVALID_NAME.asStringTranslated()))));
                    }
                    long procreation = System.currentTimeMillis();
                    player.getInventory().addItem(
                            plugin.createBaby(isBoy, result, procreation, villager.bukkit().getUniqueId()));
                    int reputation = Config.BABY_REPUTATION.asInt();
                    if (reputation > 1) villager.addMinorPositive(player.getUniqueId(), reputation);
                    villager.setProcreatingWith(null);
                    villager.setLastProcreation(procreation);
                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .open(player);
    }
}
