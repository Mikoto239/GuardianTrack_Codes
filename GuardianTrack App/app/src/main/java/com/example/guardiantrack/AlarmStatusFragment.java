package com.example.guardiantrack;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AlarmStatusFragment extends Fragment implements android.location.LocationListener {

    private ImageButton imageButton;
    private TextView textView;
    private int currentImageIndex = 1; // Start with OFF (1) or ON (2)
    private RequestQueue requestQueue;

    private String changeStatusUrl = "https://capstone2-16.onrender.com/api/changestatus";

    private Handler handler;

    private String name;
    private String email;
    private ImageView image;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private LocationManager locationManager;
    private Location userLocation;

    private String cellphonenumber;

    private String uniqueId;
    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";
    private static final int RETRY_INTERVAL_MS = 2000; // Interval between retries (2 seconds)
    private static final int TIMEOUT_MS = 20000; // Total timeout duration (20 seconds)

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_alarm_status, container, false);

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        imageButton = rootView.findViewById(R.id.iButton);
        textView = rootView.findViewById(R.id.power);
//        image = rootView.findViewById(R.id.image2);
        requestQueue = Volley.newRequestQueue(requireContext());

        imageButton.setOnClickListener(v -> changeImage());

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getActivity());

        // Update TextViews with user information
        if (account != null) {
            name = account.getDisplayName();
            email = account.getEmail();
        }

        checkUserRegistration();

        return rootView;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void checkUserRegistration() {
        // Check if the fragment is attached before doing anything
        if (!isAdded()) {
            Log.d("history", "Fragment is not attached to context.");
            return;
        }

        if (!isNetworkAvailable()) {
            // Check if the fragment is attached before showing a Toast
            if (isAdded()) {
                Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (token == null) {
            Log.d("history", "Token is null.");
            return;
        }

        String url = "https://capstone2-16.onrender.com/api/checkuser";

        JSONObject postData = new JSONObject();
        try {
            postData.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    // Check if the fragment is attached before performing any UI updates
                    if (isAdded()) {
                        try {
                            if (response.has("success") && response.getBoolean("success")) {
                                uniqueId = response.getString("uniqueId");
                                cellphonenumber = response.getString("cellphonenumber");
                                imageButton.setEnabled(true);
                                // Now that we have the uniqueId, fetch the data
                                fetchData();
                            } else {
                                Toast.makeText(requireContext(), "User registration check failed.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                error -> {

                    if (isAdded()) {
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                // Extract the response data from the server's error
                                String errorMessage = new String(error.networkResponse.data);

                                // Parse the error message as a JSON object
                                JSONObject errorResponse = new JSONObject(errorMessage);

                                // Get the message from the server response
                                String messageFromServer = errorResponse.optString("message", "An error occurred.");

                                // Display the message in the Toast
                                Toast.makeText(requireContext(), messageFromServer, Toast.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(requireContext(), "Failed to parse error response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(requireContext(), "Failed to check user registration: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        int TIMEOUT_MS = 5000;
        DefaultRetryPolicy retryPolicy = new DefaultRetryPolicy(TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);

        // Check if the fragment is attached before adding the request to the queue
        if (isAdded()) {
            requestQueue.add(jsonObjectRequest);
        }
    }



    private void fetchData() {
        if (!isAdded()) {
            // Fragment is not attached to the activity, return or handle appropriately
            return;
        }

        // Show loading indicator while fetching status
        ProgressDialog progressDialog = ProgressDialog.show(requireContext(), "", "Loading...", true);

        if (token != null) {
            String statusUrl = "https://capstone2-16.onrender.com/api/mobilehardwarestatus";

            JSONObject postData = new JSONObject();
            try {
                postData.put("token", token); // Include token in the request body
            } catch (JSONException e) {
                e.printStackTrace();
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "Failed to prepare request data", Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, statusUrl, postData,
                    response -> {
                        progressDialog.dismiss(); // Dismiss loading indicator
                        try {
                            boolean alarmStatus = response.getBoolean("status");
                            updateUI(alarmStatus);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(requireContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                        }
                    }, error -> {
                progressDialog.dismiss(); // Dismiss loading indicator in case of error
                error.printStackTrace();
                Toast.makeText(requireContext(), "Error fetching alarm status: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Bearer " + token); // Set Authorization header with Bearer token
                    return headers;
                }
            };

            requestQueue.add(jsonObjectRequest);
        } else {
            progressDialog.dismiss();
            Toast.makeText(requireContext(), "Unique ID or token is missing", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(boolean alarmStatus) {
        if (alarmStatus) {
            imageButton.setImageResource(R.drawable.poweron);
//            image.setImageResource(R.drawable.vehiclesecured);
            textView.setText("Alarm is ON");
            currentImageIndex = 2;
        } else {
            imageButton.setImageResource(R.drawable.poweroff);
//            image.setImageResource(R.drawable.vehiclenotsecured);
            textView.setText("Alarm is OFF");
            currentImageIndex = 1;
        }
    }

    // Change image method
    private void changeImage() {
        changeAlarmStatus(currentImageIndex == 1);
    }

    private void changeAlarmStatus(final boolean newStatus) {
        // Check if the status is changing from true to false and the current index is 2
        if (currentImageIndex == 2 && !newStatus) {
            // If so, directly call the method to change the status
            changeStatus(newStatus);
            return;
        }

        // Show the loading dialog before starting the pin location update
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Processing...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Call pinLocationUpdate with the progressDialog and callback to change status
        pinLocationUpdate(progressDialog, () -> {
            // After pin location update, call the method to change the status
            checkPinLocationAndChangeStatus(newStatus);
        });
    }

    private void pinLocationUpdate(ProgressDialog progressDialog, Runnable callback) {
        String url = "https://capstone2-16.onrender.com/api/currentlocation";
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
            requestBody.put("pinlocation", true);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    // Dismiss the loading dialog
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    // Check pin location and change status
                    checkPinLocationAndChangeStatus(true);
                    // Run the callback if provided
                    if (callback != null) {
                        callback.run();
                    }
                },
                error -> {
                    // Dismiss the loading dialog in case of error
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    error.printStackTrace();
                    Toast.makeText(requireContext(), "Failed to send location update", Toast.LENGTH_SHORT).show();
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

        // Delayed check for GPS signal after 3 seconds (for demonstration)
        new Handler().postDelayed(() -> {
            // Your GPS signal check logic here (if needed)
        }, 3000);
    }



    private void checkPinLocationAndChangeStatus(boolean newStatus) {
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Fetching location...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Handler handler = new Handler();
        long startTime = System.currentTimeMillis();

        // Runnable to handle retry
        Runnable retryRunnable = new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - startTime >= TIMEOUT_MS) {
                    // Timeout reached
                    progressDialog.dismiss();
                    sendOffRequest(); // Handle timeout case
                    Toast.makeText(requireContext(), "Request timed out. Sending off request...", Toast.LENGTH_SHORT).show();
                } else {
                    // Retry request
                    requestPinLocation(newStatus, progressDialog, handler, this);
                }
            }
        };

        // Start the first request
        handler.post(retryRunnable);
    }

    private void requestPinLocation(boolean newStatus, ProgressDialog progressDialog, Handler handler, Runnable retryRunnable) {
        String url = "https://capstone2-16.onrender.com/api/getlocation";
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            progressDialog.dismiss();
            sendOffRequest();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    progressDialog.dismiss(); // Dismiss the dialog on success
                    handler.removeCallbacks(retryRunnable); // Remove retry callback
                    try {
                        if (response.getBoolean("success")) {
                            double latitude = response.getDouble("latitude");
                            double longitude = response.getDouble("longitude");
                            if (latitude != 0 && longitude != 0) {
                                changeStatus(newStatus);
                            } else {
                                sendOffRequest();
                                Toast.makeText(requireContext(), "No valid GPS coordinates received.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(requireContext(), "No GPS signal, sending off request...", Toast.LENGTH_SHORT).show();
                            sendOffRequest(); // Call the method to send off request
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Error parsing pin location", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    // Handle errors, but continue retrying
                    String errorMessage;
                    if (error instanceof NetworkError) {
                        errorMessage = "Network error. Please check your internet connection.";
                    }else {
                        errorMessage = "Failed to fetch pin location: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    }
                    // Log the error for debugging purposes
                    Log.e("RequestError", errorMessage, error);
                    // Retry the request
                    handler.postDelayed(retryRunnable, RETRY_INTERVAL_MS);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        // Set a timeout of 10 seconds for each request
        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000,  // 10 seconds timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        // Add the request to the request queue
        requestQueue.add(jsonObjectRequest);
    }

    private void sendOffRequest() {
        String offRequestUrl = "https://capstone2-16.onrender.com/api/offrequest";
        JSONObject offRequestBody = new JSONObject();
        try {
            offRequestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        JsonObjectRequest offRequest = new JsonObjectRequest(Request.Method.POST, offRequestUrl, offRequestBody,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(requireContext(), "Offrequest successfully sent.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Failed to send offrequest.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
//                        Toast.makeText(requireContext(), "Error parsing offrequest response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (error instanceof NetworkError) {
                        Toast.makeText(requireContext(), "Network error. Please check your internet connection.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to send offrequest: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(offRequest);
    }

    private void changeStatus(boolean newStatus) {
        String changeStatusUrl = "https://capstone2-16.onrender.com/api/changestatus"; // Replace with your actual endpoint URL
        JSONObject postData = new JSONObject();
        try {
            postData.put("token", token);
            // Optionally, add other parameters to your postData if needed
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to prepare request data", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, changeStatusUrl, postData,
                response -> {

                    fetchData();
                    if (newStatus) {
                        // Replace R.id.fragment_container with the ID of the container where you want to place GPSLocationFragment
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new VehicleStatusFragment())
                                .addToBackStack(null)
                                .commit();
                    } else {
                        // If the status changed to false, call method to delete current location
                        deleteCurrentLocation();
                    }
                },
                error -> {
                    error.printStackTrace();
                    if (error instanceof NetworkError) {
                        Toast.makeText(requireContext(), "Network error. Please check your internet connection.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to change status: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token); // Set Authorization header with Bearer token
                return headers;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }

    private void deleteCurrentLocation() {
        String url = "https://capstone2-16.onrender.com/api/turnoff";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // Create headers for the request
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json"); // Example header, adjust as needed
        headers.put("Authorization", "Bearer " + token); // Replace YOUR_BEARER_TOKEN with your actual token

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    // Handle the response if needed
                    Toast.makeText(requireContext(), "GuardianTrack is Off!", Toast.LENGTH_SHORT).show();
                    changeAlarmStatus(false);
                },
                error -> {
                    error.printStackTrace();
//                    Toast.makeText(requireContext(), "Failed to delete current location", Toast.LENGTH_SHORT).show();
                }) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };

        // Add the request to the request queue
        requestQueue.add(jsonObjectRequest);
    }

    @Override
    public void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request the permission
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        if (locationManager == null)
            locationManager = (LocationManager) requireContext().getSystemService(requireContext().LOCATION_SERVICE);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
    }

    private void stopLocationUpdates() {
        if (locationManager != null)
            locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        userLocation = location;
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
