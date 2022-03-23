package com.magmaguy.elitemobs.items.customloottable;

import com.magmaguy.elitemobs.config.ItemSettingsConfig;
import com.magmaguy.elitemobs.config.customarenas.CustomArenasConfigFields;
import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfigFields;
import com.magmaguy.elitemobs.config.customquests.CustomQuestsConfigFields;
import com.magmaguy.elitemobs.config.customtreasurechests.CustomTreasureChestConfigFields;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.util.*;

public class CustomLootTable implements Serializable {

    @Getter
    private final List<CustomLootEntry> entries = new ArrayList<>();
    private Map<Integer, List<CustomLootEntry>> waveRewards = new HashMap();

    public CustomLootTable() {
    }

    public CustomLootTable(CustomBossesConfigFields customBossesConfigFields) {
        List<String> rawStrings = customBossesConfigFields.getUniqueLootList();
        parseConfig(rawStrings, customBossesConfigFields.getFilename());
    }

    public CustomLootTable(CustomTreasureChestConfigFields treasureChestConfigFields) {
        List<String> rawStrings = treasureChestConfigFields.getLootList();
        parseConfig(rawStrings, treasureChestConfigFields.getFilename());
    }

    public CustomLootTable(CustomQuestsConfigFields questsConfigFields) {
        List<String> rawStrings = questsConfigFields.getCustomRewardsList();
        parseConfig(rawStrings, questsConfigFields.getFilename());
    }

    public CustomLootTable(CustomArenasConfigFields customArenasConfigFields) {
        List<String> rawStrings = customArenasConfigFields.getRawArenaRewards();
        parseConfig(rawStrings, customArenasConfigFields.getFilename());
    }

    private void parseConfig(List<String> lootTable, String filename) {
        for (String rawString : lootTable)
            switch (rawString.split(":")[0].toLowerCase()) {
                case "minecraft":
                    new VanillaCustomLootEntry(entries, rawString, filename);
                    break;
                case "scrap":
                case "upgrade_item":
                    new SpecialCustomLootEntry(entries, rawString, filename);
                    break;
                default:
                    if (rawString.contains("currencyAmount="))
                        new CurrencyCustomLootEntry(entries, rawString, filename);
                    else if (rawString.contains("material="))
                        new VanillaCustomLootEntry(entries, rawString, filename);
                    else
                        new EliteCustomLootEntry(entries, rawString, filename);
            }
        for (CustomLootEntry customLootEntry : entries) {
            if (customLootEntry.getWave() > 0) {
                if (waveRewards.get(customLootEntry.getWave()) != null)
                    waveRewards.get(customLootEntry.getWave()).add(customLootEntry);
                else
                    waveRewards.put(customLootEntry.getWave(), Arrays.asList(customLootEntry));
            }
        }
    }

    public void generateCurrencyEntry(int currencyAmount) {
        new CurrencyCustomLootEntry(entries, currencyAmount);
    }

    public void generateEliteEntry(ItemStack itemStack) {
        new ItemStackCustomLootEntry(entries, itemStack);
    }

    public void bossDrop(Player player, int level, Location dropLocation) {
        for (CustomLootEntry customLootEntry : entries)
            if (customLootEntry.willDrop(player))
                if (ItemSettingsConfig.isPutLootDirectlyIntoPlayerInventory())
                    customLootEntry.directDrop(level, player);
                else
                    customLootEntry.locationDrop(level, player, dropLocation);
    }

    public void treasureChestDrop(Player player, int chestLevel, Location dropLocation) {
        for (CustomLootEntry customLootEntry : entries)
            if (customLootEntry.willDrop(player)) {
                if (ItemSettingsConfig.isPutLootDirectlyIntoPlayerInventory())
                    customLootEntry.directDrop(chestLevel * 10, player);
                else
                    customLootEntry.locationDrop(chestLevel * 10, player, dropLocation);
            }
    }

    public void questDrop(Player player, int questRewardLevel) {
        for (CustomLootEntry customLootEntry : entries)
            if (customLootEntry.willDrop(player))
                customLootEntry.directDrop(questRewardLevel, player);
    }

    public void arenaReward(Player player, int wave) {
        if (waveRewards.get(wave) == null) return;
        waveRewards.get(wave).forEach(reward -> {
            if (reward.willDrop(player))
                reward.directDrop(reward.getItemLevel(), player);
        });
    }
}