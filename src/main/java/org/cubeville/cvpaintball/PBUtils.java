package org.cubeville.cvpaintball;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public class PBUtils {

    public static void clearItemsFromInventory(PlayerInventory inv, List<ItemStack> items) {
        // clear out the chestplate
        ItemStack[] invContents = inv.getContents();
        for (int i = 0; i < invContents.length; i++) {
            ItemStack checkingItem = invContents[i];
            if (checkingItem == null) continue;
            for (ItemStack item : items) {
                if (checkingItem.isSimilar(item)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

}
