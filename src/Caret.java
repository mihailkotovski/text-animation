package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.EditText;

final class Caret {
    static final long IDLE = -1L;
    static final long NEXT_FRAME = 0L;

    private static final float WIDTH_DP = 3.0f;
    private static final float STRETCH_DP = 15.0f;
    private static final long BLINK_PERIOD = 1000L;
    private static final long BLINK_FADE = 200L;
    private static final long JUMP_MS = 260L;
    private static final long DIVE_MS = 340L;
    private static final long PULSE_MS = 190L;
    private static final long STRETCH_MS = 420L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float x = Float.NaN;
    private float y;
    private float motion;
    private long frame;
    private int offset = -1;
    private int jumpOffset = -1;
    private long jumpTime;
    private long diveTime;
    private long pulseTime;
    private long stretchTime;
    private float boost;
    private long anchor;
    private boolean focused;

    void reset() {
        x = Float.NaN;
        motion = 0.0f;
        frame = 0L;
        offset = -1;
        jumpOffset = -1;
        jumpTime = 0L;
        diveTime = 0L;
        pulseTime = 0L;
        stretchTime = 0L;
        boost = 0.0f;
        focused = false;
    }

    void typed(CharSequence text, int from, int to, long now) {
        boolean jumped = false;
        boolean dived = false;
        for (int i = from; i < to && !(jumped && dived); i++) {
            char c = text.charAt(i);
            if (c == ' ' && !jumped) {
                jumpOffset = i;
                jumpTime = now;
                jumped = true;
            } else if (c == '\n' && !dived) {
                diveTime = now;
                dived = true;
            }
        }
    }

    void erased(long now) {
        pulseTime = now;
    }

    void wake(long now) {
        anchor = now;
    }

