package org.bukkit.inventory;
import java.util.HashMap;
public interface PlayerInventory {
 HashMap<Integer, ItemStack> addItem(ItemStack... items);
 ItemStack getItemInMainHand();
}
