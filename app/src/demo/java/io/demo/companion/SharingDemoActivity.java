package io.demo.companion;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import static io.demo.companion.SharingModels.*;

/** Read-only real vehicle safety state gates a separate fictional sharing scenario. */
public final class SharingDemoActivity extends Activity {
    private final ParkingState parking = new ParkingState();
    private VehicleParkingSource parkingSource;
    private String lockedReason;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharingTransport transport;
    private MockSharingTransport scenario;
    private TextView status, vehicles;
    private LinearLayout messages;
    private EditText invite;
    private String renderedMessages = "initial";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
            scenario.tick(); handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        parkingSource = new VehicleParkingSource(this, parking, this::renderSafety);
        renderSafety();
    }
    private void buildDemoUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(14,24,34));
        LinearLayout root = column(); root.setPadding(28,20,28,30); scroll.addView(root);
        root.addView(text("동행길 · 모의 공유", 28));
        root.addView(text("정차 중 시험용 · 공식 앱 미연동 · 실제 네트워크 통신 없음\n모든 차량·주소·메시지는 가상입니다. 실제 내 차량 정보는 메인 화면에서 확인하세요.", 16));
        status = text("", 17); root.addView(status);
        LinearLayout groupActions = row(); root.addView(groupActions);
        groupActions.addView(demoButton("데모 그룹 만들기", () -> transport.createGroup()));
        invite = new EditText(this); invite.setSingleLine(true); invite.setText("DEMO42"); invite.setTextColor(Color.WHITE);
        invite.setContentDescription("데모 초대 코드"); groupActions.addView(invite, new LinearLayout.LayoutParams(200,-2));
        groupActions.addView(demoButton("코드로 데모 참여", () -> transport.joinGroup(invite.getText().toString())));
        LinearLayout sharingActions = row(); root.addView(sharingActions);
        sharingActions.addView(demoButton("공유 시작", () -> transport.startSharing()));
        sharingActions.addView(demoButton("공유 중지", () -> transport.stopSharing()));
        sharingActions.addView(demoButton("그룹 나가기", () -> transport.leaveGroup()));
        root.addView(text("모의 동행 차량 · 장소 ID가 같은 차량끼리만 ETA 비교", 18));
        vehicles = text("",18); root.addView(vehicles);
        LinearLayout networkActions = row(); root.addView(networkActions);
        networkActions.addView(demoButton("연결 끊김 시험", () -> scenario.disconnect()));
        networkActions.addView(demoButton("갱신 중단 시험", () -> scenario.freeze()));
        networkActions.addView(demoButton("모의 재연결", () -> scenario.reconnect()));
        root.addView(text("상황 메시지 · 전달·확인 모두 모의 결과",18));
        LinearLayout messageActions = row(); root.addView(messageActions);
        for (MessageKind kind : MessageKind.values()) messageActions.addView(demoButton(kind.label, () -> transport.send(kind)));
        messages = column(); root.addView(messages);
        root.addView(button("내 차량 화면으로", this::finish));
        setContentView(scroll);
    }
    @Override protected void onStart() {
        super.onStart(); parkingSource.start();
    }
    @Override protected void onStop() {
        parkingSource.stop(); stopDemo(); super.onStop();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 21) { parkingSource.stop(); parkingSource.start(); }
    }
    private void stopDemo() {
        handler.removeCallbacksAndMessages(null);
        if (transport != null) {
            if (getCurrentFocus() != null) getCurrentFocus().clearFocus();
            android.view.inputmethod.InputMethodManager input = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (input != null) input.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            transport.close();
        }
        transport = null; scenario = null; renderedMessages = "initial";
    }
    private void startDemo() {
        if (transport != null) return;
        if (!parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
        buildDemoUi(); lockedReason = null;
        scenario = new MockSharingTransport(new MockSharingTransport.Clock() {
            public long elapsed() { return SystemClock.elapsedRealtime(); }
            public long wall() { return System.currentTimeMillis(); }
        });
        transport = scenario; transport.setListener(this::render); handler.post(ticker);
    }
    private void renderSafety() {
        boolean allowed = parking.allowed(SystemClock.elapsedRealtime());
        if (allowed && transport != null) return;
        if (!allowed) stopDemo();
        String reason = parking.description(SystemClock.elapsedRealtime());
        String key = allowed + ":" + reason + ":" + parkingSource.hasPermission();
        if (key.equals(lockedReason)) return;
        lockedReason = key;
        LinearLayout locked = column(); locked.setPadding(28, 20, 28, 30);
        locked.setBackgroundColor(Color.rgb(14, 24, 34));
        locked.addView(text(allowed ? "정차 확인 · 모의 시연 가능" : "모의 시연 잠김", 24));
        locked.addView(text(reason, 18));
        locked.addView(text("P·속도 0·플랫폼 제한 없음일 때만 실행합니다. 주행·상태 미수신 시 그룹과 메시지를 삭제하며 자동 재개하지 않습니다.", 17));
        Button start = button("새 모의 시연 시작", this::startDemo); start.setEnabled(allowed); locked.addView(start);
        if (!parkingSource.hasPermission()) locked.addView(button("차량 상태 접근 허용", () ->
                requestPermissions(new String[]{VehicleParkingSource.SPEED_PERMISSION}, 21)));
        locked.addView(button("내 차량 화면으로", this::finish)); setContentView(locked);
    }
    private Button demoButton(String label, Runnable action) {
        return button(label, () -> {
            if (!parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
            action.run();
        });
    }    private void render(Snapshot snapshot) {
        if (!snapshot.demo) throw new IllegalStateException("Demo screen accepts demo data only");
        status.setText((snapshot.groupId.isEmpty() ? "그룹 미참여" : "데모 그룹 · 초대 코드 " + snapshot.inviteCode)
                + " · " + (snapshot.connection == Connection.CONNECTED ? "모의 연결됨" : "모의 연결 끊김")
                + " · " + (snapshot.sharing ? "모의 공유 중" : "공유 중지") + "\n" + snapshot.notice);
        long now = SystemClock.elapsedRealtime(); StringBuilder rows = new StringBuilder();
        Vehicle reference = snapshot.vehicles.isEmpty() ? null : snapshot.vehicles.get(0);
        for (Vehicle vehicle : snapshot.vehicles) {
            boolean stale = vehicle.stale(now);
            rows.append(vehicle.name).append(" · ").append(vehicle.address).append('\n')
                .append(vehicle.destinationName).append(" · ").append(vehicle.destinationAddress).append('\n');
            if (stale) rows.append("오래된 정보 · 최신 ETA 표시 보류");
            else if (snapshot.connection != Connection.CONNECTED) rows.append("연결 끊김 · 최신 ETA 확인 불가");
            else rows.append("약 ").append((vehicle.remainingSeconds + 59)/60).append("분 · ")
                    .append(String.format(Locale.KOREA,"%.1fkm",vehicle.remainingMeters/1000.0)).append(" 남음 · ")
                    .append(new SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(vehicle.generatedAtMillis + vehicle.remainingSeconds*1000L))).append(" 도착 예정");
            rows.append(" · ").append(Math.max(0,(now-vehicle.receivedAtElapsed)/1000)).append("초 전 갱신\n")
                .append(vehicle.sameDestination(reference) ? "같은 목적지 (모의 차량 가 기준)" : "다른 목적지 · 도착 순서 비교 제외").append("\n\n");
        }
        vehicles.setText(rows.length() == 0 ? "표시할 모의 차량이 없습니다. 그룹 참여 후 공유를 시작하세요." : rows.toString());
        StringBuilder messageKey = new StringBuilder();
        for (Message message : snapshot.messages) messageKey.append(message.id).append(message.delivery);
        if (messageKey.toString().equals(renderedMessages)) return;
        renderedMessages = messageKey.toString();
        messages.removeAllViews();
        if (snapshot.messages.isEmpty()) messages.addView(text("메시지가 없습니다.",16));
        for (Message message : snapshot.messages) {
            String delivery;
            switch (message.delivery) {
                case DELIVERED: delivery = "모의 전달됨"; break;
                case ACKNOWLEDGED: delivery = "모의 확인됨"; break;
                case FAILED: delivery = "모의 전송 실패 · 자동 재전송 없음"; break;
                default: delivery = "모의 전송 대기";
            }
            messages.addView(text(message.sender + " · " + message.kind.label + " · " + delivery,16));
            if (!message.sender.equals("나 (모의)") && message.delivery == Delivery.DELIVERED)
                messages.addView(demoButton("모의 응답 확인", () -> transport.acknowledge(message.id)));
        }
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(size); view.setPadding(0,8,0,8); return view; }
    private Button button(String label, Runnable action) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(view -> action.run()); return button; }
}
