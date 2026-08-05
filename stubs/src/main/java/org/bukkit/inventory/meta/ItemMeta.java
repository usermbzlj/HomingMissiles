package org.bukkit.inventory.meta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.enchantments.Enchantment;
import java.util.List;
public interface ItemMeta {
 void setDisplayName(String name);
 void setLore(List<String> lore);
 boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction);
 void setUnbreakable(boolean unbreakable);
 PersistentDataContainer getPersistentDataContainer();
}
