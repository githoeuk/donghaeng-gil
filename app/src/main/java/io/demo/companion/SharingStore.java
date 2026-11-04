package io.demo.companion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import static io.demo.companion.SharingModels.*;

/** Group membership lifetime and inbound ordering, independent of networking. */
public final class SharingStore {
    private long generation;
    private String group = "", invite = "";
    private boolean sharing;
    private Connection connection = Connection.DISCONNECTED;
    private final Map<String, Vehicle> vehicles = new LinkedHashMap<>();
    private final Map<String, Message> messages = new LinkedHashMap<>();
    public long enter(String groupId, String inviteCode) {
        leave(); group = groupId; invite = inviteCode; return generation;
    }
    public long token() { return generation; }
    public boolean inGroup() { return !group.isEmpty(); }
    public boolean sharing() { return sharing; }
    public boolean connected() { return connection == Connection.CONNECTED; }
    public void connect(boolean value) { connection = value && inGroup() ? Connection.CONNECTED : Connection.DISCONNECTED; }
    public void start() { if (inGroup() && connected()) sharing = true; }
    public void stop() { sharing = false; generation++; vehicles.clear(); failPending(); }
    public void leave() { stop(); group = ""; invite = ""; connection = Connection.DISCONNECTED; messages.clear(); }
    private void failPending() {
        for (Map.Entry<String, Message> entry : messages.entrySet())
            if (entry.getValue().delivery == Delivery.PENDING) entry.setValue(entry.getValue().withDelivery(Delivery.FAILED));
    }
    public void disconnect() { connect(false); generation++; failPending(); }
    private boolean accepts(long token) { return generation == token && inGroup() && sharing && connected(); }
    public boolean vehicle(long token, Vehicle vehicle) {
        if (!accepts(token) || vehicle == null || vehicle.id == null || vehicle.id.isEmpty()
                || !JourneyState.validCoordinates(vehicle.latitude, vehicle.longitude)) return false;
        Vehicle old = vehicles.get(vehicle.id);
        if (old != null && old.sequence >= vehicle.sequence) return false;
        vehicles.put(vehicle.id, vehicle); return true;
    }
    public boolean message(long token, Message message) {
        if (!accepts(token) || message == null || message.id == null || message.id.isEmpty() || messages.containsKey(message.id)) return false;
        // This UI shows recent messages, not an unbounded trip history.
        if (messages.size() >= 50) messages.remove(messages.keySet().iterator().next());
        messages.put(message.id, message); return true;
    }
    public boolean delivery(long token, String id, Delivery delivery) {
        if (!accepts(token)) return false;
        Message message = messages.get(id);
        if (message == null || message.delivery == Delivery.FAILED || delivery == Delivery.PENDING
                || message.delivery == Delivery.ACKNOWLEDGED) return false;
        if (delivery == Delivery.DELIVERED && message.delivery != Delivery.PENDING) return false;
        messages.put(id, message.withDelivery(delivery)); return true;
    }
    public Snapshot snapshot(String notice, boolean demo) {
        return new Snapshot(group, invite, notice, sharing, demo, connection,
                new ArrayList<>(vehicles.values()), new ArrayList<>(messages.values()));
    }
}
