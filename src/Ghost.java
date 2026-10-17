package com.textanimation;

final class Ghost {
    final int offset;
    final String text;
    final float x;
    final float baseline;
    final float width;
    final boolean emoji;
    long time;

    Ghost(int offset, String text, float x, float baseline, float width, boolean emoji) {
        this.offset = offset;
        this.text = text;
        this.x = x;
        this.baseline = baseline;
        this.width = width;
        this.emoji = emoji;
    }
}
