package com.textanimation;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

final class Logs {
    private static final String TAG = "TextAnimation";
    private static final Set<String> SEEN = new HashSet<>();

    private Logs() {
    }

    static void info(String message) {
        Log.i(TAG, message);
    }

    static synchronized void error(String where, Throwable error) {
        if (SEEN.add(where)) {
            Log.e(TAG, where, error);
        }
    }
}