    long draw(EditText view, Canvas canvas, Config config, int color, boolean active, long now) {
        if (!active) {
            if (focused) {
                reset();
            }
            return IDLE;
        }
        if (!focused) {
            focused = true;
            anchor = now;
        }
        Layout layout = view.getLayout();
        CharSequence text = view.getText();
        int start = view.getSelectionStart();
        if (layout == null || start < 0 || start != view.getSelectionEnd()) {
            return IDLE;
        }
        int current = Math.min(start, text.length());
        if (current != offset) {
            int delta = Math.abs(current - offset);
            if (offset >= 0 && delta > 2) {
                boost = Math.min(1.0f, delta / 18.0f);
                stretchTime = now;
            }
            offset = current;
            anchor = now;
        }

        float left = view.getCompoundPaddingLeft();
        float padding = view.getTotalPaddingTop();
        int line = layout.getLineForOffset(current);
        float targetX = left + layout.getPrimaryHorizontal(current);
        float top = padding + layout.getLineTop(line);
        float bottom = padding + layout.getLineBottom(line);
        float targetY = (top + bottom) / 2.0f;
        float lineHeight = Math.max(1.0f, bottom - top);

        float dt = frame == 0L ? 16.0f : Math.min(50.0f, now - frame);
        frame = now;
        float follow = Math.min(1.0f, config.caretSpeed / 100.0f * dt / 16.0f);
        if (Float.isNaN(x)) {
            x = targetX;
            y = targetY;
        } else {
            x += (targetX - x) * follow;
            y += (targetY - y) * follow;
        }
        float dx = targetX - x;
        float dy = targetY - y;
        float distance = (float) Math.hypot(dx, dy);
        motion += (distance - motion) * Math.min(1.0f, 0.32f * dt / 16.0f);

        float density = view.getResources().getDisplayMetrics().density;
        float width = WIDTH_DP * density;
        float reach = STRETCH_DP * density;
        float vx = x;
        float vy = y;
        float jump = 0.0f;
        float dive = 0.0f;
        float pulse = 0.0f;
        float lunge = 0.0f;
        if (config.liquid) {
            jump = jumpOffset >= 0 && jumpOffset <= text.length() ? wave(jumpTime, JUMP_MS, now) : 0.0f;
            dive = wave(diveTime, DIVE_MS, now);
            pulse = wave(pulseTime, PULSE_MS, now);
            lunge = wave(stretchTime, STRETCH_MS, now) * boost;
            if (jump > 0.0f) {
                int jumpLine = layout.getLineForOffset(jumpOffset);
                float jumpBottom = padding + layout.getLineBottom(jumpLine);
                float jumpHeight = jumpBottom - padding - layout.getLineTop(jumpLine);
                vx += (left + layout.getPrimaryHorizontal(jumpOffset) - vx) * jump;
                vy += (jumpBottom - jumpHeight * 0.08f - vy) * jump;
            }
            vy += lineHeight * 0.28f * dive;
        }

        float travel = config.liquid ? Math.min(1.0f, motion / Math.max(width, reach)) : 0.0f;
        float stretch = config.liquid
            ? Math.min(reach * (1.0f + lunge * 0.70f), motion * (0.46f + lunge * 0.28f) + width * (1.4f * jump + 1.8f * lunge))
            : 0.0f;
        float w = width * Math.max(0.55f, 1.0f + travel * 0.22f + jump * 0.28f + dive * 0.10f + lunge * 0.14f - pulse * 0.34f);
        float direction = Math.abs(dx) > 0.1f ? Math.signum(dx) : (targetX >= vx ? 1.0f : -1.0f);
        float l = vx - w / 2.0f - (direction < 0.0f ? stretch : 0.0f);
        float r = vx + w / 2.0f + (direction >= 0.0f ? stretch : 0.0f);
        float inset = lineHeight * (0.08f + travel * 0.18f + jump * 0.10f + dive * 0.08f + pulse * 0.14f);
        float shift = (vy - targetY) * 0.12f;
        float t = top + inset + shift;
        float b = bottom - inset + shift;
        float radius = w;
        if (jump > 0.0f) {
            float size = Math.max(width * 2.7f, lineHeight * 0.36f);
            l += (vx - size / 2.0f - l) * jump;
            r += (vx + size / 2.0f - r) * jump;
            t += (vy - size - t) * jump;
            b += (vy - b) * jump;
            radius += (size / 2.0f - radius) * jump;
        }
        rect.set(l, t, r, b);

        boolean moving = distance > 0.5f || motion > 0.35f || jump > 0.001f || dive > 0.001f || pulse > 0.001f || lunge > 0.001f;
        boolean blinking = config.blink && view.hasWindowFocus();
        float alpha = blinking && !moving ? blink(now) : hold(now);
        if (alpha > 0.004f) {
            paint.setColor(color);
            paint.setAlpha(Math.round(alpha * Color.alpha(color)));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        expire(now);
        boolean settling = Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f || motion > 0.2f
            || jumpTime != 0L || diveTime != 0L || pulseTime != 0L || stretchTime != 0L;
        if (settling) {
            return NEXT_FRAME;
        }
        return blinking ? untilFade(now) : IDLE;
    }

    private float hold(long now) {
        anchor = now;
        return 1.0f;
    }

    private float blink(long now) {
        if (now < anchor) {
            return hold(now);
        }
        long half = BLINK_PERIOD / 2L;
        long steady = half - BLINK_FADE;
        long phase = (now - anchor) % BLINK_PERIOD;
        if (phase < steady) {
            return 1.0f;
        }
        if (phase < half) {
            return 1.0f - Easing.smooth((phase - steady) / (float) BLINK_FADE);
        }
        if (phase < half + steady) {
            return 0.0f;
        }
        return Easing.smooth((phase - half - steady) / (float) BLINK_FADE);
    }

    private long untilFade(long now) {
        long steady = BLINK_PERIOD / 2L - BLINK_FADE;
        long phase = Math.max(0L, now - anchor) % (BLINK_PERIOD / 2L);
        return phase < steady ? steady - phase : NEXT_FRAME;
    }

    private void expire(long now) {
        if (jumpTime != 0L && now - jumpTime >= JUMP_MS) {
            jumpTime = 0L;
            jumpOffset = -1;
        }
        if (diveTime != 0L && now - diveTime >= DIVE_MS) {
            diveTime = 0L;
        }
        if (pulseTime != 0L && now - pulseTime >= PULSE_MS) {
            pulseTime = 0L;
        }
        if (stretchTime != 0L && now - stretchTime >= STRETCH_MS) {
            stretchTime = 0L;
            boost = 0.0f;
        }
    }

    private static float wave(long start, long duration, long now) {
        if (start == 0L) {
            return 0.0f;
        }
        float progress = (now - start) / (float) duration;
        return progress >= 1.0f ? 0.0f : (float) Math.sin(Math.max(0.0f, progress) * Math.PI);
    }
}
