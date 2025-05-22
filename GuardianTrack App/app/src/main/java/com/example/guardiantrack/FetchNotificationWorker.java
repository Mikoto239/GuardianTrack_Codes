package com.example.guardiantrack;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class FetchNotificationWorker extends Worker {

    public FetchNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            Intent fetchIntent = new Intent(context, MyBackgroundService.class);
            fetchIntent.setAction("FETCHNOTIFICATION");

            // Start the service as a normal service, not a foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Only use startForegroundService() if necessary, otherwise use startService()
                context.startService(fetchIntent); // This ensures it's not a foreground service
            } else {
                context.startService(fetchIntent); // This will work for lower versions too
            }

            return Result.success();
        } catch (Exception e) {
            Log.e("FetchNotificationWorker", "Failed to start service", e);
            return Result.failure();
        }
    }
}
