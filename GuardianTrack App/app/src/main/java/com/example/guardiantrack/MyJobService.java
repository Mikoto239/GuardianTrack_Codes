package com.example.guardiantrack;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

public class MyJobService extends JobService {
    private Handler handler;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String TOKEN_KEY = "TOKEN";
    private String token = "";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d("MyJobService", "Job started");

        // Retrieve the token from SharedPreferences
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        // Only start the service if the token is not null
        if (token != null && !token.isEmpty()) {
            // Start MyBackgroundService as a normal service
            Intent serviceIntent = new Intent(getApplicationContext(), MyBackgroundService.class);
            startService(serviceIntent); // Start the service without using startForegroundService()
        } else {
            Log.d("MyJobService", "No token found, not starting the background service.");
        }

        // Simulate some background work using a Handler
        handler = new Handler();
        handler.postDelayed(() -> {
            jobFinished(params, true); // Indicate that the job is done
        }, 30000); // Simulate 30 seconds of background work

        return true; // Job is still running
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Clean up work when the job is stopped
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        return true; // Reschedule the job if needed
    }
}
