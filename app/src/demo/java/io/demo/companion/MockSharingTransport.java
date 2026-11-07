package io.demo.companion;

import static io.demo.companion.SharingModels.*;

/** In-memory fictional scenario only. No SDK, location sensor, network, or disk access. */
public final class MockSharingTransport implements SharingTransport {
    public interface Clock { long elapsed(); long wall(); }
    private final Clock clock;
    private final SharingStore store = new SharingStore();
    private Listener listener;
    private String notice = "정차 중 사용하는 데모입니다. 모든 차량·메시지는 모의 데이터입니다.";
    private boolean frozen, closed;
    private long lastTick = -1, sequence, messageSequence, pendingAt = -1, pendingToken;
    private String pendingMessage;
    private boolean sentFromOverview;
    public MockSharingTransport(Clock clock) { this.clock = clock; }
    @Override public void setListener(Listener value) { listener = value; emit(); }
    @Override public Snapshot snapshot() { return store.snapshot(notice, true); }
    private void emit() { if (!closed && listener != null) listener.onChanged(snapshot()); }
    @Override public void createGroup() { enter(); }
    @Override public void joinGroup(String code) {
        if (closed) return;
        if (!"DEMO42".equals(code.trim())) { notice = "데모 초대 코드는 DEMO42입니다. 실제 그룹에는 연결되지 않습니다."; emit(); return; }
        enter();
    }
    private void enter() {
        if (closed) return;
        store.enter("demo-group", "DEMO42"); store.connect(true); frozen = false;
        pendingMessage = null; lastTick = -1;
        notice = "가상 그룹에 들어왔습니다. 체험 데이터는 외부로 전송하지 않습니다."; emit();
    }
    @Override public void startSharing() {
        if (closed) return;
        store.start(); lastTick = -1;
        notice = store.sharing() ? "모의 공유 중 · 실제 내 차량 정보는 사용하지 않습니다." : "그룹 참여와 모의 연결을 먼저 확인하세요.";
        tick(); emit();
    }
    @Override public void stopSharing() {
        if (closed) return;
        store.stop(); pendingMessage = null; notice = "공유 중지 · 모의 차량 수신과 메시지 전송이 중단됐습니다."; emit();
    }
    @Override public void leaveGroup() {
        if (closed) return;
        store.leave(); pendingMessage = null; notice = "그룹 나감 · 메모리의 그룹 정보가 삭제됐습니다."; emit();
    }
    public void disconnect() {
        if (closed) return;
        store.disconnect(); pendingMessage = null;
        notice = "모의 연결 끊김 · 마지막 정보는 최신이 아닐 수 있습니다. 자동 재전송 없음"; emit();
    }
    public void reconnect() {
        if (closed) return;
        store.connect(true); frozen = false; lastTick = -1;
        notice = store.inGroup() ? "모의 재연결 · 새 상태를 수신합니다. 실패 메시지는 다시 보내세요." : "먼저 그룹에 참여하세요."; tick(); emit();
    }
    public void freeze() { if (!closed) { frozen = true; notice = "갱신 중단 시험 · 15초가 지나면 오래된 정보로 표시됩니다."; emit(); } }
    public void tick() {
        if (closed) return;
        long now = clock.elapsed();
        refreshVehicles(now);
        if (pendingMessage != null && now - pendingAt >= 2000) store.delivery(pendingToken, pendingMessage, Delivery.DELIVERED);
        if (pendingMessage != null && now - pendingAt >= 4000) {
            store.delivery(pendingToken, pendingMessage, Delivery.ACKNOWLEDGED);
            store.message(pendingToken, new Message("reply-" + pendingMessage, "모의 차량 가", MessageKind.OK, clock.wall(), Delivery.DELIVERED));
            pendingMessage = null;
        }
        emit();
    }
    Snapshot refreshOverview() {
        if (sentFromOverview && pendingMessage != null) tick();
        else if (!closed) refreshVehicles(clock.elapsed());
        return snapshot();
    }
    private void refreshVehicles(long now) {
        if (store.sharing() && store.connected() && !frozen && (lastTick < 0 || now - lastTick >= 5000)) {
            lastTick = now; sequence++;
            store.vehicle(store.token(), vehicle("demo-a", "모의 차량 가", "demo-cafe", "데모 카페", 720, sequence));
            store.vehicle(store.token(), vehicle("demo-b", "모의 차량 나", "demo-cafe", "데모 카페", 900, sequence));
            store.vehicle(store.token(), vehicle("demo-c", "모의 차량 다", "demo-rest", "데모 휴게소", 360, sequence));
        }
    }
    private Vehicle vehicle(String id, String name, String destination, String destinationName, int seconds, long seq) {
        int step = (int) (seq % 12);
        return new Vehicle(id, name, 37.40 + step * 0.0001, 127.09, "가상시 동행로 " + (10 + step),
                destination, destinationName, "가상시 만남로 " + (destination.equals("demo-cafe") ? 100 : 200),
                seconds - step * 5, seconds * 5 - step * 20, seq, clock.wall(), clock.elapsed());
    }
    void receiveExampleMessage() {
        if (closed || !store.inGroup() || !store.sharing() || !store.connected()) return;
        store.message(store.token(), new Message("inbound-" + (++messageSequence),
                "모의 차량 나", MessageKind.REST, clock.wall(), Delivery.DELIVERED));
        emit();
    }
    void sendFromOverview(MessageKind kind) {
        if (pendingMessage != null) return;
        send(kind);
        sentFromOverview = pendingMessage != null;
    }
    @Override public void send(MessageKind kind) {
        if (closed) return;
        if (!store.inGroup() || !store.sharing() || !store.connected()) {
            notice = "보내지 못했습니다 · 그룹·공유·모의 연결을 확인하세요."; emit(); return;
        }
        if (pendingMessage != null) { notice = "앞선 모의 메시지의 결과를 기다려 주세요."; emit(); return; }
        sentFromOverview = false;
        pendingToken = store.token(); pendingMessage = "demo-message-" + (++messageSequence); pendingAt = clock.elapsed();
        store.message(pendingToken, new Message(pendingMessage, "나 (모의)", kind, clock.wall(), Delivery.PENDING));
        notice = "모의 전송 대기 · 실제 상대에게 전송하지 않습니다."; emit();
    }
    @Override public void acknowledge(String id) {
        if (!closed) { store.delivery(store.token(), id, Delivery.ACKNOWLEDGED); emit(); }
    }
    @Override public void close() { if (!closed) { store.leave(); pendingMessage = null; listener = null; closed = true; } }
}
