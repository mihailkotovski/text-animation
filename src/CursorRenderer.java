package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.Layout;
import android.view.View;
import android.widget.EditText;

final class CursorRenderer {
    private static final long BLINK_FRAME_MS = 32L;

    private final Settings settings;
    private final TelegramReflection telegram;
    private final ViewHelper helper;

    CursorRenderer(Settings settings, TelegramReflection telegram, ViewHelper helper) {
        this.settings = settings;
        this.telegram = telegram;
        this.helper = helper;
    }

    void resetCursorEffects(AnimationState state) {
        state.cursorMotion = 0.0f;
        state.spaceJumpStartTime = 0L;
        state.enterDiveStartTime = 0L;
        state.backspacePulseStartTime = 0L;
        state.selectionStretchStartTime = 0L;
        state.selectionStretchBoost = 0.0f;
        state.lastSelectionOffset = -1;
        resetSelectionHandles(state);
    }

    private void resetSelectionHandles(AnimationState state) {
        state.lastSelectionStart = -1;
        state.lastSelectionEnd = -1;
        state.selectionEffectStartTime = 0L;
        state.selectionStartX = -1.0f;
        state.selectionStartY = -1.0f;
        state.selectionEndX = -1.0f;
        state.selectionEndY = -1.0f;
        state.selectionStartSnap = 0.0f;
        state.selectionEndSnap = 0.0f;
    }

    void triggerSpaceCursorJump(EditText view, AnimationState state, String curText, int start, int end) {
        if (!settings.cursorEnabled || !settings.liquidCursorEnabled || view == null || curText == null) {
            return;
        }
        try {
            Layout layout = view.getLayout();
            if (layout == null) {
                return;
            }
            int spaceOffset = -1;
            int i = Math.max(0, start);
            int safeEnd = Math.min(curText.length(), Math.max(start, end));
            while (i < safeEnd) {
                int cp = curText.codePointAt(i);
                if (cp == ' ') {
                    spaceOffset = i;
                    break;
                }
                i += Character.charCount(cp);
            }
            if (spaceOffset < 0) {
                return;
            }
            int safeOffset = Math.max(0, Math.min(spaceOffset, view.getText().length()));
            int line = layout.getLineForOffset(safeOffset);
            float x = view.getPaddingLeft() + layout.getPrimaryHorizontal(safeOffset);
            float top = helper.textTop(view) + layout.getLineTop(line);
            float bottom = helper.textTop(view) + layout.getLineBottom(line);
            float lineHeight = Math.max(1.0f, bottom - top);
            state.spaceJumpX = x;
            state.spaceJumpY = bottom - lineHeight * 0.08f;
            state.spaceJumpStartTime = System.currentTimeMillis();
        } catch (Throwable error) {
            AnimLog.report("space cursor jump", error);
        }
    }

    void triggerEnterCursorDive(EditText view, AnimationState state, String curText, int start, int end) {
        if (!settings.cursorEnabled || !settings.liquidCursorEnabled || view == null || curText == null) {
            return;
        }
        try {
            int i = Math.max(0, start);
            int safeEnd = Math.min(curText.length(), Math.max(start, end));
            while (i < safeEnd) {
                int cp = curText.codePointAt(i);
                if (cp == '\n') {
                    state.enterDiveStartTime = System.currentTimeMillis();
                    view.invalidate();
                    return;
                }
                i += Character.charCount(cp);
            }
        } catch (Throwable error) {
            AnimLog.report("enter cursor dive", error);
        }
    }

    void triggerBackspaceCursorPulse(EditText view, AnimationState state) {
        if (!settings.cursorEnabled || !settings.liquidCursorEnabled || view == null) {
            return;
        }
        state.backspacePulseStartTime = System.currentTimeMillis();
        view.invalidate();
    }

    void hideSystemCursor(EditText view, AnimationState state) {
        try {
            if (suppressCursorDrawing(view, state)) {
                return;
            }
            if (!state.hasOriginalCursorVisible) {
                state.originalCursorVisible = view.isCursorVisible();
                state.hasOriginalCursorVisible = true;
            }
            if (view.isCursorVisible()) {
                view.setCursorVisible(false);
            }
        } catch (Throwable error) {
            AnimLog.report("hideSystemCursor", error);
        }
    }

    private boolean isCursorWanted(EditText view, AnimationState state) {
        return state.hasOriginalCursorVisible ? state.originalCursorVisible : view.isCursorVisible();
    }

