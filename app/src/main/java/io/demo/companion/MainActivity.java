package io.demo.companion;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.*;
import android.widget.*;
import android.view.*;

public class MainActivity extends Activity {
 private TextView status; private LinearLayout controls; private final Handler handler = new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(24,24,24,24); root.setBackgroundColor(Color.rgb(18,27,42));
  TextView title=new TextView(this); title.setText("동행길 · DEMO\nMinsu · arrival in 12 min\nDestination: Demo campsite"); title.setTextSize(25); title.setTextColor(Color.WHITE); root.addView(title);
  status=new TextView(this); status.setTextColor(Color.CYAN); status.setTextSize(16); root.addView(status);
  controls=new LinearLayout(this); controls.setOrientation(1); root.addView(controls);
  button("Enter PiP now",()->pip());
  button("PiP after 10 sec (open navigation)",()->{show("PiP scheduled +10s"); handler.postDelayed(()->pip(),10000);});
  button("ETA status notification after 10 sec",()->schedule(false));
  button("Group message notification after 10 sec",()->schedule(true));
  button("Request notification permission",()->{if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},10);});
  button("Clear test notifications",()->((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll());
  button("Member shares ETA after 10 sec (demo)",()->scheduleShared("I will arrive in 12 minutes."));
  button("Member proposes meeting-point change after 10 sec (demo)",()->scheduleShared("Please meet at Demo campsite east parking instead."));
  setContentView(root);
  show("PiP feature="+getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)+"; notifications="+((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled());
 }
 private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());controls.addView(b);}
 private void show(String s){status.setText(s);Log.i("CompanionTest",s);}
 private void pip(){try{boolean accepted=enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(2,1)).build());show("enterPiP returned="+accepted);}catch(Exception e){show("PiP failed: "+e);}}
 private void schedule(boolean message){show("Notification scheduled +10s: "+(message?"group message":"ETA status"));handler.postDelayed(()->notifyDemo(message),10000);}
 private void scheduleShared(String text){show("Member-initiated share scheduled +10s: "+text);handler.postDelayed(()->notifyDemo(true,text),10000);}
 private void notifyDemo(boolean message){
  notifyDemo(message,"Let's meet at the east parking entrance.");
 }
 private void notifyDemo(boolean message,String text){
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  String channel=message?"group_messages":"eta_status";
  nm.createNotificationChannel(new NotificationChannel(channel,message?"Group messages":"ETA status",NotificationManager.IMPORTANCE_HIGH));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification.Builder b=new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(open).setAutoCancel(true);
  if(message){Person self=new Person.Builder().setName("You").build();Person sender=new Person.Builder().setName("Minsu (demo)").build();b.setCategory(Notification.CATEGORY_MESSAGE).setStyle(new Notification.MessagingStyle(self).setConversationTitle("Demo trip group").addMessage(text,System.currentTimeMillis(),sender));
   PendingIntent read=PendingIntent.getBroadcast(this,0,new Intent(this,ReadReceiver.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   Bundle readExtras=new Bundle();readExtras.putBoolean("android.support.action.showsUserInterface",false);
   b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_view,"Mark as read",read).setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).addExtras(readExtras).build());
  }
  else b.setCategory(Notification.CATEGORY_STATUS).setContentTitle("동행길 ETA · DEMO").setContentText("Minsu arrives in 12 min");
  try{nm.notify(message?102:101,b.build());show("Posted "+channel+"; enabled="+nm.areNotificationsEnabled());}catch(Exception e){show("Notification error: "+e);}
 }
 @Override public void onPictureInPictureModeChanged(boolean active,Configuration config){super.onPictureInPictureModeChanged(active,config);controls.setVisibility(active?View.GONE:View.VISIBLE);show("onPiPChanged="+active);}
}
