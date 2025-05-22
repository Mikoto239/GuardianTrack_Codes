package com.example.guardiantrack;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.Toast;

public class CustomEditTextID extends androidx.appcompat.widget.AppCompatEditText {

    public CustomEditTextID(Context context) {
        super(context);
        init();
    }

    public CustomEditTextID(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomEditTextID(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No implementation needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No implementation needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Check and enforce the format HH:HH:HH:HH:HH:HH
                if (s.length() == 2 || s.length() == 5 || s.length() == 8 || s.length() == 11 || s.length() == 14) {
                    // If user inputting a colon, just append
                    if (s.charAt(s.length() - 1) == ':') {
                        return;
                    }
                    // If not, append a colon after every two characters
                    s.append(':');
                }

                // Check for special characters and remove them
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != ':') {
                        // Special character found, remove it
                        s.delete(i, i + 1);
                        Toast.makeText(getContext(), "Special characters cannot be inputted", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Calculate the total number of characters excluding the static colons
                int totalLength = s.length();

                // Check if the total length exceeds 17 characters (6 groups of 2 characters and 5 colons)
                if (totalLength > 17) {
                    // If it does, remove the excess characters
                    s.delete(17, totalLength);
                }
            }
        });
    }
}
