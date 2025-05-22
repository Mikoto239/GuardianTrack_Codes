package com.example.guardiantrack;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String PREF_VIBRATION_ENABLED = "vibration_enabled";

    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final String TOKEN_KEY = "TOKEN";

    private Switch switchVibration;
    private Switch switchSound;

    private TextView useremail;
    private Button buttonDeleteAccount;
    private String token;
    private SharedPreferences sharedPreferences;
    private SharedPreferences sharedPreferencestoken;
    private ImageView profileImageView;
    private static final String PREFS_NAMEs = "MyPrefs";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // SharedPreferences for token and settings
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences sharedPreferencestoken = getActivity().getSharedPreferences(PREFS_NAMEs, Context.MODE_PRIVATE);
        token = sharedPreferencestoken.getString(TOKEN_KEY, null);

        // Initialize UI elements
        switchVibration = view.findViewById(R.id.switchVibration);
        switchSound = view.findViewById(R.id.switchSound);
        profileImageView = view.findViewById(R.id.accImage);

        useremail = view.findViewById(R.id.SettingsEmail);
        buttonDeleteAccount = view.findViewById(R.id.buttonDeleteAccount);



        // Load saved preferences for vibration and sound settings
        boolean isVibrationEnabled = sharedPreferences.getBoolean(PREF_VIBRATION_ENABLED, true); // Default to true if not found
        boolean isSoundEnabled = sharedPreferences.getBoolean(PREF_SOUND_ENABLED, true); // Default to true if not found

        // Set the switches' states based on saved preferences
        switchVibration.setChecked(isVibrationEnabled);
        switchSound.setChecked(isSoundEnabled);

        // Set listeners to update preferences when switches change
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update vibration preference
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_VIBRATION_ENABLED, isChecked);
            editor.apply();
            Toast.makeText(getContext(), "Vibration " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update sound preference
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_SOUND_ENABLED, isChecked);
            editor.apply();
            Toast.makeText(getContext(), "Sound " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // Get the currently signed-in Google account
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
        if (account != null) {

            String personEmail = account.getEmail();
            Uri personPhoto = account.getPhotoUrl();

            useremail.setText(personEmail);

            // Load Google profile photo if available
            if (personPhoto != null) {
                Picasso.get().load(personPhoto)
                        .placeholder(R.drawable.error)  // Placeholder in case of loading failure
                        .error(R.drawable.error)        // Error image if loading fails
                        .transform(new CircleTransform())
                        .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.drawable.error);  // Default error image
            }
        }


        if (token != null) {
            buttonDeleteAccount.setVisibility(View.VISIBLE);
            buttonDeleteAccount.setBackgroundResource(R.drawable.delete_button); // Set red background
            buttonDeleteAccount.setOnClickListener(v -> showDeleteConfirmationDialog());
        } else {
            buttonDeleteAccount.setVisibility(View.GONE);
        }


        return view;
    }



    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setMessage("Do you really want to delete your account? This action cannot be undone.")
                .setTitle("Confirm Deletion")
                .setPositiveButton("Yes", (dialog, which) -> deleteUserFromDatabase())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void deleteUserFromDatabase() {
        // Step 1: Check network availability
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Step 2: Build the JSON request object
        JSONObject postData = new JSONObject();
        try {
            postData.put("token", token); // Include token in the request body
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error creating request data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Step 3: Construct the JsonObjectRequest with Authorization header
        String url = "https://capstone2-16.onrender.com/api/deleteuser";
        RequestQueue queue = Volley.newRequestQueue(requireContext());

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    // Handle successful response
                    Toast.makeText(requireContext(), "User deleted successfully", Toast.LENGTH_SHORT).show();

                    // Sign out from Google if using GoogleSignIn
                    GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                            .addOnCompleteListener(task -> {

                                SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREFS_NAMEs, Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.clear();
                                editor.apply();

                                // Stop the NotificationForegroundService
                                Intent serviceIntent = new Intent(requireContext(), MyBackgroundService.class);
                                requireContext().stopService(serviceIntent);

                                // Broadcast the token cleared action to the service
                                Intent broadcastIntent = new Intent("ACTION_CLEAR_TOKEN");
                                requireContext().sendBroadcast(broadcastIntent);

                                // Redirect to login screen
                                Intent intent = new Intent(requireContext(), LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                requireActivity().finish();
                            });
                },
                error -> {
                    // Handle error
                    Toast.makeText(requireContext(), "Not registered yet! ", Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token); // Set Authorization header with Bearer token
                return headers;
            }
        };

        // Step 4: Set timeout and retry policy
        int TIMEOUT_MS = 5000; // Timeout in milliseconds
        RetryPolicy retryPolicy = new DefaultRetryPolicy(TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);

        // Step 5: Add the request to the queue
        queue.add(jsonObjectRequest);
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
