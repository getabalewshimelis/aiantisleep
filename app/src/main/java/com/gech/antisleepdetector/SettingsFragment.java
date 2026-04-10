package com.gech.antisleepdetector;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private TextView aiResponseText;
    private TextView predictionResultText;
    private Button btnGetAdvice;
    private Button btnPickTime;

    private GenerativeModelFutures modelFutures;
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        aiResponseText = view.findViewById(R.id.ai_response_text);
        predictionResultText = view.findViewById(R.id.prediction_result_text);
        btnGetAdvice = view.findViewById(R.id.btn_get_ai_advice);
        btnPickTime = view.findViewById(R.id.btn_pick_wake_time);

        // Renamed to Smart Antisleep AI in logic and UI
        initSmartAI();

        btnGetAdvice.setOnClickListener(v -> getAiSleepAdvice());
        btnPickTime.setOnClickListener(v -> showTimePicker());

        // Load initial advice
        getAiSleepAdvice();

        return view;
    }

    private void initSmartAI() {
        // gemini-1.5-flash is the recommended model for speed and cost.
        // If 404 occurs, it might be due to the API version or region.
        GenerativeModel gm = new GenerativeModel(
                "gemini-1.5-flash", 
                "AIzaSyA_1u7Ssh1F75-mNFJKhK7ohCOmQwj-iM4"
        );
        modelFutures = GenerativeModelFutures.from(gm);
    }

    private void getAiSleepAdvice() {
        if (aiResponseText == null) return;
        aiResponseText.setText("Smart Antisleep AI is thinking...");
        
        Content content = new Content.Builder()
                .addText("Give me a short, 2-sentence tip for healthy sleep and staying alert while driving.")
                .build();

        try {
            ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);

            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String text = result.getText();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> aiResponseText.setText(text));
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String error = t.getMessage();
                            // Fallback if model is not found or region restricted
                            if (error != null && error.contains("404")) {
                                aiResponseText.setText("AI is adjusting... Tips: Take a 20-min nap if you feel drowsy while driving.");
                                // Try switching to gemini-pro automatically for the next call
                                GenerativeModel fallbackGm = new GenerativeModel("gemini-pro", "AIzaSyA_1u7Ssh1F75-mNFJKhK7ohCOmQwj-iM4");
                                modelFutures = GenerativeModelFutures.from(fallbackGm);
                            } else {
                                aiResponseText.setText("AI offline: Keep your cabin cool and well-ventilated to stay awake.");
                            }
                        });
                    }
                }
            }, executor);
        } catch (Exception e) {
            aiResponseText.setText("Smart AI is resting. Try again soon!");
        }
    }

    private void showTimePicker() {
        Calendar mcurrentTime = Calendar.getInstance();
        int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
        int minute = mcurrentTime.get(Calendar.MINUTE);

        TimePickerDialog mTimePicker = new TimePickerDialog(getContext(), (timePicker, selectedHour, selectedMinute) -> {
            predictSleepTime(selectedHour, selectedMinute);
        }, hour, minute, false);
        mTimePicker.setTitle("Select Wake-up Time");
        mTimePicker.show();
    }

    private void predictSleepTime(int wakeHour, int wakeMinute) {
        if (predictionResultText == null) return;
        predictionResultText.setText("Smart Antisleep AI is calculating...");
        
        String prompt = String.format(Locale.US, 
                "I want to wake up at %02d:%02d. Based on 90-minute sleep cycles, " +
                "what are the 3 best times for me to fall asleep tonight to feel rested? " +
                "Keep the answer very short and list only the times.", 
                wakeHour, wakeMinute);

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        try {
            ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);

            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String text = result.getText();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            predictionResultText.setText("Suggested Sleep Times:\n" + text);
                            Toast.makeText(getContext(), "Smart AI Prediction Updated", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> predictionResultText.setText("AI prediction failed. Goal: Sleep in 90-min blocks."));
                    }
                }
            }, executor);
        } catch (Exception e) {
            predictionResultText.setText("Prediction Error.");
        }
    }
}
