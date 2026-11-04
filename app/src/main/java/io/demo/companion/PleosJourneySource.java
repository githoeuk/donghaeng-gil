package io.demo.companion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NavigationStatus;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.DestinationArrivedInfo;
import kotlin.Unit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import ai.pleos.playground.navi.data.RecentDestinationInfo;

/** Foreground-only reader. Never changes the destination or uploads location. */
public final class PleosJourneySource {
    private final Context context;
    private final JourneyState state;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LocationManager locations;
    private NaviHelper navi;
    private boolean active;
    private DestinationInfo currentDestination;
    private String detailedAddress = "";
    private final List<DestinationAddressResolver.Place> recentPlaces = new ArrayList<>();
    private final NavigationSession sessions = new NavigationSession();
    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { acceptLocation(location); }
        @Override public void onProviderEnabled(String provider) { refreshLocation(); }
        @Override public void onProviderDisabled(String provider) { refreshLocation(); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };
    private NaviHelperEventListener listener;

    public PleosJourneySource(Context context, JourneyState state, Runnable changed) {
        this.context = context.getApplicationContext(); this.state = state; this.changed = changed;
        locations = this.context.getSystemService(LocationManager.class);
    }
    public void start() {
        if (active) return;
        active = true;
        final long token = sessions.open(state);
        clearPlace();
        state.connected = false;
        state.connectionMessage = "Pleos 연결 중";
        state.naviAt = -1;
        refreshLocation();
        listener = new NaviHelperEventListener() {
            @Override public void onCurrentLocationInfo(CurrentLocationInfo info) {
                dispatch(token, () -> { if (info != null) state.navi(info.getLatitude(), info.getLongitude(), info.getAddress(), now()); });
            }
            @Override public void onDestinationInfo(DestinationInfo info) {
                dispatch(token, () -> {
                    if (!sessions.acceptsDestination(token)) return;
                    if (info == null) { currentDestination = null; if (!state.routeEnded) state.clearDestination(false); return; }
                    boolean changedPlace = currentDestination == null
                            || !placeKey(currentDestination).equals(placeKey(info));
                    if (changedPlace) clearPlace();
                    currentDestination = info;
                    state.destination(info.getName(), resolvedAddress(info), info.getLatitude(), info.getLongitude(),
                            info.getRemainTime(), info.getRemainDistance(), now(), System.currentTimeMillis());
                    if (changedPlace && state.hasDestination() && !state.routeEnded) {
                        try { navi.getRecentDestinationInfo(); } catch (RuntimeException ignored) { }
                    }
                });
            }
            @Override public void onRecentDestinationInfo(List<RecentDestinationInfo> items) {
                dispatch(token, () -> {
                    if (!sessions.acceptsDestination(token) || currentDestination == null || !state.hasDestination()) return;
                    recentPlaces.clear();
                    if (items != null) for (RecentDestinationInfo item : items) {
                        if (item != null) recentPlaces.add(new DestinationAddressResolver.Place(item.getPoiId(),
                                item.getPoiSubId(), item.getPoiProvider(), item.getName(), item.getLatitude(),
                                item.getLongitude(), item.getAddress()));
                    }
                    // Address-only update: do not refresh ETA timestamps with an older destination reply.
                    detailedAddress = richerAddress(detailedAddress, resolvedAddress(currentDestination));
                    state.destinationAddress = detailedAddress;
                });
            }
            @Override public void onRouteCancelled() { dispatch(token, () -> { clearPlace(); sessions.end(token, state, false); }); }
            @Override public void onRouteStarted(RouteStartInfo info) { dispatch(token, () -> { restart(); }); }
            @Override public void onDestinationArrived(DestinationArrivedInfo info) {
                dispatch(token, () -> { clearPlace(); sessions.end(token, state, true); });
            }
            @Override public void onDestinationChanged() { dispatch(token, () -> { restart(); }); }
            @Override public void onNavigationStatus(NavigationStatus status) {
                if (status == NavigationStatus.INACTIVE || status == NavigationStatus.FREE_DRIVE) {
                    dispatch(token, () -> { clearPlace(); sessions.idle(token, state); });
                } else if (status == NavigationStatus.ROUTE_DRIVE) {
                    dispatch(token, () -> sessions.driving(token, state));
                }
            }
            @Override public void onError(NaviErrorCode error) {
                dispatch(token, () -> { state.connected = false; state.connectionMessage = "내비 정보 수신 오류 · 다시 연결해 주세요"; });
            }
        };
        try {
            navi = new NaviHelper(context);
            navi.initialize(() -> {
                dispatch(token, () -> {
                    state.connected = true; state.connectionMessage = "Pleos 연결됨";
                    navi.addListener(listener); poll();
                });
                return Unit.INSTANCE;
            }, error -> {
                dispatch(token, () -> { state.connected = false; state.connectionMessage = "Pleos 내비 연결 실패"; });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException error) {
            state.connectionMessage = "이 환경에서 Pleos 내비를 연결할 수 없습니다";
        }
        handler.postDelayed(() -> {
            if (active && sessions.accepts(token) && !state.connected) {
                state.connectionMessage = "Pleos 연결 대기 시간 초과 · 다시 연결해 주세요"; changed.run();
            }
        }, 15000);
        changed.run();
    }
    private void dispatch(long token, Runnable update) {
        handler.post(() -> { if (active && sessions.accepts(token)) { update.run(); changed.run(); } });
    }
    private final Runnable polling = () -> poll();
    private void poll() {
        handler.removeCallbacks(polling);
        if (!active || !state.connected) return;
        try {
            navi.getCurrentLocationInfo(); navi.getDestinationInfo();
        } catch (RuntimeException error) {
            state.connected = false; state.connectionMessage = "내비 연결이 끊겼습니다 · 다시 연결해 주세요";
        }
        changed.run();
        handler.postDelayed(polling, 5000);
    }
    public void refreshLocation() {
        if (!active || locations == null) return;
        locations.removeUpdates(gpsListener);
        state.precisePermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        state.locationPermission = state.precisePermission || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        state.locationEnabled = locations.isLocationEnabled();
        state.providerAvailable = false;
        if (state.locationPermission && state.locationEnabled) {
            for (String provider : state.precisePermission
                    ? Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    : Arrays.asList(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    if (!locations.getAllProviders().contains(provider) || !locations.isProviderEnabled(provider)) continue;
                    Location last = locations.getLastKnownLocation(provider);
                    if (last != null) acceptLocation(last);
                    locations.requestLocationUpdates(provider, 1000, 0, gpsListener, Looper.getMainLooper());
                    state.providerAvailable = true;
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
        }
        changed.run();
    }
    private void acceptLocation(Location location) {
        if (!active || location == null) return;
        state.gps(location.getLatitude(), location.getLongitude(), location.hasAccuracy() ? location.getAccuracy() : Float.POSITIVE_INFINITY,
                location.getElapsedRealtimeNanos() / 1000000);
        changed.run();
    }
    public void stop() {
        active = false; sessions.close(); clearPlace(); handler.removeCallbacksAndMessages(null);
        if (locations != null) locations.removeUpdates(gpsListener);
        if (navi != null) {
            try { if (listener != null) navi.removeListener(listener); navi.release(); }
            catch (RuntimeException ignored) { }
            navi = null;
        }
        state.connected = false;
    }
    private void clearPlace() { currentDestination = null; recentPlaces.clear(); detailedAddress = ""; }
    private void restart() {
        // A new listener closure rejects replies queued by the previous SDK connection.
        stop(); start();
    }
    private static String richerAddress(String current, String candidate) {
        return candidate.length() > current.length() ? candidate : current;
    }
    private String resolvedAddress(DestinationInfo info) {
        return richerAddress(detailedAddress, DestinationAddressResolver.resolve(new DestinationAddressResolver.Place(info.getPoiId(),
                info.getPoiSubId(), info.getPoiProvider(), info.getName(), info.getLatitude(),
                info.getLongitude(), info.getAddress()), recentPlaces));
    }
    private static String placeKey(DestinationInfo info) {
        return info.getPoiProvider() + "|" + info.getPoiId() + "|" + info.getPoiSubId()
                + "|" + info.getName() + "|" + info.getLatitude() + "|" + info.getLongitude();
    }
    private static long now() { return SystemClock.elapsedRealtime(); }
}