    private boolean suppressCursorDrawing(EditText view, AnimationState state) {
        if (telegram.setAllowDrawCursorMethod != null && telegram.editTextBoldCursorClass != null && telegram.editTextBoldCursorClass.isInstance(view)) {
            try {
                if (!state.cursorDrawSuppressed) {
                    state.originalAllowDrawCursor = readAllowDrawCursor(view);
                    state.cursorDrawSuppressed = true;
                    telegram.setAllowDrawCursorMethod.invoke(view, Boolean.FALSE);
                } else if (telegram.allowDrawCursorField != null && telegram.allowDrawCursorField.getBoolean(view)) {
                    telegram.setAllowDrawCursorMethod.invoke(view, Boolean.FALSE);
                }
                return true;
            } catch (Throwable error) {
                AnimLog.report("suppressCursorDrawing", error);
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                if (!state.cursorDrawableReplaced) {
                    state.originalCursorDrawable = view.getTextCursorDrawable();
                    state.cursorDrawableReplaced = true;
                    view.setTextCursorDrawable(createInvisibleCursorDrawable(view));
                }
                return true;
            } catch (Throwable error) {
                AnimLog.report("suppressCursorDrawing", error);
                state.cursorDrawableReplaced = false;
                state.originalCursorDrawable = null;
            }
        }
        return false;
    }

    private boolean readAllowDrawCursor(EditText view) {
        if (telegram.allowDrawCursorField != null) {
            try {
                return telegram.allowDrawCursorField.getBoolean(view);
            } catch (Throwable error) {
                AnimLog.report("readAllowDrawCursor", error);
            }
        }
        return true;
    }

    private Drawable createInvisibleCursorDrawable(EditText view) {
        ShapeDrawable drawable = new ShapeDrawable(new RectShape());
        drawable.getPaint().setColor(0);
        drawable.setIntrinsicWidth(Math.max(1, Math.round(helper.dp(view, 2.0f))));
        drawable.setIntrinsicHeight(Math.max(1, Math.round(view.getTextSize())));
        return drawable;
    }

    void restoreSystemCursor(View view, AnimationState state) {
        try {
            if (state.cursorDrawSuppressed) {
                state.cursorDrawSuppressed = false;
                if (telegram.setAllowDrawCursorMethod != null && telegram.editTextBoldCursorClass != null && telegram.editTextBoldCursorClass.isInstance(view)) {
                    telegram.setAllowDrawCursorMethod.invoke(view, state.originalAllowDrawCursor ? Boolean.TRUE : Boolean.FALSE);
                }
                state.originalAllowDrawCursor = true;
            }
            if (state.cursorDrawableReplaced) {
                state.cursorDrawableReplaced = false;
                if (view instanceof EditText && android.os.Build.VERSION.SDK_INT >= 29) {
                    ((EditText) view).setTextCursorDrawable(state.originalCursorDrawable);
                }
                state.originalCursorDrawable = null;
            }
            if (view instanceof EditText && state.hasOriginalCursorVisible) {
                ((EditText) view).setCursorVisible(state.originalCursorVisible);
                state.hasOriginalCursorVisible = false;
            }
        } catch (Throwable error) {
            AnimLog.report("restoreSystemCursor", error);
        }
    }

    private boolean getCursorMetrics(EditText view, Layout layout, int offset, float[] out) {
        try {
            int safeOffset = Math.max(0, Math.min(offset, view.getText().length()));
            int line = layout.getLineForOffset(safeOffset);
            float x = view.getPaddingLeft() + layout.getPrimaryHorizontal(safeOffset);
            float top = helper.textTop(view) + layout.getLineTop(line);
            float bottom = helper.textTop(view) + layout.getLineBottom(line);
            out[0] = x;
            out[1] = top;
            out[2] = bottom;
            out[3] = (top + bottom) / 2.0f;
            out[4] = Math.max(1.0f, bottom - top);
            return true;
        } catch (Throwable error) {
            AnimLog.report("getCursorMetrics", error);
            return false;
        }
    }

