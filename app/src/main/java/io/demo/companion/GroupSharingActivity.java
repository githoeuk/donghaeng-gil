package io.demo.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
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

/** UI binds to the sharing owner. Leaving this screen does not end explicitly started sharing. */
public final class GroupSharingActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ParkingState parking=new ParkingState();
    private SharingService service;
    private boolean binding;
    private final Runnable observer=this::render;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName component,IBinder binder){
            service=((SharingService.LocalBinder)binder).service();
            if(!service.endpoint().isEmpty()){endpoint.setText(service.endpoint());name.setText(service.vehicleName());}
            renderedMessages="initial";service.attach(observer);
        }
        public void onServiceDisconnected(ComponentName component){service=null;relay=null;updateText(status,"공유 서비스 연결이 종료됐습니다. 다시 화면을 열어 확인하세요.");}
    };
    private VehicleParkingSource parkingSource;
    private HttpSharingTransport relay;
    private EditText endpoint,name,code;
    private TextView safety,status,vehicles,groupHelp;
    private Button copyInvite;
    private LinearLayout messages;
    private String renderedMessages="initial";
    private final List<View> parkedControls=new ArrayList<>();
    private AlertDialog consent;
    private final Runnable ticker=new Runnable(){public void run(){
        render(); handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(14,24,34));
        LinearLayout root=column(); root.setPadding(28,18,28,24); scroll.addView(root);
        root.addView(text("동행길 · 실제 서버 공유",26));
        root.addView(text("모의 모드가 아닙니다. 공유 시작 후 내비 등 다른 앱에서도 실제 위치·목적지·ETA를 전송합니다. 중지 버튼이나 지속 알림에서 종료하세요.",16));
        safety=text("차량 상태 확인 중",16);root.addView(safety);
        Button permissions=parkedButton("위치·차량 상태 권한",()->requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,VehicleParkingSource.SPEED_PERMISSION},22));root.addView(permissions);
        LinearLayout setup=row();root.addView(setup);
        endpoint=edit(BuildConfig.DEBUG?"http://10.0.2.2:8787":"","서버 주소");setup.addView(endpoint,new LinearLayout.LayoutParams(0,-2,3));
        name=edit("차량","차량 이름");setup.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        setup.addView(parkedButton("서버 연결",this::connect)); parkedControls.add(endpoint); parkedControls.add(name);
        status=text("서버 연결 전 · 위치 전송 중지",17);root.addView(status);
        groupHelp=text("두 차량 모두 같은 서버에 연결하세요. 한 차량이 그룹을 만들고, 다른 차량은 받은 초대 코드로 참여합니다.",16);root.addView(groupHelp);
        LinearLayout group=row();root.addView(group);
        group.addView(parkedButton("그룹 만들기",()->{if(relay!=null)relay.createGroup();}));
        code=edit("","초대 코드");group.addView(code,new LinearLayout.LayoutParams(240,-2));parkedControls.add(code);
        group.addView(parkedButton("그룹 참여",()->{if(relay!=null)relay.joinGroup(code.getText().toString());}));
        copyInvite=parkedButton("초대 코드 복사",this::copyInviteCode);parkedControls.remove(copyInvite);root.addView(copyInvite);
        group.addView(parkedButton("공유 시작",this::confirmSharing));
        group.addView(button("공유 중지",()->{if(service!=null)service.stopSharing();}));
        group.addView(button("그룹 나가기",()->{if(service!=null)service.leaveGroup();}));
        vehicles=text("서버에서 받은 차량 정보가 없습니다.",17);root.addView(vehicles);
        LinearLayout actions=row();root.addView(actions);
        for(MessageKind kind:MessageKind.values())actions.addView(parkedButton(kind.label,()->{if(relay!=null)relay.send(kind);}));
        messages=column();root.addView(messages);
        root.addView(button("내 차량 화면으로",this::finish));setContentView(scroll);
        parkingSource=new VehicleParkingSource(this,parking,this::render);
    }
    private void copyInviteCode(){
        if(relay==null)return;
        Snapshot snapshot=relay.snapshot();
        if(snapshot.groupId.isEmpty() || snapshot.inviteCode.isEmpty()
            || snapshot.connection!=Connection.CONNECTED)return;
        android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
        if(clipboard==null)return;
        android.content.ClipData clip=android.content.ClipData.newPlainText("동행길 초대 코드",snapshot.inviteCode);
        if(android.os.Build.VERSION.SDK_INT>=33){
            android.os.PersistableBundle extras=new android.os.PersistableBundle();
            extras.putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        android.widget.Toast.makeText(this,"초대 코드를 복사했습니다. 동행할 상대에게 직접 전달하세요.",android.widget.Toast.LENGTH_LONG).show();
    }
    private void connect(){
        if(service==null){updateText(status,"공유 서비스 연결을 기다려 주세요.");return;}
        try{service.connect(endpoint.getText().toString(),name.getText().toString());}
        catch(IllegalArgumentException error){updateText(status,error.getMessage());}
    }
    private void confirmSharing(){
        if(service==null || relay==null)return;
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},23);
            updateText(status,"지속 알림 권한 허용 후 공유 시작을 다시 눌러 주세요.");return;
        }
        consent=new AlertDialog.Builder(this).setTitle("실제 위치 공유 시작")
            .setMessage("현재 위치 좌표·주소, 내비 목적지·남은 시간·거리를 입력한 서버와 그룹 구성원에게 보냅니다. 내비 등 다른 앱으로 이동해도 공유 중지·그룹 탈퇴·앱 작업 종료 전까지 전송합니다. 지속 알림에서 언제든 중지할 수 있습니다. 서버는 메모리에 임시 보관합니다.\n서버: "+endpoint.getText())
            .setNegativeButton("취소",null).setPositiveButton("위치 공유 시작",(dialog,which)->{
                if(parking.allowed(SystemClock.elapsedRealtime()) && service!=null)service.requestStart(this,parking);
            }).create();consent.show();
    }
    @Override protected void onStart(){
        super.onStart();parkingSource.start();
        binding=bindService(new Intent(this,SharingService.class),connection,Context.BIND_AUTO_CREATE);
        handler.post(ticker);
    }
    @Override protected void onStop(){
        handler.removeCallbacksAndMessages(null);parkingSource.stop();
        if(consent!=null)consent.dismiss();
        if(service!=null)service.detach(observer);
        if(binding){unbindService(connection);binding=false;}
        service=null;relay=null;super.onStop();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==22){parkingSource.stop();parkingSource.start();}
        if(request==23)updateText(status,"알림 권한을 확인했습니다. 공유 시작을 다시 눌러 동의해 주세요.");
    }
    private void render(){
        if(safety==null)return;
        relay=service==null?null:service.transport();
        boolean parked=parking.allowed(SystemClock.elapsedRealtime());
        updateText(safety,(parked?"정차 확인 · 설정·메시지 가능":"설정·메시지 잠김 · ")+parking.description(SystemClock.elapsedRealtime()));
        for(View control:parkedControls)control.setEnabled(parked);
        if(!parked && consent!=null && consent.isShowing())consent.dismiss();
        if(!parked){endpoint.clearFocus();name.clearFocus();code.clearFocus();android.view.inputmethod.InputMethodManager input=getSystemService(android.view.inputmethod.InputMethodManager.class);if(input!=null)input.hideSoftInputFromWindow(code.getWindowToken(),0);}
        boolean sharingActive=service!=null && service.active();
        endpoint.setEnabled(parked && !sharingActive);name.setEnabled(parked && !sharingActive);
        if(relay==null){
            copyInvite.setEnabled(false);
            updateText(status,service==null?"공유 서비스 연결 대기":"서버 연결 전 · 위치 공유 중지");
            updateText(groupHelp,"1. 두 차량 모두 같은 서버 주소로 연결하세요. 로컬 시험은 PC에서 중계 서버를 먼저 실행해야 합니다.");
            updateText(vehicles,"공유된 차량 정보가 없습니다.");
            parkedControls.removeIf(view -> view.getParent()==messages);messages.removeAllViews();renderedMessages="initial";
            return;
        }
        Snapshot snapshot=relay.snapshot(); if(snapshot.demo)throw new IllegalStateException("Real screen cannot show mock data");
        updateText(status,(snapshot.groupId.isEmpty()?"그룹 미참여":"그룹 연결 · 초대 코드 "+snapshot.inviteCode)
            +" · "+(snapshot.connection==Connection.CONNECTED?"서버 연결됨":"연결 끊김/요청 확인 필요")
            +" · "+(snapshot.sharing?"내 위치 공유 중":"내 위치 공유 중지")+"\n"+snapshot.notice+(service.status().isEmpty()?"":"\n"+service.status())+"\n"+relay.roster());
        boolean inGroup=!snapshot.groupId.isEmpty();
        copyInvite.setEnabled(parked && inGroup && !snapshot.inviteCode.isEmpty()
            && snapshot.connection==Connection.CONNECTED);
        updateText(groupHelp,snapshot.connection!=Connection.CONNECTED
            ?"서버 연결 상태를 확인하세요. 두 차량이 같은 서버에 연결되어야 참여할 수 있습니다."
            :!inGroup
                ?"2. 처음 시작하는 차량은 ‘그룹 만들기’를 누르세요. 참여하는 차량은 상대에게 받은 초대 코드를 입력하고 ‘그룹 참여’를 누르세요."
                :"3. 초대 코드를 복사해 상대에게 직접 전달하세요. 상대는 같은 서버에서 코드를 입력해 참여합니다. 각 차량이 동의 후 ‘공유 시작’을 눌러야 위치가 전송됩니다. 코드는 생성 후 30분간 유효합니다.");
        long now=SystemClock.elapsedRealtime();Vehicle mine=null;
        for(Vehicle vehicle:snapshot.vehicles)if(vehicle.id.equals(relay.userId()))mine=vehicle;
        StringBuilder rows=new StringBuilder();
        for(Vehicle vehicle:snapshot.vehicles){
            if(vehicle.stale(now) || snapshot.connection!=Connection.CONNECTED){
                rows.append(vehicle.name).append(vehicle.id.equals(relay.userId())?" (나)":" (동행)")
                    .append(" · 최신 위치·목적지·ETA 확인 불가 · 갱신 지연\n\n");
                continue;
            }
            rows.append(vehicle.name).append(vehicle.id.equals(relay.userId())?" (나)":" (동행)").append(" · ")
                .append(vehicle.address.isEmpty()?"주소 확인 중":vehicle.address).append('\n');
            if(BuildConfig.DEBUG)rows.append(String.format(Locale.ROOT,"개발용 좌표 %.6f, %.6f\n",vehicle.latitude,vehicle.longitude));
            rows.append(vehicle.destinationName.isEmpty()?"목적지 없음/확인 중":vehicle.destinationName).append(" · ").append(vehicle.destinationAddress).append('\n');
            if(vehicle.remainingSeconds<0)rows.append("ETA 확인 중 · 내비/위치 조건 확인 필요");
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
