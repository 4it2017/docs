package com.example.paul.weatherstation;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Paul on 21-Aug-17 at 5:15 PM.
 */

public class WeatherFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    MqttAndroidClient mqttAndroidClient;
    MqttConnectOptions mqttConnectOptions;
    private final String serverUri = "tcp://m20.cloudmqtt.com:16691";
    private final String refreshTopic = "nodemcu/requests";
    private final String deviceId = "16261926";
    private final String temperatureTopic = "nodemcu/" + deviceId + "/temperature";
    private final String humidityTopic = "nodemcu/" + deviceId + "/humidity";
    private final String pressureTopic = "nodemcu/" + deviceId + "/pressure";
    private final WeatherRecord weatherRecord = new WeatherRecord();
    private Context context;
    public DatabaseHandler db;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.weather_fragment, container, false);
        final TextView temperatureText = (TextView) view.findViewById(R.id.temperature_value_text);
        final TextView humidityText = (TextView) view.findViewById(R.id.humidity_level_text);
        context = getActivity();
        db = new DatabaseHandler(context);

        //Set Last Values To Views
        if(db.getLastWeatherRecord() != null) {
            temperatureText.setText(db.getLastWeatherRecord().getTemperature());
            humidityText.setText(db.getLastWeatherRecord().getHumidity());
        } else {
            temperatureText.setText("-");
            humidityText.setText("-");
        }

        //Refresh Action

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe);
        swipeRefreshLayout.setColorSchemeColors(
                Color.RED, Color.GREEN, Color.BLUE, Color.CYAN);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                publishRefreshMessage();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        //mqtt

        this.mqttAndroidClient = this.createMqttAndroidClient();
        this.mqttConnectOptions = createMqttConnectOptions();

        this.mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Toast.makeText(context, "Connection lost!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                switch (topic) {
                    case temperatureTopic:
                        temperatureText.setText(message.toString());
                        weatherRecord.setTemperature(message.toString());
                        addToDb();
                        break;

                    case humidityTopic:
                        humidityText.setText(message.toString());
                        weatherRecord.setHumidity(message.toString());
                        addToDb();
                        break;

                    case pressureTopic:
                        weatherRecord.setPressure(message.toString());
                        addToDb();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribeToTopics();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Toast.makeText(getContext(), "Something went wrong connecting to server.", Toast.LENGTH_SHORT).show();
                    exception.printStackTrace();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }

        return view;
    }



    private MqttConnectOptions createMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setUserName("android");
        options.setPassword("android".toCharArray());
        return options;
    }

    private MqttAndroidClient createMqttAndroidClient() {
        String clientId = MqttClient.generateClientId();
        return new MqttAndroidClient(getContext(), this.serverUri,clientId);
    }

    public void subscribeToTopics(){
        try {
            mqttAndroidClient.subscribe("nodemcu/#", 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Toast.makeText(getContext(), "Something went wrong.", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    private void publishRefreshMessage(){

        try {
            mqttAndroidClient.publish(refreshTopic, getRefreshMessage());

        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private MqttMessage getRefreshMessage(){
        String payload = "NEED REFRESH!";
        byte[] encodedPayload;
        encodedPayload = payload.getBytes();
        return new MqttMessage(encodedPayload);
    }

    private boolean isWeatherRecordLoaded(WeatherRecord weatherRecord){
        return weatherRecord.getPressure() != null
                && weatherRecord.getHumidity() != null
                && weatherRecord.getTemperature() != null;
    }

    private String getCurrentTime(){
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy | HH:mm:ss");
        return sdf.format(c.getTime());
    }

    private void addToDb(){
        if(isWeatherRecordLoaded(weatherRecord)){
            Log.d("Insert: ", "Inserting ..");
            weatherRecord.setTime(getCurrentTime());
            db.addWeatherRecord(weatherRecord);
            weatherRecord.clear();
            addWeatherRecordsToLog();
        }
    }

    private void addWeatherRecordsToLog(){
        //Adding weatherRecords to Log
        Log.d("Reading: ", "Reading all contacts..");
        List<WeatherRecord> weatherRecordList = db.getAllWeatherRecords();

        for (WeatherRecord weatherRecord : weatherRecordList) {
            String log = "Id: " + weatherRecord.getID() + " ,Time: " + weatherRecord.getTime() + " ,Temperature: " + weatherRecord.getTemperature() + " ,Humidity:" + weatherRecord.getHumidity() + " ,Pressure:" + weatherRecord.getPressure();
            // Writing Contacts to log
            Log.d("Name: ", log);
            System.out.println("Name: " + log);
        }
    }
}
