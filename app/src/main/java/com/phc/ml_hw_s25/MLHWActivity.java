package com.phc.ml_hw_s25;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nex3z.fingerpaintview.FingerPaintView;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class MLHWActivity extends AppCompatActivity {
    private Interpreter interpreter;
    private FingerPaintView fingerPaintView;
    private Button buttonDetect, buttonClear, buttonReset;
    private TextView tvInstruction, tvResult, tvTimeCoat;

    // PIN authentication variables
    private static final String PREFS_NAME = "PINPrefs";
    private static final String KEY_PIN = "stored_pin";
    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCKOUT_DURATION = 15000; // 15 seconds
    private static final int PIN_LENGTH = 4;

    private enum AppState {
        SET_PIN, CONFIRM_PIN, VERIFY_PIN, LOCKED
    }

    private AppState currentState = AppState.SET_PIN;
    private String pendingPin = "";
    private String enteredPin = "";
    private int failedAttempts = 0;
    private long lockoutEndTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mlhw);

        fingerPaintView = findViewById(R.id.finger_paint_view);
        buttonDetect = findViewById(R.id.button_detect);
        buttonClear = findViewById(R.id.button_clear);
        buttonReset = findViewById(R.id.button_reset);
        tvInstruction = findViewById(R.id.tv_instruction);
        tvResult = findViewById(R.id.tv_result);
        tvTimeCoat = findViewById(R.id.tv_time_coat);

        // Load the MNIST model
        try {
            interpreter = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
            throw new RuntimeException(e);
        }

        // Check if PIN is already set
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(KEY_PIN)) {
            currentState = AppState.VERIFY_PIN;
            tvInstruction.setText("Enter your PIN (0/" + PIN_LENGTH + ")");
        } else {
            currentState = AppState.SET_PIN;
            tvInstruction.setText("Draw digit 1 of " + PIN_LENGTH + " to set your PIN");
        }

        buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleDetectClick();
            }
        });

        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fingerPaintView.clear();
                tvResult.setText("");
                tvTimeCoat.setText("");
            }
        });

        buttonReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear saved PIN and restart setup
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().clear().apply();

                pendingPin = "";
                enteredPin = "";
                failedAttempts = 0;
                currentState = AppState.SET_PIN;

                fingerPaintView.clear();
                tvResult.setText("");
                tvTimeCoat.setText("");
                tvInstruction.setText("Draw digit 1 of " + PIN_LENGTH + " to set your PIN");
                buttonDetect.setEnabled(true);
                buttonClear.setEnabled(true);

                Toast.makeText(MLHWActivity.this, "PIN reset. Please set a new PIN.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleDetectClick() {
        // Check if locked out
        if (currentState == AppState.LOCKED) {
            long remainingTime = (lockoutEndTime - System.currentTimeMillis()) / 1000;
            Toast.makeText(this, "Locked out. Try again in " + remainingTime + " seconds", Toast.LENGTH_SHORT).show();
            return;
        }

        long startTime = System.currentTimeMillis();

        // Get the drawn bitmap and classify it
        Bitmap bitmap = fingerPaintView.exportToBitmap(28, 28);
        int prediction = doInference(bitmap);

        long endTime = System.currentTimeMillis();
        tvTimeCoat.setText("Time: " + (endTime - startTime) + " ms");
        tvResult.setText("Detected: " + prediction);

        // Handle based on current state
        switch (currentState) {
            case SET_PIN:
                showDigitConfirmation(prediction, true);
                break;

            case CONFIRM_PIN:
                // This state is handled by dialog
                break;

            case VERIFY_PIN:
                showDigitConfirmation(prediction, false);
                break;
        }
    }

    private void showDigitConfirmation(final int digit, final boolean isSettingPin) {
        new AlertDialog.Builder(this)
            .setTitle("Confirm Digit")
            .setMessage("You drew: " + digit + "\nIs this correct?")
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (isSettingPin) {
                        pendingPin += digit;
                        fingerPaintView.clear();
                        tvResult.setText("PIN so far: " + pendingPin);

                        if (pendingPin.length() < PIN_LENGTH) {
                            tvInstruction.setText("Draw digit " + (pendingPin.length() + 1) + " of " + PIN_LENGTH + " to set your PIN");
                        } else {
                            showFinalPinConfirmationDialog();
                        }
                    } else {
                        // Verifying PIN
                        enteredPin += digit;
                        fingerPaintView.clear();
                        tvResult.setText("Entered: " + enteredPin);

                        if (enteredPin.length() < PIN_LENGTH) {
                            tvInstruction.setText("Enter your PIN (" + enteredPin.length() + "/" + PIN_LENGTH + ")");
                        } else {
                            verifyPin();
                        }
                    }
                }
            })
            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    fingerPaintView.clear();
                    if (isSettingPin) {
                        tvResult.setText("PIN so far: " + pendingPin);
                    } else {
                        tvResult.setText("Entered: " + enteredPin);
                    }
                    Toast.makeText(MLHWActivity.this, "Please draw the digit again", Toast.LENGTH_SHORT).show();
                }
            })
            .setCancelable(false)
            .show();
    }

    private void showFinalPinConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Confirm PIN")
            .setMessage("Your complete PIN is: " + pendingPin + "\nIs this correct?")
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Save PIN and move to verification
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(KEY_PIN, pendingPin).apply();

                    currentState = AppState.VERIFY_PIN;
                    enteredPin = "";
                    tvInstruction.setText("Enter your PIN (0/" + PIN_LENGTH + ")");
                    fingerPaintView.clear();
                    tvResult.setText("");
                    tvTimeCoat.setText("");

                    Toast.makeText(MLHWActivity.this, "PIN set successfully!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    pendingPin = "";
                    fingerPaintView.clear();
                    tvResult.setText("");
                    tvTimeCoat.setText("");
                    tvInstruction.setText("Draw digit 1 of " + PIN_LENGTH + " to set your PIN");
                    Toast.makeText(MLHWActivity.this, "Please draw your PIN again", Toast.LENGTH_SHORT).show();
                }
            })
            .setCancelable(false)
            .show();
    }

    private void verifyPin() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String storedPin = prefs.getString(KEY_PIN, "");

        if (enteredPin.equals(storedPin)) {
            // Successful authentication
            failedAttempts = 0;
            Toast.makeText(this, "PIN correct! Access granted.", Toast.LENGTH_LONG).show();
            tvInstruction.setText("Authenticated successfully!");
            tvResult.setText("Welcome!");
            buttonDetect.setEnabled(false);
            buttonClear.setEnabled(false);
        } else {
            // Failed attempt
            failedAttempts++;

            if (failedAttempts >= MAX_ATTEMPTS) {
                // Lock out the user
                currentState = AppState.LOCKED;
                lockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION;

                tvInstruction.setText("Too many failed attempts. Locked for 15 seconds.");
                buttonDetect.setEnabled(false);
                fingerPaintView.clear();
                tvResult.setText("");
                tvTimeCoat.setText("");

                // Schedule unlock after 15 seconds
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        currentState = AppState.VERIFY_PIN;
                        failedAttempts = 0;
                        enteredPin = "";
                        tvInstruction.setText("Enter your PIN (0/" + PIN_LENGTH + ")");
                        buttonDetect.setEnabled(true);
                        Toast.makeText(MLHWActivity.this, "You can try again now", Toast.LENGTH_SHORT).show();
                    }
                }, LOCKOUT_DURATION);
            } else {
                Toast.makeText(this, "Incorrect PIN. Attempts remaining: " + (MAX_ATTEMPTS - failedAttempts),
                               Toast.LENGTH_SHORT).show();
                enteredPin = "";
                fingerPaintView.clear();
                tvResult.setText("");
                tvTimeCoat.setText("");
                tvInstruction.setText("Enter your PIN (0/" + PIN_LENGTH + ")");
            }
        }
    }

    private int doInference(Bitmap bitmap) {
        // Convert bitmap to grayscale float array
        float[][] input = new float[1][784]; // 28x28 = 784

        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < 28; x++) {
                int pixel = bitmap.getPixel(x, y);

                // Extract RGB channels and calculate grayscale using luminance formula
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                int gray = (r + g + b) / 3;

                // MNIST expects white digit on black background
                // FingerPaintView draws black on white, so invert
                float normalized = 1.0f - (gray / 255.0f);
                input[0][y * 28 + x] = normalized;
            }
        }

        // Output array for 10 classes (digits 0-9)
        float[][] output = new float[1][10];

        // Run inference
        interpreter.run(input, output);

        // Find the class with highest probability
        int maxIndex = 0;
        float maxProb = output[0][0];
        for (int i = 1; i < 10; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        // Load MNIST model (you'll need to add mnist.tflite to assets)
        AssetFileDescriptor assetFileDescriptor =
                this.getAssets().openFd("mnist.tflite");

        FileInputStream fileInputStream =
                new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();

        long startOffset = assetFileDescriptor.getStartOffset();
        long length = assetFileDescriptor.getLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
    }
}