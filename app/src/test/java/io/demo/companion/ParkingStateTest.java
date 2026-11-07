package io.demo.companion;

public final class ParkingStateTest {
    private static int checks;
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void parked(ParkingState state, long token, long time) {
        state.sample(token,state.revision(),time,time,0f,ParkingState.PARK,true,false,0);
    }
    public static void main(String[] args) {
        ParkingState state = new ParkingState();
        expect(!state.allowed(1000),"no source means locked");
        long token = state.open(); expect(!state.allowed(1000),"connection without values means locked");
        parked(state,token,1000); expect(!state.allowed(1000),"first parked sample does not immediately open");
        parked(state,token,2000); expect(state.allowed(2000),"parked hold opens");
        expect(state.allowed(5000),"freshness boundary"); expect(!state.allowed(5001),"missing updates expire");
        expect(state.description(5001).contains("갱신 지연"),"expired explanation");
        parked(state,token,6000); expect(!state.allowed(6000),"fresh data after gap requires hold again");
        parked(state,token,7000); expect(state.allowed(7000),"fresh parked state restored");
        float[] invalidSpeeds = {0.001f, -0.001f, 10f, -10f, Float.NaN, Float.POSITIVE_INFINITY};
        for (float speed : invalidSpeeds) {
            state.sample(token,state.revision(),8000,8000,speed,ParkingState.PARK,true,false,0);
            expect(!state.allowed(8000),"movement or invalid speed locks: " + speed);
        }
        for (int gear : new int[]{0,1,2,8,16,-1}) {
            state.sample(token,state.revision(),9000,9000,0f,gear,true,false,0);
            expect(!state.allowed(9000),"non-P at zero speed locks: " + gear);
        }
        state.sample(token,state.revision(),10000,10000,0f,ParkingState.PARK,false,false,0);
        expect(!state.allowed(10000),"missing UX state locks");
        state.sample(token,state.revision(),10000,10000,0f,ParkingState.PARK,true,true,0);
        expect(!state.allowed(10000),"requires distraction optimization locks");
        state.sample(token,state.revision(),10000,10000,0f,ParkingState.PARK,true,false,1);
        expect(!state.allowed(10000),"active UX restriction locks");
        parked(state,token,11000); parked(state,token,12000); long oldRevision = state.revision();
        state.fail(token,"connection lost"); expect(!state.allowed(12000),"service loss immediately locks");
        state.sample(token,oldRevision,12000,12000,0f,ParkingState.PARK,true,false,0);
        expect(!state.allowed(12000),"safe read begun before unsafe event cannot reopen");
        parked(state,token,13000); parked(state,token,14000); expect(state.allowed(14000),"new read after loss recovers only after hold");
        state.sample(token,state.revision(),14000,18001,0f,ParkingState.PARK,true,false,0);
        expect(!state.allowed(18001),"slow IPC or old speed source timestamp locks");
        state.sample(token,state.revision(),19000,18000,0f,ParkingState.PARK,true,false,0);
        expect(!state.allowed(18000),"future sample locks");
        state.sample(token,state.revision(),-1,18000,0f,ParkingState.PARK,true,false,0);
        expect(!state.allowed(18000),"missing timestamp locks");
        parked(state,token,20000); parked(state,token,21000);
        expect(!state.allowed(19999),"clock before sample fails closed");
        state.close(); expect(!state.allowed(21000),"background or stop locks");
        long next = state.open(); parked(state,next,22000); parked(state,next,23000);
        expect(state.allowed(23000),"new lifetime valid");
        state.fail(token,"late old failure"); expect(state.allowed(23000),"old listener cannot mutate new lifetime");
        state.sample(token,state.revision(),23000,23000,1f,8,true,true,1);
        expect(state.allowed(23000),"old listener samples ignored");
        state.fail(next,"permission revoked"); expect(!state.allowed(23000),"permission loss locks");
        ParkingState driving = new ParkingState();
        long driveSession = driving.open();
        driving.sample(driveSession, driving.revision(), 24000, 24000, 5f, 8, true, true, 8);
        expect(driving.messagesAllowed(24000) && !driving.allowed(24000), "driving allows presets when text is permitted");
        expect(!driving.messagesAllowed(27001), "stale vehicle state locks presets");
        driving.sample(driveSession, driving.revision(), 28000, 28000, 5f, 8, true, true, ParkingState.NO_TEXT_MESSAGE);
        expect(!driving.messagesAllowed(28000), "platform text restriction locks presets");
        driving.sample(driveSession, driving.revision(), 29000, 29000, 5f, 8, true, true, 8);
        driving.fail(driveSession, "state unavailable");
        expect(!driving.messagesAllowed(29000), "missing vehicle state locks presets");
        System.out.println("Passed " + checks + " parking-safety checks.");
    }
}
