package cn.yjj.homingmissiles.hud;

final class HudState {
    private static final long STALE_NANOS = 2_000_000_000L;

    private HudProtocol.StatePayload latest = new HudProtocol.StatePayload(0, 0, 0, 0, 0, 0, 0, 0);
    private long receivedAt;

    void accept(HudProtocol.StatePayload payload) {
        latest = payload;
        receivedAt = System.nanoTime();
    }

    HudProtocol.StatePayload latest() {
        return latest;
    }

    boolean live() {
        return receivedAt != 0 && System.nanoTime() - receivedAt < STALE_NANOS;
    }

    void reset() {
        receivedAt = 0;
        latest = new HudProtocol.StatePayload(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
