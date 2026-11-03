package io.demo.companion;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReadReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  context.getSystemService(NotificationManager.class).cancel(102);
 }
}
