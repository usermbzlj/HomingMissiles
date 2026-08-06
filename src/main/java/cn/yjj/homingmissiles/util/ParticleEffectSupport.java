package cn.yjj.homingmissiles.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Bridges particle data-type changes between supported Paper API revisions.
 */
public final class ParticleEffectSupport {
    private ParticleEffectSupport() {
    }

    public static boolean spawnFlash(World world, Location location, Color color) {
        Class<?> dataType = Particle.FLASH.getDataType();
        if (dataType == Color.class) {
            world.spawnParticle(Particle.FLASH, location, 1, 0.0, 0.0, 0.0, 0.0, color);
            return true;
        }
        if (dataType == Void.class) {
            world.spawnParticle(Particle.FLASH, location, 1, 0.0, 0.0, 0.0, 0.0);
            return true;
        }
        return false;
    }
}
