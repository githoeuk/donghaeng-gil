package io.demo.companion;

import android.app.Activity;
import android.app.AlertDialog;
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
    private static MockSharingTransport scenario;
    private boolean visible;
    private TextView status, vehicles;
    private LinearLayout messages;
    private EditText invite;
    private Button createGroup, requestJoin, approveRequest, startSharing, stopSharing, leaveGroup;
    private TextView nextStep;
    private Button receiveExample;
    private AlertDialog sharingConsent;
    private enum Approval { NONE, HOST_PENDING, GUEST_PENDING, APPROVED }
    private static Approval approval = Approval.NONE;
    private String renderedMessages = "initial";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!visible || !parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
            scenario.tick(); handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        parkingSource = new VehicleParkingSource(this, parking, this::renderSafety);
    }
    private void buildDemoUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(14,24,34));
        LinearLayout root = column(); root.setPadding(28,20,28,30); scroll.addView(root);
        root.addView(text("동행길 · 가상 차량 체험", 28));
        root.addView(text("체험 화면의 차량·주소·메시지는 모두 가상이며 외부로 전송하지 않습니다. 실제 내 차량 정보는 메인 화면에서 확인하세요.", 16));
        status = text("", 17); root.addView(status);
        nextStep = text("", 18); root.addView(nextStep);
        LinearLayout groupActions = row(); root.addView(groupActions);
        createGroup = demoButton("그룹 만들기", () -> {
            approval = Approval.HOST_PENDING;
            scenario.createGroup();
        });
        groupActions.addView(createGroup);
        invite = new EditText(this); invite.setSingleLine(true); invite.setHint("DEMO42");
        invite.setTextColor(Color.WHITE);
        invite.setContentDescription("체험 초대 코드");
        groupActions.addView(invite, new LinearLayout.LayoutParams(200, -2));
        requestJoin = demoButton("참여 요청", this::requestJoin);
        groupActions.addView(requestJoin);
        approveRequest = demoButton("참여 승인", this::approveRequest);
        root.addView(approveRequest);
        LinearLayout sharingActions = row(); root.addView(sharingActions);
        startSharing = demoButton("공유 시작", this::confirmSharing);
        stopSharing = demoButton("공유 중지", () -> scenario.stopSharing());
        leaveGroup = demoButton("그룹 나가기", () -> {
            approval = Approval.NONE;
            scenario.leaveGroup();
        });
        sharingActions.addView(startSharing);
        sharingActions.addView(stopSharing);
        sharingActions.addView(leaveGroup);
        root.addView(text("가상 동행 차량 · 같은 목적지로 가는 차량끼리 도착 시간 비교", 18));
        vehicles = text("",18); root.addView(vehicles);
        LinearLayout networkActions = row(); root.addView(networkActions);
        networkActions.addView(demoButton("연결 끊김 시험", () -> scenario.disconnect()));
        networkActions.addView(demoButton("갱신 중단 시험", () -> scenario.freeze()));
        networkActions.addView(demoButton("모의 재연결", () -> scenario.reconnect()));
        root.addView(text("가상 상황 메시지",18));
        root.addView(text("가상 차량과 메시지를 주고받는 연습입니다. 전달과 확인 상태도 체험에서 재현합니다.",16));
        LinearLayout messageActions = row(); root.addView(messageActions);
        for (MessageKind kind : MessageKind.values()) messageActions.addView(demoButton(kind.label, () -> scenario.send(kind)));
        receiveExample = demoButton("가상 차량에서 메시지 받기", () -> scenario.receiveExampleMessage());
        root.addView(receiveExample);
        messages = column(); root.addView(messages);
        root.addView(button("내 차량 화면으로", this::finish));
        root.addView(button("체험 종료", this::endDemo));
        setContentView(scroll);
    }
    @Override protected void onStart() {
        super.onStart();
        visible = true;
        lockedReason = "checking";
        parkingSource.start();
        renderSafety();
    }
    @Override protected void onStop() {
        visible = false;
        suspendDemoUi();
        parkingSource.stop();
        super.onStop();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 21) { parkingSource.stop(); parkingSource.start(); }
    }
    private void suspendDemoUi() {
        if (sharingConsent != null) {
            sharingConsent.dismiss();
            sharingConsent = null;
        }
        handler.removeCallbacks(ticker);
        if (scenario != null) scenario.setListener(null);
        lockedReason = null;
    }
    private void endDemo() {
        suspendDemoUi();
        if (scenario != null) scenario.close();
        scenario = null;
        approval = Approval.NONE;
        MainActivity.demoOverview = null;
        finish();
    }
    private void requestJoin() {
        if (!"DEMO42".equalsIgnoreCase(invite.getText().toString().trim())) {
            invite.setError("체험 초대 코드 DEMO42를 입력해 주세요.");
            return;
        }
        approval = Approval.GUEST_PENDING;
        render(scenario.snapshot());
    }
    private void approveRequest() {
        if (approval == Approval.GUEST_PENDING) {
            approval = Approval.APPROVED;
            scenario.joinGroup("DEMO42");
        } else if (approval == Approval.HOST_PENDING) {
            approval = Approval.APPROVED;
            render(scenario.snapshot());
        }
    }
    private void confirmSharing() {
        if (approval != Approval.APPROVED) return;
        sharingConsent = new AlertDialog.Builder(this)
                .setTitle("가상 위치 공유 시작")
                .setMessage("가상 차량의 위치·목적지·예상 도착 시간을 체험 화면에 표시합니다. 실제 차량 정보는 사용하지 않으며 외부로 전송하지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("체험 공유 시작", (dialog, which) -> {
                    if (visible && scenario != null && approval == Approval.APPROVED
                            && parking.allowed(SystemClock.elapsedRealtime())) scenario.startSharing();
                }).create();
        sharingConsent.show();
    }
    private void startDemo() {
        if (scenario != null) return;
        if (!visible || !parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
        scenario = new MockSharingTransport(new MockSharingTransport.Clock() {
            public long elapsed() { return SystemClock.elapsedRealtime(); }
            public long wall() { return System.currentTimeMillis(); }
        });
        MainActivity.demoOverview = scenario::refreshOverview;
        showActiveDemo();
    }
    private void showActiveDemo() {
        lockedReason = null;
        renderedMessages = "initial";
        buildDemoUi();
        scenario.setListener(this::render);
        handler.post(ticker);
    }
    private void renderSafety() {
        if (!visible) return;
        boolean allowed = parking.allowed(SystemClock.elapsedRealtime());
        if (allowed && scenario != null) {
            if (lockedReason != null) showActiveDemo();
            return;
        }
        String reason = parking.description(SystemClock.elapsedRealtime());
        String key = allowed + ":" + reason + ":" + parkingSource.hasPermission();
        if (key.equals(lockedReason)) return;
        suspendDemoUi();
        if (getCurrentFocus() != null) getCurrentFocus().clearFocus();
        android.view.inputmethod.InputMethodManager input = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (input != null) input.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        lockedReason = key;
        LinearLayout locked = column(); locked.setPadding(28, 20, 28, 30);
        locked.setBackgroundColor(Color.rgb(14, 24, 34));
        locked.addView(text(allowed ? "정차 확인 · 체험 가능" : "정차 후 체험할 수 있어요", 24));
        locked.addView(text(reason, 18));
        locked.addView(text(scenario == null ? "P 기어로 정차하면 새 체험을 시작할 수 있습니다."
                : "그룹과 메시지를 보관했습니다. 정차가 확인되면 체험을 이어갑니다.", 17));
        if (scenario == null) {
            Button start = button("새 체험 시작", this::startDemo);
            start.setEnabled(allowed);
            locked.addView(start);
        } else {
            locked.addView(button("체험 종료", this::endDemo));
        }
        if (!parkingSource.hasPermission()) locked.addView(button("차량 상태 접근 허용", () ->
                requestPermissions(new String[]{VehicleParkingSource.SPEED_PERMISSION}, 21)));
        locked.addView(button("내 차량 화면으로", this::finish)); setContentView(locked);
    }
    private Button demoButton(String label, Runnable action) {
        return button(label, () -> {
            if (!visible || !parking.allowed(SystemClock.elapsedRealtime())) { renderSafety(); return; }
            action.run();
        });
    }    private void render(Snapshot snapshot) {
        if (!snapshot.demo) throw new IllegalStateException("Demo screen accepts demo data only");
        renderSteps(snapshot);
        status.setText((snapshot.groupId.isEmpty() ? "그룹 미참여" : "데모 그룹 · 초대 코드 " + snapshot.inviteCode)
                + " · " + (snapshot.connection == Connection.CONNECTED ? "모의 연결됨" : "모의 연결 끊김")
                + " · " + (snapshot.sharing ? "모의 공유 중" : "공유 중지") + "\n" + snapshot.notice);
        long now = SystemClock.elapsedRealtime(); StringBuilder rows = new StringBuilder();
        Vehicle reference = snapshot.vehicles.isEmpty() ? null : snapshot.vehicles.get(0);
        for (Vehicle vehicle : snapshot.vehicles) {
            boolean stale = vehicle.stale(now);
            rows.append(vehicle.name).append(" · ").append(vehicle.address).append('\n')
                .append(vehicle.destinationName).append(" · ").append(vehicle.destinationAddress).append('\n');
            if (stale) rows.append("오래된 정보 · 예상 도착 시간 표시 보류");
            else if (snapshot.connection != Connection.CONNECTED) rows.append("연결 끊김 · 예상 도착 시간 확인 불가");
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
            boolean incoming = !message.sender.equals("나 (모의)");
            String delivery;
            switch (message.delivery) {
                case DELIVERED: delivery = incoming ? "가상 메시지 수신" : "가상 상대에게 전달됨"; break;
                case ACKNOWLEDGED: delivery = incoming ? "내가 확인함" : "가상 상대가 확인함"; break;
                case FAILED: delivery = "모의 전송 실패 · 자동 재전송 없음"; break;
                default: delivery = "모의 전송 대기";
            }
            messages.addView(text(message.sender + " · " + message.kind.label + " · " + delivery,16));
            if (incoming && message.delivery == Delivery.DELIVERED)
                messages.addView(demoButton("받은 메시지 확인", () -> scenario.acknowledge(message.id)));
        }
    }
    private void renderSteps(Snapshot snapshot) {
        createGroup.setEnabled(approval == Approval.NONE);
        invite.setEnabled(approval == Approval.NONE);
        requestJoin.setEnabled(approval == Approval.NONE);
        boolean pending = approval == Approval.HOST_PENDING || approval == Approval.GUEST_PENDING;
        approveRequest.setVisibility(pending ? android.view.View.VISIBLE : android.view.View.GONE);
        approveRequest.setText(approval == Approval.GUEST_PENDING ? "가상 그룹장 승인 재현" : "가상 차량 참여 승인");
        startSharing.setEnabled(approval == Approval.APPROVED && !snapshot.sharing
                && snapshot.connection == Connection.CONNECTED);
        receiveExample.setEnabled(snapshot.sharing && snapshot.connection == Connection.CONNECTED);
        stopSharing.setEnabled(snapshot.sharing);
        leaveGroup.setEnabled(approval != Approval.NONE);
        String step;
        switch (approval) {
            case HOST_PENDING: step = "2. 가상 차량의 참여 요청이 왔습니다. 승인해 주세요."; break;
            case GUEST_PENDING: step = "2. 승인 대기 중입니다. 가상 그룹장의 승인을 재현해 보세요."; break;
            case APPROVED: step = snapshot.sharing ? "공유 중 · 가상 차량과 도착 시간을 확인하고 메시지를 보내 보세요."
                    : "3. 참여가 승인됐습니다. 공유 시작을 눌러 전송 범위를 확인하세요."; break;
            default: step = "1. 그룹을 만들거나 초대 코드 DEMO42로 참여를 요청하세요.";
        }
        if (!step.contentEquals(nextStep.getText())) nextStep.setText(step);
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(size); view.setPadding(0,8,0,8); return view; }
    private Button button(String label, Runnable action) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(view -> action.run()); return button; }
}
