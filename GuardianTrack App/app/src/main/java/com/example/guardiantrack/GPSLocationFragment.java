package com.example.guardiantrack;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GPSLocationFragment extends Fragment implements  LocationListener, OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private GoogleMap googleMap;

    private static final String TAG = "GPSLocationFragment";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager locationManager;
    private RequestQueue requestQueue;

    private String uniqueId;

    private boolean policeStationFetched = false;
    private boolean isUserLocationMarkerAdded = false;
    private Marker theftLocationMarker;
    private boolean isMapTouched = false;
    private MyBackgroundService backgroundService;
    private  double userlatitude;
    private  double userlongitude;
    private  double pinlatitude;
    private double pinlongitude;

    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";
    private String time = "";
    private Marker userLocationMarker;
    private Marker pinLocationMarker;




    private boolean isRunning = false;
    private MqttHandler mqttHandler;




    private Runnable checkForNewDataRunnable = new Runnable() {
        @Override
        public void run() {
            if(token != null)
            {
                startBackgroundService();
                fetchNotifications();
                if(MqttHandler.theftoccured){
                    fetchNearestPoliceStation(pinlatitude,pinlongitude);
                }
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_g_p_s_location, container, false);

        locationManager = (LocationManager) requireActivity().getSystemService(requireActivity().LOCATION_SERVICE);
        requestQueue = Volley.newRequestQueue(requireContext());

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "MapFragment is null");
        }
        mqttHandler = new MqttHandler(requireContext());

        if (!isNetworkAvailable()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            }
        } else {
            checkUserRegistration();
        }

        // Find the FloatingActionButton and set the OnClickListener
        FloatingActionButton infoFab = view.findViewById(R.id.info_fab);
        infoFab.setOnClickListener(v -> showPinDescriptionsDialog());

        return view;
    }

    private void showPinDescriptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_pin_descriptions, null);

        builder.setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void stopfocus(){
        isMapTouched = true;
    }


    private void startBackgroundService() {
        Intent stopIntent = new Intent(getActivity(), MyBackgroundService.class);
        stopIntent.setAction("STOP_ALARM_AND_VIBRATION");
        getActivity().startService(stopIntent);
    }


    @Override
    public void onResume() {
        super.onResume();
        policeStationFetched = false;
        isRunning = true;
        handler.post(checkForNewDataRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();

        MqttHandler.uniqueId = null;
        isRunning = false;
        handler.removeCallbacks(checkForNewDataRunnable);

    }
    private void checkUserRegistration() {
        if (!isNetworkAvailable()) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            }
            return;
        }


        if (token == null) {
            Log.d("gpslocation", "Token is null.");
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
                    if (!isAdded()) {
                        return;
                    }

                    try {
                        if (response.has("success") && response.getBoolean("success")) {
                            uniqueId = response.getString("uniqueId");
                            fetchHardwareStatus();
                        } else {
                            Toast.makeText(requireContext(), "User registration check failed.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String errorMessage = new String(error.networkResponse.data);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to check user registration: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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


        int TIMEOUT_MS = 10000;
        DefaultRetryPolicy retryPolicy = new DefaultRetryPolicy(TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);


        requestQueue.add(jsonObjectRequest);
    }
    private void fetchHardwareStatus() {
        if (!isAdded()) {
            Log.e("VehicleStatusFragment", "Fragment is not attached to activity");
            return;
        }

        if (token == null) {
            Toast.makeText(requireContext(), "Token is null", Toast.LENGTH_SHORT).show();
            return;
        }

        String statusUrl = "https://capstone2-16.onrender.com/api/mobilehardwarestatus";
        JSONObject postData = new JSONObject();
        try {
            postData.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error creating request body", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, statusUrl, postData,
                response -> {
                    try {
                        boolean alarmStatus = response.getBoolean("status");

                        if (alarmStatus) {
                            MqttHandler.uniqueId = uniqueId;
                            fetchPinLocation();
                            showfetchNotifications();
                        } else {
                            addMarker(0, 0, "Stopparking", "Stop", true);
                            MqttHandler.uniqueId = null;
                            MqttHandler.theftoccured = false;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    // Provide detailed error logging
                    String errorMessage;
                    if (error.networkResponse != null) {
                        errorMessage = "Error: " + error.networkResponse.statusCode + " - " + new String(error.networkResponse.data);
                    } else {
                        errorMessage = "Network Error: " + error.getMessage();
                    }
                    Log.e("NetworkError", errorMessage);
                    Toast.makeText(requireContext(), "Error fetching alarm status: " + errorMessage, Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        // Set a custom retry policy with timeout
        int timeoutMs = 10000; // Timeout in milliseconds
        int maxRetries = 3; // Number of retries
        RetryPolicy retryPolicy = new DefaultRetryPolicy(timeoutMs, maxRetries, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);

        // Add request to the queue
        RequestQueue requestQueue = Volley.newRequestQueue(requireContext());
        requestQueue.add(jsonObjectRequest);
    }


    private void fetchPinLocation() {
        Log.d(TAG, "fetchPinLocation " + token);

        if (!isAdded()) {
            Log.e("VehicleStatusFragment", "Fragment is not attached to activity");
            return;
        }

        Context context = getContext();
        if (context == null) {
            Log.e("VehicleStatusFragment", "Context is null");
            return;
        }

        String url = "https://capstone2-16.onrender.com/api/getlocation";
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error creating request body", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue requestQueue = Volley.newRequestQueue(context);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    try {
                        if (response.has("success") && response.getBoolean("success")) {
                            double latitude = response.getDouble("latitude");
                            double longitude = response.getDouble("longitude");
                            time = response.getString("time");
                            pinlatitude = latitude;
                            pinlongitude = longitude;
                            Log.d(TAG, "Saved longitude: " + longitude);
                            if (latitude != 0 || longitude != 0) {
                                addMarker(latitude, longitude, "Vehicle Pinned Location", "Hardware Location", true);
                            } else {
                                Toast.makeText(context, "Invalid location coordinates", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(context, "Latitude or longitude missing in response", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(context, "Failed to parse response", Toast.LENGTH_SHORT).show();
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
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        int timeoutMs = 10000; // Timeout in milliseconds
        int maxRetries = 3; // Number of retries
        RetryPolicy retryPolicy = new DefaultRetryPolicy(timeoutMs, maxRetries, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);

        requestQueue.add(jsonObjectRequest);
    }


    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d("vehicle", "Updating user location marker");


        if (userLocationMarker != null) {
            userLocationMarker.remove();
        }
        userlatitude = location.getLatitude();
        userlongitude =location.getLongitude();
        addMarker(location.getLatitude(), location.getLongitude(), "Me","User", false);


        isUserLocationMarkerAdded = true;


        updateMapFocus();
    }


    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // Ensure locationManager is initialized
        if (locationManager == null) {
            locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        }

        if (locationManager != null) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, this);
                    Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnownLocation != null) {
                        onLocationChanged(lastKnownLocation);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException while requesting location updates: " + e.getMessage());
                }
            } else {
                Toast.makeText(requireContext(), "Please enable location services", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "LocationManager is not initialized.");
        }
    }    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(requireContext(), "Location permission is required for this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }






    private void fetchNotifications() {
        if (!isAdded() || token == null) {
            return;  // Check if fragment is attached and token is available
        }

        if( MqttHandler.theftoccured){
            if (!policeStationFetched) {
                fetchNearestPoliceStation(userlatitude, userlongitude);
                policeStationFetched = true; // Set the flag to true to prevent repeated fetch
            }

            updateMapFocus();
            addMarker(MqttHandler.latitude, MqttHandler.longitude, "Theft Location" ,"Theft", true);
            MqttHandler.latitude = 0;
            MqttHandler.longitude = 0;
            MqttHandler.theftoccured = false;
        }
    }

    private void handleError(VolleyError error) {
        if (error != null && error.networkResponse != null && error.networkResponse.data != null) {
            String errorMessage = new String(error.networkResponse.data);
            Log.e("VehicleStatusFragment", "Error message: " + errorMessage);
        } else {
            Log.e("VehicleStatusFragment", "Error:sd " + error.getMessage());
        }
    }
    private void fetchNearestParkingLots(double latitude, double longitude) {
        if (!isAdded()) {
            Log.e("VehicleStatusFragment", "Fragment is not attached to activity");
            return;
        }
        String apiKey = "fsq3fjZ30p67DsLXEruaFe6suM4FTrKw3NLI/OnzLW71lVk=";
        int radius = 50000; // in meters

        String url = "https://api.foursquare.com/v3/places/search?query=parking&ll=" + latitude + "," + longitude + "&radius=" + radius;

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");
                        Log.d("FetchParkingLots", "Response received: " + response.toString());

                        if (results.length() > 0) {
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject place = results.getJSONObject(i);
                                double lat = place.getJSONObject("geocodes").getJSONObject("main").getDouble("latitude");
                                double lng = place.getJSONObject("geocodes").getJSONObject("main").getDouble("longitude");
                                String name = place.getString("name");


                                addMarker(lat, lng, name,"Parking", true);
                            }
                        } else {
                            // Indicate no results
//                            Toast.makeText(requireContext(), "No parking lots found nearby", Toast.LENGTH_SHORT).show();
                            Log.d("FetchParkingLots", "No parking lots found");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                        Log.e("FetchParkingLots", "JSON Parsing Error: " + e.getMessage());
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(requireContext(), "Error fetching parking lots: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("FetchParkingLots", "Request Error: " + error.getMessage());
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", apiKey);
                return headers;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }
    private void showfetchNotifications() {
        if (!isAdded() || token == null) {
            return;
        }

        String url = "https://capstone2-16.onrender.com/api/latestmapnotification";
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error creating request body", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue requestQueue = Volley.newRequestQueue(requireContext());

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.has("data")) {
                                JSONArray dataArray = response.getJSONArray("data");

                                for (int i = 0; i < dataArray.length(); i++) {
                                    JSONObject dataObject = dataArray.getJSONObject(i);
                                    String collection = dataObject.optString("collection", "");

                                    double latitude = dataObject.optDouble("currentlatitude", 0.0);
                                    double longitude = dataObject.optDouble("currentlongitude", 0.0);
                                    if (collection.equals("Theft")) {
                                        if (!policeStationFetched) {
                                            fetchNearestPoliceStation(userlatitude, userlongitude);
                                            policeStationFetched = true;
                                        }

                                        if(!MqttHandler.theftoccured){
                                            addMarker(latitude, longitude, "Theft Location" ,"Theft", true);
                                        }
                                        updateMapFocus();

                                    }
                                }
                            } else {
                                Toast.makeText(requireContext(), "Unexpected response format", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(requireContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage;
                        if (error.networkResponse != null) {
                            errorMessage = "Error: " + error.networkResponse.statusCode + " - " + new String(error.networkResponse.data);
                        } else {
                            errorMessage = "Network Error: " + error.getMessage();
                        }
                        Log.e("NetworkError", errorMessage);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }



    private void fetchNearestPoliceStation(double latitude, double longitude) {
        if (!isAdded()) {
            Log.e("GPSLocationFragment", "Fragment is not attached to activity");
            return;
        }

        String apiKey = "nS_T4uKvTTxbKJlFhq5q_ddP9a_5_u6EW3I3ZdvMJ00";
        int radius = 10000; // in meters (10 km)

        String url = "https://discover.search.hereapi.com/v1/discover?at=" + latitude + "," + longitude + "&q=police+station&apiKey=" + apiKey + "&radius=" + radius;

        url = url.replace(" ", "%20");

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // Check if the response contains the expected fields
                        if (response.has("items")) {
                            JSONArray items = response.getJSONArray("items");
                            Log.d("FetchPoliceStations", "Response received: " + response.toString());

                            // Limit the number of police stations to 5
                            int count = 0;
                            if (items.length() > 0) {
                                for (int i = 0; i < items.length() && count < 5; i++) {
                                    JSONObject place = items.getJSONObject(i);
                                    JSONObject position = place.getJSONObject("position");
                                    double lat = position.getDouble("lat");
                                    double lng = position.getDouble("lng");
                                    String name = place.getString("title");

                                    // Add marker for the police station
                                    addMarker(lat, lng, name, "Police", false);

                                    // Increment the count
                                    count++;
                                }
                            } else {
                                // Indicate no results
                                Toast.makeText(requireContext(), "No police stations found within 10 km", Toast.LENGTH_SHORT).show();
                                Log.d("FetchPoliceStations", "No police stations found within 10 km");
                            }
                        } else {
                            Toast.makeText(requireContext(), "Unexpected response format", Toast.LENGTH_SHORT).show();
                            Log.e("FetchPoliceStations", "Unexpected response format: " + response.toString());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Error parsing JSON response", Toast.LENGTH_SHORT).show();
                        Log.e("FetchPoliceStations", "JSON Parsing Error: " + e.getMessage());
                    }
                },
                error -> {
                    error.printStackTrace();
                    Toast.makeText(requireContext(), "Error fetching police stations: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("FetchPoliceStations", "Request Error: " + error.getMessage());
                });

        // Check if requestQueue is initialized
        if (requestQueue != null) {
            // Add the request to the RequestQueue
            requestQueue.add(jsonObjectRequest);
        } else {
            Log.e("FetchPoliceStations", "RequestQueue is not initialized");
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;

        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        // Handle user gestures that move the camera
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                stopfocus();
            }
        });

        googleMap.setOnMarkerClickListener(marker -> {

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 18));


            if (marker.equals(theftLocationMarker)) {
                Log.d("Map", "Theft marker clicked");

                isMapTouched = false;
            }

            return false;
        });
        checkLocationPermissions();
        fetchPinLocation();
        fetchNotifications();


    }

    public void addMarker(double latitude, double longitude, String title, String type, boolean isTheftLocation) {
        if (getActivity() == null || googleMap == null || !isAdded()) {
            return;
        }
        if (title.equals("Stopparking")) {
            fetchNearestParkingLots(userlatitude, userlongitude);
            return;
        }

        LatLng position = new LatLng(latitude, longitude);
        int drawableResId = R.drawable.poweron; // Default icon

        // Determine the drawable resource based on the type
        if (type.equals("Parking")) {
            drawableResId = R.drawable.parkinglot;
        } else if (type.equals("Police")) {
            drawableResId = R.drawable.police;
        } else if (type.equals("User")) {
            drawableResId = R.drawable.user;

            // Remove existing "User Location" marker if present
            if (userLocationMarker != null) {
                userLocationMarker.remove();
            }
        } else if (type.equals("Hardware Location")) {
            drawableResId = R.drawable.park;
        } else if (type.equals("Theft")) {
            drawableResId = R.drawable.theft;

            // Remove existing "Theft Location" marker if present
            if (theftLocationMarker != null) {
                theftLocationMarker.remove();
            }
        }


        // Scale the drawable resource to a size similar to the default Google Maps pin (48px width)
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(getScaledBitmap(drawableResId, 150
        ));

        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(title)
                .icon(icon);

        Marker marker = googleMap.addMarker(markerOptions);

        // Assign markers to appropriate variables
        if (title.equals("Me")) {
            userLocationMarker = marker;
        } else if (title.equals("Hardware Location")) {
            pinLocationMarker = marker;
        } else if (title.equals("Theft Location")) {
            theftLocationMarker = marker;
        }

        updateMapFocus();
    }


    private Bitmap getScaledBitmap(int drawableResId, int desiredWidth) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), drawableResId);

        // Calculate proportional height based on the desired width
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        float aspectRatio = (float) originalHeight / (float) originalWidth;
        int scaledHeight = Math.round(desiredWidth * aspectRatio);

        // Scale the bitmap with the new dimensions
        return Bitmap.createScaledBitmap(originalBitmap, desiredWidth, scaledHeight, false);
    }
    private void updateMapFocus() {
        if (googleMap == null) {
            return;
        }

        LatLng position = null;
        if (theftLocationMarker != null) {
            position = theftLocationMarker.getPosition();
        } else if (pinLocationMarker != null) {
            position = pinLocationMarker.getPosition();
        } else if (userLocationMarker != null) {
            position = userLocationMarker.getPosition();
        }

        if (position != null && !isMapTouched) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18));
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }






    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