    private boolean drawSelectionCursorEffects(EditText view, Canvas canvas, AnimationState state, Layout layout, int selectionStart, int selectionEnd, int color, float baseWidth, long now) {
        int start = Math.max(0, Math.min(selectionStart, selectionEnd));
        int end = Math.max(0, Math.max(selectionStart, selectionEnd));
        end = Math.min(end, view.getText().length());
        if (start == end) {
            return false;
        }
        boolean startChanged = state.lastSelectionStart >= 0 && state.lastSelectionStart != start;
        boolean endChanged = state.lastSelectionEnd >= 0 && state.lastSelectionEnd != end;
        if (state.lastSelectionStart != start || state.lastSelectionEnd != end) {
            state.selectionStartSnap = startChanged ? -1.0f : (endChanged ? 0.35f : 0.0f);
            state.selectionEndSnap = endChanged ? 1.0f : (startChanged ? -0.35f : 0.0f);
            state.lastSelectionStart = start;
            state.lastSelectionEnd = end;
            state.selectionEffectStartTime = now;
        }

        float[] startMetrics = new float[5];
        float[] endMetrics = new float[5];
        if (!getCursorMetrics(view, layout, start, startMetrics) || !getCursorMetrics(view, layout, end, endMetrics)) {
            return false;
        }

        float pulseProgress = state.selectionEffectStartTime == 0L ? 1.0f : Math.min(1.0f, (now - state.selectionEffectStartTime) / 360.0f);
        float pulse = (float) Math.sin(pulseProgress * Math.PI);
        if (pulseProgress >= 1.0f) {
            state.selectionStartSnap = 0.0f;
            state.selectionEndSnap = 0.0f;
        }
        float stretchPower = settings.selectionLiquidStretch / 100.0f;
        float sidePower = settings.selectionLiquidSide / 100.0f;
        float strength = 0.88f + Math.min(1.15f, stretchPower * 0.35f + sidePower * 0.22f);
        float factor = Math.max(0.12f, Math.min(0.42f, 0.30f - stretchPower * 0.055f));
        if (state.selectionStartX < 0.0f || state.selectionStartY < 0.0f) {
            state.selectionStartX = startMetrics[0];
            state.selectionStartY = startMetrics[3];
        } else {
            state.selectionStartX += (startMetrics[0] - state.selectionStartX) * factor;
            state.selectionStartY += (startMetrics[3] - state.selectionStartY) * factor;
        }
        if (state.selectionEndX < 0.0f || state.selectionEndY < 0.0f) {
            state.selectionEndX = endMetrics[0];
            state.selectionEndY = endMetrics[3];
        } else {
            state.selectionEndX += (endMetrics[0] - state.selectionEndX) * factor;
            state.selectionEndY += (endMetrics[3] - state.selectionEndY) * factor;
        }

        float dx = endMetrics[0] - startMetrics[0];
        float dy = endMetrics[3] - startMetrics[3];
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float closeDistance = Math.max(helper.dp(view, 18.0f), Math.max(startMetrics[4], endMetrics[4]) * (1.20f + sidePower * 0.45f));
        float magnet = Math.max(0.0f, 1.0f - Math.min(1.0f, distance / closeDistance)) * strength;

        float startLagX = startMetrics[0] - state.selectionStartX;
        float endLagX = endMetrics[0] - state.selectionEndX;
        float startLag = Math.abs(startLagX);
        float endLag = Math.abs(endLagX);

        drawSelectionHandle(canvas, state, startMetrics, state.selectionStartX, state.selectionStartY, color, baseWidth, pulse, magnet, state.selectionStartSnap, -1.0f, startLagX, startLag);
        drawSelectionHandle(canvas, state, endMetrics, state.selectionEndX, state.selectionEndY, color, baseWidth, pulse, magnet, state.selectionEndSnap, 1.0f, endLagX, endLag);
        return pulseProgress < 1.0f
            || startLag > 0.5f
            || endLag > 0.5f
            || Math.abs(startMetrics[3] - state.selectionStartY) > 0.5f
            || Math.abs(endMetrics[3] - state.selectionEndY) > 0.5f;
    }

