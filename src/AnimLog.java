package com.textanimation;

import java.util.HashSet;

final class AnimLog {
    private static final HashSet<String> reportedErrors = new HashSet<>();
    static volatile boolean debugLogging = false;

    private AnimLog() {
    }

    static void report(String where, Throwable error) {
        if (debugLogging || reportedErrors.add(where)) {
            log(where + " error: " + error);
        }
    }

    static void log(String message) {
        try {
            android.util.Log.d("TextAnimationCore", String.valueOf(message));
        } catch (Throwable ignored) {
        }
    }
}
