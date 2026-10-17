package com.textanimation;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.util.SparseArray;

final class Motion {
    static final float SLIDE_DP = 20.0f;
    static final float SCALE_FROM = 0.0f;
    static final float ROTATE_FROM = -15.0f;
    static final float BLUR_DP = 3.5f;

    private static final float SHARP_FROM = 0.2f;
    private static final SparseArray<BlurMaskFilter> FILTERS = new SparseArray<>();

    private Motion() {
    }

    static float clamp(float value) {
        return value < 0.0f ? 0.0f : Math.min(value, 1.0f);
    }

    static float sharp(float shown) {
        return shown <= SHARP_FROM ? 0.0f : (shown - SHARP_FROM) / (1.0f - SHARP_FROM);
    }

    static void transform(Canvas canvas, Config config, float cx, float cy, float progress) {
        if (!config.scale && !config.rotate) {
            return;
        }
        canvas.translate(cx, cy);
        if (config.scale) {
            float scale = SCALE_FROM + (1.0f - SCALE_FROM) * progress;
            canvas.scale(scale, scale);
        }
        if (config.rotate) {
            canvas.rotate(ROTATE_FROM * (1.0f - progress));
        }
        canvas.translate(-cx, -cy);
    }

    static BlurMaskFilter blur(float radius) {
        int key = Math.max(1, Math.round(radius * 2.0f));
        BlurMaskFilter filter = FILTERS.get(key);
        if (filter == null) {
            filter = new BlurMaskFilter(key / 2.0f, BlurMaskFilter.Blur.NORMAL);
            FILTERS.put(key, filter);
        }
        return filter;
    }
}
