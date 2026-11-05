package io.demo.companion;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static io.demo.companion.SharingModels.*;

/** Actual HTTP relay adapter. Memory-only tokens and latest state; no offline location queue. */
public final class HttpSharingTransport implements SharingTransport {
    private final String endpoint, name;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Listener listener;
    private volatile String bearer = "", userId = "", shareEpoch = "";
    private volatile boolean closed, desiredSharing;
    private volatile long shareRevision;
    private Vehicle latest;
    private long sequence, sentAt = -1;
    private String sentKey = "", roster = "";
    private boolean busy;
    private boolean sessionInvalid;
    private String actionNotice = "";
    private long pollIntervalMs=1000;
    public void setBackground(boolean background){pollIntervalMs=background?5000:1000;}
    private Snapshot current = new Snapshot("","","서버 연결 전 · 실제 위치 전송 중지",false,false,Connection.DISCONNECTED,new ArrayList<>(),new ArrayList<>());
    public HttpSharingTransport(String endpoint, String name) {
        this.endpoint = RelayEndpoint.validate(endpoint,BuildConfig.DEBUG);
        if (name.trim().isEmpty() || name.length()>24) throw new IllegalArgumentException("차량 이름은 1~24자로 입력하세요.");
        this.name = name.trim(); handler.post(ticker);
    }
    public String userId() { return userId; }
    public String roster() { return roster; }
    @Override public void setListener(Listener value) { listener = value; emit(); }
    @Override public Snapshot snapshot() { return current; }
    private void emit() { if (!closed && listener != null) listener.onChanged(current); }
    private void notice(String value, Connection connection) {
        current = new Snapshot(current.groupId,current.inviteCode,value,desiredSharing,false,connection,current.vehicles,current.messages); emit();
    }
    private static JSONObject object(Object... values) {
        JSONObject json = new JSONObject();
        try { for(int i=0;i<values.length;i+=2)json.put((String)values[i],values[i+1]); return json; }
        catch(Exception error){throw new IllegalArgumentException(error);}
    }
    public void connect() {
        if(closed)return;
        worker.execute(() -> {
            if(closed)return;
            try {
                if(bearer.isEmpty()) {
                    JSONObject reply=request("POST","/v1/sessions",object("name",name));
                    bearer=reply.getString("token"); userId=reply.getString("userId");
                }
                JSONObject reply=request("GET","/v1/group",null);
                handler.post(()->{if(!closed)apply(reply,"서버 연결됨 · 공유 시작 전에는 위치를 보내지 않습니다.");});
            } catch(Exception error){failed(error);}
        });
    }
    private void command(String path, JSONObject body) {
        if(closed)return;
        worker.execute(()->{
            if(closed)return;
            try { JSONObject reply=request("POST",path,body); handler.post(()->{if(!closed){actionNotice="";apply(reply,"요청 반영됨 · 실제 서버 연결");}}); }
            catch(Exception error){failed(error);}
        });
    }
    @Override public void createGroup(){command("/v1/groups",object());}
    @Override public void joinGroup(String code){command("/v1/groups/join",object("code",code.trim().toUpperCase(java.util.Locale.ROOT)));}
    @Override public void publish(Vehicle own){latest=own;}
    @Override public void startSharing(){
        if(closed)return;
        final long revision=++shareRevision; latest=null;
        worker.execute(()->{
            if(closed || revision!=shareRevision)return;
            try {
                JSONObject reply=request("POST","/v1/sharing",object("enabled",true));
                if(closed || revision!=shareRevision)return;
                shareEpoch=reply.getString("shareEpoch"); sequence=0; sentAt=-1; sentKey=""; desiredSharing=true;
                handler.post(()->{if(!closed && revision==shareRevision)apply(reply,"실제 위치·목적지·ETA 공유 시작 · 다른 앱에서도 유지되며 알림에서 중지할 수 있습니다.");});
            }catch(Exception error){failed(error);}
        });
    }
    @Override public void stopSharing(){desiredSharing=false; shareEpoch=""; shareRevision++; latest=null; notice("공유 중지 요청 · 새 위치 전송 중단",current.connection); command("/v1/sharing",object("enabled",false));}
    @Override public void leaveGroup(){desiredSharing=false; shareEpoch=""; shareRevision++; latest=null; command("/v1/groups/leave",object());}
    @Override public void send(MessageKind kind){
        if(!desiredSharing){notice("그룹 참여 후 공유를 시작하세요.",current.connection);return;}
        command("/v1/messages",object("id",UUID.randomUUID().toString(),"kind",kind.name()));
    }
    @Override public void acknowledge(String id){command("/v1/messages/"+id+"/ack",object());}
    private final Runnable ticker = new Runnable(){
        @Override public void run(){ if(closed)return; poll(); handler.postDelayed(this,pollIntervalMs); }
    };
    private void poll(){
        if(closed || sessionInvalid || bearer.isEmpty() || busy)return;
        busy=true; final Vehicle own=latest; final long revision=shareRevision, capturedAt=SystemClock.elapsedRealtime();
        worker.execute(()->{
            if(closed)return;
            try {
                JSONObject reply;
                String key=own==null?"unavailable":own.destinationId+":"+(own.remainingSeconds>=0);
                boolean upload=desiredSharing && !shareEpoch.isEmpty() && revision==shareRevision
                        && (sentAt<0 || capturedAt-sentAt>=5000 || !sentKey.equals(key));
                if(upload){
                    JSONObject body=object("epoch",shareEpoch,"sequence",++sequence,"available",own!=null);
                    if(own!=null){
                        long age=SystemClock.elapsedRealtime()-own.receivedAtElapsed;
                        if(age<0 || age>15000)body.put("available",false);
                        else body.put("state",object("latitude",own.latitude,"longitude",own.longitude,"address",own.address,
                            "destinationId",own.destinationId,"destinationName",own.destinationName,"destinationAddress",own.destinationAddress,
                            "remainingSeconds",own.remainingSeconds,"remainingMeters",own.remainingMeters,"sourceAgeMs",age));
                    }
                    if(revision!=shareRevision || !desiredSharing)return;
                    reply=request("PUT","/v1/state",body); sentAt=capturedAt; sentKey=key;
                }else reply=request("GET","/v1/group",null);
                JSONArray messages=reply.getJSONArray("messages");
                for(int i=0;i<messages.length();i++){
                    JSONObject message=messages.getJSONObject(i);
                    if(!message.getString("senderId").equals(userId) && message.getString("delivery").equals("PENDING"))
                        reply=request("POST","/v1/messages/"+message.getString("id")+"/received",object());
                }
                JSONObject result=reply;
                handler.post(()->{if(!closed)apply(result,"실제 서버 동기화 · 화면 수신 약 1초 / 다른 앱 사용 중 약 5초");});
            }catch(Exception error){failed(error);}
            finally { handler.post(()->busy=false); }
        });
    }
    private void apply(JSONObject reply,String notice){
        if(sessionInvalid)return;
        try {
            String group=reply.getString("groupId");
            if(group.isEmpty()){desiredSharing=false; shareEpoch="";}
            long now=SystemClock.elapsedRealtime(), wall=System.currentTimeMillis(), serverNow=reply.getLong("serverNow");
            List<Vehicle> vehicles=new ArrayList<>(); List<Message> messages=new ArrayList<>(); StringBuilder names=new StringBuilder();
            JSONArray members=reply.getJSONArray("members");
            for(int i=0;i<members.length();i++){
                JSONObject member=members.getJSONObject(i);
                names.append(member.getString("name")).append(member.getString("id").equals(userId)?" (나)":"")
                    .append(member.getBoolean("online")?"":" · 연결 지연").append(member.getBoolean("sharing")?" · 공유 중":" · 공유 중지").append('\n');
                JSONObject state=member.optJSONObject("state"); if(state==null)continue;
                long delay=reply.optLong("_roundTripMs",0); long age=Math.max(0,state.getLong("sourceAgeMs"))+delay; if(!member.getBoolean("online"))age=Math.max(age,16000);
                vehicles.add(new Vehicle(member.getString("id"),member.getString("name"),state.getDouble("latitude"),state.getDouble("longitude"),
                    state.getString("address"),state.getString("destinationId"),state.getString("destinationName"),state.getString("destinationAddress"),
                    state.getInt("remainingSeconds"),state.getInt("remainingMeters"),state.getLong("sequence"),
                    wall-Math.max(0,serverNow-state.getLong("generatedAt"))-delay,now-age));
            }
            JSONArray source=reply.getJSONArray("messages");
            for(int i=0;i<source.length();i++){
                JSONObject message=source.getJSONObject(i);
                messages.add(new Message(message.getString("id"),message.getString("senderId"),message.getString("sender"),
                    MessageKind.valueOf(message.getString("kind")),message.getLong("sentAt"),Delivery.valueOf(message.getString("delivery"))));
            }
            roster=names.toString();
            current=new Snapshot(group,reply.getString("inviteCode"),actionNotice.isEmpty()?notice:actionNotice,desiredSharing,false,Connection.CONNECTED,vehicles,messages); emit();
        }catch(Exception error){notice("서버 데이터 형식 확인 필요",Connection.DISCONNECTED);}
    }
    private void failed(Exception error){
        final String message=error instanceof ApiException ? ((ApiException)error).code : "NETWORK_ERROR";
        handler.post(()->{
            if(closed || sessionInvalid)return;
            if("UNAUTHORIZED".equals(message)){
                sessionInvalid=true;desiredSharing=false;shareEpoch="";shareRevision++;latest=null;roster="";
                current=new Snapshot("","","서버 재시작 또는 세션 만료 · 서버 연결을 다시 누르고 새 그룹 생성/참여 후 공유에 다시 동의하세요.",false,false,Connection.DISCONNECTED,new ArrayList<>(),new ArrayList<>());
                emit();
            }else if("INVALID_OR_EXPIRED_CODE".equals(message)){
                actionNotice="초대 코드가 틀렸거나 만료됐습니다. 상대에게 새 그룹의 코드를 받아 다시 입력하세요.";notice(actionNotice,current.connection);
            }else if("LEAVE_FIRST".equals(message)){
                actionNotice="이미 그룹에 참여 중입니다. 다른 그룹에 참여하려면 먼저 그룹에서 나가세요.";notice(actionNotice,current.connection);
            }else if("GROUP_FULL".equals(message)){
                actionNotice="참여 차량이 가득 찬 그룹입니다. 다른 그룹 코드를 확인하세요.";notice(actionNotice,current.connection);
            }else{
                current=new Snapshot(current.groupId,current.inviteCode,"서버에 연결할 수 없습니다. 최신 위치·ETA를 숨깁니다. 서버 상태를 확인하세요. 메시지는 자동 재전송하지 않습니다.",desiredSharing,false,Connection.DISCONNECTED,new ArrayList<>(),new ArrayList<>());
                emit();
            }
        });
    }
    private JSONObject request(String method,String path,JSONObject body)throws Exception{
        long requestAt=SystemClock.elapsedRealtime();
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint+path).openConnection();
        try{
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            connection.setUseCaches(false); connection.setRequestMethod(method);
            if(!bearer.isEmpty())connection.setRequestProperty("Authorization","Bearer "+bearer);
            if(body!=null){ byte[] data=body.toString().getBytes(StandardCharsets.UTF_8); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json"); connection.setFixedLengthStreamingMode(data.length);
                try(java.io.OutputStream output=connection.getOutputStream()){output.write(data);} }
            int status=connection.getResponseCode(); InputStream stream=status>=200 && status<300?connection.getInputStream():connection.getErrorStream();
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream input=stream){byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>131072)throw new java.io.IOException("Response too large"); output.write(buffer,0,count);} }
            JSONObject result=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
            if(status<200 || status>=300)throw new ApiException(result.optString("error","HTTP_"+status)); result.put("_roundTripMs",Math.max(0,SystemClock.elapsedRealtime()-requestAt)); return result;
        }finally{connection.disconnect();}
    }
    private static final class ApiException extends Exception { final String code; ApiException(String code){this.code=code;} }
    @Override public void close(){
        if(closed)return;
        closed=true; desiredSharing=false; shareRevision++; latest=null; listener=null; handler.removeCallbacksAndMessages(null);
        worker.execute(()->{try{if(!bearer.isEmpty())request("POST","/v1/groups/leave",object());}catch(Exception ignored){}finally{bearer="";shareEpoch="";}});
        worker.shutdown();
    }
}
