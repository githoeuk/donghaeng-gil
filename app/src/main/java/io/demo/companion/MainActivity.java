package io.demo.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 20, VEHICLE_REQUEST = 21;
    private final ParkingState parking = new ParkingState();
    private VehicleParkingSource parkingSource;
    static java.util.function.Supplier<SharingModels.Snapshot> demoOverview;
    static java.util.function.Consumer<SharingModels.MessageKind> demoSend;
    static Runnable demoReset;
    private LinearLayout demoQuickMessages;
    private TextView parkingLabel, demoStatus, demoVehicles;
    private LinearLayout demoCard;
    private Button demoButton, demoEndButton, groupButton, vehiclePermission;
    private AlertDialog demoEndConfirmation;
    private final JourneyState state = new JourneyState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PleosJourneySource source;
    private TextView connection, position, positionDetail, destination, destinationDetail, eta, etaDetail;
    private Button permission;
    private String lastStatus;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(14, 24, 34));
        LinearLayout root = column(); root.setPadding(dp(32), dp(24), dp(32), dp(24));
        scroll.addView(root);
        TextView brand = text("동행길", 32, Color.WHITE); brand.setTypeface(null, Typeface.BOLD);
        root.addView(brand);
        root.addView(text("따로 출발해도, 함께 도착하다", 17, Color.rgb(156, 178, 192)));
        TextView vehicleHeading = text("내 차량 정보", 24, Color.WHITE);
        vehicleHeading.setTypeface(null, Typeface.BOLD);
        vehicleHeading.setPadding(0, dp(18), 0, 0);
        root.addView(vehicleHeading);
        connection = text("Pleos 연결 중", 16, Color.rgb(101, 222, 201));
        connection.setPadding(0, dp(4), 0, dp(10));
        root.addView(connection);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout locationCard = card("현재 위치");
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1); left.setMargins(0, 0, dp(12), 0);
        row.addView(locationCard, left);
        position = text("위치 권한을 허용해 주세요", 23, Color.WHITE); locationCard.addView(position);
        positionDetail = text("", 16, Color.rgb(169, 190, 203)); locationCard.addView(positionDetail);
        permission = button("내 위치 사용", this::requestLocation);
        locationCard.addView(permission);
        LinearLayout destinationCard = card("내비 목적지");
        row.addView(destinationCard, new LinearLayout.LayoutParams(0, -1, 1));
        destination = text("내비 정보를 확인하고 있습니다", 23, Color.WHITE); destinationCard.addView(destination);
        destinationDetail = text("", 16, Color.rgb(169, 190, 203)); destinationCard.addView(destinationDetail);

        LinearLayout etaCard = card("예상 도착");
        LinearLayout.LayoutParams etaLayout = new LinearLayout.LayoutParams(-1, -2); etaLayout.setMargins(0, dp(16), 0, 0);
        root.addView(etaCard, etaLayout);
        eta = text("연결 중", 36, Color.rgb(101, 222, 201)); eta.setTypeface(null, Typeface.BOLD); etaCard.addView(eta);
        etaDetail = text("", 18, Color.rgb(188, 206, 217)); etaCard.addView(etaDetail);
        LinearLayout actions = new LinearLayout(this);
        groupButton = button("실제 동행 공유", () -> startActivity(new Intent(this, GroupSharingActivity.class)));
        groupButton.setEnabled(false);
        actions.addView(groupButton);
        actions.addView(button("다시 연결", () -> { source.stop(); source.start(); }));
        if (BuildConfig.DEBUG) {
            actions.addView(button("알림 시험", () -> startActivity(new Intent().setClassName(getPackageName(), getPackageName() + ".NotificationDemoActivity"))));
        }
        root.addView(actions);
        parkingLabel = text("차량 상태 확인 중", 16, Color.rgb(169, 190, 203));
        root.addView(parkingLabel);
        vehiclePermission = button("차량 상태 접근 허용", () -> requestPermissions(new String[]{VehicleParkingSource.SPEED_PERMISSION}, VEHICLE_REQUEST));
        root.addView(vehiclePermission);
        if (BuildConfig.HAS_SHARING_DEMO) {
            demoCard = card("가상 체험", Color.rgb(37, 34, 59));
            demoCard.addView(text("가상 팀원의 위치·도착 시간·메시지를 체험할 수 있어요. 체험 데이터는 외부로 보내지 않습니다.",
                    17, Color.rgb(207, 198, 233)));
            demoStatus = text("", 18, Color.WHITE);
            demoCard.addView(demoStatus);

            LinearLayout demoActions = new LinearLayout(this);
            demoButton = demoAction("가상 체험 시작", () -> {
                if (parking.allowed(SystemClock.elapsedRealtime())) openDemo();
                else renderParking();
            });
            demoButton.setEnabled(false);
            demoActions.addView(demoButton);
            demoEndButton = demoAction("체험 종료", this::confirmDemoEnd);
            demoEndButton.setVisibility(View.GONE);
            demoActions.addView(demoEndButton);
            demoCard.addView(demoActions);

            demoVehicles = text("", 17, Color.rgb(207, 198, 233));
            demoCard.addView(demoVehicles);
            demoQuickMessages = new LinearLayout(this);
            for (SharingModels.MessageKind kind : SharingModels.MessageKind.SEND_PRESETS) {
                demoQuickMessages.addView(button(kind.label, () -> {
                    if (demoSend != null && parking.messagesAllowed(SystemClock.elapsedRealtime())) {
                        demoSend.accept(kind);
                        renderDemoStatus(parking.allowed(SystemClock.elapsedRealtime()));
                    }
                }));
            }
            demoCard.addView(demoQuickMessages);
            LinearLayout.LayoutParams demoLayout = new LinearLayout.LayoutParams(-1, -2);
            demoLayout.topMargin = dp(12);
            root.addView(demoCard, demoLayout);
        }
        setContentView(scroll);
        source = new PleosJourneySource(this, state, this::render);
        parkingSource = new VehicleParkingSource(this, parking, this::renderParking);
    }
    @Override protected void onStart() {
        super.onStart(); source.start(); if (parkingSource != null) parkingSource.start(); handler.post(ticker);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(ticker); source.stop(); if (parkingSource != null) parkingSource.stop();
        if (demoEndConfirmation != null) demoEndConfirmation.dismiss();
        super.onStop();
    }
    private void renderParking() {
        if (groupButton == null) return;
        boolean allowed = parking.allowed(SystemClock.elapsedRealtime());
        groupButton.setEnabled(allowed || parking.messagesAllowed(SystemClock.elapsedRealtime()));
        renderDemoStatus(allowed);
        parkingLabel.setText((allowed ? "정차 중에 이용할 수 있어요 · " : "정차 후 이용해 주세요 · ") + parking.description(SystemClock.elapsedRealtime()));
        vehiclePermission.setVisibility(parkingSource.hasPermission() ? View.GONE : View.VISIBLE);
        if (!allowed && demoEndConfirmation != null) demoEndConfirmation.dismiss();
    }
    private void confirmDemoEnd() {
        if (demoReset == null || !parking.allowed(SystemClock.elapsedRealtime())) return;
        demoEndConfirmation = new AlertDialog.Builder(this).setTitle("가상 체험을 종료할까요?")
                .setMessage("가상 그룹과 팀원, 메시지가 초기화됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("체험 종료", (ignored, which) -> {
                    if (demoReset != null && parking.allowed(SystemClock.elapsedRealtime())) {
                        demoReset.run();
                        renderParking();
                    }
                }).show();
    }
    private void openDemo() {
        startActivity(new Intent().setClassName(getPackageName(), getPackageName() + ".SharingDemoActivity"));
    }
    private void renderDemoStatus(boolean allowed) {
        if (demoCard == null) return;
        SharingModels.Snapshot snapshot = demoOverview == null ? null : demoOverview.get();
        boolean active = snapshot != null;
        demoButton.setText(active ? "가상 체험 이어가기" : "가상 체험 시작");
        demoButton.setEnabled(allowed);
        demoEndButton.setVisibility(active ? View.VISIBLE : View.GONE);
        demoEndButton.setEnabled(allowed);
        demoVehicles.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) {
            demoStatus.setText(allowed ? "정차 확인 · 새 체험을 시작할 수 있어요" : "정차 후 체험을 시작할 수 있어요");
            demoQuickMessages.setVisibility(View.GONE);
            return;
        }
        boolean canSend = parking.messagesAllowed(SystemClock.elapsedRealtime())
                && snapshot.sharing && snapshot.connection == SharingModels.Connection.CONNECTED;
        demoQuickMessages.setVisibility(canSend ? View.VISIBLE : View.GONE);
        String group = snapshot.groupId.isEmpty() ? "참여한 그룹 없음" : "가상 동행 그룹 · " + snapshot.inviteCode;
        String sharing = snapshot.sharing ? "가상 팀원 정보 갱신 중" : "가상 공유 중지";
        demoStatus.setText(group + "\n" + sharing + " · "
                + (allowed ? "체험 이어가기 가능" : "그룹 설정 잠김 · 정차 확인 필요"));
        if (snapshot.vehicles.isEmpty()) {
            demoVehicles.setText("표시할 가상 팀원 정보가 없습니다.");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        StringBuilder rows = new StringBuilder("가상 팀원 상황");
        for (SharingModels.Vehicle vehicle : snapshot.vehicles) {
            rows.append('\n').append(vehicle.name).append(" · ").append(vehicle.destinationName).append(" · ");
            if (snapshot.connection != SharingModels.Connection.CONNECTED) rows.append("모의 연결 끊김");
            else if (vehicle.stale(now)) rows.append("갱신 대기");
            else rows.append("마지막 예상 소요 약 ").append((vehicle.remainingSeconds + 59) / 60).append("분");
        }
        demoVehicles.setText(rows);
    }
    private void requestLocation() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) { source.refreshLocation(); render(); }
        if (requestCode == VEHICLE_REQUEST && parkingSource != null) { parkingSource.stop(); parkingSource.start(); }
    }
    private void render() {
        if (connection == null) return;
        long now = SystemClock.elapsedRealtime();
        renderDemoStatus(parking.allowed(now));
        connection.setText(state.connectionMessage);
        permission.setVisibility(state.precisePermission ? View.GONE : View.VISIBLE);
        permission.setText(state.locationPermission ? "정확한 위치 사용" : "내 위치 사용");
        if (!state.locationPermission) {
            position.setText("위치 권한이 필요합니다");
            positionDetail.setText("‘내 위치 사용’을 눌러 허용해 주세요. 위치는 이 화면에서만 사용합니다.");
        } else if (!state.locationEnabled) {
            position.setText("위치 기능이 꺼져 있습니다"); positionDetail.setText("차량의 위치 설정을 켜 주세요.");
        } else if (state.gpsAt < 0) {
            position.setText(state.providerAvailable ? "위치 수신 대기 중" : "위치 정보를 받을 수 없습니다");
            positionDetail.setText(state.precisePermission ? "위치를 받으면 자동으로 갱신됩니다." : "대략적인 위치 권한 사용 중 · 정확한 위치를 허용하면 다시 확인합니다.");
        } else {
            String address = state.currentAddress(now);
            position.setText(address.isEmpty() ? "주소 확인 대기" : address);
            String accuracy = Double.isInfinite(state.gpsAccuracy) ? "정확도 알 수 없음" : String.format(Locale.KOREA, "정확도 ±%.0fm", state.gpsAccuracy);
            positionDetail.setText((JourneyState.fresh(state.gpsAt, now) ? "" : "최근 위치 · 갱신 지연\n")
                    + (address.isEmpty() ? "현재 위치에 맞는 주소를 확인할 수 없습니다.\n" : "")
                    + (state.precisePermission ? "차량 GPS" : "대략적인 위치") + " · " + accuracy + "\n" + age(state.gpsAt, now));
        }
        if (state.hasDestination()) {
            destination.setText(state.destinationName);
            destinationDetail.setText((state.destinationAddress.isEmpty() ? "주소 정보가 없습니다" : state.destinationAddress)
                    + "\n" + age(state.destinationAt, now));
        } else {
            destination.setText(state.destinationKnown ? "목적지가 없습니다" : "내비 정보 확인 중");
            destinationDetail.setText("내비게이션에서 목적지를 설정하면 자동으로 표시됩니다.");
        }
        JourneyState.EtaStatus status = state.etaStatus(now);
        switch (status) {
            case READY:
                eta.setText(duration(state.remainingSeconds));
                etaDetail.setText(arrival() + " 도착 예정 · " + distance(state.remainingMeters) + " 남음\n내비 경로 기준 · " + age(state.destinationAt, now));
                break;
            case CHECK_LOCATION:
                eta.setText("위치 확인 필요");
                String reason = state.mismatch(now) ? "GPS와 내비 위치가 " + distance((int) JourneyState.distanceMeters(state.gpsLatitude, state.gpsLongitude, state.naviLatitude, state.naviLongitude)) + " 다릅니다."
                        : "최신 GPS와 내비 위치가 모두 확인되어야 합니다.";
                etaDetail.setText(reason + "\n내비가 제공한 예상 소요 시간: " + duration(state.remainingSeconds) + " · 현재 위치 기준 여부 확인 중");
                break;
            case NO_DESTINATION: eta.setText("목적지를 설정해 주세요"); etaDetail.setText("경로 안내를 시작하면 남은 시간과 도착 예정 시각을 표시합니다."); break;
            case ARRIVED: eta.setText("목적지 도착"); etaDetail.setText("내비게이션에서 도착 상태를 받았습니다."); break;
            case STALE: eta.setText("도착시간 갱신 대기"); etaDetail.setText("마지막 정보를 최신 도착시간으로 표시하지 않습니다. 연결 상태를 확인해 주세요."); break;
            case WAITING: eta.setText("도착시간 확인 중"); etaDetail.setText("내비게이션에서 유효한 남은 시간을 기다리고 있습니다."); break;
            case CONNECTING: eta.setText("Pleos 연결 중"); etaDetail.setText("내비게이션 정보를 불러오고 있습니다."); break;
            default: eta.setText("내비 연결 확인 필요"); etaDetail.setText("‘다시 연결’을 눌러 재시도해 주세요.");
        }
        // Status only: coordinates and destinations are not written to the production log.
        if (!status.name().equals(lastStatus)) {
            android.util.Log.i("DonghaengMain", "etaStatus=" + status.name()); lastStatus = status.name();
        }
    }
    private String arrival() {
        return new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(state.destinationWallTime + state.remainingSeconds * 1000L));
    }
    private static String duration(int seconds) { return "약 " + Math.max(1, (seconds + 59L) / 60) + "분"; }
    private static String distance(int meters) { return meters < 1000 ? meters + "m" : String.format(Locale.KOREA, "%.1fkm", meters / 1000.0); }
    private static String age(long at, long now) {
        long seconds = Math.max(0, (now - at) / 1000);
        return seconds < 60 ? seconds + "초 전 갱신" : seconds / 60 + "분 전 갱신";
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card(String title) {
        return card(title, Color.rgb(25, 41, 54));
    }
    private LinearLayout card(String title, int color) {
        LinearLayout view = column(); view.setPadding(dp(22), dp(18), dp(22), dp(20));
        GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(16)); view.setBackground(background);
        TextView label = text(title, 17, Color.rgb(156, 178, 192));
        label.setPadding(0, 0, 0, dp(8));
        view.addView(label);
        return view;
    }
    private Button demoAction(String label, Runnable action) {
        Button view = button(label, action);
        view.setTextSize(18);
        view.setMinHeight(dp(56));
        return view;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, dp(4), 0, dp(5)); return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setOnClickListener(v -> action.run()); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
