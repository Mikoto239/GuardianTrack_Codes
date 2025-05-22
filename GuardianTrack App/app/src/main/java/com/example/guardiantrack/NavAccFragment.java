package com.example.guardiantrack;

import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NavAccFragment extends Fragment {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String USER_ID_KEY = "uniqueId";
    private static final String PHONE_NUMBER_KEY = "cellphonenumber";
    private static final String TOKEN_KEY = "TOKEN";

    private SharedPreferences sharedPreferences;
    private String token = "";

    private TextView nameTextView, emailTextView;
    private ImageView profileImageView;
    private CustomEditTextID idEditText;
    private CustomEditTextNum phoneNumberEditText;
    private Button registerButton, updateButton;

    public NavAccFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nav_acc, container, false);

        // Initialize SharedPreferences
        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        token = sharedPreferences.getString(TOKEN_KEY, null);

        // Initialize views
        nameTextView = view.findViewById(R.id.accName);
        emailTextView = view.findViewById(R.id.accEmail);
        profileImageView = view.findViewById(R.id.accImage);
        idEditText = view.findViewById(R.id.accUid);
        registerButton = view.findViewById(R.id.Regbutton);
//        updateButton = view.findViewById(R.id.updateButton);
//        phoneNumberEditText = view.findViewById(R.id.accPhoneNumber);
        // Load user information from Google Sign-In if available
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getActivity());
        if (account != null) {
            String personName = account.getDisplayName();
            String personEmail = account.getEmail();
            Uri personPhoto = account.getPhotoUrl();

            if (personName != null) {
                nameTextView.setText("Name: " + personName);
            }

            if (personEmail != null) {
                emailTextView.setText("Email: " + personEmail);
            }

            if (personPhoto != null) {
                Picasso.get().load(personPhoto)
                        .placeholder(R.drawable.error)  // Placeholder in case of loading failure
                        .error(R.drawable.error)        // Error image if loading fails
                        .transform(new CircleTransform())
                        .into(profileImageView);
            } else {
                profileImageView.setImageResource(R.drawable.error);
            }
        }

        // Check user registration status on fragment create
        checkUserRegistration();

        // Set onClickListener for Register Button
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRegisterConfirmationDialog();
            }
        });

        // Set onClickListener for Update Button
