package io.demo.companion;

import static io.demo.companion.SharingModels.*;

public final class SharingTest {
    private static int checks;
    private static void expect(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Clock implements MockSharingTransport.Clock {
        long now = 1000;
        public long elapsed() { return now; }
        public long wall() { return 1000000 + now; }
    }
    private static Vehicle vehicle(String id, long seq, long at) {
        return new Vehicle(id,"test",37.4,127.1,"address","poi","destination","address",600,2000,seq,1000000,at);
    }
    public static void main(String[] args) {
        Clock clock = new Clock(); MockSharingTransport mock = new MockSharingTransport(clock);
        expect(mock.snapshot().demo && mock.snapshot().groupId.isEmpty(),"explicit demo with no initial membership");
        mock.startSharing(); expect(!mock.snapshot().sharing,"no sharing outside group");
        mock.joinGroup("wrong"); expect(mock.snapshot().groupId.isEmpty(),"invalid demo invitation rejected");
        mock.joinGroup("DEMO42"); expect(!mock.snapshot().groupId.isEmpty(),"valid local demo invitation");
        expect(mock.snapshot().vehicles.isEmpty(),"participation does not imply sharing");
        mock.startSharing(); Snapshot started = mock.snapshot();
        expect(started.vehicles.size() == 3,"three fictional vehicles");
        expect(started.vehicles.get(0).sameDestination(started.vehicles.get(1)),"same destination");
        expect(!started.vehicles.get(0).sameDestination(started.vehicles.get(2)),"different destination excluded");
        long firstSequence = started.vehicles.get(0).sequence;
        clock.now += 5000; mock.tick(); expect(mock.snapshot().vehicles.get(0).sequence > firstSequence,"five-second updates");
        expect(started.vehicles.get(0).sequence == firstSequence,"snapshot remains immutable after update");
        mock.send(MessageKind.REST); expect(mock.snapshot().messages.get(0).delivery == Delivery.PENDING,"message pending");
        clock.now += 2000; mock.tick(); expect(mock.snapshot().messages.get(0).delivery == Delivery.DELIVERED,"simulated delivery");
        clock.now += 2000; mock.tick(); expect(mock.snapshot().messages.get(0).delivery == Delivery.ACKNOWLEDGED,"simulated acknowledgement");
        expect(mock.snapshot().messages.size() == 2,"one inbound confirmation");
        mock.acknowledge(mock.snapshot().messages.get(1).id);
        expect(mock.snapshot().messages.get(1).delivery == Delivery.ACKNOWLEDGED,"user acknowledges inbound reply");
        mock.tick(); expect(mock.snapshot().messages.size() == 2,"no duplicate confirmation on later ticks");
        mock.freeze(); clock.now += 16000; mock.tick();
        expect(mock.snapshot().vehicles.get(0).stale(clock.now),"frozen state becomes stale");
        mock.send(MessageKind.WAIT); mock.disconnect(); clock.now += 5000; mock.tick();
        expect(mock.snapshot().connection == Connection.DISCONNECTED,"disconnection visible");
        expect(mock.snapshot().messages.get(2).delivery == Delivery.FAILED,"pending send fails on disconnect");
        int count = mock.snapshot().messages.size(); mock.send(MessageKind.GO);
        expect(mock.snapshot().messages.size() == count,"offline message not silently queued");
        mock.reconnect(); expect(!mock.snapshot().vehicles.get(0).stale(clock.now),"new data after reconnect");
        expect(mock.snapshot().messages.get(2).delivery == Delivery.FAILED,"failed message not replayed");
        mock.stopSharing(); clock.now += 5000; mock.tick();
        expect(mock.snapshot().vehicles.isEmpty() && !mock.snapshot().sharing,"stop clears shared vehicles and blocks ticks");
        mock.startSharing(); expect(mock.snapshot().vehicles.size() == 3,"explicit resume works");
        mock.leaveGroup(); mock.tick(); expect(mock.snapshot().groupId.isEmpty() && mock.snapshot().messages.isEmpty(),"leave clears membership and messages");
        mock.createGroup(); expect(!mock.snapshot().groupId.isEmpty(),"demo create flow");
        mock.startSharing(); mock.close(); clock.now += 5000; mock.tick(); mock.createGroup();
        expect(mock.snapshot().groupId.isEmpty(),"closed transport cannot repopulate state");

        MockSharingTransport overviewMock = new MockSharingTransport(clock);
        overviewMock.createGroup();
        overviewMock.startSharing();
        overviewMock.send(MessageKind.WAIT);
        clock.now += 16000;
        Snapshot overview = overviewMock.refreshOverview();
        expect(!overview.vehicles.get(0).stale(clock.now), "overview refreshes vehicles after inactivity");
        expect(overview.messages.get(0).delivery == Delivery.PENDING, "overview does not advance message delivery");

        MockSharingTransport quickSend = new MockSharingTransport(clock);
        quickSend.createGroup();
        quickSend.startSharing();
        quickSend.sendFromOverview(MessageKind.WAIT);
        clock.now += 4000;
        Snapshot afterQuickSend = quickSend.refreshOverview();
        expect(afterQuickSend.messages.get(0).delivery == Delivery.ACKNOWLEDGED, "quick message completes on overview");
        expect(afterQuickSend.messages.size() == 2, "quick message generates one fictional reply");

        MockSharingTransport receiver = new MockSharingTransport(clock);
        receiver.createGroup();
        receiver.startSharing();
        receiver.receiveExampleMessage();
        Message incoming = receiver.snapshot().messages.get(0);
        expect(incoming.sender.equals("모의 차량 나") && incoming.delivery == Delivery.DELIVERED,
                "fictional peer initiates a message without a local send");
        receiver.acknowledge(incoming.id);
        expect(receiver.snapshot().messages.get(0).delivery == Delivery.ACKNOWLEDGED, "incoming message can be acknowledged");
        receiver.disconnect();
        receiver.receiveExampleMessage();
        expect(receiver.snapshot().messages.size() == 1, "offline peer cannot deliver an example");

        SharingStore store = new SharingStore(); long token = store.enter("group-a","code"); store.connect(true); store.start();
        expect(store.vehicle(token,vehicle("a",2,1000)),"first inbound version accepted");
        expect(!store.vehicle(token,vehicle("a",1,2000)),"out-of-order position rejected");
        expect(!store.vehicle(token,vehicle("a",2,2000)),"duplicate position rejected");
        Message message = new Message("m","a",MessageKind.WAIT,1000000,Delivery.DELIVERED);
        expect(store.message(token,message),"inbound message");
        expect(!store.message(token,message),"duplicate message rejected");
        expect(store.delivery(token,"m",Delivery.ACKNOWLEDGED),"acknowledge known message");
        expect(!store.delivery(token,"m",Delivery.DELIVERED),"late delivery cannot downgrade acknowledgement");
        store.stop(); expect(!store.vehicle(token,vehicle("b",1,2000)),"late callback after stop rejected");
        store.start(); expect(!store.message(token,new Message("late","a",MessageKind.OK,1,Delivery.DELIVERED)),"old callback rejected after resume");
        long current = store.token(); expect(store.vehicle(current,vehicle("b",1,2000)),"new lifetime accepted");
        store.disconnect(); store.connect(true);
        expect(!store.vehicle(current,vehicle("b",2,3000)),"pre-disconnect callback rejected after reconnect");
        store.enter("group-b","other"); store.connect(true); store.start();
        expect(!store.message(current,new Message("old-group","a",MessageKind.OK,1,Delivery.DELIVERED)),"previous group cannot inject message");
        expect(store.snapshot("",true).vehicles.isEmpty(),"new group has no old vehicles");
        System.out.println("Passed " + checks + " sharing checks.");
    }
}
