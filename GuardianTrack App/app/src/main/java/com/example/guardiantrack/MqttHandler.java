package com.example.guardiantrack;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

public class MqttHandler implements MqttCallback {
    private static final String BROKER_URL = "tcp://broker.emqx.io";
    private static final String TOPIC = "gt/t";
    private static final String TAG = "MqttHandler";
    private MqttClient client;
    private Context context;

    public static boolean theftoccured = false;
    public static double latitude = 0;
    public static double longitude = 0;

    public static  String uniqueId ="";

    public MqttHandler(Context context) {
        this.context = context;

        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network available. Cannot connect to broker.");
            return;
        }

        try {

            client = new MqttClient(BROKER_URL, MqttClient.generateClientId(), new MemoryPersistence());
            client.setCallback(this);

            // Configure MQTT connection options
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(20);

            // Connect to the broker
            client.connect(options);
            Log.i(TAG, "Connected to broker: " + BROKER_URL);

            // Subscribe to the topic
            client.subscribe(TOPIC);
            Log.i(TAG, "Subscribed to topic: " + TOPIC);

        } catch (MqttException e) {
            Log.e(TAG, "Error connecting to broker: ", e);
        }
    }


    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {

            String payload = new String(message.getPayload());


            JSONObject jsonObject = new JSONObject(payload);

            String macAddress = jsonObject.optString("Macaddress", "Unknown");
            if (uniqueId.equals(macAddress)) {
                theftoccured = true;
                latitude = jsonObject.optDouble("latitude", Double.NaN);
                longitude = jsonObject.optDouble("longitude", Double.NaN);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing message payload: ", e);
        }
    }


    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection lost: " + cause.getMessage(), cause);
    }

    // Callback method when message delivery is complete
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.i(TAG, "Delivery complete for message with ID: " + token.getMessageId());
    }

    public void publishMessage(String messageContent) {
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network available. Cannot publish message.");
            return;
        }

        try {
            MqttMessage message = new MqttMessage(messageContent.getBytes());
            message.setQos(1);
            client.publish(TOPIC, message);
            Log.i(TAG, "Message published to topic " + TOPIC + ": " + messageContent);
        } catch (MqttException e) {
            Log.e(TAG, "Error publishing message: ", e);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }


}
