package com.textanimation;

final class Easing {
    private Easing() {
    }

    static float applyEasing(int curve, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        switch (curve) {
            case 1:
                return t;
            case 2: {
                float inv = 1.0f - t;
                return 1.0f - inv * inv * inv;
            }
            case 3: {
                float c1 = 1.70158f;
                float c3 = c1 + 1.0f;
                float inv = t - 1.0f;
                return 1.0f + c3 * inv * inv * inv + c1 * inv * inv;
            }
            case 4: {
                if (t <= 0.0f || t >= 1.0f) {
                    return t;
                }
                double c4 = (2.0 * Math.PI) / 3.0;
                return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0);
            }
            case 5:
                return bounceOut(t);
            default:
                return easeOutQuint(t);
        }
    }

    private static float bounceOut(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1.0f / d1) {
            return n1 * t * t;
        }
        if (t < 2.0f / d1) {
            float shifted = t - 1.5f / d1;
            return n1 * shifted * shifted + 0.75f;
        }
        if (t < 2.5f / d1) {
            float shifted = t - 2.25f / d1;
            return n1 * shifted * shifted + 0.9375f;
        }
        float shifted = t - 2.625f / d1;
        return n1 * shifted * shifted + 0.984375f;
    }

    static float smoothStep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float easeOutQuint(float t) {
        float inv = 1.0f - Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - inv * inv * inv * inv * inv;
    }
}
