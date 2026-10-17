package com.textanimation;

final class Easing {
    static final int COUNT = 6;

    private static final int LINEAR = 1;
    private static final int CUBIC = 2;
    private static final int BACK = 3;
    private static final int ELASTIC = 4;
    private static final int BOUNCE = 5;

    private Easing() {
    }

    static float apply(int curve, float t) {
        t = Motion.clamp(t);
        switch (curve) {
            case LINEAR:
                return t;
            case CUBIC: {
                float inv = 1.0f - t;
                return 1.0f - inv * inv * inv;
            }
            case BACK: {
                float c1 = 1.70158f;
                float inv = t - 1.0f;
                return 1.0f + (c1 + 1.0f) * inv * inv * inv + c1 * inv * inv;
            }
            case ELASTIC:
                if (t <= 0.0f || t >= 1.0f) {
                    return t;
                }
                return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0);
            case BOUNCE:
                return bounce(t);
            default: {
                float inv = 1.0f - t;
                return 1.0f - inv * inv * inv * inv * inv;
            }
        }
    }

    static float smooth(float t) {
        t = Motion.clamp(t);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float bounce(float t) {
        float n = 7.5625f;
        float d = 2.75f;
        if (t < 1.0f / d) {
            return n * t * t;
        }
        if (t < 2.0f / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        }
        if (t < 2.5f / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
