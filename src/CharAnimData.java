package com.textanimation;

public class CharAnimData {
    public static final int TRIGGER_NONE = 0;
    public static final int TRIGGER_BOUNCE = 1;

    public final long startTime;
    public final String text;
    public final int length;
    public final int trigger;
    public final float heat;

    public CharAnimData(long startTime, String text, int length) {
        this(startTime, text, length, TRIGGER_NONE);
    }

    public CharAnimData(long startTime, String text, int length, int trigger) {
        this(startTime, text, length, trigger, 0.0f);
    }

    public CharAnimData(long startTime, String text, int length, int trigger, float heat) {
        this.heat = heat;
        this.startTime = startTime;
        this.text = text;
        this.length = length;
        this.trigger = trigger;
    }
}
