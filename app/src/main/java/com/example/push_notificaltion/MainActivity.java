package com.example.push_notificaltion;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import java.util.ArrayList;
import java.util.logging.Handler;

public class MainActivity extends AppCompatActivity {
    // Các khai báo biến
    private static final String MQTT_BROKER_URL = "ssl://180af39806594f33a711b65aed30f9b4.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_TOPIC = "iot/cua";
    private static final String MQTT_USERNAME = "nhungpham";
    private static final String MQTT_PASSWORD = "Nhung123@";

    private ImageButton btnVoice, btn_open, btn_close;
    private TextView txtNotification;
    private MqttClient mqttClient;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
        }

        txtNotification = findViewById(R.id.txtVoiceResult);
        btnVoice = findViewById(R.id.btnVoiceToText);
        btn_open = findViewById(R.id.btn_open);
        btn_close = findViewById(R.id.btn_close);

        connectToMqttBroker();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    txtNotification.setText(spokenText);
                    sendMqttMessage(spokenText);
                }
            }

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        btnVoice.setOnClickListener(v -> startVoiceRecognition());

        btn_open.setOnClickListener(v -> {
            sendMqttMessage("1");
        });

        btn_close.setOnClickListener(v -> {
            sendMqttMessage("0");
        });
    }

    private void sendMqttMessage(String message) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish(MQTT_TOPIC, message.getBytes(), 1, false);
                // Thông báo đã gửi tin nhắn
                Toast.makeText(MainActivity.this, "Đã gửi tin nhắn: " + message, Toast.LENGTH_SHORT).show();
                // Nếu là lệnh mở cửa
                if (message.equals("1")) {
                    Toast.makeText(MainActivity.this, "Đã gửi lệnh mở cửa", Toast.LENGTH_SHORT).show();
                }
                // Nếu là lệnh đóng cửa
                else if (message.equals("0")) {
                    Toast.makeText(MainActivity.this, "Đã gửi lệnh đóng cửa", Toast.LENGTH_SHORT).show();
                }
                txtNotification.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        txtNotification.setText(" ");
                    }
                }, 1500);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }



    private void connectToMqttBroker() {
        try {
            mqttClient = new MqttClient(MQTT_BROKER_URL, MqttClient.generateClientId(), null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(MQTT_USERNAME);
            options.setPassword(MQTT_PASSWORD.toCharArray());
            mqttClient.connect(options);
            mqttClient.subscribe("MQTT_TOPIC", 1, (topic, message) -> {
                String messageContent = new String(message.getPayload());
                runOnUiThread(() -> txtNotification.setText(messageContent));
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói gì đó...");

        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            e.printStackTrace();
            txtNotification.setText("Thiết bị không hỗ trợ giọng nói!");
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                txtNotification.setText(result.get(0));
                sendMqttMessage(result.get(0));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        speechRecognizer.destroy();
    }
}

