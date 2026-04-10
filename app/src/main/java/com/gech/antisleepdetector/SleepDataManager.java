package com.gech.antisleepdetector;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SleepDataManager {
    private static final String PREFS_NAME = "SleepDataPrefs";
    private static final String KEY_SLEEP_EVENTS = "sleep_timestamps";

    public static void saveSleepEvent(Context context, long timestamp) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> timestamps = new HashSet<>(prefs.getStringSet(KEY_SLEEP_EVENTS, new HashSet<>()));
        
        timestamps.add(String.valueOf(timestamp));
        
        // Optional: Keep only last 48 hours to save space
        long cleanupThreshold = System.currentTimeMillis() - (48 * 60 * 60 * 1000);
        timestamps.removeIf(s -> {
            try { return Long.parseLong(s) < cleanupThreshold; } catch (Exception e) { return true; }
        });
        
        prefs.edit().putStringSet(KEY_SLEEP_EVENTS, timestamps).apply();
    }

    public static Set<String> getSleepEvents(Context context) {
        if (context == null) return new HashSet<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_SLEEP_EVENTS, new HashSet<>());
    }
}
