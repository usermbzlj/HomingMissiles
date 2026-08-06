package cn.yjj.homingmissiles.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** A client-side HMD whose optical origin is always the vanilla crosshair origin. */
final class FlightHudRenderer {
    private static final int GREEN = 0xFF48FF69;
    private static final int AMBER = 0xFFFFB22F;
    private static final int RED = 0xFFFF4538;

    private final HudState state;
    private float fullVisibility;
    private float aimVisibility;
    private float smoothYaw;
    private float smoothPitch;
    private double smoothSpeed;
    private double smoothAltitude;
    private double smoothClearance;
    private float smoothTargetX;
    private float smoothTargetY;
    private float smoothProgress;
    private boolean initialized;

    FlightHudRenderer(HudState state) {
        this.state = state;
    }

    void render(GuiGraphics graphics, DeltaTracker ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        HudProtocol.StatePayload packet = state.latest();
        boolean live = player != null && state.live() && packet.serverEnabled();
        boolean showFull = live && packet.fullHudEnabled() && (packet.gliding() || player.isFallFlying());
        boolean showAim = live && packet.aiming();

        fullVisibility = approach(fullVisibility, showFull ? 1.0f : 0.0f, showFull ? 0.16f : 0.22f);
        aimVisibility = approach(aimVisibility, showAim ? 1.0f : 0.0f, showAim ? 0.25f : 0.30f);
        if (player == null || (fullVisibility < 0.01f && aimVisibility < 0.01f)) {
            initialized = false;
            return;
        }

        updateTelemetry(player, packet);

        // Same invariant used by FlightHud: screen midpoint comes directly from the
        // current scaled GUI dimensions on every rendered frame.
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int halfWidth = Math.max(72, Math.round(graphics.guiWidth() * 0.30f));
        int halfHeight = Math.max(44, Math.round(graphics.guiHeight() * 0.30f));

        if (fullVisibility >= 0.01f) {
            drawFlightHud(graphics, minecraft, centerX, centerY, halfWidth, halfHeight,
                    packet, alpha(GREEN, fullVisibility));
        }
        if (aimVisibility >= 0.01f) {
            drawAim(graphics, minecraft, centerX, centerY, halfWidth, halfHeight,
                    packet, aimVisibility);
        }
    }

    void reset() {
        initialized = false;
        fullVisibility = 0;
        aimVisibility = 0;
    }

