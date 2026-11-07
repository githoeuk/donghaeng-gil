package io.demo.companion;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import static io.demo.companion.SharingModels.*;

/** Owns actual SDK collection and transport independently of any Activity. Never restarts consent. */
public final class SharingService extends Service {
    public static final String STOP = "io.demo.companion.STOP_SHARING";
    private static final String START = "io.demo.companion.START_SHARING", CHANNEL = "donghaeng_sharing";
    private static final String MESSAGE_CHANNEL = "donghaeng_messages";
    private static final String DISMISS_MESSAGE = "io.demo.companion.DISMISS_MESSAGE";
    private static final int NOTIFICATION = 301, MESSAGE_NOTIFICATION = 303;
    private static final long MESSAGE_VISIBLE_MS = 3000;
    private static final long MESSAGE_ALERT_INTERVAL_MS = 4000;
    private final ParkingState messageParking = new ParkingState();
    private final Set<String> receivedMessages = new LinkedHashSet<>();
    private VehicleParkingSource messageVehicleSource;
    private long lastMessageAlert = -MESSAGE_VISIBLE_MS;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharingLifetime lifetime = new SharingLifetime();
    private final JourneyState journey = new JourneyState();
    private final Set<Runnable> observers = new HashSet<>();
    private final LocalBinder binder = new LocalBinder();
    private PleosJourneySource source;
    private HttpSharingTransport transport;
    private ParkingState armedParking;
    private boolean foreground, ending;
    private long disconnectedAt = -1;
    private String endpoint = "", name = "", status = "";
    public final class LocalBinder extends Binder { public SharingService service() { return SharingService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onCreate() {
        super.onCreate();
        source = new PleosJourneySource(this,journey,()->{});
        NotificationChannel channel = new NotificationChannel(CHANNEL,"동행길 위치 공유",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("다른 앱 사용 중에도 진행되는 위치·도착시간 공유와 중지");channel.setSound(null,null);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(channel);
        NotificationChannel messages = new NotificationChannel(MESSAGE_CHANNEL, "동행길 상황 메시지", NotificationManager.IMPORTANCE_HIGH);
        messages.setSound(null, null);
        messages.enableVibration(false);
        notifications.createNotificationChannel(messages);
        messageVehicleSource = new VehicleParkingSource(this, messageParking, () -> {
            if (!messageParking.messagesAllowed(SystemClock.elapsedRealtime())) dismissMessageAlert();
        });
    }
    public HttpSharingTransport transport() { return transport; }
    public String endpoint() { return endpoint; }
    public String vehicleName() { return name; }
    public String status() { return status; }
    public boolean active() { return lifetime.retaining(); }
    public void attach(Runnable observer) {
        observers.add(observer); if(transport!=null)transport.setBackground(false); observer.run();
    }
    public void detach(Runnable observer) {
        observers.remove(observer); if(transport!=null)transport.setBackground(observers.isEmpty());
    }
    private void emit() { for(Runnable observer:new HashSet<>(observers))observer.run(); }
    public void connect(String address,String vehicleName) {
        if(active()){status="공유 중에는 서버를 바꿀 수 없습니다. 먼저 공유를 중지하세요.";emit();return;}
        HttpSharingTransport candidate = new HttpSharingTransport(address,vehicleName);
        if(transport!=null)transport.close();transport=candidate;endpoint=address;name=vehicleName;status="";
        receivedMessages.clear();
        transport.setListener(snapshot->{
            if(ending)return;
            if(lifetime.phase()==SharingLifetime.Phase.STARTING){
                if(snapshot.sharing)lifetime.confirmed();
                else if(snapshot.connection==Connection.DISCONNECTED){endSharing("공유 시작 실패 · 다시 동의 후 시작하세요.",false);return;}
            }else if(lifetime.phase()==SharingLifetime.Phase.ACTIVE && !snapshot.sharing){
                endSharing("그룹 또는 공유 세션이 종료됐습니다.",false);return;
            }
            if(snapshot.connection==Connection.CONNECTED)disconnectedAt=-1;
            else if(active() && disconnectedAt<0)disconnectedAt=SystemClock.elapsedRealtime();
            alertIncomingMessage(snapshot);
            emit();
        });transport.connect();emit();
    }
    private boolean permitted() {
        boolean location=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        LocationManager manager=getSystemService(LocationManager.class);
        NotificationManager notifications=getSystemService(NotificationManager.class);
        NotificationChannel channel=notifications.getNotificationChannel(CHANNEL);
        return location && manager!=null && manager.isLocationEnabled()
                && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
                && notifications.areNotificationsEnabled() && channel!=null && channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public void requestStart(Activity activity,ParkingState parking) {
        long now=SystemClock.elapsedRealtime();
        boolean grouped=transport!=null && !transport.snapshot().groupId.isEmpty() && transport.snapshot().connection==Connection.CONNECTED;
        long token=lifetime.arm(!observers.isEmpty() && !activity.isFinishing(),parking.allowed(now),permitted(),grouped,now);
        if(token<0){status="정차·그룹 연결·위치 및 알림 권한을 확인하세요. 이미 공유 중이면 다시 시작하지 않습니다.";emit();return;}
        armedParking=parking;status="공유 서비스 시작 중";
        try { activity.startForegroundService(new Intent(activity,SharingService.class).setAction(START).putExtra("generation",token)); }
        catch(RuntimeException error){lifetime.stop();armedParking=null;status="공유 서비스를 시작할 수 없습니다. 화면에서 다시 시작하세요.";emit();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null){endSharing("자동 공유 재시작은 지원하지 않습니다.",true);return START_NOT_STICKY;}
        if (DISMISS_MESSAGE.equals(intent.getAction())) {
            dismissMessageAlert();
            if (!active()) stopSelf();
            return START_NOT_STICKY;
        }
        if(STOP.equals(intent.getAction())){endSharing("공유 중지됨",false);return START_NOT_STICKY;}
        if(!START.equals(intent.getAction())){if(!active())stopSelf();return START_NOT_STICKY;}
        boolean eligible=armedParking!=null && armedParking.allowed(SystemClock.elapsedRealtime());
        if(!lifetime.begin(intent.getLongExtra("generation",-1),permitted(),eligible,SystemClock.elapsedRealtime())){
            if(!active())endSharing("공유 시작 조건이 바뀌었습니다. 다시 확인해 주세요.",false);
            return START_NOT_STICKY;
        }
        armedParking=null;
        try {
            Notification notification=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION,notification);
            foreground=true;disconnectedAt=-1;status="내비 등 다른 앱에서도 공유 유지 · 알림에서 중지 가능";
            messageVehicleSource.start();source.start();handler.post(ticker);transport.startSharing();emit();
        }catch(RuntimeException error){endSharing("위치 공유 서비스를 시작할 수 없습니다.",false);}
        return START_NOT_STICKY;
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,301,new Intent(this,GroupSharingActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,302,new Intent(this,SharingService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("동행길 · 위치 공유 중").setContentText("내비 사용 중에도 위치·도착시간 공유 · 아래 공유 중지로 종료")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"공유 중지",stop).build());
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    private void alertIncomingMessage(Snapshot snapshot) {
        if (snapshot.connection != Connection.CONNECTED) return;
        Message newest = null;
        for (Message message : snapshot.messages) {
            if (message.senderId.equals(transport.userId()) || message.delivery != Delivery.DELIVERED
                    || !receivedMessages.add(message.id)) continue;
            if (receivedMessages.size() > 100) receivedMessages.remove(receivedMessages.iterator().next());
            newest = message;
        }
        long now = SystemClock.elapsedRealtime();
        if (newest == null || !foreground || !snapshot.sharing || !messageParking.messagesAllowed(now)
                || now - lastMessageAlert < MESSAGE_ALERT_INTERVAL_MS) return;

        Person sender = new Person.Builder().setName(newest.sender).build();
        Person self = new Person.Builder().setName(name).build();
        PendingIntent dismiss = PendingIntent.getService(this, MESSAGE_NOTIFICATION,
                new Intent(this, SharingService.class).setAction(DISMISS_MESSAGE),
                PendingIntent.FLAG_IMMUTABLE);
        android.os.Bundle readExtras = new android.os.Bundle();
        readExtras.putBoolean("android.support.action.showsUserInterface", false);
        Notification.Action read = new Notification.Action.Builder(null, "알림 닫기", dismiss)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .addExtras(readExtras).build();
        Notification notification = new Notification.Builder(this, MESSAGE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentTitle(newest.sender)
                .setContentText(newest.kind.label)
                .setStyle(new Notification.MessagingStyle(self).addMessage(newest.kind.label, newest.sentAtMillis, sender))
                .addAction(read)
                .setTimeoutAfter(MESSAGE_VISIBLE_MS)
                .build();
        lastMessageAlert = now;
        getSystemService(NotificationManager.class).notify(MESSAGE_NOTIFICATION, notification);
    }
    private void dismissMessageAlert() {
        getSystemService(NotificationManager.class).cancel(MESSAGE_NOTIFICATION);
    }

    private final Runnable ticker=new Runnable(){public void run(){
        if(!active())return;
        long now=SystemClock.elapsedRealtime();
        if(!permitted()){endSharing("위치 또는 알림 권한·설정이 꺼져 공유를 중지했습니다.",false);return;}
        if(lifetime.expired(now) || (disconnectedAt>=0 && now-disconnectedAt>60000)){
            endSharing("공유 시간 또는 연결 대기 한도를 넘어 중지했습니다. 화면에서 다시 시작하세요.",false);return;
        }
        if(transport!=null)transport.publish(JourneyPublisher.current(journey,now,System.currentTimeMillis()));
        handler.postDelayed(this,1000);
    }};
    public void stopSharing() { endSharing("공유 중지됨",false); }
    public void leaveGroup() { endSharing("그룹에서 나갔습니다.",true); }
    private void endSharing(String reason,boolean leave) {
        if(ending)return;ending=true;
        lifetime.stop();armedParking=null;handler.removeCallbacksAndMessages(null);source.stop();messageVehicleSource.stop();dismissMessageAlert();disconnectedAt=-1;
        if(transport!=null){transport.publish(null);if(leave)transport.leaveGroup();else transport.stopSharing();}
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
        status=reason;stopSelf();
        if(observers.isEmpty() && transport!=null){transport.close();transport=null;}
        ending=false;emit();
    }
    @Override public boolean onUnbind(Intent intent) {
        if(!active()){if(transport!=null){transport.close();transport=null;}stopSelf();}
        return false;
    }
    @Override public void onTaskRemoved(Intent rootIntent) { endSharing("앱 작업 종료",true);super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() {
        lifetime.stop();handler.removeCallbacksAndMessages(null);source.stop();
        messageVehicleSource.stop();dismissMessageAlert();
        if(transport!=null){transport.close();transport=null;}
        if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);
        observers.clear();super.onDestroy();
    }
}
