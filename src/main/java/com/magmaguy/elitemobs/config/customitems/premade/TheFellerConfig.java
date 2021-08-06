package com.magmaguy.elitemobs.config.customitems.premade;

import com.magmaguy.elitemobs.config.customitems.CustomItemsConfigFields;
import com.magmaguy.elitemobs.items.customitems.CustomItem;
import org.bukkit.Material;

import java.util.Arrays;

public class TheFellerConfig extends CustomItemsConfigFields {
    public TheFellerConfig() {
        super("the_feller",
                true,
                Material.DIAMOND_AXE,
                "&2The Feller",
                Arrays.asList("&aEven in your sleep,", "&ayou can feel this axe''s", "&asaplust"));
        setEnchantments(Arrays.asList("LOOT_BONUS_BLOCKS,4", "SILK_TOUCH,1", "DURABILITY,6", "DIG_SPEED,6", "VANISHING_CURSE,1"));
        setPotionEffects(Arrays.asList("FAST_DIGGING,1,self,continuous", "NIGHT_VISION,0,self,continuous"));
        setItemType(CustomItem.ItemType.unique);
    }
}
