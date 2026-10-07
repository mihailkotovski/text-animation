package com.textanimation;

import org.json.JSONException;
import org.json.JSONObject;

final class Settings {
    int duration = 300;
    boolean blurEnabled = true;
    int blurDuration = 300;
    int blurRadius = 10;
    int blurTextDelay = 20;
    boolean slideEnabled = true;
    int slideDist = 20;
    boolean scaleEnabled = false;
    float scaleStart = 0.0f;
    boolean rotateEnabled = false;
    int rotateAngle = -15;
    boolean deleteAnimEnabled = true;
    int particleCount = 5;
    float particleSpeed = 1.0f;
    float particleSize = 1.0f;
    float particleSpread = 1.0f;
    int particleStyle = Particle.STYLE_DOTS;
    boolean cursorEnabled = true;
    int cursorSpeed = 25;
    int cursorWidth = 5;
    boolean cursorBlinkEnabled = true;
    int cursorBlinkPeriod = 1000;
    int cursorBlinkSmoothness = 100;
    boolean liquidCursorEnabled = false;
    int liquidScaleFactor = 15;
    int selectionCursorEffect = 0;
    int selectionLiquidStretch = 60;
    int selectionLiquidSide = 50;
    boolean ignoreSpaces = true;
    boolean animateAllLines = true;
    boolean debugMode = false;
    int easingCurve = 0;
    boolean insertWaveEnabled = true;
    int insertWaveStep = 25;
    boolean sendBurstEnabled = true;
    int sendBurstPower = 100;
    boolean implodeEnabled = true;
    int implodeCount = 4;
    boolean glyphParticles = true;
    boolean charTriggersEnabled = true;
    boolean powerModeEnabled = false;
    int powerComboWindow = 700;
    int powerShake = 4;
    boolean powerSparks = true;
    boolean powerShowCombo = true;
    boolean tiltWindEnabled = false;
    int tiltWindStrength = 100;
    boolean cursorPushParticles = true;
    boolean hapticEnabled = false;
    boolean rtlEnabled = false;
    boolean colorFlashEnabled = true;
    int colorFlashStrength = 70;
    boolean deleteGhostEnabled = true;
    boolean particlesOverlayEnabled = true;

