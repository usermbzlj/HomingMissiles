package org.bukkit.inventory;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
 public ItemStack(Material material) {}
 public Material getType() { return null; }
 public boolean hasItemMeta() { return false; }
 public ItemMeta getItemMeta() { return null; }
 public boolean setItemMeta(ItemMeta meta) { return true; }
}
