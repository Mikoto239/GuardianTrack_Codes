package com.example.guardiantrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Vibrator;

import java.util.HashMap;
import java.util.Map;

public class NotificationActionReceiver extends BroadcastReceiver {

    private static final Map<String, MediaPlayer> mediaPlayerMap = new HashMap<>();
    private static final Map<String, Vibrator> vibratorMap = new HashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MyBackgroundService.class);
        context.startService(serviceIntent);
        String action = intent.getAction();
        if (action != null && action.equals("STOP_SOUND")) {
            String mediaPlayerId = intent.getStringExtra("media_player_id");
            if (mediaPlayerId != null) {
                MediaPlayer mediaPlayer = mediaPlayerMap.get(mediaPlayerId);
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayerMap.remove(mediaPlayerId);
                }

                Vibrator vibrator = vibratorMap.get(mediaPlayerId);
                if (vibrator != null) {
                    vibrator.cancel();
                    vibratorMap.remove(mediaPlayerId);
                }
            }
        }
    }

    public static void addMediaPlayer(String mediaPlayerId, MediaPlayer mediaPlayer) {
        mediaPlayerMap.put(mediaPlayerId, mediaPlayer);
    }

    public static void addVibrator(String mediaPlayerId, Vibrator vibrator) {
        vibratorMap.put(mediaPlayerId, vibrator);
    }
}
