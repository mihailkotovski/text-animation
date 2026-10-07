package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.view.View;
import android.widget.EditText;

import de.robv.android.xposed.XC_MethodHook;

final class PowerMode {
    private static final int POWER_MAX_COMBO = 30;
    private static final int POWER_MILESTONE = 10;

    private final Settings settings;
    private final ViewHelper helper;

    PowerMode(Settings settings, ViewHelper helper) {
        this.settings = settings;
        this.helper = helper;
    }

    void registerTypingCombo(EditText view, AnimationState state, int insertedCount, long now) {
        if (!settings.powerModeEnabled) {
            state.combo = 0;
            return;
        }
        if (insertedCount > 4) {
            return;
        }
        if (state.lastTypeTime > 0L && now - state.lastTypeTime <= settings.powerComboWindow) {
            state.combo = Math.min(999, state.combo + 1);
        } else {
            state.combo = 1;
        }
        state.lastTypeTime = now;
        if (state.combo > 0 && state.combo % POWER_MILESTONE == 0) {
            triggerShake(view, state, now, 1.0f);
            helper.haptic(view, true);
        }
    }

    float comboIntensity(AnimationState state, long now) {
        if (!settings.powerModeEnabled || state.combo <= 1 || state.lastTypeTime <= 0L) {
            return 0.0f;
        }
        long window = Math.max(200, settings.powerComboWindow);
        long idle = now - state.lastTypeTime;
        if (idle > window * 2L) {
            return 0.0f;
        }
        float fade = idle <= window ? 1.0f : 1.0f - (idle - window) / (float) window;
        float level = Math.min(1.0f, state.combo / (float) POWER_MAX_COMBO);
        return Math.max(0.0f, Math.min(1.0f, level * fade));
    }

    void triggerShake(View view, AnimationState state, long startTime, float scale) {
        if (settings.powerShake <= 0 || scale <= 0.0f || view == null) {
            return;
        }
        state.shakeStartTime = startTime;
        state.shakeAmp = helper.dp(view, settings.powerShake) * scale;
        view.invalidate();
    }

    void applyShake(View view, AnimationState state, XC_MethodHook.MethodHookParam param) {
        try {
            if (state.shakeStartTime <= 0L || state.shakeAmp <= 0.0f) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < state.shakeStartTime) {
                view.invalidate();
                return;
            }
            float progress = (now - state.shakeStartTime) / 320.0f;
            if (progress >= 1.0f) {
                state.shakeStartTime = 0L;
                state.shakeAmp = 0.0f;
                return;
            }
            if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Canvas)) {
                return;
            }
            float falloff = 1.0f - progress;
            float amplitude = state.shakeAmp * falloff * falloff;
            float dx = (float) Math.sin(progress * 42.0f) * amplitude;
            float dy = (float) Math.cos(progress * 33.0f) * amplitude * 0.55f;
            Canvas canvas = (Canvas) param.args[0];
            state.shakeSaveCount = canvas.save();
            canvas.translate(dx, dy);
            view.invalidate();
        } catch (Throwable error) {
            AnimLog.report("applyShake", error);
        }
    }

    void drawComboLabel(EditText view, Canvas canvas, AnimationState state, long now) {
        try {
            if (!settings.powerModeEnabled || !settings.powerShowCombo || state.combo < 2) {
                return;
            }
            float intensity = comboIntensity(state, now);
            if (intensity <= 0.02f) {
                return;
            }
            Layout layout = view.getLayout();
            if (layout == null) {
                return;
            }
            if (state.comboPaint == null) {
                state.comboPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                state.comboPaint.setTextAlign(Paint.Align.LEFT);
            }
            Paint source = view.getPaint();
            float textSize = source == null ? helper.dp(view, 16.0f) : source.getTextSize();
            state.comboPaint.setColor(source == null ? Color.GRAY : source.getColor());
            state.comboPaint.setTextSize(textSize * (0.58f + intensity * 0.30f));
            state.comboPaint.setAlpha(Math.round(Math.min(1.0f, 0.25f + intensity * 0.75f) * 175.0f));
            String label = "x" + state.combo;
            float labelWidth = state.comboPaint.measureText(label);

            CharSequence content = view.getText();
            int textLength = content == null ? 0 : content.length();
            int offset = Math.max(0, Math.min(view.getSelectionEnd(), textLength));
            int line = layout.getLineForOffset(offset);
            float x;
            if (settings.rtlEnabled && layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT) {
                float lineLeft = view.getPaddingLeft() + layout.getLineLeft(line);
                float anchor = Math.min(lineLeft, state.cursorX >= 0.0f ? state.cursorX : lineLeft);
                x = anchor - helper.dp(view, 9.0f) - labelWidth;
                float limit = view.getScrollX() + view.getPaddingLeft() + helper.dp(view, 2.0f);
                if (x < limit) {
                    return;
                }
            } else {
                float lineRight = view.getPaddingLeft() + layout.getLineRight(line);
                float anchor = Math.max(lineRight, state.cursorX >= 0.0f ? state.cursorX : lineRight);
                x = anchor + helper.dp(view, 9.0f);
                float limit = view.getScrollX() + view.getWidth() - view.getPaddingRight() - helper.dp(view, 2.0f);
                if (x + labelWidth > limit) {
                    return;
                }
            }
            float y = helper.textTop(view) + layout.getLineBaseline(line);
            canvas.drawText(label, x, y, state.comboPaint);
            view.invalidate();
        } catch (Throwable error) {
            AnimLog.report("drawComboLabel", error);
        }
    }
}
