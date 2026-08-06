package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.util.ParticleEffectSupport;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class ParticleCompatibilityTest {
    private static final class RecordingWorld implements World {
        private Particle particle;
        private Object data;

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public List<Player> getPlayers() {
            return List.of();
        }

        @Override
        public List<Entity> getEntities() {
            return List.of();
        }

        @Override
        public void spawnParticle(Particle particle, Location location, int count,
                                  double offsetX, double offsetY, double offsetZ, double extra) {
            this.particle = particle;
            this.data = null;
        }

        @Override
        public <T> void spawnParticle(Particle particle, Location location, int count,
                                      double offsetX, double offsetY, double offsetZ,
                                      double extra, T data) {
            this.particle = particle;
            this.data = data;
        }

        @Override
        public void playSound(Location location, Sound sound, float volume, float pitch) {
        }

        @Override
        public Item dropItemNaturally(Location location, ItemStack item) {
            return null;
        }
    }

    public static void main(String[] args) {
        RecordingWorld world = new RecordingWorld();
        Color color = Color.fromRGB(255, 232, 168);
        if (!ParticleEffectSupport.spawnFlash(world, null, color)) {
            throw new AssertionError("Paper 1.21.11 FLASH should be supported");
        }
        if (world.particle != Particle.FLASH || world.data != color) {
            throw new AssertionError("FLASH must carry its required Color data");
        }
        System.out.println("ParticleCompatibilityTest: PASS");
    }
}
