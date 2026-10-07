package com.textanimation;

final class GhostGlyph {
    final String text;
    final float x;
    final float baseline;
    final float width;
    final long startTime;

    GhostGlyph(String text, float x, float baseline, float width, long startTime) {
        this.text = text;
        this.x = x;
        this.baseline = baseline;
        this.width = width;
        this.startTime = startTime;
    }
}
