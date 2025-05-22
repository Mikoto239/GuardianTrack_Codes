package com.example.guardiantrack;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.Toast;

public class CustomEditTextNum extends androidx.appcompat.widget.AppCompatEditText {
    boolean isValidInput = true;
    private static final int REQUIRED_DIGITS = 11;

    public CustomEditTextNum(Context context) {
        super(context);
        init();
    }

    public CustomEditTextNum(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomEditTextNum(Context context, AttributeSet attrs, int defStyleAttr) {
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
                String input = s.toString();
                StringBuilder numbersOnly = new StringBuilder();

                // Iterate over each character in the input
                for (int i = 0; i < input.length(); i++) {
                    char c = input.charAt(i);

                    // Check if the character is a digit
                    if (Character.isDigit(c)) {
                        // If it is a digit, append it to the numbersOnly StringBuilder
                        numbersOnly.append(c);
                    } else {
                        isValidInput = false;
                        // If it is not a digit, skip adding it
                    }
                }

                // Display toast message if input is not valid
                if (!isValidInput) {
                    Toast.makeText(getContext(), "Please enter numbers only", Toast.LENGTH_SHORT).show();
                }

                // Limit the input to 11 numbers
                if (numbersOnly.length() > REQUIRED_DIGITS) {
                    numbersOnly.delete(REQUIRED_DIGITS, numbersOnly.length());
                }

                // Update the EditText with the modified text
                if (!input.equals(numbersOnly.toString())) {
                    s.replace(0, s.length(), numbersOnly.toString());
                }
            }
        });
        setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !isValidInput) {
                Toast.makeText(getContext(), "Please enter numbers only", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
