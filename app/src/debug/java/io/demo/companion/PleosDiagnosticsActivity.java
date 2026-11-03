package io.demo.companion;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NavigationStatus;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.*;
import kotlin.Unit;

/** Debug-only probe: reports SDK callbacks without substituting demo values. */
public class PleosDiagnosticsActivity extends Activity {
    private static final String TAG = "DonghaengProbe";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView output;
    private NaviHelper navi;
    private boolean connected, destroyed;
    private int polls;
    private boolean testRouteRequested;
    private LocationManager locations;
    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            reportGps("GPS_UPDATE", location);
        }
    };
    private final NaviHelperEventListener listener = new NaviHelperEventListener() {
        @Override public void onRouteStarted(RouteStartInfo info) { report("ROUTE_STARTED " + info); }
        @Override public void onRoutingCompleted() { report("ROUTING_COMPLETED"); }
        @Override public void onRouteCancelled() { report("ROUTE_CANCELLED"); }
        @Override public void onCurrentLocationInfo(CurrentLocationInfo info) {
            report("CURRENT_LOCATION " + info);
        }
        @Override public void onDestinationInfo(DestinationInfo info) {
            report("DESTINATION " + info);
        }
        @Override public void onRouteStateInfo(RouteStateInfo info) {
            report("ROUTE_STATE " + info);
        }
        @Override public void onDrivingInfo(DrivingInfo info) {
            report("DRIVING " + info);
        }
        @Override public void onTripInfo(TripInfo info) {
            report("TRIP distanceMeters=" + info.getDistance() + " etaSeconds=" + info.getEta());
        }
        @Override public void onNavigationStatus(NavigationStatus status) {
            report("NAVIGATION_STATUS " + status);
        }
        @Override public void onError(NaviErrorCode error) {
            report("NAVI_ERROR " + error);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        output = new TextView(this);
        output.setTextSize(18);
        root.addView(output);
        scroll.addView(root);
        setContentView(scroll);
        report("START NaviHelper 3.0.5 / debug probe; route changes are opt-in");
        locations = getSystemService(LocationManager.class);
        try {
            reportGps("GPS_LAST", locations.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        } catch (SecurityException error) {
            report("GPS_PERMISSION_REQUIRED: grant precise location for the debug probe");
        }
        navi = new NaviHelper(getApplicationContext());
        navi.initialize(() -> {
            if (destroyed) return Unit.INSTANCE;
            connected = true;
            report("CONNECTED");
            navi.addListener(listener);
            handler.post(this::poll);
            if (getIntent().getBooleanExtra("probeTrip", false)) {
                report("REQUEST_TRIP synthetic destination 37.4200,127.1000");
                navi.requestTrip(new TripRequest(37.4200, 127.1000, "", "Donghaeng diagnostic destination", ""));
                handler.postDelayed(() -> {
                    report("REQUEST_TRIP synthetic destination 37.5000,127.0300");
                    navi.requestTrip(new TripRequest(37.5000, 127.0300, "", "Donghaeng second diagnostic destination", ""));
                }, 6000);
            }
            if (getIntent().getBooleanExtra("probeRoute", false)) {
                report("REQUEST_ROUTE synthetic destination 37.4200,127.1000; automatic cancel after 24s");
                testRouteRequested = true;
                navi.requestRoute(new RouteInfo(37.4200, 127.1000, null, null,
                        "Donghaeng diagnostic destination", "", "", "", RouteOption.RECOMMENDED, "", false));
                handler.postDelayed(this::cancelTestRoute, 24000);
            }
            return Unit.INSTANCE;
        }, error -> {
            report("CONNECT_FAILED " + error);
            return Unit.INSTANCE;
        });
        handler.postDelayed(() -> {
            if (!connected) report("TIMEOUT: no SDK connection within 20 seconds");
        }, 20000);
    }

    private void poll() {
        if (destroyed || !connected || polls++ >= 11) return;
        report("REQUEST currentLocation/destination/routeState #" + polls);
        try {
            navi.getCurrentLocationInfo();
            navi.getDestinationInfo();
            navi.getRouteStateInfo();
        } catch (RuntimeException error) {
            report("REQUEST_FAILED " + error);
        }
        handler.postDelayed(this::poll, 3000);
    }

    private void cancelTestRoute() {
        if (testRouteRequested && connected) {
            testRouteRequested = false;
            navi.cancelRoute();
            report("CANCEL_TEST_ROUTE");
        }
    }

    private void report(String line) {
        Log.i(TAG, line);
        handler.post(() -> {
            if (!destroyed) output.append(line + "\n\n");
        });
    }

    private void reportGps(String source, Location location) {
        if (location == null) { report(source + " null"); return; }
        long ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1000000;
        report(source + " latitude=" + location.getLatitude() + " longitude=" + location.getLongitude()
                + " accuracyMeters=" + location.getAccuracy() + " ageMs=" + ageMs);
    }

    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (locations != null) locations.removeUpdates(gpsListener);
        if (navi != null) {
            cancelTestRoute();
            navi.removeListener(listener);
            navi.release();
        }
        super.onDestroy();
    }
}
