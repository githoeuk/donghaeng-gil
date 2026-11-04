package io.demo.companion;

/** Consent lifetime is in-memory. Visibility alone never starts or restores a sharing session. */
public final class SharingLifetime {
    public enum Phase { IDLE, ARMED, STARTING, ACTIVE }
    private Phase phase = Phase.IDLE;
    private long generation, armedAt, startedAt;
    public Phase phase() { return phase; }
    public boolean retaining() { return phase != Phase.IDLE; }
    public long arm(boolean visible, boolean parked, boolean permissions, boolean grouped, long now) {
        if (retaining() || !visible || !parked || !permissions || !grouped) return -1;
        armedAt = now; phase = Phase.ARMED; return ++generation;
    }
    public boolean begin(long token, boolean permissions, boolean parked, long now) {
        if (phase != Phase.ARMED || token != generation) return false;
        if (!permissions || !parked || now < armedAt || now - armedAt > 3000) { stop(); return false; }
        startedAt = now; phase = Phase.STARTING; return true;
    }
    public void confirmed() { if (phase == Phase.STARTING) phase = Phase.ACTIVE; }
    public boolean expired(long now) {
        return phase == Phase.STARTING ? now < startedAt || now-startedAt > 10000
                : phase == Phase.ACTIVE && (now < startedAt || now-startedAt >= 7200000);
    }
    public void stop() { generation++; phase = Phase.IDLE; }
}
