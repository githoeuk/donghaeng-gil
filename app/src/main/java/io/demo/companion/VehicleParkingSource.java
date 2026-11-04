package io.demo.companion;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** AAOS read-only monitor. No GPS fallback, simulator overrides, or vehicle control APIs. */
public final class VehicleParkingSource {
    public static final String SPEED_PERMISSION = "android.car.permission.CAR_SPEED";
    private final Context context;
    private final ParkingState state;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Car car;
    private CarPropertyManager properties;
    private CarUxRestrictionsManager ux;
    private CarPropertyManager.CarPropertyEventCallback propertyListener;
    private ExecutorService worker;
    private boolean active, inFlight, subscribed;
    private long token, connectionEpoch;
    public VehicleParkingSource(Context context, ParkingState state, Runnable changed) {
        this.context = context.getApplicationContext(); this.state = state; this.changed = changed;
    }
    public boolean hasPermission() { return context.checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED; }
    public void start() {
        if (active) return;
        active = true; inFlight = false; subscribed = false; token = state.open();
        final long captured = token;
        handler.post(ticker);
        if (!hasPermission()) { fail(captured, "차량 속도 접근 권한이 필요합니다"); return; }
        worker = Executors.newSingleThreadExecutor();
        try {
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connectedCar, ready) -> {
                if (!state.accepts(captured)) return;
                if (!ready) { detach(); fail(captured, "차량 서비스 연결 끊김"); return; }
                attach(connectedCar, captured);
            });
        } catch (RuntimeException | LinkageError error) { fail(captured, "차량 상태 서비스를 사용할 수 없습니다"); }
    }
    private void attach(Car connectedCar, long captured) {
        detach();
        final long epoch = connectionEpoch;
        try {
            properties = (CarPropertyManager) connectedCar.getCarManager(Car.PROPERTY_SERVICE);
            ux = (CarUxRestrictionsManager) connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
            if (properties == null || ux == null) { fail(captured, "차량 상태·UX 서비스 미지원"); return; }
            propertyListener = new CarPropertyManager.CarPropertyEventCallback() {
                @Override public void onChangeEvent(CarPropertyValue value) {
                    if (!state.accepts(captured) || epoch != connectionEpoch) return;
                    boolean valid = value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE;
                    if (!valid) { fail(captured, "차량 속성 미수신·사용 불가"); return; }
                    Object data = value.getValue();
                    if (value.getPropertyId() == VehiclePropertyIds.PERF_VEHICLE_SPEED
                            && (!(data instanceof Float) || !Float.isFinite((Float)data) || ((Float)data) != 0f))
                        fail(captured, "차량 움직임 감지 · 시연 잠김");
                    if (value.getPropertyId() == VehiclePropertyIds.GEAR_SELECTION
                            && (!(data instanceof Integer) || ((Integer)data) != VehicleGear.GEAR_PARK))
                        fail(captured, "P 기어가 아닙니다 · 시연 잠김");
                }
                @Override public void onErrorEvent(int propertyId, int areaId) { if (epoch == connectionEpoch) fail(captured, "차량 속성 수신 오류"); }
            };
            // Capability/permission failures must never be replaced with fabricated parked values.
            if (properties.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null
                    || properties.getCarPropertyConfig(VehiclePropertyIds.GEAR_SELECTION) == null) {
                fail(captured, "차량 속도·기어 읽기 미지원"); return;
            }
            boolean speed = properties.registerCallback(propertyListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI);
            boolean gear = properties.registerCallback(propertyListener, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            ux.registerListener(restrictions -> {
                if (epoch != connectionEpoch) return;
                if (restrictions == null || restrictions.isRequiresDistractionOptimization()
                        || restrictions.getActiveRestrictions() != CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                    fail(captured, "플랫폼 주행 제한 활성 · 시연 잠김");
            });
            subscribed = speed && gear;
            if (!subscribed) fail(captured, "차량 상태 구독 실패");
        } catch (RuntimeException | LinkageError error) { subscribed = false; fail(captured, "차량 상태 권한·서비스 확인 필요"); }
    }
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            changed.run(); // Also locks when no successful read arrives within the freshness window.
            query(); handler.postDelayed(this, 500);
        }
    };
    private void query() {
        if (!active || !subscribed || inFlight || worker == null || properties == null || ux == null) return;
        inFlight = true;
        final long captured = token, epoch = connectionEpoch, revision = state.revision(), requestedAt = now();
        final CarPropertyManager currentProperties = properties;
        final CarUxRestrictionsManager currentUx = ux;
        worker.execute(() -> {
            try {
                CarPropertyValue<Float> speed = currentProperties.getProperty(Float.class, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0);
                CarPropertyValue<Integer> gear = currentProperties.getProperty(Integer.class, VehiclePropertyIds.GEAR_SELECTION, 0);
                CarUxRestrictions restrictions = currentUx.getCurrentCarUxRestrictions();
                boolean available = speed != null && gear != null && speed.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                        && gear.getStatus() == CarPropertyValue.STATUS_AVAILABLE && speed.getValue() != null && gear.getValue() != null;
                handler.post(() -> {
                    if (!state.accepts(captured) || epoch != connectionEpoch) return;
                    inFlight = false;
                    if (!available) { fail(captured, "차량 상태를 받지 못했습니다"); return; }
                    long speedAt = speed.getTimestamp() / 1000000L;
                    long sampleAt = speedAt > 0 && speedAt <= now() ? Math.min(requestedAt, speedAt) : -1;
                    state.sample(captured, revision, sampleAt, now(), speed.getValue(), gear.getValue(), restrictions != null,
                            restrictions != null && restrictions.isRequiresDistractionOptimization(),
                            restrictions == null ? -1 : restrictions.getActiveRestrictions());
                    changed.run();
                });
            } catch (RuntimeException | LinkageError error) {
                handler.post(() -> { if (state.accepts(captured) && epoch == connectionEpoch) { inFlight = false; fail(captured, "차량 상태 조회 실패 · 시연 잠김"); } });
            }
        });
    }
    private void fail(long captured, String reason) { if (state.accepts(captured)) { state.fail(captured, reason); changed.run(); } }
    private void detach() {
        connectionEpoch++; subscribed = false; inFlight = false;
        try { if (properties != null && propertyListener != null) properties.unregisterCallback(propertyListener); } catch (RuntimeException ignored) { }
        try { if (ux != null) ux.unregisterListener(); } catch (RuntimeException ignored) { }
        properties = null; ux = null; propertyListener = null;
    }
    public void stop() {
        active = false; state.close(); handler.removeCallbacksAndMessages(null);
        detach();
        try { if (car != null) car.disconnect(); } catch (RuntimeException ignored) { }
        if (worker != null) worker.shutdownNow();
        worker = null; car = null; properties = null; ux = null; propertyListener = null; subscribed = false; inFlight = false;
    }
    private static long now() { return SystemClock.elapsedRealtime(); }
}
