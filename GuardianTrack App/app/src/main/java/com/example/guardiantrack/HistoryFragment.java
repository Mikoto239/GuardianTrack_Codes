package com.example.guardiantrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class HistoryFragment extends Fragment {
    private TextView emptyView;
    private LinearLayout linearLayout;
    private String name;
    private String email;
    private String uniqueId;
    private RequestQueue requestQueue;
    private ProgressBar loadingProgressBar;
    private List<Object> dataList = new ArrayList<>();
    private double pinlatitude;
    private double pinlongitude;
    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";

    public HistoryFragment() {
        // Required empty public constructor
    }

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        linearLayout = rootView.findViewById(R.id.linearLayout);
        emptyView = rootView.findViewById(R.id.emptyView);
        loadingProgressBar = rootView.findViewById(R.id.loadingProgressBar);

        // Initialize the RequestQueue
        requestQueue = Volley.newRequestQueue(requireContext());

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getActivity());
        if (account != null) {
            name = account.getDisplayName();
            email = account.getEmail();
        }
        showLoadingScreen();
        checkUserRegistration();
    }

    private void showLoadingScreen() {
        loadingProgressBar.setVisibility(View.VISIBLE);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadingProgressBar.setVisibility(View.GONE);
            }
        }, 3000); // 3000 milliseconds = 3 seconds
    }

    private void hideLoadingScreen() {
        loadingProgressBar.setVisibility(View.GONE);
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void checkUserRegistration() {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (token == null) {
//            Toast.makeText(requireContext(), "Token not found in SharedPreferences.", Toast.LENGTH_SHORT).show();
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
                    try {
                        if (response.has("success") && response.getBoolean("success")) {
                            fetchHistoryData();
                        } else {
                            Toast.makeText(requireContext(), "User registration check failed.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
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

        requestQueue.add(jsonObjectRequest);
    }

    private void fetchHistoryData() {
        String url = "https://capstone2-16.onrender.com/api/allnotification";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    try {
                        JSONArray dataArray = response.getJSONArray("data");

                        // If no data is returned
                        if (dataArray.length() == 0) {
                            hideLoadingScreen();
                            emptyView.setVisibility(View.VISIBLE); // Show empty view
                            return;
                        } else {
                            emptyView.setVisibility(View.GONE); // Hide empty view if there is data
                        }

                        // Initialize Firebase database reference
                        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference().child("history");

                        for (int i = 0; i < dataArray.length(); i++) {
                            JSONObject dataObject = dataArray.getJSONObject(i);
                            String collection = dataObject.getString("collection");

                            if (collection.equals("MinorAlert")) {
                                // Add ArduinoData to dataList
                                String description = dataObject.getString("description");
                                double latitude = dataObject.getDouble("latitude");
                                double longitude = dataObject.getDouble("longitude");
                                String vibrateAt = dataObject.getString("vibrateAt");
                                String level = dataObject.getString("level");
                                String address = dataObject.getString("address");
                                String uniqueId = dataObject.getString("uniqueId");
                                ArduinoData arduinoData = new ArduinoData(description, latitude, longitude, vibrateAt, level, address, uniqueId);
                                dataList.add(arduinoData);
                            } else if (collection.equals("Theft")) {
                                // Add TheftDetails to dataList
                                double currentlatitude = dataObject.getDouble("currentlatitude");
                                double currentlongitude = dataObject.getDouble("currentlongitude");
                                String happenedAt = dataObject.getString("happenedAt");
                                String level = dataObject.getString("level");
                                String description = dataObject.getString("description");
                                String uniqueId = dataObject.getString("uniqueId");
                                String address  = dataObject.getString("address");
                                TheftDetails theftDetails = new TheftDetails(currentlatitude, currentlongitude, description, uniqueId, level,address, happenedAt);
                                dataList.add(theftDetails);
                            }
                        }

                        for (Object data : dataList) {
                            JSONObject dataJson = new JSONObject();
                            try {
                                if (data instanceof ArduinoData) {
                                    // Add ArduinoData fields to the JSON object
                                    ArduinoData arduinoData = (ArduinoData) data;
                                    dataJson.put("description", arduinoData.description);
                                    dataJson.put("latitude", arduinoData.latitude);
                                    dataJson.put("longitude", arduinoData.longitude);
                                    dataJson.put("vibrateAt", arduinoData.vibrateAt);
                                    dataJson.put("level", arduinoData.level);
                                    dataJson.put("address", arduinoData.address);
                                    dataJson.put("uniqueId", arduinoData.uniqueId);
                                } else if (data instanceof TheftDetails) {
                                    TheftDetails theftDetails = (TheftDetails) data;
                                    dataJson.put("description", theftDetails.description);
                                    dataJson.put("currentlatitude", theftDetails.currentlatitude);
                                    dataJson.put("currentlongitude", theftDetails.currentlongitude);
                                    dataJson.put("happenedAt", theftDetails.happenedAt);
                                    dataJson.put("level", theftDetails.level);
                                    dataJson.put("address", theftDetails.address);
                                    dataJson.put("uniqueId", theftDetails.uniqueId);
                                }

                                databaseRef.push().setValue(dataJson.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        // Update UI with the parsed data
                        updateUI(dataList);
                        hideLoadingScreen();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to parsfsdfe response", Toast.LENGTH_SHORT).show();
                    }
                },
                this::handleError) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }



    private void handleError(VolleyError error) {
        hideLoadingScreen();
        if (error != null && error.networkResponse != null && error.networkResponse.data != null) {
            // Server returned an error response
            String errorMessage = new String(error.networkResponse.data);

            Toast.makeText(requireContext(), "No record has been found!", Toast.LENGTH_SHORT).show();
        } else if (error instanceof TimeoutError) {
            // Timeout occurred
            Toast.makeText(requireContext(), "Network error: Timeout occurred", Toast.LENGTH_SHORT).show();
        } else {
            // Other types of errors
            Toast.makeText(requireContext(), "Unknown error occurred", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(List<Object> dataList) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        linearLayout.removeAllViews(); // Clear existing views

        // Define margin in pixels
        int margin = 5; // Adjust this value as needed

        for (Object data : dataList) {
            View itemView;

         if (data instanceof TheftDetails) {
                    itemView = inflater.inflate(R.layout.item_theft_details, linearLayout, false);
                    setupTheftDetailsView(itemView, (TheftDetails) data);
                    addViewWithMargin(itemView, margin);
            } else if (data instanceof ArduinoData) {
                itemView = inflater.inflate(R.layout.item_arduino_data, linearLayout, false);
                setupminoralertdisplay(itemView, (ArduinoData) data);
                addViewWithMargin(itemView, margin);
            } else {
                // Handle unsupported data type
                continue;
            }
        }

        // Show empty view if dataList is empty
        emptyView.setVisibility(dataList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupTheftDetailsView(View itemView, TheftDetails theftDetails) {
        TextView addressxml = itemView.findViewById(R.id.address);
        TextView theftdate = itemView.findViewById(R.id.theftdate);
        TextView thefttime = itemView.findViewById(R.id.thefttime);
        TextView descriptionxml = itemView.findViewById(R.id.theftdescription);
        TextView levelxml = itemView.findViewById(R.id.theftlevel);

        String happenedAt = theftDetails.happenedAt;
        String formattedDate = formatDate(happenedAt);
        String formattedTime = formatTime(happenedAt);

        Spanned level = Html.fromHtml("<b>Severity:</b> Critical Alert!");
        Spanned description = Html.fromHtml("<b>Description:</b> Critical Alert! Potential theft detected!");
        Spanned address = Html.fromHtml("<b>Address:</b> " + theftDetails.address);
        Spanned date = Html.fromHtml("<b>Date:</b> " + formattedDate );
        Spanned time = Html.fromHtml("<b>Time:</b> " + formattedTime );

        descriptionxml.setText(description);
        addressxml.setText(address);
        theftdate.setText(date);
        thefttime.setText(time);
        levelxml.setText(level);
    }


    private void setupminoralertdisplay(View itemView, ArduinoData arduinoData) {
        TextView descriptionTextView = itemView.findViewById(R.id.arduinoVibrationDurationTextView);
        TextView vibrateAtDateTextView = itemView.findViewById(R.id.arduinodate);
        TextView vibrateAtTimeTextView = itemView.findViewById(R.id.arduinotime);
        TextView vibrateLevelview = itemView.findViewById(R.id.vibratelevel);
        TextView typeLevelview = itemView.findViewById(R.id.typevibration);

        String severity="";

        if(arduinoData.level.equals("Level 1")){
            severity ="Warning!";
        }
        else if(arduinoData.level.equals("Level 2")){
            severity ="Minor!";
        }
        else if(arduinoData.level.equals("Level 3")){
            severity ="Major!";
        }
        Spanned vibratelevel = Html.fromHtml("<b>Severity:</b> " + severity);
        Spanned descriptionText = Html.fromHtml("<b>Description:</b> " + arduinoData.description);

        String vibrateAtString = arduinoData.vibrateAt;
        String formattedDate = formatDate(vibrateAtString);
        String formattedTime = formatTime(vibrateAtString); // Include seconds

        Spanned vibrateAtDateText = Html.fromHtml("<b>Date:</b> " + formattedDate);
        Spanned vibrateAtTimeText = Html.fromHtml("<b>Time:</b> " + formattedTime);

        vibrateLevelview.setText(vibratelevel);
        descriptionTextView.setText(descriptionText);
        vibrateAtDateTextView.setText(vibrateAtDateText);
        vibrateAtTimeTextView.setText(vibrateAtTimeText);

        setBackgroundWithRadius(itemView, arduinoData.level);
        setTextColorForLevel(arduinoData.level, typeLevelview);
    }

    private String formatDate(String dateString) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());

        try {
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return dateString;
        }
    }
    private String formatTime(String dateTimeString) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault());
        inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat outputFormatUTC = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
        outputFormatUTC.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat outputFormatPHT = new SimpleDateFormat("hh:mm:ss a 'PHT'", Locale.getDefault());
        outputFormatPHT.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));

        try {
            Date date = inputFormat.parse(dateTimeString);
            String utcTime = outputFormatUTC.format(date);
            return utcTime;
        } catch (ParseException e) {
            e.printStackTrace();
            return dateTimeString;
        }
    }


    private void setTextColorForLevel(String level, TextView... textViews) {
        int textColor;
        switch (level) {
            case "Level 1":
                textColor = Color.parseColor("#003700"); // Dark Green for Level 1
                break;
            case "Level 2":
                textColor = Color.parseColor("#BDA600"); // Dark Yellow for Level 2
                break;
            case "Level 3":
                textColor = Color.parseColor("#C65D00"); // Dark Orange for Level 3
                break;
            case "Level 4":
                textColor = Color.parseColor("#5C0000"); // Dark Red for Level 4
                break;
            default:
                textColor = Color.BLACK; // Default color
                break;
        }

        // Set the text color for the specified TextViews
        for (TextView textView : textViews) {
            textView.setTextColor(textColor);
        }
    }

    private void setBackgroundWithRadius(View itemView, String level) {
        GradientDrawable drawable = new GradientDrawable();

        drawable.setCornerRadius(14f);


        switch (level) {
            case "Level 1":
                drawable.setColor(Color.parseColor("#FFFFFF"));
                break;
            case "Level 2":
                drawable.setColor(Color.parseColor("#FFFFFF"));
                break;
            case "Level 3":
                drawable.setColor(Color.parseColor("#FFFFFF"));
                break;
            default:
                drawable.setColor(Color.TRANSPARENT);
                break;
        }



        itemView.setBackground(drawable);
    }




    private void addViewWithMargin(View itemView, int margin) {
        linearLayout.addView(itemView);

        // Add margin to the bottom of the item view
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
        layoutParams.bottomMargin = margin;
        itemView.setLayoutParams(layoutParams);
    }

    // Method to parse date and time from a string in ISO 8601 format
    private String[] parseDateTime(String dateTimeString) {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        try {
            Date date = isoFormat.parse(dateTimeString);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

            // Set timezone to UTC for displaying time
            timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            return new String[]{dateFormat.format(date), timeFormat.format(date)};
        } catch (ParseException e) {
            e.printStackTrace();
            return new String[]{"", ""};
        }
    }



    public class ArduinoData {
        public String description;
        public double latitude;
        public double longitude;
        public String vibrateAt;
        public String level;
        public String address;
        public String uniqueId;

        public ArduinoData(String description, double latitude, double longitude, String vibrateAt, String level, String address, String uniqueId) {
            this.description = description;
            this.latitude = latitude;
            this.longitude = longitude;
            this.vibrateAt = vibrateAt;
            this.level = level;
            this.address = address;
            this.uniqueId = uniqueId;
        }
    }


    public class TheftDetails {
        public double currentlatitude;
        public double currentlongitude;
        public String description;
        public String uniqueId;
        public String level;
        public String address;
        public String happenedAt;

        public TheftDetails(double currentlatitude, double currentlongitude, String description, String uniqueId, String level, String address, String happenedAt) {
            this.currentlatitude = currentlatitude;
            this.currentlongitude = currentlongitude;
            this.description = description;
            this.uniqueId = uniqueId;
            this.level = level;
            this.address = address;
            this.happenedAt = happenedAt;
        }
    }


}
