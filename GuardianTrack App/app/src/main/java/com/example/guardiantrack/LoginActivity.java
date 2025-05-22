package com.example.guardiantrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class LoginActivity extends Activity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 3000;

    private static final String PREFS_NAME = "MyPrefs";
    private static final String TOKEN_KEY = "TOKEN";

    private GoogleSignInOptions googleSignInOptions;
    private GoogleSignInClient googleSignInClient;
    private ImageButton googleBtn;

    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        googleBtn = findViewById(R.id.googlebtn);

        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

        // Initialize the RequestQueue
        requestQueue = Volley.newRequestQueue(this);

        googleBtn.setOnClickListener(v -> signIn());

        // Check if the user is already signed in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            navigateToMainActivity();
        }

        // Stop the foreground service when the login activity is created
        stopForegroundService();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Optionally, stop the foreground service when the activity is resumed
        stopForegroundService();
    }

    private void stopForegroundService() {
        Intent serviceIntent = new Intent(this, MyBackgroundService.class);
        stopService(serviceIntent); // Stop the service
    }

    private void signIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            String name = account.getDisplayName();
            String email = account.getEmail();

            // Check for internet connection before fetching the token
            if (isConnected()) {
                fetchToken(name, email);
            } else {
                Toast.makeText(this, "No internet connection. Please try again later.", Toast.LENGTH_SHORT).show();
            }

        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            handleSignInError(e);
        }
    }

    private void handleSignInError(ApiException e) {
        if (e.getStatusCode() == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            Toast.makeText(this, "Sign in was cancelled.", Toast.LENGTH_SHORT).show();
        } else if (e.getStatusCode() == GoogleSignInStatusCodes.SIGN_IN_FAILED) {
            Toast.makeText(this, "Sign in failed. Please try again.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "An unexpected error occurred: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchToken(String name, String email) {
        String url = "https://capstone2-16.onrender.com/api/sendtoken";

        JSONObject postData = new JSONObject();
        try {
            postData.put("name", name);
            postData.put("email", email);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            String token = response.getString("token");

                            saveTokenToSharedPreferences(token);
                            Toast.makeText(getApplicationContext(), "Login Successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "Token not received from server", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), "Failed to parse token response", Toast.LENGTH_SHORT).show();
                    }

                    // Navigate to MainActivity regardless of token status
                    navigateToMainActivity();
                },
                error -> {
                    Log.e(TAG, "Error during token fetch: " + error.getMessage());
                    Toast.makeText(getApplicationContext(), "Welcome!", Toast.LENGTH_SHORT).show();
                    navigateToMainActivity(); // Navigate even if there is an error
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        // Add the request to the RequestQueue
        requestQueue.add(jsonObjectRequest);
    }

    private void saveTokenToSharedPreferences(String token) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(TOKEN_KEY, token);
        editor.apply();
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}