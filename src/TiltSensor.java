package com.textanimation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

final class TiltSensor {
    private final Settings settings;
    private SensorManager tiltSensorManager;
    private SensorEventListener tiltListener;
    boolean tiltRegistered = false;
    volatile float tiltX = 0.0f;
    volatile float tiltY = 1.0f;

    TiltSensor(Settings settings) {
        this.settings = settings;
    }

    void ensure(View view) {
        if (!settings.tiltWindEnabled || tiltRegistered || view == null) {
            return;
        }
        try {
            Context context = view.getContext();
            Context appContext = context == null ? null : context.getApplicationContext();
            if (appContext != null) {
                context = appContext;
            }
            if (context == null) {
                return;
            }
            Object service = context.getSystemService(Context.SENSOR_SERVICE);
            if (!(service instanceof SensorManager)) {
                return;
            }
            SensorManager manager = (SensorManager) service;
            Sensor sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (sensor == null) {
                sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            if (sensor == null) {
                return;
            }
            SensorEventListener listener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    update(event);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI);
            tiltSensorManager = manager;
            tiltListener = listener;
            tiltRegistered = true;
        } catch (Throwable error) {
            AnimLog.report("tilt sensor", error);
        }
    }

    private void update(SensorEvent event) {
        try {
            if (event == null || event.values == null || event.values.length < 2) {
                return;
            }
            float screenX = Math.max(-1.0f, Math.min(1.0f, -event.values[0] / SensorManager.GRAVITY_EARTH));
            float screenY = Math.max(-1.0f, Math.min(1.0f, event.values[1] / SensorManager.GRAVITY_EARTH));
            float magnitude = (float) Math.sqrt(screenX * screenX + screenY * screenY);
            float blend = Math.min(1.0f, magnitude / 0.35f);
            float targetX = screenX * blend;
            float targetY = screenY * blend + (1.0f - blend);
            tiltX += (targetX - tiltX) * 0.25f;
            tiltY += (targetY - tiltY) * 0.25f;
        } catch (Throwable error) {
            AnimLog.report("update", error);
        }
    }

    void release() {
        try {
            if (tiltSensorManager != null && tiltListener != null) {
                tiltSensorManager.unregisterListener(tiltListener);
            }
        } catch (Throwable error) {
            AnimLog.report("release", error);
        } finally {
            tiltSensorManager = null;
            tiltListener = null;
            tiltRegistered = false;
            tiltX = 0.0f;
            tiltY = 1.0f;
        }
    }
}
