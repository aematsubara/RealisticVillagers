package me.matsubara.realisticvillagers.entity;

import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.TextDisplay;

public interface Nameable {

    TextDisplay getNametagEntity();

    void setNametagEntity(TextDisplay display);

    BlockDisplay getNametagItemEntity();

    void setNametagItemEntity(BlockDisplay display);

    int getCurrentAmountOfLines();

    void setCurrentAmountOfLines(int currentAmountOfLines);
}