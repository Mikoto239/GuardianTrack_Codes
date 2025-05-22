package com.example.guardiantrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationFragment extends Fragment {
    private TextView emptyView;
    private static ListView listViewNotifications;
    private String name;
    private String email;

    private RequestQueue requestQueue;
    private String uniqueId;
    private ProgressBar loadingProgressBar;
    private List<PinLocationData> dataList = new ArrayList<>();

    private static final String PREFS_NAME = "MyPrefs";
    private String token = "";
    private static final String TOKEN_KEY = "TOKEN";

    public NotificationFragment() {
        // Required empty public constructor
    }

    public static NotificationFragment newInstance() {
        return new NotificationFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_notification, container, false);
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        listViewNotifications = rootView.findViewById(R.id.listViewNotifications);
        emptyView = rootView.findViewById(R.id.textViewEmpty);
        loadingProgressBar = rootView.findViewById(R.id.loadingProgressBar);

        requestQueue = Volley.newRequestQueue(getActivity()); // Initialize the requestQueue

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

                            fetchPinHistory();
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

    private void fetchPinHistory() {
        String url = "https://capstone2-16.onrender.com/api/getpinhistory";
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    try {
                        JSONArray pinHistory = response.getJSONArray("pinhistory");


                        if (pinHistory.length() == 0) {
                            Log.d("fetchPinHistory", "Pin history is empty");
                            hideLoadingScreen();
                            emptyView.setVisibility(View.VISIBLE);
                            return;
                        }

                        for (int i = 0; i < pinHistory.length(); i++) {
                            JSONObject pinData = pinHistory.getJSONObject(i);
                            double latitude = pinData.getDouble("currentlatitude");
                            double longitude = pinData.getDouble("currentlongitude");
                            String pinAt = pinData.getString("pinAt");
                            String address = pinData.getString("address");
                            dataList.add(new PinLocationData(latitude, longitude, pinAt, address));
                        }

                        hideLoadingScreen();
                        displayPinHistory();

                        // Check if dataList is not empty, then hide the empty view
                        if (!dataList.isEmpty()) {
                            emptyView.setVisibility(View.GONE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    handleError(error);
                    Log.e("fetchPinHistory", "Error fetching pin history: " + error.getMessage());
                }) {
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

    private void displayPinHistory() {
        NotificationAdapter adapter = new NotificationAdapter(getActivity(), dataList);
        listViewNotifications.setAdapter(adapter);
    }

    private void handleError(VolleyError error) {
        hideLoadingScreen();
        if (error != null && error.networkResponse != null && error.networkResponse.data != null) {
            String errorMessage = new String(error.networkResponse.data);
            Toast.makeText(requireContext(), "No record has been found!", Toast.LENGTH_SHORT).show();
        } else if (error instanceof TimeoutError) {
            Toast.makeText(requireContext(), "Network error: Timeout occurred", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Unknown error occurred", Toast.LENGTH_SHORT).show();
        }
    }

    static class PinLocationData {
        double latitude;
        double longitude;
        String pinAt;
        String date;
        String time;
        String address;

        public PinLocationData(double latitude, double longitude, String pinAt, String address) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address;
            this.pinAt = pinAt;
            separateDateTime(pinAt);
        }

        private void separateDateTime(String pinAt) {
            // Split the pinAt string using 'T' as the separator
            String[] parts = pinAt.split("T");
            if (parts.length == 2) {
                // Date is the first part
                this.date = parts[0];

                // Convert the date into "Month Day, Year" format (e.g., January 1, 2024)
                String[] dateParts = this.date.split("-");
                int month = Integer.parseInt(dateParts[1]);
                String monthName = getMonthName(month); // Convert month number to month name
                this.date = monthName + " " + dateParts[2] + ", " + dateParts[0]; // Month Day, Year format

                // Time is the second part
                String timePart = parts[1];

                // Check if timePart contains timezone information
                int timezoneIndex = timePart.indexOf("+");
                if (timezoneIndex != -1) {
                    // Extract time until the timezone information
                    this.time = timePart.substring(0, timezoneIndex);
                } else {
                    // If timezone information is not present, use the whole time part
                    this.time = timePart;
                }

                // Check if time contains milliseconds or 'Z' (UTC)
                String[] timeAndMillis = this.time.split("\\.");
                String timeWithoutMillis = timeAndMillis[0]; // Time without milliseconds

                // If the time includes 'Z', strip it out (for UTC)
                if (timeWithoutMillis.endsWith("Z")) {
                    timeWithoutMillis = timeWithoutMillis.substring(0, timeWithoutMillis.length() - 1);
                }

                // Split the time into hours, minutes, and seconds
                String[] timeParts = timeWithoutMillis.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                int second = Integer.parseInt(timeParts[2]);

                // Convert military time to standard time with AM/PM
                String amPm;
                if (hour == 0) {
                    hour = 12;
                    amPm = "AM";
                } else if (hour < 12) {
                    amPm = "AM";
                } else {
                    amPm = "PM";
                    if (hour > 12) {
                        hour -= 12;
                    }
                }

                // Update time with standard time format (no milliseconds)
                this.time = String.format("%d:%02d:%02d %s", hour, minute, second, amPm);
            } else {
                // If the format is not as expected, set date and time to empty strings
                this.date = "";
                this.time = "";
            }
        }

        // Helper method to get month name from month number
        private String getMonthName(int month) {
            String[] months = {
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
            };
            if (month >= 1 && month <= 12) {
                return months[month - 1]; // Adjust because month is 1-based
            }
            return "Invalid Month"; // In case an invalid month number is provided
        }


    }
        static class NotificationAdapter extends ArrayAdapter<PinLocationData> {
        private Context context;
        private List<PinLocationData> dataList;
        private int expandedPosition = -1; // Track expanded position
        private GoogleMap currentGoogleMap; // Track current GoogleMap instance
        private Marker currentMarker; // Track current Marker on the map

        public NotificationAdapter(Context context, List<PinLocationData> dataList) {
            super(context, R.layout.notification_item, dataList);
            this.context = context;
            this.dataList = dataList;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.notification_item, parent, false);
                holder = new ViewHolder();
                holder.mapContainer = convertView.findViewById(R.id.map_container);
                holder.addressTextView = convertView.findViewById(R.id.address);
                holder.dateTextView = convertView.findViewById(R.id.textViewDate);
                holder.timeTextView = convertView.findViewById(R.id.textViewTime);
                holder.showMapButton = convertView.findViewById(R.id.showonmap);
                holder.closeMapButton = convertView.findViewById(R.id.closeMap);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            PinLocationData pinData = dataList.get(position);

            // Set text data
            // Create SpannableStrings for each TextView and bold the "Address", "Date", and "Time" words
            SpannableString addressText = new SpannableString("Address: " + pinData.address);
            SpannableString dateText = new SpannableString("Date: " + pinData.date);
            SpannableString timeText = new SpannableString("Time: " + pinData.time);

// Bold only the words "Address", "Date", and "Time"
            addressText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            dateText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            timeText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

// Set the formatted text to the TextViews
            holder.addressTextView.setText(addressText);
            holder.dateTextView.setText(dateText);
            holder.timeTextView.setText(timeText);

            // Set initial visibility of map container based on expanded position
            holder.mapContainer.setVisibility(position == expandedPosition ? View.VISIBLE : View.GONE);

            // Set button click listener to show or hide the map
            holder.showMapButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (expandedPosition == position) {
                        // Clicking on already expanded item, hide map
                        holder.mapContainer.setVisibility(View.GONE);
                        holder.showMapButton.setText("Show on Map");
                        expandedPosition = -1; // Reset expanded position
                        clearMap(); // Clear map state
                    } else {
                        // Collapse previously expanded item if any
                        collapsePreviousItem();

                        // Expand clicked item, show map
                        expandedPosition = position;
                        holder.showMapButton.setText("Unpin"); // Change button text to "Unpin"
                        notifyDataSetChanged(); // Notify adapter to redraw list
                    }
                }
            });

            // Set close button click listener
            holder.closeMapButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Hide map
                    holder.mapContainer.setVisibility(View.GONE);
                    holder.showMapButton.setText("Show on Map");
                    expandedPosition = -1; // Reset expanded position
                    clearMap(); // Clear map state
                    notifyDataSetChanged(); // Notify adapter to redraw list
                }
            });

            // Setup map if this item is expanded
            if (position == expandedPosition) {
                setupMap(holder.mapContainer, pinData);
            }

            return convertView;
        }

        private void collapsePreviousItem() {
            if (expandedPosition != -1) {
                // Find the view holder of the previously expanded item
                View previousExpandedView = getViewByPosition(expandedPosition);
                if (previousExpandedView != null) {
                    ViewHolder previousHolder = (ViewHolder) previousExpandedView.getTag();
                    if (previousHolder != null) {
                        // Hide map and reset button text
                        previousHolder.mapContainer.setVisibility(View.GONE);
                        previousHolder.showMapButton.setText("Show on Map");
                    }
                }
                expandedPosition = -1; // Reset expanded position
                clearMap(); // Clear map state
            }
        }

            private void setupMap(FrameLayout mapContainer, PinLocationData pinData) {
                SupportMapFragment mapFragment = new SupportMapFragment();
                FragmentTransaction transaction = ((FragmentActivity) context).getSupportFragmentManager().beginTransaction();
                transaction.replace(mapContainer.getId(), mapFragment);
                transaction.commit();

                mapFragment.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        googleMap.getUiSettings().setMapToolbarEnabled(false);
                        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                        clearMap();

                        MarkerOptions markerOptions = new MarkerOptions();
                        LatLng pinLocation = new LatLng(pinData.latitude, pinData.longitude);
                        markerOptions.position(pinLocation);
                        markerOptions.title(pinData.date + " " + pinData.time + " - " + pinData.address);

                        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(getScaledBitmap(context, R.drawable.park, 150));
                        markerOptions.icon(icon);

                        currentMarker = googleMap.addMarker(markerOptions);
                        currentGoogleMap = googleMap;
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pinLocation, 19f));
                    }
                });
            }


            private Bitmap getScaledBitmap(Context context, int drawableResId, int desiredWidth) {
                Bitmap originalBitmap = BitmapFactory.decodeResource(context.getResources(), drawableResId);
                int originalWidth = originalBitmap.getWidth();
                int originalHeight = originalBitmap.getHeight();
                float aspectRatio = (float) originalHeight / originalWidth;
                int scaledHeight = Math.round(desiredWidth * aspectRatio);
                return Bitmap.createScaledBitmap(originalBitmap, desiredWidth, scaledHeight, false);
            }

            private void clearMap() {
            if (currentMarker != null) {
                currentMarker.remove(); // Remove current marker from the map
                currentMarker = null; // Reset current marker
                currentGoogleMap = null; // Reset current GoogleMap instance
            }
        }

        private View getViewByPosition(int position) {
            int firstVisiblePosition = listViewNotifications.getFirstVisiblePosition();
            int lastVisiblePosition = listViewNotifications.getLastVisiblePosition();

            if (position < firstVisiblePosition || position > lastVisiblePosition) {
                return null;
            }

            return listViewNotifications.getChildAt(position - firstVisiblePosition);
        }

        static class ViewHolder {
            FrameLayout mapContainer;
            TextView addressTextView;
            TextView dateTextView;
            TextView timeTextView;
            Button showMapButton;
            Button closeMapButton;
        }
    }

}
