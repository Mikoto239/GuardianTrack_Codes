package com.example.guardiantrack;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.BuildConfig;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String TOKEN_KEY = "TOKEN";

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle actionBarDrawerToggle;
    private FragmentManager fragmentManager;
    private NavigationView navigationView;
    private ImageButton btnVehicleStatus;
    private ImageButton btnGpsLocation;
    private ImageButton btnAlarmStatus;
    private GoogleSignInOptions googleSignInOptions;
    private GoogleSignInClient googleSignInClient;

    private SharedPreferences sharedPreferences;

    private AlertDialog myDialog;
    private String token;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        schedulePeriodicWork();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }

        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

        drawerLayout = findViewById(R.id.drawer_layout);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();

        checkAndRequestBatteryOptimization();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        fragmentManager = getSupportFragmentManager();

        // Load initial fragment (Vehicle Status)
        replaceFragment(new VehicleStatusFragment(), false);

        // Initialize buttons
        btnVehicleStatus = findViewById(R.id.btn_vehicle_status);
        btnGpsLocation = findViewById(R.id.btn_gps_location);
        btnAlarmStatus = findViewById(R.id.btn_alarm_status);
        navigationView = findViewById(R.id.nav_view);

        // Set button click listeners for fragment changes
        btnVehicleStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFragmentChange(new VehicleStatusFragment());
            }
        });

        btnGpsLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFragmentChange(new GPSLocationFragment());
            }
        });

        btnAlarmStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFragmentChange(new AlarmStatusFragment());
            }
        });

        // Navigation item selection listener
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (isNetworkAvailable()) {
                    Fragment selectedFragment = null;
                    int itemId = item.getItemId();

                    if (itemId == R.id.nav_account) {
                        selectedFragment = new NavAccFragment();
                        Toast.makeText(MainActivity.this, "Account Details", Toast.LENGTH_SHORT).show();
                    } else if (itemId == R.id.nav_settings) {
                        selectedFragment = new SettingsFragment();
                        Toast.makeText(MainActivity.this, "Settings", Toast.LENGTH_SHORT).show();
                    } else if (itemId == R.id.nav_history) {
                        selectedFragment = new HistoryFragment();
                        Toast.makeText(MainActivity.this, "Notification", Toast.LENGTH_SHORT).show();
                    } else if (itemId == R.id.nav_notification) {
                        selectedFragment = new NotificationFragment();
                        Toast.makeText(MainActivity.this, "History", Toast.LENGTH_SHORT).show();
                    } else if (itemId == R.id.nav_usermanual) {
                        selectedFragment = new UserManualFragment();
                        Toast.makeText(MainActivity.this, "User Manual", Toast.LENGTH_SHORT).show();
                    } else if (itemId == R.id.nav_logout) {
                        showLogoutConfirmation();
                        return true;
                    }

                    if (selectedFragment != null) {
                        replaceFragment(selectedFragment, true);
                        drawerLayout.closeDrawers();
                        return true;
                    }
                    return false;
                } else {
                    showNetworkWarning();
                    return true;
                }
            }
        });

        if (!isNetworkAvailable()) {
            showNetworkWarning();
        } else {
            obtainFCMToken();
        }
    }


    private void schedulePeriodicWork() {
        // Define constraints (e.g., require network connection)
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Task requires a network connection
                .build();

        // Create a PeriodicWorkRequest to run the FetchNotificationWorker every 15 minutes
        PeriodicWorkRequest fetchNotificationRequest =
                new PeriodicWorkRequest.Builder(FetchNotificationWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints) // Apply constraints
                        .build();

        // Enqueue the work request
        WorkManager.getInstance(this).enqueue(fetchNotificationRequest);

        Log.d("WorkManager", "Periodic work scheduled with constraints.");
    }

    // Method to handle fragment changes
    private void handleFragmentChange(Fragment fragment) {
        replaceFragment(fragment, true);
    }

    // Helper method to replace fragments
    private void replaceFragment(Fragment fragment, boolean addToBackStack) {
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);

        if (currentFragment == null || !currentFragment.getClass().equals(fragment.getClass())) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE); // Smooth transition
            transaction.replace(R.id.fragment_container, fragment);

            if (addToBackStack) {
                transaction.addToBackStack(null);
            }

            transaction.commitAllowingStateLoss();
        }
    }

    private void showNetworkWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and try again.")
                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isNetworkAvailable()) {
                            dialog.dismiss();
                            // Re-enable buttons or interaction here if needed
                        } else {
                            Toast.makeText(MainActivity.this, "Still no internet connection. Please check your settings.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setCancelable(false) // Prevents dismissal by clicking outside the dialog
                .show();
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
//    public boolean foregroundservice() {
//        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
//            if (NotificationForegroundService.class.getName().equals(service.service.getClassName())) {
//                return true;
//            }
//        }
//        return false;
//    }

    private void checkAndRequestBatteryOptimization() {
        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasRequestedOptimization = sharedPreferences.getBoolean("requested_optimization", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName()) && !hasRequestedOptimization) {
                new AlertDialog.Builder(this)
                        .setTitle("Battery Optimization")
                        .setMessage("For better app performance, please allow this app to ignore battery optimizations.")
                        .setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putBoolean("requested_optimization", true);
                                editor.apply();

                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void obtainFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.e(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        String token = task.getResult();
                        Log.d(TAG, "FCM registration token: " + token);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myDialog != null && myDialog.isShowing()) {
            myDialog.dismiss();
        }
    }

    private void replaceFragment(Fragment fragment) {
        fragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void showLogoutConfirmation() {
        new LogoutFragment().show(getSupportFragmentManager(), "logout_dialog");
    }

    public void logout() {

        Intent serviceIntent = new Intent(this, MyBackgroundService.class);
        stopService(serviceIntent);

        // Clear the shared preferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear(); // This removes all preferences
        editor.apply();


        token = "";
        editor.putString("token", token); // Save the empty token to SharedPreferences
        editor.apply(); // Apply the changes
        Log.d(TAG, "logout: All preferences cleared. Token set to: " + token);

        // Delay sending the broadcast to avoid race conditions
        new Handler().postDelayed(() -> {
            Intent broadcastIntent = new Intent("ACTION_CLEAR_TOKEN");
            sendBroadcast(broadcastIntent);
        }, 500);  // 500ms delay, adjust if necessary

        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

}