    void applyJson(String settingsJson) throws JSONException {
        JSONObject obj = new JSONObject(settingsJson == null ? "{}" : settingsJson);
        duration = clamp(optInt(obj, "duration", duration), 80, 900);
        blurEnabled = optBoolean(obj, "blur_enabled", blurEnabled);
        blurDuration = clamp(optInt(obj, "blur_duration", blurDuration), 80, 900);
        blurRadius = clamp(optInt(obj, "blur_radius", blurRadius), 0, 30);
        blurTextDelay = clamp(optInt(obj, "blur_text_delay", blurTextDelay), 0, 100);
        slideEnabled = optBoolean(obj, "slide_enabled", slideEnabled);
        slideDist = clamp(optInt(obj, "slide_dist", slideDist), 0, 60);
        scaleEnabled = optBoolean(obj, "scale_enabled", scaleEnabled);
        scaleStart = clamp(optFloat(obj, "scale_start", scaleStart), 0.0f, 1.0f);
        rotateEnabled = optBoolean(obj, "rotate_enabled", rotateEnabled);
        rotateAngle = clamp(optInt(obj, "rotate_angle", rotateAngle), -45, 45);
        deleteAnimEnabled = optBoolean(obj, "delete_anim_enabled", deleteAnimEnabled);
        particleCount = clamp(optInt(obj, "particle_count", particleCount), 0, 12);
        particleSpeed = Math.max(0.1f, clamp(optFloat(obj, "particle_speed", particleSpeed * 50.0f), 0.0f, 120.0f) / 50.0f);
        particleSize = Math.max(0.1f, clamp(optFloat(obj, "particle_size", particleSize * 50.0f), 10.0f, 120.0f) / 50.0f);
        particleSpread = clamp(optFloat(obj, "particle_spread", particleSpread * 50.0f), 0.0f, 120.0f) / 50.0f;
        particleStyle = clamp(optInt(obj, "particle_style", particleStyle), Particle.STYLE_DOTS, Particle.STYLE_FALL);
        cursorEnabled = optBoolean(obj, "cursor_enabled", cursorEnabled);
        cursorSpeed = clamp(optInt(obj, "cursor_speed", cursorSpeed), 1, 60);
        cursorWidth = clamp(optInt(obj, "cursor_width", cursorWidth), 1, 12);
        cursorBlinkEnabled = optBoolean(obj, "cursor_blink_enabled", cursorBlinkEnabled);
        cursorBlinkPeriod = clamp(optInt(obj, "cursor_blink_period", cursorBlinkPeriod), 300, 2500);
        cursorBlinkSmoothness = clamp(optInt(obj, "cursor_blink_smoothness", cursorBlinkSmoothness), 0, 100);
        liquidCursorEnabled = optBoolean(obj, "liquid_cursor_enabled", liquidCursorEnabled);
        liquidScaleFactor = clamp(optInt(obj, "liquid_scale_factor", liquidScaleFactor), 0, 40);
        selectionCursorEffect = clamp(optInt(obj, "selection_cursor_effect", selectionCursorEffect), 0, 1);
        selectionLiquidStretch = clamp(optInt(obj, "selection_liquid_stretch", selectionLiquidStretch), 0, 200);
        selectionLiquidSide = clamp(optInt(obj, "selection_liquid_side", selectionLiquidSide), 0, 200);
        ignoreSpaces = optBoolean(obj, "ignore_spaces", ignoreSpaces);
        animateAllLines = optBoolean(obj, "animate_all_lines", animateAllLines);
        debugMode = optBoolean(obj, "debug_mode", debugMode);
        easingCurve = clamp(optInt(obj, "easing_curve", easingCurve), 0, 5);
        insertWaveEnabled = optBoolean(obj, "insert_wave_enabled", insertWaveEnabled);
        insertWaveStep = clamp(optInt(obj, "insert_wave_step", insertWaveStep), 0, 120);
        sendBurstEnabled = optBoolean(obj, "send_burst_enabled", sendBurstEnabled);
        sendBurstPower = clamp(optInt(obj, "send_burst_power", sendBurstPower), 0, 200);
        implodeEnabled = optBoolean(obj, "implode_enabled", implodeEnabled);
        implodeCount = clamp(optInt(obj, "implode_count", implodeCount), 0, 12);
        glyphParticles = optBoolean(obj, "glyph_particles", glyphParticles);
        charTriggersEnabled = optBoolean(obj, "char_triggers_enabled", charTriggersEnabled);
        powerModeEnabled = optBoolean(obj, "power_mode_enabled", powerModeEnabled);
        powerComboWindow = clamp(optInt(obj, "power_combo_window", powerComboWindow), 200, 2000);
        powerShake = clamp(optInt(obj, "power_shake", powerShake), 0, 12);
        powerSparks = optBoolean(obj, "power_sparks", powerSparks);
        powerShowCombo = optBoolean(obj, "power_show_combo", powerShowCombo);
        tiltWindEnabled = optBoolean(obj, "tilt_wind_enabled", tiltWindEnabled);
        tiltWindStrength = clamp(optInt(obj, "tilt_wind_strength", tiltWindStrength), 0, 200);
        cursorPushParticles = optBoolean(obj, "cursor_push_particles", cursorPushParticles);
        hapticEnabled = optBoolean(obj, "haptic_enabled", hapticEnabled);
        rtlEnabled = optBoolean(obj, "rtl_enabled", rtlEnabled);
        colorFlashEnabled = optBoolean(obj, "color_flash_enabled", colorFlashEnabled);
        colorFlashStrength = clamp(optInt(obj, "color_flash_strength", colorFlashStrength), 0, 100);
        deleteGhostEnabled = optBoolean(obj, "delete_ghost_enabled", deleteGhostEnabled);
        particlesOverlayEnabled = optBoolean(obj, "particles_overlay_enabled", particlesOverlayEnabled);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean optBoolean(JSONObject obj, String key, boolean fallback) {
        Object value = obj.opt(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return obj.optBoolean(key, fallback);
    }

    private int optInt(JSONObject obj, String key, int fallback) {
        Object value = obj.opt(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private float optFloat(JSONObject obj, String key, float fallback) {
        Object value = obj.opt(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
