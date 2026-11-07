package io.demo.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transport-neutral immutable payloads. Monotonic receive time is local, never sent. */
public final class SharingModels {
    private SharingModels() { }
    public enum Connection { DISCONNECTED, CONNECTED }
    public enum Delivery { PENDING, DELIVERED, ACKNOWLEDGED, FAILED }
    public enum MessageKind {
        REST("휴게소 들를게요"), WAIT("잠시 기다려 주세요"), GO("먼저 출발하세요"), OK("확인했어요");
        static final List<MessageKind> SEND_PRESETS = Collections.unmodifiableList(java.util.Arrays.asList(REST, WAIT, GO));
        public final String label;
        MessageKind(String label) { this.label = label; }
    }
    public static final class Vehicle {
        public final String id, name, address, destinationId, destinationName, destinationAddress;
        public final double latitude, longitude;
        public final int remainingSeconds, remainingMeters;
        public final long sequence, generatedAtMillis, receivedAtElapsed;
        public Vehicle(String id, String name, double latitude, double longitude, String address,
                String destinationId, String destinationName, String destinationAddress, int seconds,
                int meters, long sequence, long generatedAtMillis, long receivedAtElapsed) {
            this.id = id; this.name = name; this.latitude = latitude; this.longitude = longitude;
            this.address = address; this.destinationId = destinationId; this.destinationName = destinationName;
            this.destinationAddress = destinationAddress; remainingSeconds = seconds; remainingMeters = meters;
            this.sequence = sequence; this.generatedAtMillis = generatedAtMillis; this.receivedAtElapsed = receivedAtElapsed;
        }
        public boolean stale(long now) { return now < receivedAtElapsed || now - receivedAtElapsed > 15000; }
        public boolean sameDestination(Vehicle other) {
            return other != null && destinationId != null && !destinationId.isEmpty() && destinationId.equals(other.destinationId);
        }
    }
    public static final class Message {
        public final String id, sender, senderId;
        public final MessageKind kind;
        public final long sentAtMillis;
        public final Delivery delivery;
        public Message(String id, String sender, MessageKind kind, long sentAtMillis, Delivery delivery) {
            this(id, "", sender, kind, sentAtMillis, delivery);
        }
        public Message(String id, String senderId, String sender, MessageKind kind, long sentAtMillis, Delivery delivery) {
            this.id = id; this.senderId = senderId; this.sender = sender; this.kind = kind; this.sentAtMillis = sentAtMillis; this.delivery = delivery;
        }
        public Message withDelivery(Delivery value) { return new Message(id, senderId, sender, kind, sentAtMillis, value); }
    }
    public static final class Snapshot {
        public final String groupId, inviteCode, notice;
        public final boolean sharing, demo;
        public final Connection connection;
        public final List<Vehicle> vehicles;
        public final List<Message> messages;
        public Snapshot(String groupId, String inviteCode, String notice, boolean sharing, boolean demo,
                Connection connection, List<Vehicle> vehicles, List<Message> messages) {
            this.groupId = groupId; this.inviteCode = inviteCode; this.notice = notice;
            this.sharing = sharing; this.demo = demo; this.connection = connection;
            this.vehicles = Collections.unmodifiableList(new ArrayList<>(vehicles));
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }
}