    private void drawSelectionHandle(Canvas canvas, AnimationState state, float[] metrics, float visualX, float visualY, int color, float baseWidth, float pulse, float magnet, float snapDirection, float side, float lagX, float lag) {
        if (state.cursorPaint == null) {
            state.cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        if (state.cursorRect == null) {
            state.cursorRect = new RectF();
        }
        float lineHeight = metrics[4];
        float targetCenterY = (metrics[1] + metrics[2]) / 2.0f;
        float stretchPower = settings.selectionLiquidStretch / 100.0f;
        float sidePower = settings.selectionLiquidSide / 100.0f;
        float horizontalLiquid = Math.min(1.0f, Math.abs(lagX) / Math.max(baseWidth, lineHeight * 0.22f));
        float totalLiquid = Math.min(1.0f, lag / Math.max(baseWidth, lineHeight * 0.25f));
        float snap = pulse * snapDirection * baseWidth * (1.25f + sidePower * 0.65f);
        float width = Math.max(1.0f, baseWidth * (1.08f + totalLiquid * (0.34f + stretchPower * 0.46f) + pulse * 0.18f + magnet * (0.18f + sidePower * 0.28f)));
        float maxHorizontalStretch = lineHeight * (0.34f + stretchPower * 0.78f + sidePower * 0.34f);
        float stretch = Math.min(maxHorizontalStretch, lag * (0.38f + stretchPower * 0.72f) + pulse * baseWidth * (0.55f + stretchPower * 1.25f));
        float sideOffset = side * width * (0.14f + magnet * (0.18f + sidePower * 0.30f) + horizontalLiquid * sidePower * 0.22f);
        float x = visualX + sideOffset + snap + lagX * sidePower * 0.16f;
        float centerShift = (visualY - targetCenterY) * 0.24f;
        float top = metrics[1] + lineHeight * (0.10f - pulse * 0.030f) + centerShift;
        float bottom = metrics[2] - lineHeight * (0.10f - pulse * 0.018f) + centerShift;
        float leadingStretch = stretch * (0.25f + horizontalLiquid * 0.30f);
        float trailingStretch = stretch;
        float left = x - width / 2.0f - (side < 0.0f ? trailingStretch : leadingStretch);
        float right = x + width / 2.0f + (side > 0.0f ? trailingStretch : leadingStretch);
        state.cursorRect.set(left, top, right, bottom);
        state.cursorPaint.setColor(color);
        state.cursorPaint.setAlpha(255);
        canvas.drawRoundRect(state.cursorRect, width, width, state.cursorPaint);
    }

    void drawCursor(EditText view, Canvas canvas, AnimationState state) {
        try {
            if (!view.isFocused() || view.getSelectionStart() < 0 || view.getLayout() == null || !isCursorWanted(view, state)) {
                return;
            }
            Layout layout = view.getLayout();
            int selectionStart = Math.max(0, Math.min(view.getSelectionStart(), view.getText().length()));
            int selectionEnd = Math.max(0, Math.min(view.getSelectionEnd(), view.getText().length()));
            boolean hasSelection = selectionStart != selectionEnd;
            if (hasSelection && settings.selectionCursorEffect == 0) {
                return;
            }
            int offset = hasSelection ? selectionEnd : selectionStart;
            int line = layout.getLineForOffset(offset);
            float targetX = view.getPaddingLeft() + layout.getPrimaryHorizontal(offset);
            float top = helper.textTop(view) + layout.getLineTop(line);
            float bottom = helper.textTop(view) + layout.getLineBottom(line);
            float targetY = (top + bottom) / 2.0f;

            long now = System.currentTimeMillis();
            float dt = state.lastCursorDrawTime == 0 ? 16.0f : Math.min(50.0f, now - state.lastCursorDrawTime);
            state.lastCursorDrawTime = now;
            if (settings.liquidCursorEnabled) {
                if (state.lastSelectionOffset >= 0 && state.lastSelectionOffset != offset) {
                    int selectionDelta = Math.abs(offset - state.lastSelectionOffset);
                    if (selectionDelta > 2) {
                        state.selectionStretchBoost = Math.min(1.0f, selectionDelta / 18.0f);
                        state.selectionStretchStartTime = now;
                    }
                }
                state.lastSelectionOffset = offset;
            }
            float factor = Math.min(1.0f, Math.max(0.05f, settings.cursorSpeed / 100.0f) * (dt / 16.0f));
            if (state.cursorX < 0 || state.cursorY < 0) {
                state.cursorX = targetX;
                state.cursorY = targetY;
            } else {
                state.cursorX += (targetX - state.cursorX) * factor;
                state.cursorY += (targetY - state.cursorY) * factor;
            }
            float deltaX = targetX - state.cursorX;
            float deltaY = targetY - state.cursorY;
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            state.cursorMotion += (distance - state.cursorMotion) * Math.min(1.0f, 0.32f * (dt / 16.0f));

            if (state.cursorPaint == null) {
                state.cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            if (state.cursorRect == null) {
                state.cursorRect = new RectF();
            }
            int color = telegram.getThemeColorForView(view, telegram.keyChatMessagePanelCursor, view.getPaint().getColor());
            state.cursorPaint.setColor(color);
            state.cursorPaint.setAlpha(255);

            float baseWidth = Math.max(1.0f, helper.dp(view, settings.cursorWidth));
            if (hasSelection) {
                if (drawSelectionCursorEffects(view, canvas, state, layout, selectionStart, selectionEnd, color, baseWidth, now)) {
                    view.postInvalidateOnAnimation();
                }
                return;
            }
            resetSelectionHandles(state);
            float visualX = state.cursorX;
            float visualY = state.cursorY;
            float lineHeight = Math.max(1.0f, bottom - top);
            float jumpPulse = 0.0f;
            if (settings.liquidCursorEnabled && state.spaceJumpStartTime > 0L) {
                float jumpProgress = Math.min(1.0f, (now - state.spaceJumpStartTime) / 260.0f);
                jumpPulse = (float) Math.sin(jumpProgress * Math.PI);
                visualX += (state.spaceJumpX - visualX) * jumpPulse;
                visualY += (state.spaceJumpY - visualY) * jumpPulse;
                if (jumpProgress >= 1.0f) {
                    state.spaceJumpStartTime = 0L;
                    state.spaceJumpX = -1.0f;
                    state.spaceJumpY = -1.0f;
                    jumpPulse = 0.0f;
                }
            }
            float enterDivePulse = 0.0f;
            if (settings.liquidCursorEnabled && state.enterDiveStartTime > 0L) {
                float diveProgress = Math.min(1.0f, (now - state.enterDiveStartTime) / 340.0f);
                enterDivePulse = (float) Math.sin(diveProgress * Math.PI);
                visualY += lineHeight * 0.28f * enterDivePulse;
                if (diveProgress >= 1.0f) {
                    state.enterDiveStartTime = 0L;
                    enterDivePulse = 0.0f;
                }
            }
            float backspacePulse = 0.0f;
            if (settings.liquidCursorEnabled && state.backspacePulseStartTime > 0L) {
                float backspaceProgress = Math.min(1.0f, (now - state.backspacePulseStartTime) / 190.0f);
                backspacePulse = (float) Math.sin(backspaceProgress * Math.PI);
                if (backspaceProgress >= 1.0f) {
                    state.backspacePulseStartTime = 0L;
                    backspacePulse = 0.0f;
                }
            }
            float selectionPulse = 0.0f;
            if (settings.liquidCursorEnabled && state.selectionStretchStartTime > 0L) {
                float selectionProgress = Math.min(1.0f, (now - state.selectionStretchStartTime) / 420.0f);
                selectionPulse = (float) Math.sin(selectionProgress * Math.PI) * state.selectionStretchBoost;
                if (selectionProgress >= 1.0f) {
                    state.selectionStretchStartTime = 0L;
                    state.selectionStretchBoost = 0.0f;
                    selectionPulse = 0.0f;
                }
            }

            float motionLimit = Math.max(baseWidth, helper.dp(view, Math.max(1, settings.liquidScaleFactor)));
            float motionProgress = settings.liquidCursorEnabled ? Math.min(1.0f, state.cursorMotion / motionLimit) : 0.0f;
            float stretchLimit = helper.dp(view, settings.liquidScaleFactor) * (1.0f + selectionPulse * 0.70f);
            float stretch = settings.liquidCursorEnabled
                ? Math.min(stretchLimit, state.cursorMotion * (0.46f + selectionPulse * 0.28f) + baseWidth * 1.4f * jumpPulse + baseWidth * 1.8f * selectionPulse)
                : 0.0f;
            float dynamicWidth = baseWidth * (1.0f + motionProgress * 0.22f + jumpPulse * 0.28f + enterDivePulse * 0.10f + selectionPulse * 0.14f - backspacePulse * 0.34f);
            dynamicWidth = Math.max(baseWidth * 0.55f, dynamicWidth);
            float direction = Math.abs(deltaX) > 0.1f ? (deltaX > 0.0f ? 1.0f : -1.0f) : (targetX >= visualX ? 1.0f : -1.0f);
            float left = visualX - dynamicWidth / 2.0f - (direction < 0.0f ? stretch : 0.0f);
            float right = visualX + dynamicWidth / 2.0f + (direction >= 0.0f ? stretch : 0.0f);
            float centerY = visualY;
            float heightInset = Math.max(0.0f, lineHeight * (0.08f + motionProgress * 0.18f + jumpPulse * 0.10f + enterDivePulse * 0.08f + backspacePulse * 0.14f));
            float rectTop = top + heightInset + (centerY - targetY) * 0.12f;
            float rectBottom = bottom - heightInset + (centerY - targetY) * 0.12f;
            float roundRadius = dynamicWidth;
            if (jumpPulse > 0.0f) {
                float circleMorph = jumpPulse;
                float circleSize = Math.max(baseWidth * 2.7f, lineHeight * 0.36f);
                float circleLeft = visualX - circleSize / 2.0f;
                float circleRight = visualX + circleSize / 2.0f;
                float circleTop = visualY - circleSize;
                float circleBottom = visualY;
                left += (circleLeft - left) * circleMorph;
                right += (circleRight - right) * circleMorph;
                rectTop += (circleTop - rectTop) * circleMorph;
                rectBottom += (circleBottom - rectBottom) * circleMorph;
                roundRadius += (circleSize / 2.0f - roundRadius) * circleMorph;
            }
            state.cursorRect.set(left, rectTop, right, rectBottom);

            boolean cursorActive = distance > 0.5f
                || state.cursorMotion > 0.35f
                || jumpPulse > 0.001f
                || enterDivePulse > 0.001f
                || backspacePulse > 0.001f
                || selectionPulse > 0.001f;
            float blinkAlpha = updateCursorBlink(state, now, cursorActive);
            if (blinkAlpha > 0.004f) {
                state.cursorPaint.setAlpha(Math.round(Math.max(0.0f, Math.min(1.0f, blinkAlpha)) * 255.0f));
                canvas.drawRoundRect(state.cursorRect, roundRadius, roundRadius, state.cursorPaint);
            }

            boolean settling = Math.abs(targetX - state.cursorX) > 0.5f
                || Math.abs(targetY - state.cursorY) > 0.5f
                || state.cursorMotion > 0.2f
                || state.spaceJumpStartTime > 0L
                || state.enterDiveStartTime > 0L
                || state.backspacePulseStartTime > 0L
                || state.selectionStretchStartTime > 0L;
            if (settling) {
                view.postInvalidateOnAnimation();
            } else if (settings.cursorBlinkEnabled) {
                scheduleBlinkFrame(view, state, now);
            }
        } catch (Throwable error) {
            AnimLog.report("cursor draw", error);
        }
    }

    private float updateCursorBlink(AnimationState state, long now, boolean active) {
        if (!settings.cursorBlinkEnabled || active || state.cursorBlinkAnchor <= 0L || now < state.cursorBlinkAnchor) {
            state.cursorBlinkAnchor = now;
            return 1.0f;
        }
        float half = settings.cursorBlinkPeriod / 2.0f;
        float fade = half * (settings.cursorBlinkSmoothness / 100.0f);
        float hold = half - fade;
        float elapsed = (now - state.cursorBlinkAnchor) % (long) settings.cursorBlinkPeriod;
        float alpha;
        if (elapsed < hold) {
            alpha = 1.0f;
        } else if (elapsed < half) {
            alpha = 1.0f - Easing.smoothStep((elapsed - hold) / Math.max(1.0f, fade));
        } else if (elapsed < half + hold) {
            alpha = 0.0f;
        } else {
            alpha = Easing.smoothStep((elapsed - half - hold) / Math.max(1.0f, fade));
        }
        return alpha;
    }

    private void scheduleBlinkFrame(View view, AnimationState state, long now) {
        float half = settings.cursorBlinkPeriod / 2.0f;
        float hold = half - half * (settings.cursorBlinkSmoothness / 100.0f);
        float elapsed = Math.max(0L, now - state.cursorBlinkAnchor) % (long) settings.cursorBlinkPeriod;
        float phase = elapsed < half ? elapsed : elapsed - half;
        long delay = phase < hold ? Math.max(1L, (long) Math.ceil(hold - phase)) : BLINK_FRAME_MS;
        long wake = now + delay;
        if (state.cursorWakeTime > now && state.cursorWakeTime <= wake) {
            return;
        }
        state.cursorWakeTime = wake;
        view.postInvalidateDelayed(delay);
    }
}
