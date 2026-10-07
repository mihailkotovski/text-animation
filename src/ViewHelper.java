package com.textanimation;

import android.text.Layout;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

final class ViewHelper {
    private final Settings settings;
    private final TelegramReflection telegram;

    ViewHelper(Settings settings, TelegramReflection telegram) {
        this.settings = settings;
        this.telegram = telegram;
    }

    float textTop(TextView view) {
        return view.getExtendedPaddingTop() + telegram.getVerticalOffset(view);
    }

    int getLastLineStart(EditText view) {
        try {
            Layout layout = view.getLayout();
            if (layout != null && layout.getLineCount() > 0) {
                return layout.getLineStart(layout.getLineCount() - 1);
            }
        } catch (Throwable error) {
            AnimLog.report("getLastLineStart", error);
        }
        return 0;
    }

    boolean isRtlGlyph(Layout layout, int offset) {
        if (!settings.rtlEnabled || layout == null) {
            return false;
        }
        try {
            CharSequence text = layout.getText();
            int limit = text == null ? 0 : text.length();
            if (limit <= 0) {
                return false;
            }
            return layout.isRtlCharAt(Math.max(0, Math.min(offset, limit - 1)));
        } catch (Throwable error) {
            AnimLog.report("isRtlGlyph", error);
            return false;
        }
    }

    boolean isRtlView(View view) {
        return settings.rtlEnabled && view != null && view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    int accentColor(View view, int fallback) {
        return telegram.getThemeColorForView(view, telegram.keyChatMessagePanelCursor, fallback);
    }

    static int mixRgb(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    float dp(View view, float value) {
        if (view == null) {
            return value;
        }
        return value * view.getResources().getDisplayMetrics().density;
    }

    void haptic(View view, boolean strong) {
        if (!settings.hapticEnabled || view == null) {
            return;
        }
        try {
            view.performHapticFeedback(
                strong ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            );
        } catch (Throwable error) {
            AnimLog.report("haptic", error);
        }
    }
}
