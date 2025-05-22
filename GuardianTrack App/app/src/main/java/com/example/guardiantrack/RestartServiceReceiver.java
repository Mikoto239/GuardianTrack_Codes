package com.example.guardiantrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class RestartServiceReceiver extends BroadcastReceiver {
    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";

    @Override
    public void onReceive(Context context, Intent intent) {

        // Retrieve the token from SharedPreferences
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        // Create an Intent for the background service
        Intent backgroundIntent = new Intent(context, MyBackgroundService.class);

        // Check if token is not null and start the service
        if (token != null) {
            // Use startService() for all versions of Android
            context.startService(backgroundIntent);
        } else {
            // In case token is null, start the service anyway
            context.startService(backgroundIntent);
        }
    }
}
