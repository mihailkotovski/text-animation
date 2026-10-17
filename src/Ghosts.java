package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.widget.EditText;

import java.util.ArrayList;

final class Ghosts {
    static final int LIMIT = 160;

    private final ArrayList<Ghost> items = new ArrayList<>();
    private final Paint paint = new Paint();

    void add(Ghost ghost, long now) {
        if (items.size() < LIMIT) {
            ghost.time = now;
            items.add(ghost);
        }
    }

    void clear() {
        items.clear();
    }

    boolean draw(EditText view, Canvas canvas, Config config, long now) {
        if (items.isEmpty()) {
            return false;
        }
        TextPaint source = view.getPaint();
        paint.set(source);
        int alpha = source.getAlpha();
        float density = view.getResources().getDisplayMetrics().density;
        float slide = config.slide ? Motion.SLIDE_DP * density : 0.0f;
        float blur = config.blur ? Motion.BLUR_DP * density : 0.0f;
        float lift = source.getTextSize() / 3.0f;
        for (int i = items.size() - 1; i >= 0; i--) {
            Ghost ghost = items.get(i);
            float raw = (now - ghost.time) / (float) config.duration;
            if (raw >= 1.0f) {
                items.remove(i);
                continue;
            }
            float fade = 1.0f - Math.max(0.0f, raw);
            float progress = Easing.apply(config.easing, fade);
            float shown = Motion.clamp(progress);
            int save = canvas.save();
            canvas.translate(0.0f, -slide * (1.0f - progress));
            Motion.transform(canvas, config, ghost.x + ghost.width / 2.0f, ghost.baseline - lift, progress);
            float radius = blur * (1.0f - shown);
            if (radius >= 0.5f) {
                int blurred = (int) (Math.min(1.0f, (1.0f - shown) * 2.0f) * fade * alpha);
                if (blurred > 4) {
                    paint.setAlpha(blurred);
                    paint.setMaskFilter(Motion.blur(radius));
                    canvas.drawText(ghost.text, ghost.x, ghost.baseline, paint);
                    paint.setMaskFilter(null);
                }
                fade *= shown;
            }
            int sharp = (int) (fade * alpha);
            if (sharp > 0) {
                paint.setAlpha(sharp);
                canvas.drawText(ghost.text, ghost.x, ghost.baseline, paint);
            }
            canvas.restoreToCount(save);
        }
        return !items.isEmpty();
    }
}
