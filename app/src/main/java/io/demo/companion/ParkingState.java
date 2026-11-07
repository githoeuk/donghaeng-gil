package io.demo.companion;

/** Conservative local policy: a recent successful read, P, exact zero speed, and no UX restrictions. */
public final class ParkingState {
    public static final long FRESH_MS = 3000, HOLD_MS = 1000;
    // AAOS VehicleGear.GEAR_PARK; kept Android-free for deterministic tests.
    public static final int PARK = 4;
    private long session, revision, readAt = -1, safeSince = -1;
    // AAOS CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE.
    static final int NO_TEXT_MESSAGE = 0x80;
    private long messageReadAt = -1;
    private boolean active, safe;
    public void unpark(long token, String message) {
        if (!accepts(token)) return;
        revision++;
        safe = false;
        safeSince = -1;
        readAt = -1;
        reason = message;
    }
    public boolean messagesAllowed(long now) {
        return active && messageReadAt >= 0 && now >= messageReadAt && now - messageReadAt <= FRESH_MS;
    }
    private String reason = "차량 상태 확인 전";
    public long open() { session++; active = true; reset("차량 상태 확인 중"); return session; }
    public void close() { active = false; session++; reset("차량 상태 확인 중지"); }
    public boolean accepts(long token) { return active && session == token; }
    public long revision() { return revision; }
    public void fail(long token, String message) { if (accepts(token)) reset(message); }
    private void reset(String message) { revision++; safe = false; safeSince = -1; readAt = -1; messageReadAt = -1; reason = message; }
    public boolean sample(long token, long requestRevision, long requestedAt, long now,
            float speed, int gear, boolean uxKnown, boolean requiresDO, int restrictions) {
        if (!accepts(token) || requestRevision != revision) return false;
        if (requestedAt < 0 || now < requestedAt || now - requestedAt > FRESH_MS) {
            reset("차량 상태 갱신 지연"); return false;
        }
        if (!Float.isFinite(speed) || !uxKnown || restrictions < 0 || gear <= 0) { reset("유효한 차량 상태를 받지 못했습니다"); return false; }
        if (speed != 0f || gear != PARK || requiresDO || restrictions != 0) {
            unpark(token, "주행·기어·플랫폼 제한으로 설정 잠김");
            messageReadAt = (restrictions & NO_TEXT_MESSAGE) == 0 ? requestedAt : -1;
            return false;
        }
        if (requestedAt < readAt) return false;
        if (!safe || now - readAt > FRESH_MS) safeSince = requestedAt;
        messageReadAt = requestedAt; safe = true; readAt = requestedAt; reason = "P · 속도 0 · 플랫폼 제한 없음";
        return true;
    }
    public boolean allowed(long now) {
        return active && safe && now >= readAt && now - readAt <= FRESH_MS
                && now >= safeSince && now - safeSince >= HOLD_MS;
    }
    public String description(long now) {
        if (active && safe && (now < readAt || now - readAt > FRESH_MS)) return "차량 상태 갱신 지연 · 시연 잠김";
        if (active && safe && !allowed(now)) return "정차 상태 유지 확인 중";
        return reason;
    }
}
