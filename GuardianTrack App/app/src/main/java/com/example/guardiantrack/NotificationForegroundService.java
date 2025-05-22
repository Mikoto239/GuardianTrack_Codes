//package com.example.guardiantrack;
//
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.os.Build;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.PowerManager;
//import android.provider.Settings;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.core.app.NotificationCompat;
//import androidx.work.Constraints;
//import androidx.work.ExistingPeriodicWorkPolicy;
//import androidx.work.NetworkType;
//import androidx.work.PeriodicWorkRequest;
//import androidx.work.WorkManager;
//
//import java.util.concurrent.TimeUnit;
//
//public class NotificationForegroundService extends Service {
//    private PowerManager.WakeLock wakeLock;
//    private static final String CHANNEL_ID = "ForegroundServiceChannel";
//    private static final String PREFS_NAME = "MyPrefs";
//    private static final String TOKEN_KEY = "TOKEN";
//    private String token = null;
//    private Handler handler = new Handler();
//    private Runnable runnable;
//    private static final long CHECK_INTERVAL = 5 * 60 * 1000; // Check every 5 minutes
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//
//        // Retrieve token
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//        token = sharedPreferences.getString(TOKEN_KEY, null);
//
//        if (token == null) {
//            stopSelf(); // Stop the service if the token is null
//            return;
//        }
//        Log.d("NotificationForegroundService", "onCreate called");
//
//        // Create notification channel and start service in the foreground
//        createNotificationChannel();
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setSmallIcon(R.drawable.guardiantracklogo)
//                .setContentTitle("GuardianTrack Service")
//                .setContentText("Service is running in the background")
//                .setPriority(NotificationCompat.PRIORITY_LOW)
//                .setOngoing(true)
//                .build();
//
//        startForeground(1, notification);
//
//        // Initialize the wake lock
//        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//        if (powerManager != null) {
//            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
//            wakeLock.acquire();
//        }
//
//        // Handle battery optimizations
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
//                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                startActivity(intent);
//            }
//        }
//
//        // Setup WorkManager for periodic tasks
//        setupWorkManager();
//
//        // Start the background service initially
//        startMyBackgroundService();
//
//        // Schedule the periodic token check
//        startTokenCheck();
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        // Check for token again in case it changes during service running
//        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//        token = sharedPreferences.getString(TOKEN_KEY, null);
//
//        // If no valid token, stop the service
//        if (token == null) {
//            stopSelf();
//            return START_NOT_STICKY;
//        }
//
//        // Start the background service
//        startMyBackgroundService();
//
//        return START_STICKY;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//
//        // Stop the periodic token check
//        handler.removeCallbacks(runnable);
//
//        // Release the wake lock if held
//        if (wakeLock != null && wakeLock.isHeld()) {
//            wakeLock.release();
//        }
//
//        // Restart the service if it is destroyed
//        Intent broadcastIntent = new Intent(this, RestartServiceReceiver.class);
//        sendBroadcast(broadcastIntent);
//    }
//
//    private void setupWorkManager() {
//        Log.e("WorkManager", "Setting up periodic work");
//
//        Constraints constraints = new Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)  // Adjust this constraint based on your needs
//                .build();
//
//        // Create a periodic work request for every 15 minutes
//        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
//                FetchNotificationWorker.class,
//                15, TimeUnit.MINUTES) // Set the repeat interval
//                .setConstraints(constraints)
//                .build();
//
//        // Enqueue the work request
//        WorkManager.getInstance(getApplicationContext())
//                .enqueueUniquePeriodicWork("FetchNotificationWork", ExistingPeriodicWorkPolicy.REPLACE, workRequest);
//    }
//
//    private void createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel serviceChannel = new NotificationChannel(
//                    CHANNEL_ID,
//                    "GuardianTrack",
//                    NotificationManager.IMPORTANCE_DEFAULT
//            );
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            manager.createNotificationChannel(serviceChannel);
//        }
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null; // This service is not meant to be bound
//    }
//
//    private void startMyBackgroundService() {
//        Intent fetchIntent = new Intent(getApplicationContext(), MyBackgroundService.class);
//        fetchIntent.setAction("FETCHNOTIFICATION");
//        getApplicationContext().startService(fetchIntent);
//    }
//
//    private void startTokenCheck() {
//        runnable = new Runnable() {
//            @Override
//            public void run() {
//                SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//                token = sharedPreferences.getString(TOKEN_KEY, null);
//
//                if (token != null) {
//                    startMyBackgroundService();
//                } else {
//                    stopSelf();
//                }
//
//                // Schedule the next check
//                handler.postDelayed(this, CHECK_INTERVAL);
//            }
//        };
//
//        // Start the first check immediately
//        handler.post(runnable);
//    }
//}
