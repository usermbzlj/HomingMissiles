package cn.yjj.homingmissiles.hud;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

final class HudProtocol {
    static final byte VERSION = 1;
    static final byte HELLO = 1;
    static final byte SET_HUD = 2;
    static final byte TOGGLE_HUD = 3;

    private HudProtocol() {
    }

    record ControlPayload(byte operation, boolean enabled) implements CustomPacketPayload {
        static final Type<ControlPayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("homingmissiles", "control"));
        static final StreamCodec<FriendlyByteBuf, ControlPayload> CODEC =
                CustomPacketPayload.codec(ControlPayload::write, ControlPayload::new);

        ControlPayload(FriendlyByteBuf buffer) {
            this(readOperation(buffer), buffer.readBoolean());
        }

        private static byte readOperation(FriendlyByteBuf buffer) {
            byte version = buffer.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported HomingMissiles HUD protocol " + version);
            }
            return buffer.readByte();
        }

        static ControlPayload hello() {
            return new ControlPayload(HELLO, false);
        }

        static ControlPayload toggle() {
            return new ControlPayload(TOGGLE_HUD, false);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeByte(VERSION);
            buffer.writeByte(operation);
            buffer.writeBoolean(enabled);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    record StatePayload(int flags, float lockProgress, float targetX, float targetY,
                        int activeMissiles, int threatCount, float threatProximity,
                        int threatSector) implements CustomPacketPayload {
        static final Type<StatePayload> ID = new Type<>(
                Identifier.fromNamespaceAndPath("homingmissiles", "hud_state"));
        static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
                CustomPacketPayload.codec(StatePayload::write, StatePayload::new);

        StatePayload(RegistryFriendlyByteBuf buffer) {
            this(readAndCheckVersion(buffer), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readFloat(),
                    buffer.readUnsignedByte());
        }

        private static int readAndCheckVersion(RegistryFriendlyByteBuf buffer) {
            int version = buffer.readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported HomingMissiles HUD protocol " + version);
            }
            return buffer.readUnsignedShort();
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeByte(VERSION);
            buffer.writeShort(flags);
            buffer.writeFloat(lockProgress);
            buffer.writeFloat(targetX);
            buffer.writeFloat(targetY);
            buffer.writeByte(activeMissiles);
            buffer.writeByte(threatCount);
            buffer.writeFloat(threatProximity);
            buffer.writeByte(threatSector);
        }

        boolean serverEnabled() { return (flags & 1) != 0; }
        boolean fullHudEnabled() { return (flags & (1 << 1)) != 0; }
        boolean gliding() { return (flags & (1 << 2)) != 0; }
        boolean aiming() { return (flags & (1 << 3)) != 0; }
        boolean targetPresent() { return (flags & (1 << 4)) != 0; }
        boolean locked() { return (flags & (1 << 5)) != 0; }
        boolean threatened() { return (flags & (1 << 6)) != 0; }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
