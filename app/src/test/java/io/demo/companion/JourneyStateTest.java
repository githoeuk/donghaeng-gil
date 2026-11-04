package io.demo.companion;

/** Run with javac/java; no device or Android classes required. */
public final class JourneyStateTest {
    private static int checks;
    private static void expect(boolean condition, String name) {
        checks++; if (!condition) throw new AssertionError(name);
    }
    private static JourneyState ready() {
        JourneyState state = new JourneyState();
        state.connected = true; state.connectionMessage = "Pleos 연결됨";
        state.locationPermission = true; state.precisePermission = true;
        state.gps(37.41272, 127.09468, 5, 1000);
        state.navi(37.41272, 127.09468, 1000);
        state.destination("시험 목적지", 37.42, 127.10, 300, 1800, 1000, 1000000);
        return state;
    }
    public static void main(String[] args) {
        JourneyState state = ready();
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.READY, "valid live data");
        expect(state.etaStatus(16001) == JourneyState.EtaStatus.STALE, "stale ETA withheld");
        state = ready(); state.navi(37.50, 127.03, 1000);
        expect(state.mismatch(1000), "different source coordinates detected");
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.CHECK_LOCATION, "mismatched ETA qualified");
        state = ready(); state.locationPermission = false;
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.CHECK_LOCATION, "permission denied");
        state = ready(); state.locationEnabled = false;
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.CHECK_LOCATION, "location disabled");
        state = ready(); state.gpsAt = -1;
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.CHECK_LOCATION, "no GPS fix");
        state = ready(); state.gpsAccuracy = 2000;
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.CHECK_LOCATION, "low accuracy withheld");
        state = ready(); state.remainingSeconds = 0;
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.WAITING, "zero time is not arrival");
        state = ready(); state.destination("", 0, 0, 0, 0, 1000, 1000000);
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.NO_DESTINATION, "empty destination");
        state = ready(); state.endRoute(false);
        state.destination("stale response", 37.42, 127.10, 300, 1800, 1100, 1000000);
        expect(!state.hasDestination(), "late reply cannot revive cancelled route");
        state.beginRoute(); state.destination("new route", 37.42, 127.10, 300, 1800, 1100, 1000000);
        expect(state.hasDestination(), "new route accepted");
        state = ready(); state.endRoute(true);
        state.destination("", 0, 0, 0, 0, 1100, 1000000);
        expect(state.etaStatus(1100) == JourneyState.EtaStatus.ARRIVED, "explicit arrival retained");
        state = ready(); state.gps(0, 0, 5, 900);
        expect(state.gpsLatitude == 37.41272, "older GPS fix ignored");
        expect(!JourneyState.fresh(2000, 1000), "future fix rejected as fresh");
        expect(!JourneyState.validCoordinates(Double.NaN, 127), "invalid coordinate rejected");
        expect(!JourneyState.validCoordinates(91, 127), "out-of-range coordinate rejected");
        state = ready(); state.connected = false; state.connectionMessage = "연결 실패";
        expect(state.etaStatus(1000) == JourneyState.EtaStatus.UNAVAILABLE, "connection failure withholds old ETA");
        state = ready(); state.navi(37.41272, 127.09468, " 수정구 시흥동 ", 1000);
        expect("수정구 시흥동".equals(state.currentAddress(1000)), "matching Pleos address displayed");
        state.gps(37.50, 127.03, 5, 1100);
        expect(state.currentAddress(1100).isEmpty(), "old address hidden after GPS moves");
        state = ready(); state.navi(37.41272, 127.09468, "수정구 시흥동", 1000);
        expect(state.currentAddress(16001).isEmpty(), "stale address hidden");
        state.locationPermission = false;
        expect(state.currentAddress(1000).isEmpty(), "address hidden without permission");
        state = ready(); state.navi(37.41272, 127.09468, null, 1000);
        expect(state.currentAddress(1000).isEmpty(), "missing address does not become coordinates");
        state.destination("시험 목적지", "수정구 시흥동", 37.42, 127.1, 300, 1800, 1000, 1000000);
        expect("수정구 시흥동".equals(state.destinationAddress), "destination address captured");
        state.endRoute(false);
        expect(state.destinationAddress.isEmpty(), "destination address cleared with route");
        System.out.println("Passed " + checks + " journey-state checks.");
    }
}
