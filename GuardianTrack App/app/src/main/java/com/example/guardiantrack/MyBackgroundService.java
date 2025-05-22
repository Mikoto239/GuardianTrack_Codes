package com.example.guardiantrack;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.guardiantrack.HistoryFragment;
import com.example.guardiantrack.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyBackgroundService extends Service {

    private static final String NOTIFICATION_CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 123;

    private long lastFetchedTimestamp = 0;


    private RequestQueue requestQueue;

    private String name;
    private String email;
    private String uniqueId;

    Vibrator vibrator;
    JSONObject latestArduinoData = null;
    JSONObject latestTheftDetails = null;
    String latestArduinoDateString = "";
    String latestTheftDateString = "";
    String latestlevelarduino = "";
    String latestdescriptionarduino = "";
    String latestlevelttheft = "";
    String latestdescriptiontheft ="";
    String theftlevel ="";
    String theftdescription ="";
    String arduinolevel = "";
    String arduinodescription ="";

    private static MediaPlayer mediaPlayer;
    private  static MediaPlayer alertsound;
    private String lastArduinoNotificationDate = "";
    private String lastTheftNotificationDate = "";

    private static final long INTERVAL = 1000; // 10 seconds
    private Handler mHandler = new Handler();
    private boolean isRunning = false;
    private boolean previousAlarmStatus = false; // Initialize with a default value

    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";
    private PowerManager.WakeLock wakeLock;

    private static final String PREFS_NAMEs = "NotificationPrefs";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";
    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final long ALARMINTERVAL = 5000;
    private ScheduledExecutorService scheduler;
    private Handler handler;
    private Runnable fetchTask;
    private static final String ACTION_CLEAR_TOKEN = "ACTION_CLEAR_TOKEN";
    private BroadcastReceiver tokenClearReceiver;



    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        if (token == null) {
            stopSelf(); // Stop the service if the token is null
            return;
        }

        fetchSharedPreferencesData();
        requestQueue = Volley.newRequestQueue(this);
        handler = new Handler();

        // Initialize and register BroadcastReceiver
        tokenClearReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CLEAR_TOKEN.equals(intent.getAction())) {
                    token = "";
                    handleTokenClearing();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_CLEAR_TOKEN);
        registerReceiver(tokenClearReceiver, filter);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
        wakeLock.acquire();

        createNotificationChannel(); // Create the notification channel once

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::fetchnotificationData, 0, 1, TimeUnit.SECONDS);
        scheduleAlarmManager();
        setupWorkManager();
        fetchnotificationData();
        ignoreBatteryOptimization();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        if (token == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
//
        startForeground(NOTIFICATION_ID, createNotification());

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if ("STOP_ALARM_AND_VIBRATION".equals(action)) {
                stopAlarmAndVibration();
                fetchnotificationData();
            } else if ("FETCHNOTIFICATION".equals(action)) {
                fetchnotificationData();
            }
        }

        setupAlarmManager();
        return START_STICKY;
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("") // Leave the title empty
                .setContentText("") // Leave the text empty
                .setSmallIcon(R.drawable.vehiclesecured) // Use a subtle icon if possible
                .setPriority(NotificationCompat.PRIORITY_MIN) // Set priority to low
                .setOngoing(true); // Keep the notification ongoing

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }




    @Override
    public void onDestroy() {
        super.onDestroy();

        // Check if the token is null or empty
        if (token == null || token.isEmpty()) {
            stopForeground(true); // Remove the foreground status
            stopSelf(); // Stop the service
        }

        // Release MediaPlayer resources
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Release AlertSound resources
        if (alertsound != null) {
            alertsound.release();
            alertsound = null;
        }

        // Release WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        // Remove all callbacks from Handler
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // Shutdown Scheduler if used
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // Unregister BroadcastReceiver
        if (tokenClearReceiver != null) {
            unregisterReceiver(tokenClearReceiver);
        }

        // Check the token state and handle restarting service if needed
        if (token != null && !token.isEmpty()) { // Check if token is non-null and non-empty
            Intent broadcastIntent = new Intent(this, RestartServiceReceiver.class);
            sendBroadcast(broadcastIntent);
            setupAlarmManager(); // Set up the alarm manager if required
            setupWorkManager(); // Set up work manager if required
        }
    }


    private void handleTokenClearing() {
        // Clear token and stop service
        token = null;
        stopSelf();
    }


    private void setupWorkManager() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                FetchNotificationWorker.class,
                15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(getApplicationContext())
                .enqueueUniquePeriodicWork("FetchNotificationWork", ExistingPeriodicWorkPolicy.REPLACE, workRequest);
    }

    private void fetchSharedPreferencesData() {
        new Thread(() -> {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            token = sharedPreferences.getString(TOKEN_KEY, null);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAMEs, Context.MODE_PRIVATE);
            boolean isVibrationEnabled = prefs.getBoolean(PREF_VIBRATION_ENABLED, true);
            boolean isSoundEnabled = prefs.getBoolean(PREF_SOUND_ENABLED, true);

            runOnUiThread(() -> {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                Log.e("background " + token, "Vibrator is null");

                if (!isVibrationEnabled && vibrator != null) {
                    vibrator.cancel();
                }

                if (!isSoundEnabled) {
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        alertsound.stop();
                    }
                }
            });
        }).start();
    }

    private void runOnUiThread(Runnable action) {
        new Handler(getMainLooper()).post(action);
    }
    public void stopAlarmAndVibration() {

        if (vibrator != null) {
            vibrator.cancel();
        } else {
            Log.e("background", "Vibrator is null");
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    alertsound.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                alertsound.release();
                alertsound = null;
            } catch (IllegalStateException e) {
                Log.e("background", "MediaPlayer is in an illegal state");
            }
        } else {
            Log.e("background", "MediaPlayer is null");
        }


        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        SharedPreferences sharedPreferences = getSharedPreferences("notification_ids", MODE_PRIVATE);
        for (String notificationId : sharedPreferences.getAll().keySet()) {
            notificationManager.cancel(notificationId.hashCode());
        }
    }

    private void triggerNotification(String description, String date, String level) {
        String title = level;
        String des ="";
        String type = "";
        if (level.equals("Level 1")) {
            type = "Warning Alert!";
            des = "Vibration Detected!";
        } else if (level.equals("Level 2")) {
            type = "Minor Alert!";
            des = "Vibration Detected!";
        } else if (level.equals("Level 3")) {
            type = "Major Alert!";
            des = "Vibration Detected!";
        } else if (level.equals("Level 4")) {
            type = "Critical Alert!";
            des = "Theft Detected! Please take immediate action!";
        }
        String notificationText = des + " \nTime: " + date;

        String notificationId = level + "_" + date;
        SharedPreferences sharedPreferences = getSharedPreferences("notification_ids", MODE_PRIVATE);
        if (sharedPreferences.getBoolean(notificationId, false)) {
            return;
        }

        SharedPreferences notificationPrefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE);
        boolean isVibrationEnabled = notificationPrefs.getBoolean("vibration_enabled", true);
        boolean isSoundEnabled = notificationPrefs.getBoolean("sound_enabled", true);

        // Initialize vibration pattern and media player
        long[] vibrationPattern = null;
        int soundDuration = 0;
        int mediaPlayerResId = 0;
        int alertSoundResId = 0;

        switch (level) {
            case "Level 1":
                vibrationPattern = new long[]{0, 1000}; // Vibrate for 1 second
                mediaPlayerResId = R.raw.sound1;
                alertSoundResId = R.raw.alert1;
                soundDuration = 1000; // duration in milliseconds
                break;
            case "Level 2":
                vibrationPattern = new long[]{0, 3000}; // Vibrate for 3 seconds
                mediaPlayerResId = R.raw.sound4;
                alertSoundResId = R.raw.alert2;
                soundDuration = 3000;
                break;
            case "Level 3":
                vibrationPattern = new long[]{0, 5000}; // Vibrate for 5 seconds
                mediaPlayerResId = R.raw.sound2;
                alertSoundResId = R.raw.alert3;
                soundDuration = 5000;
                break;
            case "Level 4":
                vibrationPattern = new long[]{0, 10000}; // Vibrate for 10 seconds
                mediaPlayerResId = R.raw.sound3;
                alertSoundResId = R.raw.alert4;
                soundDuration = 10000;
                break;
        }

        // Create MediaPlayer instances
        mediaPlayer = MediaPlayer.create(this, mediaPlayerResId);
        alertsound = MediaPlayer.create(this, alertSoundResId);

        if (isSoundEnabled && mediaPlayer != null) {
            mediaPlayer.setLooping(true); // Enable looping
            mediaPlayer.start();
        }

        if (isVibrationEnabled && vibrationPattern != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));
            } else {
                vibrator.vibrate(vibrationPattern, -1);
            }
        }

        // Create notification builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.vehiclesecured)
                .setContentTitle(type)
                .setContentText(notificationText)
                .setPriority(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setAutoCancel(true);

        // Set intent for notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("fragmentToLoad", "historyFragment");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        // Display notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            // Handle permission not granted
            return;
        }
        notificationManager.notify(notificationId.hashCode(), builder.build()); // Use hashCode of notificationId as unique notification ID

        // Mark notification as shown
        sharedPreferences.edit().putBoolean(notificationId, true).apply();

        long[] finalVibrationPattern = vibrationPattern;
        int finalSoundDuration = soundDuration;

        // Stop media and start alert sound after media playback duration
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                }
                if (alertsound != null) {
                    alertsound.start();
                }
                if (finalVibrationPattern != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.cancel(); // Cancel vibration
                    } else {
                        vibrator.cancel(); // Cancel vibration after the same duration as the pattern
                    }
                }
            }
        }, finalSoundDuration);
    }


    private void setupAlarmManager() {
        Log.e("background", "Setting up alarm");
        Log.e("token", token);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Toast.makeText(getApplicationContext(), "AlarmManager service is not available", Toast.LENGTH_SHORT).show();
            return;
        }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            Toast.makeText(getApplicationContext(), "PowerManager service is not available", Toast.LENGTH_SHORT).show();
            return;
        }

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
        wakeLock.acquire(10 * 60 * 1000);

        Intent intent = new Intent(this, RestartServiceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long intervalMillis = 15 * 60 * 1000;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMillis, pendingIntent);
        } else {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), intervalMillis, pendingIntent);
        }
    }


    private void scheduleAlarmManager() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, RestartServiceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 60000, pendingIntent);
            } else {
                Intent permissionIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(permissionIntent);
            }
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60000, pendingIntent);
        }
    }



    private void ignoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Restart the service if it is killed
        Intent restartServiceIntent = new Intent(getApplicationContext(), MyBackgroundService.class);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmService = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);

        startService(new Intent(getApplicationContext(), RestartServiceReceiver.class));
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // Handle low memory and restart the service if needed
        startService(new Intent(getApplicationContext(), RestartServiceReceiver.class));
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_COMPLETE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            // Restart service if necessary
            startService(new Intent(getApplicationContext(), RestartServiceReceiver.class));
        }
    }









    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public void fetchnotificationData() {
        if (!isNetworkAvailable()) {
            return;
        }

        if (token != null) {
            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("token", token);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String url = "https://capstone2-16.onrender.com/api/getlatestnotification";

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    response -> {
                        try {
                            if (response.has("data")) {
                                JSONArray dataArray = response.getJSONArray("data");

                                for (int i = 0; i < dataArray.length(); i++) {
                                    JSONObject dataObject = dataArray.getJSONObject(i);
                                    String collection = dataObject.optString("collection", "");

                                    String dateString;
                                    String description;
                                    if (collection.equals("MinorAlert")) {
                                        dateString = dataObject.optString("vibrateAt", "");
                                        description = dataObject.optString("description", "");

                                        // Format date string to include AM/PM (hh:mm:ss a)
                                        if (!dateString.isEmpty()) {
                                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                                            SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
                                            try {
                                                Date date = inputFormat.parse(dateString);
                                                dateString = outputFormat.format(date);
                                            } catch (ParseException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        if (!dateString.isEmpty() && (latestArduinoData == null || dateString.compareTo(latestArduinoDateString) > 0)) {
                                            latestArduinoData = dataObject;
                                            latestArduinoDateString = dateString;
                                            latestlevelarduino = latestArduinoData.optString("level", "");
                                            latestdescriptionarduino = description;
                                        }
                                    } else if (collection.equals("Theft")) {
                                        dateString = dataObject.optString("happenedAt", "");
                                        description = dataObject.optString("description", "");

                                        // Format date string to include AM/PM (hh:mm:ss a)
                                        if (!dateString.isEmpty()) {
                                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                                            SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
                                            try {
                                                Date date = inputFormat.parse(dateString);
                                                dateString = outputFormat.format(date);
                                            } catch (ParseException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        if (!dateString.isEmpty() && (latestTheftDetails == null || dateString.compareTo(latestTheftDateString) > 0)) {
                                            latestTheftDetails = dataObject;
                                            latestTheftDateString = dateString;
                                            latestdescriptiontheft = description;
                                            latestlevelttheft = latestTheftDetails.optString("level", "");
                                        }
                                    }
                                }

                                // Check against the last notified date/time to prevent duplicate notifications
                                if (latestArduinoData != null && !latestArduinoDateString.equals(lastArduinoNotificationDate)) {
                                    triggerNotification(latestdescriptionarduino, latestArduinoDateString, latestlevelarduino);
                                    lastArduinoNotificationDate = latestArduinoDateString;
                                }
                                if (latestTheftDetails != null && !latestTheftDateString.equals(lastTheftNotificationDate)) {
                                    triggerNotification(latestdescriptiontheft, latestTheftDateString, latestlevelttheft);
                                    lastTheftNotificationDate = latestTheftDateString;
                                }

                                if (latestArduinoData == null && latestTheftDetails == null) {
                                    //   Toast.makeText(getApplicationContext(), "No new notifications found", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                //  Toast.makeText(getApplicationContext(), "Unexpected response format", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            // Toast.makeText(getApplicationContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                        }
                    },
                    error -> {
                        error.printStackTrace();
//                        Toast.makeText(getApplicationContext(), "Error fetching data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.d("background", "notification is null.");
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Bearer " + token);
                    return headers;
                }
            };

            requestQueue.add(jsonObjectRequest);
        }
    }




    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }




    public static MediaPlayer getMediaPlayerInstance() {
        return mediaPlayer;
    }



}