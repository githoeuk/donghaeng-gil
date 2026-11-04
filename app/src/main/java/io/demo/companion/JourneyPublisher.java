package io.demo.companion;

import java.util.Locale;

/** Converts actual journey data, never substitutes mock coordinates or ETA. */
public final class JourneyPublisher {
    public static SharingModels.Vehicle current(JourneyState state, long now, long wall) {
        if (!state.locationPermission || !state.locationEnabled || !JourneyState.fresh(state.gpsAt,now)
                || !Double.isFinite(state.gpsAccuracy) || state.gpsAccuracy < 0 || state.gpsAccuracy > 100
                || !JourneyState.validCoordinates(state.gpsLatitude,state.gpsLongitude)) return null;
        boolean destination = state.hasDestination() && JourneyState.fresh(state.destinationAt,now) && !state.routeEnded;
        boolean eta = state.etaStatus(now) == JourneyState.EtaStatus.READY;
        String id = destination ? String.format(Locale.ROOT,"coordinate:%.5f,%.5f",state.destinationLatitude,state.destinationLongitude) + ":" + state.destinationName.trim() : "";
        int seconds = eta ? Math.max(0,state.remainingSeconds-(int)((now-state.destinationAt)/1000)) : -1;
        if (seconds == 0) eta = false;
        long at = eta ? Math.min(state.gpsAt,Math.min(state.destinationAt,state.naviAt)) : state.gpsAt;
        return new SharingModels.Vehicle("","",state.gpsLatitude,state.gpsLongitude,state.currentAddress(now),id,
                destination ? state.destinationName : "", destination ? state.destinationAddress : "",
                eta ? seconds : -1, eta ? state.remainingMeters : -1,0,wall,at);
    }
}
