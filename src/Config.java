package com.textanimation;

import org.json.JSONException;
import org.json.JSONObject;

final class Config {
    static final int STYLE_DUST = 0;
    static final int STYLE_SNOW = 1;
    static final int STYLE_SAKURA = 2;
    static final int STYLE_LETTERS = 3;
    static final int STYLE_FALL = 4;
    static final int MAX_AMOUNT = 12;

    static final Config DEFAULT = new Config(new JSONObject());

    final int duration;
    final int easing;
    final boolean blur;
    final boolean slide;
    final boolean scale;
    final boolean rotate;
    final boolean ghosts;
    final boolean particles;
    final int style;
    final int amount;
    final boolean burst;
    final boolean caret;
    final int caretSpeed;
    final boolean blink;
    final boolean liquid;
    final boolean haptic;

    private Config(JSONObject json) {
        duration = clamp(json.optInt("duration", 300), 80, 1200);
        easing = clamp(json.optInt("easing", 0), 0, Easing.COUNT - 1);
        blur = json.optBoolean("blur", true);
        slide = json.optBoolean("slide", true);
        scale = json.optBoolean("scale", false);
        rotate = json.optBoolean("rotate", false);
        ghosts = json.optBoolean("ghosts", true);
        particles = json.optBoolean("particles", true);
        style = clamp(json.optInt("style", STYLE_DUST), STYLE_DUST, STYLE_FALL);
        amount = clamp(json.optInt("amount", 5), 1, MAX_AMOUNT);
        burst = json.optBoolean("burst", true);
        caret = json.optBoolean("caret", true);
        caretSpeed = clamp(json.optInt("caret_speed", 25), 5, 100);
        blink = json.optBoolean("blink", true);
        liquid = json.optBoolean("liquid", false);
        haptic = json.optBoolean("haptic", false);
    }

    static Config parse(String json) {
        try {
            return new Config(new JSONObject(json == null ? "{}" : json));
        } catch (JSONException error) {
            throw new IllegalArgumentException("malformed settings", error);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