    private void updateTelemetry(LocalPlayer player, HudProtocol.StatePayload packet) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length() * 20.0;
        double altitude = player.getY();
        double clearance;
        try {
            int ground = player.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(player.getX()), Mth.floor(player.getZ()));
            clearance = Math.max(0.0, altitude - ground);
        } catch (RuntimeException ignored) {
            clearance = 0.0;
        }

        if (!initialized) {
            smoothYaw = yaw;
            smoothPitch = pitch;
            smoothSpeed = speed;
            smoothAltitude = altitude;
            smoothClearance = clearance;
            smoothTargetX = packet.targetX();
            smoothTargetY = packet.targetY();
            smoothProgress = packet.lockProgress();
            initialized = true;
            return;
        }
        smoothYaw += Mth.wrapDegrees(yaw - smoothYaw) * 0.22f;
        smoothPitch = lerp(smoothPitch, pitch, 0.22f);
        smoothSpeed = lerp(smoothSpeed, speed, 0.18);
        smoothAltitude = lerp(smoothAltitude, altitude, 0.18);
        smoothClearance = lerp(smoothClearance, clearance, 0.18);
        smoothTargetX = lerp(smoothTargetX, packet.targetX(), 0.32f);
        smoothTargetY = lerp(smoothTargetY, packet.targetY(), 0.32f);
        smoothProgress = lerp(smoothProgress, packet.lockProgress(), 0.24f);
    }

    private void drawFlightHud(GuiGraphics g, Minecraft mc, int cx, int cy,
                               int hw, int hh, HudProtocol.StatePayload packet, int color) {
        int left = cx - hw;
        int right = cx + hw;
        int top = cy - hh;
        int bottom = cy + hh;
        int corner = Math.max(8, Math.min(hw, hh) / 6);

        corner(g, left, top, corner, 1, 1, color);
        corner(g, right, top, corner, -1, 1, color);
        corner(g, left, bottom, corner, 1, -1, color);
        corner(g, right, bottom, corner, -1, -1, color);

        int pitchOffset = Math.round(clamp(-smoothPitch / 45.0f, -1, 1) * hh * 0.48f);
        for (int degree = -30; degree <= 30; degree += 10) {
            int y = cy + pitchOffset + Math.round(degree / 30.0f * hh * 0.55f);
            if (y < top + 8 || y > bottom - 8) continue;
            int width = degree == 0 ? 24 : 13;
            hLine(g, cx - width - 7, cx - 7, y, color);
            hLine(g, cx + 7, cx + width + 7, y, color);
        }

        int heading = Math.floorMod(Math.round(smoothYaw + 180.0f), 360);
        String headingText = String.format(Locale.ROOT, "%03d", heading);
        g.drawCenteredString(mc.font, headingText, cx, top + 4, color);
        hLine(g, cx - 20, cx - 6, top + 14, color);
        hLine(g, cx + 6, cx + 20, top + 14, color);
        vLine(g, cx, top + 11, top + 16, color);

        g.drawString(mc.font, "SPD", left + 7, cy - 17, color, false);
        g.drawString(mc.font, String.format(Locale.ROOT, "%03.0f", smoothSpeed), left + 7, cy - 7, color, false);
        String altitude = String.format(Locale.ROOT, "%4.0f", smoothAltitude);
        g.drawString(mc.font, "ALT", right - 7 - mc.font.width("ALT"), cy - 17, color, false);
        g.drawString(mc.font, altitude, right - 7 - mc.font.width(altitude), cy - 7, color, false);
        g.drawCenteredString(mc.font, String.format(Locale.ROOT, "AGL %.0f", smoothClearance), cx, bottom - 12, color);

        String hardpoints = "◆".repeat(Math.min(4, packet.activeMissiles()))
                + "◇".repeat(Math.max(0, 4 - Math.min(4, packet.activeMissiles())));
        g.drawString(mc.font, hardpoints, left + 7, bottom - 12, color, false);

        drawFlightPath(g, cx, cy, hw, hh, color);
        if (packet.threatened()) {
            drawThreat(g, mc, cx, cy, hw, hh, packet);
        }
    }

    private void drawFlightPath(GuiGraphics g, int cx, int cy, int hw, int hh, int color) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 velocity = player.getDeltaMovement();
        double horizontal = Math.hypot(velocity.x, velocity.z);
        if (velocity.lengthSqr() < 1.0E-6) return;
        float velocityYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float velocityPitch = (float) -Math.toDegrees(Math.atan2(velocity.y, horizontal));
        float yawOffset = Mth.wrapDegrees(velocityYaw - smoothYaw);
        float pitchOffset = velocityPitch - smoothPitch;
        int x = cx + Math.round(clamp(-yawOffset / 42.0f, -1, 1) * hw * 0.55f);
        int y = cy + Math.round(clamp(pitchOffset / 34.0f, -1, 1) * hh * 0.55f);
        outline(g, x - 3, y - 3, x + 3, y + 3, color);
        hLine(g, x - 8, x - 4, y, color);
        hLine(g, x + 4, x + 8, y, color);
        vLine(g, x, y + 4, y + 7, color);
    }

    private void drawAim(GuiGraphics g, Minecraft mc, int cx, int cy, int hw, int hh,
                         HudProtocol.StatePayload packet, float visibility) {
        int markerColor = alpha(packet.locked() ? GREEN : AMBER, visibility);
        int x = cx;
        int y = cy;
        if (packet.targetPresent()) {
            x += Math.round(smoothTargetX * hw * 0.42f);
            y += Math.round(smoothTargetY * hh * 0.42f);
        }
        int radius = packet.locked() ? 9 : 12;
        bracket(g, x, y, radius, markerColor);
        String label = packet.locked() ? "LOCK" : packet.targetPresent()
                ? String.format(Locale.ROOT, "%02.0f%%", smoothProgress * 100.0f)
                : "ACQUIRE";
        g.drawCenteredString(mc.font, label, x, y + radius + 5, markerColor);

        int barWidth = 44;
        int barY = cy + Math.max(26, hh / 3);
        g.fill(cx - barWidth / 2, barY, cx + barWidth / 2, barY + 2, alpha(0xFF173C22, visibility));
        int filled = Math.round(barWidth * clamp(smoothProgress, 0, 1));
        g.fill(cx - barWidth / 2, barY, cx - barWidth / 2 + filled, barY + 2, markerColor);
    }

    private void drawThreat(GuiGraphics g, Minecraft mc, int cx, int cy, int hw, int hh,
                            HudProtocol.StatePayload packet) {
        double angle = packet.threatSector() * Math.PI / 4.0;
        int x = cx + (int) Math.round(-Math.sin(angle) * hw * 0.82);
        int y = cy + (int) Math.round(-Math.cos(angle) * hh * 0.82);
        int base = packet.threatProximity() >= 0.65f ? RED : AMBER;
        int color = alpha(base, fullVisibility);
        bracket(g, x, y, 6, color);
        String warning = packet.threatCount() > 1 ? "MISSILE ×" + packet.threatCount() : "MISSILE";
        g.drawCenteredString(mc.font, warning, cx, cy - hh + 19, color);
    }

    private static void corner(GuiGraphics g, int x, int y, int length, int sx, int sy, int color) {
        hLine(g, x, x + sx * length, y, color);
        vLine(g, x, y, y + sy * length, color);
    }

    private static void bracket(GuiGraphics g, int x, int y, int r, int color) {
        int arm = Math.max(3, r / 2);
        corner(g, x - r, y - r, arm, 1, 1, color);
        corner(g, x + r, y - r, arm, -1, 1, color);
        corner(g, x - r, y + r, arm, 1, -1, color);
        corner(g, x + r, y + r, arm, -1, -1, color);
    }

    private static void outline(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        hLine(g, left, right, top, color);
        hLine(g, left, right, bottom, color);
        vLine(g, left, top, bottom, color);
        vLine(g, right, top, bottom, color);
    }

    private static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, color);
    }

    private static void vLine(GuiGraphics g, int x, int y1, int y2, int color) {
        g.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, color);
    }

    private static int alpha(int color, float multiplier) {
        int original = color >>> 24;
        int alpha = Math.round(original * clamp(multiplier, 0, 1));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float approach(float current, float target, float factor) {
        float next = current + (target - current) * factor;
        return Math.abs(next - target) < 0.004f ? target : next;
    }

    private static float lerp(float current, float target, float factor) {
        return current + (target - current) * factor;
    }

    private static double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
