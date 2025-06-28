// MainActivity.java
package com.example.aichatbot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private EditText inputField;
    private Button sendButton;
    private TextView responseView;
    private ProgressBar loadingIndicator;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private static final String HISTORY_KEY = "chat_history";
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputField = findViewById(R.id.inputField);
        sendButton = findViewById(R.id.sendButton);
        responseView = findViewById(R.id.responseView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        Button micButton = findViewById(R.id.micButton);

        prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE);
        responseView.setText(prefs.getString(HISTORY_KEY, ""));

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        sendButton.setOnClickListener(v -> {
            String userMessage = inputField.getText().toString().trim();
            if (userMessage.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter a message", Toast.LENGTH_SHORT).show();
                return;
            }
            inputField.setText("");
            responseView.setText("");
            showLoading(true);
            getTogetherReply(userMessage);
        });

        micButton.setOnClickListener(v -> startSpeechRecognition());
    }

    private void showLoading(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!isLoading);
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, 1);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (!result.isEmpty()) {
                inputField.setText(result.get(0));
            }
        }
    }

    private void getTogetherReply(String prompt) {
        JSONObject requestBodyJson = new JSONObject();
        try {
            JSONObject messageObject = new JSONObject();
            messageObject.put("role", "user");
            messageObject.put("content", prompt);

            JSONArray messagesArray = new JSONArray();
            messagesArray.put(messageObject);

            requestBodyJson.put("model", "mistralai/Mixtral-8x7B-Instruct-v0.1");
            requestBodyJson.put("messages", messagesArray);
        } catch (JSONException e) {
            runOnUiThread(() -> {
                responseView.setText("Error creating request: " + e.getMessage());
                showLoading(false);
            });
            return;
        }

        RequestBody body = RequestBody.create(requestBodyJson.toString(), MediaType.parse("application/json; charset=utf-8"));
        String apiKey = "tgp_v1_rTXR49J0_3DwbANfWN_W-tKelNMsTEkkDTngE2OXCjQ";
        Request request = new Request.Builder()
                .url("https://api.together.xyz/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    responseView.setText("Request failed: " + e.getMessage());
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONObject obj = new JSONObject(responseStr);
                        JSONArray choices = obj.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String reply = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            runOnUiThread(() -> {
                                responseView.setText(reply.trim());
                                prefs.edit().putString(HISTORY_KEY, responseView.getText().toString()).apply();
                                tts.speak(reply.trim(), TextToSpeech.QUEUE_FLUSH, null, null);
                                showLoading(false);
                            });
                        } else {
                            runOnUiThread(() -> {
                                responseView.setText("No reply found in response.");
                                showLoading(false);
                            });
                        }
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            responseView.setText("JSON Parsing error: " + e.getMessage() + "\nResponse: " + responseStr);
                            showLoading(false);
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        responseView.setText("Request not successful: " + response.code() + "\n" + responseStr);
                        showLoading(false);
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
