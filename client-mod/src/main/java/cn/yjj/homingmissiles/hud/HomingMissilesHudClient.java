package cn.yjj.homingmissiles.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class HomingMissilesHudClient implements ClientModInitializer {
    private static final HudState STATE = new HudState();
    private static final FlightHudRenderer RENDERER = new FlightHudRenderer(STATE);
    private static final KeyMapping TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.homingmissiles_hud.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.MISC));

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(HudProtocol.ControlPayload.ID, HudProtocol.ControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HudProtocol.StatePayload.ID, HudProtocol.StatePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                HudProtocol.StatePayload.ID, (payload, context) -> STATE.accept(payload));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendIfSupported(
                HudProtocol.ControlPayload.hello()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATE.reset();
            RENDERER.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) {
                sendIfSupported(HudProtocol.ControlPayload.toggle());
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("homingmissiles_hud", "flight_hud"),
                RENDERER::render);
    }

    private static void sendIfSupported(HudProtocol.ControlPayload payload) {
        if (ClientPlayNetworking.canSend(HudProtocol.ControlPayload.ID)) {
            ClientPlayNetworking.send(payload);
        }
    }
}