//        updateButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!phoneNumberEditText.isEnabled()) {
//                    enableEditing();
//                } else {
//                    showUpdateConfirmationDialog();
//                }
//            }
//        });

        return view;
    }

    // Method to check network availability
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    // Method to check user registration status
    private void checkUserRegistration() {
        // Step 1: Check network availability
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Step 2: Build the JSON request object
        String url = "https://capstone2-16.onrender.com/api/checkuser";
        RequestQueue queue = Volley.newRequestQueue(getContext());

        JSONObject postData = new JSONObject();
        try {
            postData.put("token", token);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Step 3: Construct the JsonObjectRequest with Authorization header
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    try {
                        if (response.has("uniqueId") ) {
                            String uniqueId = response.getString("uniqueId");
//                            String phoneNumber = response.getString("cellphonenumber");

                            idEditText.setText(uniqueId);
//                            phoneNumberEditText.setText(phoneNumber);

                            // User is registered, disable fields and show update button
                            idEditText.setEnabled(false);
//                            phoneNumberEditText.setEnabled(false);
//                            updateButton.setVisibility(View.VISIBLE);
                            registerButton.setVisibility(View.GONE);
                        } else {
                            // User is not registered, enable fields and show register button
                            idEditText.setEnabled(true);
//                            phoneNumberEditText.setEnabled(true);
//                            updateButton.setVisibility(View.GONE);
                            registerButton.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String errorMessage = new String(error.networkResponse.data);
//                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                        Log.d("nav acc", "Token is null.");
                    } else {
                        Toast.makeText(requireContext(), "Failed to check user registration: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
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


    private void registerUser() {
        String uniqueId = idEditText.getText().toString().trim();
//        String cellphonenumber = phoneNumberEditText.getText().toString().trim();
//
//        // Validate input fields
//        if (uniqueId.isEmpty() || cellphonenumber.isEmpty() || cellphonenumber.length() != 11) {
//            Toast.makeText(requireContext(), "Please input a valid ID and a 11-digit phone number", Toast.LENGTH_SHORT).show();
//            return;
//        }

        // Perform user registration API call
        performUserRegistration(uniqueId);
    }

    // Method to show registration confirmation dialog
    private void showRegisterConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setMessage("Are you sure you want to register?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        registerUser(); // Call registerUser method on positive button click
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // Dismiss dialog on negative button click
                    }
                });
        builder.create().show(); // Create and show the dialog
    }

    private void performUserRegistration(String uniqueId) {
        String url = "https://capstone2-16.onrender.com/api/userregister";
        String name = nameTextView.getText().toString().replace("Name: ", "");
        String email = emailTextView.getText().toString().replace("Email: ", "");
        RequestQueue queue = Volley.newRequestQueue(getContext());

        JSONObject postData = new JSONObject();
        try {
            postData.put("uniqueId", uniqueId);
//            postData.put("cellphonenumber", cellphonenumber);
            postData.put("name", name);
            postData.put("email", email);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    // Handle registration success
                    Toast.makeText(requireContext(), "Successfully registered.", Toast.LENGTH_SHORT).show();

                    // Extract token from response
                    try {
                        String token = response.getString("token");
                        // Save token to SharedPreferences
                        saveTokenToSharedPreferences(token);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "Failed to get token", Toast.LENGTH_SHORT).show();
                    }

                    // Save phone number to SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
//                    editor.putString(PHONE_NUMBER_KEY, cellphonenumber);
                    editor.apply();

                    // Disable fields and update button visibility
                    idEditText.setEnabled(false);
//                    phoneNumberEditText.setEnabled(false);
//                    updateButton.setVisibility(View.VISIBLE);
                    registerButton.setVisibility(View.GONE);
                },
                error -> {
                    // Handle registration error
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

        queue.add(jsonObjectRequest);
    }

    private void saveTokenToSharedPreferences(String token) {
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(TOKEN_KEY, token);
        editor.apply();
    }


}

    // Method to enable editing of phone number
//    private void enableEditing() {
//        phoneNumberEditText.setEnabled(true);
//        phoneNumberEditText.requestFocus();
//        updateButton.setText("Save Number");
//    }

    // Method to show update confirmation dialog
//    private void showUpdateConfirmationDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
//        builder.setMessage("Are you sure you want to save the new number?")
//                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        saveNewPhoneNumber(); // Call saveNewPhoneNumber method on positive button click
//                    }
//                })
//                .setNegativeButton("No", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss(); // Dismiss dialog on negative button click
//                    }
//                });
//        builder.create().show(); // Create and show the dialog
//    }
//
//    // Method to save updated phone number
//    private void saveNewPhoneNumber() {
//        String newPhoneNumber = phoneNumberEditText.getText().toString().trim();
//
//        // Validate new phone number format
//        if (newPhoneNumber.isEmpty() || newPhoneNumber.length() != 11) {
//            Toast.makeText(requireContext(), "Please input a valid 11-digit phone number", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Perform phone number update API call
//        performPhoneNumberUpdate(newPhoneNumber);
//    }
//
//    // Method to perform phone number update API call
//    private void performPhoneNumberUpdate(String newPhoneNumber) {
//        // Step 1: Check network availability
//        if (!isNetworkAvailable()) {
//            Toast.makeText(requireContext(), "No internet connection. Please check your network settings.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Step 2: Retrieve token from SharedPreferences
//        String token = sharedPreferences.getString(TOKEN_KEY, null);
//        if (token == null) {
//            Toast.makeText(requireContext(), "Token not found. Please log in again.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Log the token for debugging
//        Log.d("performPhoneNumberUpdate", "Token: " + token);
//
//        // Step 3: Build the JSON request object
//        String url = "https://capstone2-16.onrender.com/api/updateusernumber";
//        RequestQueue queue = Volley.newRequestQueue(getContext());
//
//        JSONObject postData = new JSONObject();
//        try {
//            postData.put("token", token);
//            postData.put("phonenumber", newPhoneNumber);
//        } catch (JSONException e) {
//            e.printStackTrace();
//            Toast.makeText(requireContext(), "Failed to create request data", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Step 4: Construct the JsonObjectRequest with Authorization header
//        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, postData,
//                response -> {
//                    try {
//                        if (response.has("message")) {
//                            String message = response.getString("message");
//                            boolean success = response.getBoolean("success");
//
//                            if (success) {
//                                // Update successful, disable fields and update button text
//                                phoneNumberEditText.setEnabled(false);
//                                updateButton.setText("Update Number");
//                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
//                            } else {
//                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
//                            }
//                        } else {
//                            Toast.makeText(requireContext(), "Unexpected response from server", Toast.LENGTH_SHORT).show();
//                        }
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                        Toast.makeText(requireContext(), "Failed to parse response", Toast.LENGTH_SHORT).show();
//                    }
//                },
//                error -> {
//                    if (error.networkResponse != null && error.networkResponse.data != null) {
//                        String errorMessage = new String(error.networkResponse.data);
//                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
//                    } else {
//                        Toast.makeText(requireContext(), "Failed to update phone number: " + error.getMessage(), Toast.LENGTH_SHORT).show();
//                    }
//
//                    // Log the error for debugging
//                    Log.e("performPhoneNumberUpdate", "Error: " + error.getMessage());
//                }) {
//            @Override
//            public Map<String, String> getHeaders() {
//                Map<String, String> headers = new HashMap<>();
//                headers.put("Content-Type", "application/json");
//                headers.put("Authorization", "Bearer " + token); // Set Authorization header with Bearer token
//                return headers;
//            }
//        };
//
//        // Step 5: Set timeout and retry policy
//        int TIMEOUT_MS = 5000; // Timeout in milliseconds
//        RetryPolicy retryPolicy = new DefaultRetryPolicy(TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
//        jsonObjectRequest.setRetryPolicy(retryPolicy);
//
//        // Step 6: Add the request to the queue
//        queue.add(jsonObjectRequest);
//    }
//}
