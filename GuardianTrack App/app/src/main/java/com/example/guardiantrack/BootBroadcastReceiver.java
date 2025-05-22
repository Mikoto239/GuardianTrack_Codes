package com.example.guardiantrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-launch the service after device reboot
            Intent serviceIntent = new Intent(context, MyBackgroundService.class);
            context.startService(serviceIntent);
        }
    }
}
