package me.matsubara.realisticvillagers.hologram;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class SpeechBubble {

    private final RealisticVillagers plugin;
    private final LivingEntity entity;
    private final String text;

    private TextDisplay display;
    private BukkitTask task;
    private boolean done = false;

    @SuppressWarnings("deprecation")
    public SpeechBubble(RealisticVillagers plugin, LivingEntity entity, String rawText) {
        this.plugin = plugin;
        this.entity = entity;

        String colorCode = cfg().getString("hologram.speech-bubble.text-color", "&0");
        this.text = ChatColor.translateAlternateColorCodes('&', colorCode) + ChatColor.stripColor(rawText);
    }

    private FileConfiguration cfg() {
        FileConfiguration c = plugin.getHologramConfig();
        return c != null ? c : new org.bukkit.configuration.file.YamlConfiguration();
    }

    public void start() {
        if (text == null || text.isEmpty()) return;

        FileConfiguration c = cfg();
        int  lineWidth  = c.getInt("hologram.speech-bubble.line-width",  150);
        float viewRange = (float) c.getDouble("hologram.speech-bubble.view-range", 10.0);
        boolean seeThrough = c.getBoolean("hologram.speech-bubble.see-through", true);
        Color bg = Color.fromARGB(
                c.getInt("hologram.speech-bubble.background.alpha", 255),
                c.getInt("hologram.speech-bubble.background.red",   255),
                c.getInt("hologram.speech-bubble.background.green", 255),
                c.getInt("hologram.speech-bubble.background.blue",  255));

        display = entity.getWorld().spawn(speechLocation(), TextDisplay.class, d -> {
            d.setText("");
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setDefaultBackground(false);
            d.setBackgroundColor(bg);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(lineWidth);
            d.setViewRange(viewRange);
            d.setDisplayWidth(4.0f);
            d.setDisplayHeight(4.0f);
            d.setSeeThrough(seeThrough);
            d.setPersistent(false);
        });

        int totalLen   = text.length();
        int charsPerTick = c.getInt("hologram.speech-bubble.chars-per-tick", 2);
        int lingerTicks  = c.getInt("hologram.speech-bubble.linger-ticks",  60);
        int[] revealed   = {0};

        task = new BukkitRunnable() {
            int linger = 0;

            @Override
            public void run() {
                if (!entity.isValid() || display.isDead()) {
                    remove();
                    cancel();
                    return;
                }

                display.teleport(speechLocation());

                if (!done) {
                    revealed[0] = Math.min(revealed[0] + charsPerTick, totalLen);
                    display.setText(text.substring(0, revealed[0]));
                    if (revealed[0] >= totalLen) done = true;
                } else {
                    if (++linger >= lingerTicks) {
                        remove();
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void remove() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (display != null && !display.isDead()) {
            display.remove();
            display = null;
        }
    }

    private org.bukkit.Location speechLocation() {
        org.bukkit.Location loc = entity.getLocation().clone();
        double offset = cfg().getDouble("hologram.speech-bubble.height-offset", 0.8);
        loc.setY(loc.getY() + entity.getHeight() + offset);
        return loc;
    }
}
