package cn.yjj.homingmissiles.item;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import cn.yjj.homingmissiles.config.SettingsManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;

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
        trySetGlint(meta);
        bow.setItemMeta(meta);
        return bow;
    }

    private void trySetGlint(ItemMeta meta) {
        try {
            Method method = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
            method.invoke(meta, Boolean.TRUE);
        } catch (ReflectiveOperationException ignored) {
            // API较旧或服务端实现缺失时，仅失去强制附魔光泽，不影响物品识别。
        }
    }
}
