package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Region;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.widget.EditText;

import java.util.ArrayList;

final class Glyphs {
    private static final int LIMIT = 160;

    private final ArrayList<Glyph> items = new ArrayList<>();
    private final Paint blurPaint = new Paint();

    private static final class Glyph {
        int start;
        int end;
        final String text;
        final long time;
        float left;
        float top;
        float right;
        float bottom;
        float baseline;

        Glyph(int start, int end, String text, long time) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.time = time;
        }
    }

    void clear() {
        items.clear();
    }

    void add(EditText view, Clusters clusters, String text, int from, int to, long now) {
        CharSequence content = view.getText();
        for (int i = from; i < to && items.size() < LIMIT; ) {
            int next = clusters.next(i, to);
            if (!Clusters.blank(text, i, next) && !Clusters.emoji(content, i, next)) {
                items.add(new Glyph(i, next, text.substring(i, next), now));
            }
            i = next;
        }
    }

    void shift(int from, int removed, int inserted) {
        int end = from + removed;
        int delta = inserted - removed;
        for (int i = items.size() - 1; i >= 0; i--) {
            Glyph glyph = items.get(i);
            if (glyph.end <= from) {
                continue;
            }
            if (glyph.start >= end) {
                glyph.start += delta;
                glyph.end += delta;
            } else {
                items.remove(i);
            }
        }
    }

    boolean measure(EditText view, Config config, long now) {
        if (items.isEmpty()) {
            return false;
        }
        Layout layout = view.getLayout();
        if (layout == null) {
            items.clear();
            return false;
        }
        CharSequence text = view.getText();
        TextPaint paint = view.getPaint();
        int length = text.length();
        float left = view.getCompoundPaddingLeft();
        float top = view.getTotalPaddingTop();
        for (int i = items.size() - 1; i >= 0; i--) {
            Glyph glyph = items.get(i);
            if (now - glyph.time >= config.duration || glyph.end > length) {
                items.remove(i);
                continue;
            }
            int line = layout.getLineForOffset(glyph.start);
            float a = layout.getPrimaryHorizontal(glyph.start);
            float b = layout.getLineForOffset(glyph.end) == line ? layout.getPrimaryHorizontal(glyph.end) : a;
            if (a == b) {
                float width = paint.measureText(text, glyph.start, glyph.end);
                b = layout.isRtlCharAt(glyph.start) ? a - width : a + width;
            }
            glyph.left = left + Math.min(a, b);
            glyph.right = left + Math.max(a, b);
            glyph.top = top + layout.getLineTop(line);
            glyph.bottom = top + layout.getLineBottom(line);
            glyph.baseline = top + layout.getLineBaseline(line);
        }
        return !items.isEmpty();
    }

    @SuppressWarnings("deprecation")
    void clip(Canvas canvas) {
        for (int i = 0; i < items.size(); i++) {
            Glyph glyph = items.get(i);
            if (Build.VERSION.SDK_INT >= 26) {
                canvas.clipOutRect(glyph.left, glyph.top, glyph.right, glyph.bottom);
            } else {
                canvas.clipRect(glyph.left, glyph.top, glyph.right, glyph.bottom, Region.Op.DIFFERENCE);
            }
        }
    }

    boolean draw(EditText view, Canvas canvas, Config config, long now) {
        Layout layout = view.getLayout();
        if (items.isEmpty() || layout == null) {
            return false;
        }
        TextPaint paint = view.getPaint();
        int color = paint.getColor();
        int alpha = paint.getAlpha();
        float density = view.getResources().getDisplayMetrics().density;
        float slide = config.slide ? Motion.SLIDE_DP * density : 0.0f;
        float blur = config.blur ? Motion.BLUR_DP * density : 0.0f;
        float lift = paint.getTextSize() / 3.0f;
        float dx = view.getCompoundPaddingLeft();
        float dy = view.getTotalPaddingTop();
        if (blur > 0.0f) {
            blurPaint.set(paint);
        }
        try {
            for (int i = 0; i < items.size(); i++) {
                Glyph glyph = items.get(i);
                float progress = Easing.apply(config.easing, Motion.clamp((now - glyph.time) / (float) config.duration));
                float shown = Motion.clamp(progress);
                int save = canvas.save();
                canvas.translate(0.0f, -slide * (1.0f - progress));
                Motion.transform(canvas, config, (glyph.left + glyph.right) / 2.0f, glyph.baseline - lift, progress);
                if (blur > 0.0f && shown < 1.0f) {
                    int blurred = (int) ((1.0f - shown) * alpha);
                    if (blurred > 4) {
                        blurPaint.setAlpha(blurred);
                        blurPaint.setMaskFilter(Motion.blur(blur * (1.0f - shown)));
                        canvas.drawText(glyph.text, glyph.left, glyph.baseline, blurPaint);
                    }
                }
                int sharp = (int) (Motion.sharp(shown) * alpha);
                if (sharp > 0) {
                    canvas.clipRect(glyph.left, glyph.top, glyph.right, glyph.bottom);
                    canvas.translate(dx, dy);
                    paint.setAlpha(sharp);
                    layout.draw(canvas);
                }
                canvas.restoreToCount(save);
            }
        } finally {
            paint.setColor(color);
            blurPaint.setMaskFilter(null);
        }
        return true;
    }
}
