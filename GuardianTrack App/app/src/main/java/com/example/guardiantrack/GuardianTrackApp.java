package com.example.guardiantrack;

import android.app.Application;
import android.util.Log;

public class GuardianTrackApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize logging here (example with Flogger)
        // Flogger.initialize();  // Hypothetical initialization
        Log.d("GuardianTrackApp", "Logging framework initialized.");
    }
}