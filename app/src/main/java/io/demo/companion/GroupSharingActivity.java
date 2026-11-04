package io.demo.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import static io.demo.companion.SharingModels.*;

/** Foreground-only real sharing. Setup/messages are parked-only; started sharing may continue while visible. */
public final class GroupSharingActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final JourneyState journey=new JourneyState();
    private final ParkingState parking=new ParkingState();
    private PleosJourneySource journeySource;
    private VehicleParkingSource parkingSource;
    private HttpSharingTransport relay;
    private EditText endpoint,name,code;
    private TextView safety,status,vehicles;
    private LinearLayout messages;
    private String renderedMessages="initial";
    private final List<View> parkedControls=new ArrayList<>();
    private AlertDialog consent;
    private final Runnable ticker=new Runnable(){public void run(){
        if(relay!=null)relay.publish(JourneyPublisher.current(journey,SystemClock.elapsedRealtime(),System.currentTimeMillis()));
        render(); handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(14,24,34));
        LinearLayout root=column(); root.setPadding(28,18,28,24); scroll.addView(root);
        root.addView(text("동행길 · 실제 서버 공유",26));
        root.addView(text("모의 모드가 아닙니다. 공유 시작을 누른 동안만 실제 위치·목적지·ETA를 전송합니다. 화면을 벗어나면 그룹 연결을 종료합니다.",16));
        safety=text("차량 상태 확인 중",16);root.addView(safety);
        Button permissions=parkedButton("위치·차량 상태 권한",()->requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,VehicleParkingSource.SPEED_PERMISSION},22));root.addView(permissions);
        LinearLayout setup=row();root.addView(setup);
        endpoint=edit(BuildConfig.DEBUG?"http://10.0.2.2:8787":"","서버 주소");setup.addView(endpoint,new LinearLayout.LayoutParams(0,-2,3));
        name=edit("차량","차량 이름");setup.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        setup.addView(parkedButton("서버 연결",this::connect)); parkedControls.add(endpoint); parkedControls.add(name);
        status=text("서버 연결 전 · 위치 전송 중지",17);root.addView(status);
        LinearLayout group=row();root.addView(group);
        group.addView(parkedButton("그룹 만들기",()->{if(relay!=null)relay.createGroup();}));
        code=edit("","초대 코드");group.addView(code,new LinearLayout.LayoutParams(240,-2));parkedControls.add(code);
        group.addView(parkedButton("그룹 참여",()->{if(relay!=null)relay.joinGroup(code.getText().toString());}));
        group.addView(parkedButton("공유 시작",this::confirmSharing));
        group.addView(button("공유 중지",()->{if(relay!=null)relay.stopSharing();}));
        group.addView(button("그룹 나가기",()->{if(relay!=null)relay.leaveGroup();}));
        vehicles=text("서버에서 받은 차량 정보가 없습니다.",17);root.addView(vehicles);
        LinearLayout actions=row();root.addView(actions);
        for(MessageKind kind:MessageKind.values())actions.addView(parkedButton(kind.label,()->{if(relay!=null)relay.send(kind);}));
        messages=column();root.addView(messages);
        root.addView(button("내 차량 화면으로",this::finish));setContentView(scroll);
        journeySource=new PleosJourneySource(this,journey,()->{});
        parkingSource=new VehicleParkingSource(this,parking,this::render);
    }
    private void connect(){
        try{
            if(relay!=null)relay.close();
            relay=new HttpSharingTransport(endpoint.getText().toString(),name.getText().toString());
            relay.setListener(snapshot->render());relay.connect();
        }catch(IllegalArgumentException error){updateText(status,error.getMessage());relay=null;}
    }
    private void confirmSharing(){
        if(relay==null)return;
        consent=new AlertDialog.Builder(this).setTitle("실제 위치 공유 시작")
            .setMessage("현재 위치 좌표·주소, 내비 목적지·남은 시간·거리를 입력한 서버와 그룹 구성원에게 보냅니다. 화면 종료 또는 공유 중지 전까지 전송하며 서버는 메모리에서만 임시 보관합니다.\n서버: "+endpoint.getText())
            .setNegativeButton("취소",null).setPositiveButton("위치 공유 시작",(dialog,which)->{
                if(parking.allowed(SystemClock.elapsedRealtime()) && relay!=null)relay.startSharing();
            }).create();consent.show();
    }
    @Override protected void onStart(){super.onStart();journeySource.start();parkingSource.start();handler.post(ticker);}
    @Override protected void onStop(){
        handler.removeCallbacksAndMessages(null);journeySource.stop();parkingSource.stop();
        if(consent!=null)consent.dismiss(); if(relay!=null){relay.close();relay=null;} super.onStop();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==22){journeySource.refreshLocation();parkingSource.stop();parkingSource.start();}
    }
    private void render(){
        if(safety==null)return;
        boolean parked=parking.allowed(SystemClock.elapsedRealtime());
        updateText(safety,(parked?"정차 확인 · 설정·메시지 가능":"설정·메시지 잠김 · ")+parking.description(SystemClock.elapsedRealtime()));
        for(View control:parkedControls)control.setEnabled(parked);
        if(!parked && consent!=null && consent.isShowing())consent.dismiss();
        if(!parked){endpoint.clearFocus();name.clearFocus();code.clearFocus();android.view.inputmethod.InputMethodManager input=getSystemService(android.view.inputmethod.InputMethodManager.class);if(input!=null)input.hideSoftInputFromWindow(code.getWindowToken(),0);}
        if(relay==null)return;
        Snapshot snapshot=relay.snapshot(); if(snapshot.demo)throw new IllegalStateException("Real screen cannot show mock data");
        updateText(status,(snapshot.groupId.isEmpty()?"그룹 미참여":"그룹 연결 · 초대 코드 "+snapshot.inviteCode)
            +" · "+(snapshot.connection==Connection.CONNECTED?"서버 연결됨":"연결 끊김/요청 확인 필요")
            +" · "+(snapshot.sharing?"내 위치 공유 중":"내 위치 공유 중지")+"\n"+snapshot.notice+"\n"+relay.roster());
        long now=SystemClock.elapsedRealtime();Vehicle mine=null;
        for(Vehicle vehicle:snapshot.vehicles)if(vehicle.id.equals(relay.userId()))mine=vehicle;
        StringBuilder rows=new StringBuilder();
        for(Vehicle vehicle:snapshot.vehicles){
            rows.append(vehicle.name).append(vehicle.id.equals(relay.userId())?" (나)":" (동행)").append(" · ")
                .append(vehicle.address.isEmpty()?"주소 확인 중":vehicle.address).append('\n');
            if(BuildConfig.DEBUG)rows.append(String.format(Locale.ROOT,"개발용 좌표 %.6f, %.6f\n",vehicle.latitude,vehicle.longitude));
            rows.append(vehicle.destinationName.isEmpty()?"목적지 없음/확인 중":vehicle.destinationName).append(" · ").append(vehicle.destinationAddress).append('\n');
            if(vehicle.stale(now) || snapshot.connection!=Connection.CONNECTED)rows.append("최신 위치·ETA 확인 불가 · 갱신 지연");
            else if(vehicle.remainingSeconds<0)rows.append("ETA 확인 중 · 내비/위치 조건 확인 필요");
            else rows.append("약 ").append((vehicle.remainingSeconds+59)/60).append("분 · ")
                .append(String.format(Locale.KOREA,"%.1fkm",vehicle.remainingMeters/1000.0)).append(" · ")
                .append(new SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(vehicle.generatedAtMillis+vehicle.remainingSeconds*1000L))).append(" 도착 예정");
            rows.append(" · ").append(Math.max(0,(now-vehicle.receivedAtElapsed)/1000)).append("초 전 데이터\n");
            if(!vehicle.id.equals(relay.userId()))rows.append(mine==null?"내 차량 비교 기준 확인 중":vehicle.sameDestination(mine)?"같은 목적지":"다른 목적지 · 도착 순서 비교 제외").append('\n');
            rows.append('\n');
        }
        updateText(vehicles,rows.length()==0?"공유된 최신 위치가 없습니다. 공유 시작과 위치·내비 상태를 확인하세요.":rows.toString());
        StringBuilder key=new StringBuilder(Boolean.toString(parked));for(Message message:snapshot.messages)key.append(message.id).append(message.delivery);
        if(key.toString().equals(renderedMessages))return;renderedMessages=key.toString();parkedControls.removeIf(view -> view.getParent()==messages);messages.removeAllViews();
        if(!parked){messages.addView(text("주행·상태 미확인 중 메시지는 숨깁니다. 정차 후 확인하세요.",16));return;}
        if(snapshot.messages.isEmpty())messages.addView(text("수신된 메시지가 없습니다.",16));
        for(Message message:snapshot.messages){
            boolean own=message.senderId.equals(relay.userId());
            String delivery=message.delivery==Delivery.ACKNOWLEDGED?(own?"상대가 확인":"내가 확인"):message.delivery==Delivery.DELIVERED?"앱 수신 확인":"상대 앱 수신 대기";
            messages.addView(text(message.sender+(own?" (나)":"")+" · "+message.kind.label+" · "+delivery,17));
            if(!own && message.delivery==Delivery.DELIVERED)messages.addView(parkedButton("메시지 확인",()->{if(relay!=null)relay.acknowledge(message.id);}));
        }
    }
    private static void updateText(TextView view,CharSequence value){if(!view.getText().toString().contentEquals(value))view.setText(value);}
    private EditText edit(String value,String hint){EditText input=new EditText(this);input.setSingleLine(true);input.setText(value);input.setHint(hint);input.setContentDescription(hint);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.LTGRAY);return input;}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);return layout;}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.WHITE);view.setTextSize(size);view.setPadding(0,6,0,8);return view;}
    private Button button(String label,Runnable action){Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setOnClickListener(view->action.run());return button;}
    private Button parkedButton(String label,Runnable action){Button button=button(label,()->{if(parking.allowed(SystemClock.elapsedRealtime()))action.run();else render();});parkedControls.add(button);return button;}
}
