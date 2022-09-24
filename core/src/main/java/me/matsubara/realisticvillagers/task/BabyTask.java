package me.matsubara.realisticvillagers.task;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class BabyTask extends BukkitRunnable {

    private final RealisticVillagers plugin;
    private final IVillagerNPC villager;
    private final Player player;

    private int count = 0;

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public BabyTask(RealisticVillagers plugin, Villager villager, Player player) {
        this.plugin = plugin;
        this.villager = plugin.getConverter().getNPC(villager).get();
        this.player = player;
    }

    @Override
    public void run() {
        if (++count == 10) {
            boolean isBoy = ThreadLocalRandom.current().nextBoolean();
            new AnvilGUI.Builder()
                    .preventClose()
                    .title(Config.BABY_TITLE.asStringTranslated().replace("%sex%", isBoy ? Config.BOY.asString() : Config.GIRL.asString()))
                    .text(Config.BABY_TEXT.asStringTranslated())
                    .itemLeft(new ItemStack(Material.PAPER))
                    .onComplete((opener, result) -> {

                        long procreation = System.currentTimeMillis();
                        opener.getInventory().addItem(plugin.createBaby(isBoy, result, procreation, villager.bukkit().getUniqueId()));

                        villager.addMinorPositive(opener.getUniqueId(), Config.BABY_REPUTATION.asInt());
                        villager.setProcreatingWith(null);

                        villager.setLastProcreation(procreation);
                        return AnvilGUI.Response.close();
                    })
                    .plugin(plugin)
                    .open(player);
            cancel();
            return;
        }

        villager.jumpIfPossible();
        player.spawnParticle(Particle.HEART, villager.bukkit().getEyeLocation(), 3, 0.1d, 0.1d, 0.1d);
    }
}