package cn.yjj.homingmissiles.item;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import cn.yjj.homingmissiles.config.SettingsManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class HomingBowFactory {
    private final SettingsManager settingsManager;
    private final NamespacedKey homingBowKey;

    public HomingBowFactory(HomingMissilesPlugin plugin, SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.homingBowKey = new NamespacedKey(plugin, "homing_bow");
    }

    public boolean isHomingBow(ItemStack item) {
        if (item == null || item.getType() != Material.BOW || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte flag = meta.getPersistentDataContainer().get(homingBowKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public ItemStack create() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.setDisplayName(settingsManager.current().bowName());
        meta.setLore(settingsManager.current().bowLore());
        meta.getPersistentDataContainer().set(homingBowKey, PersistentDataType.BYTE, (byte) 1);
        applyConfiguredEnchantments(meta);
        bow.setItemMeta(meta);
        return bow;
    }

    private void applyConfiguredEnchantments(ItemMeta meta) {
        var settings = settingsManager.current();
        if (settings.bowFlame()) {
            meta.addEnchant(Enchantment.FLAME, 1, true);
        }
        if (settings.bowInfinity()) {
            meta.addEnchant(Enchantment.INFINITY, 1, true);
        }
        if (settings.bowUnbreakable()) {
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.setUnbreakable(true);
        }
        if (settings.bowPowerLevel() > 0) {
            meta.addEnchant(Enchantment.POWER, settings.bowPowerLevel(), true);
        }
    }
}
