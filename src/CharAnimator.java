package com.textanimation;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class CharAnimator {
    private static final int INSERT_WAVE_CAP = 700;
    private static final int IMPLODE_BUDGET = 120;
    private static final int MAX_GHOSTS = 160;
    private static final int HEAT_COLOR = 0xFFFF5A1F;

    private final Settings settings;
    private final ViewHelper helper;
    private final GraphemeClusters clusters;
    private final PowerMode power;
    private final ParticleSpawner spawner;
    private final SparseArray<BlurMaskFilter> blurFilters = new SparseArray<>();

    CharAnimator(Settings settings, ViewHelper helper, GraphemeClusters clusters, PowerMode power, ParticleSpawner spawner) {
        this.settings = settings;
        this.helper = helper;
        this.clusters = clusters;
        this.power = power;
        this.spawner = spawner;
    }

    void shiftExistingAnimations(AnimationState state, int curLen, int prevLen, int commonPrefix, int commonSuffix, int shift) {
        Iterator<Map.Entry<Integer, CharAnimData>> iterator = state.charStartTimes.entrySet().iterator();
        Map<Integer, CharAnimData> shifted = null;
        while (iterator.hasNext()) {
            Map.Entry<Integer, CharAnimData> entry = iterator.next();
            int idx = entry.getKey();
            if (idx < commonPrefix) {
                continue;
            }
            if (idx >= prevLen - commonSuffix) {
                if (shift == 0) {
                    continue;
                }
                int newIdx = idx + shift;
                iterator.remove();
                if (newIdx >= 0 && newIdx < curLen) {
                    if (shifted == null) {
                        shifted = new HashMap<>();
                    }
                    shifted.put(newIdx, entry.getValue());
                }
            } else {
                iterator.remove();
            }
        }
        if (shifted != null) {
            state.charStartTimes.putAll(shifted);
        }
    }

    void addInsertedAnimations(EditText view, AnimationState state, String curText, int start, int end, int lastLineStart, long now) {
        CharSequence editable = view.getText();
        Spannable spannable = editable instanceof Spannable ? (Spannable) editable : null;
        int insertedCount = Math.max(1, end - start);
        boolean block = insertedCount > 1;
        boolean wave = settings.insertWaveEnabled && settings.insertWaveStep > 0 && block;
        boolean implode = settings.implodeEnabled && settings.implodeCount > 0 && block;
        int implodeStep = 1;
        if (implode) {
            int coveredChars = Math.max(1, IMPLODE_BUDGET / Math.max(1, settings.implodeCount));
            implodeStep = Math.max(1, (insertedCount + coveredChars - 1) / coveredChars);
        }
        float intensity = power.comboIntensity(state, now);
        int order = 0;
        clusters.setText(curText);
        int i = clusters.floor(curText, start);
        int limit = clusters.ceil(curText, end);
        while (i < limit) {
            int next = clusters.next(i, limit);
            int charLen = next - i;
            int cp = curText.codePointAt(i);
            String text = curText.substring(i, next);
            boolean onAnimatedLine = settings.animateAllLines || i >= lastLineStart;
            boolean blank = settings.ignoreSpaces && text.trim().isEmpty();
            boolean emoji = clusters.isEmojiLike(spannable, i, next, text);
            if (onAnimatedLine && !blank && !emoji) {
                long waveDelay = wave ? Math.min(INSERT_WAVE_CAP, (long) order * settings.insertWaveStep) : 0L;
                int trigger = settings.charTriggersEnabled && cp == '?' ? CharAnimData.TRIGGER_BOUNCE : CharAnimData.TRIGGER_NONE;
                state.charStartTimes.put(i, new CharAnimData(now + waveDelay, text, charLen, trigger, intensity));
                if (settings.charTriggersEnabled && cp == '!') {
                    power.triggerShake(view, state, now + waveDelay, 0.5f);
                }
                if (implode && order % implodeStep == 0) {
                    spawner.spawnImplodeParticles(view, state, i, text, waveDelay);
                }
                if (settings.powerModeEnabled && settings.powerSparks && !block && intensity > 0.25f) {
                    spawner.spawnStyledParticles(view, state, i, text, 1 + Math.round(intensity * 3.0f), Particle.STYLE_SPARKS, 0.7f);
                }
                order++;
            } else if (emoji && settings.charTriggersEnabled && onAnimatedLine) {
                spawner.spawnStyledParticles(view, state, i, text, 3, Particle.STYLE_SPARKS, 0.8f);
            }
            i = next;
        }
    }

    void updateHiddenSpans(EditText view, AnimationState state) {
        try {
            CharSequence text = view.getText();
            if (!(text instanceof Spannable)) {
                return;
            }
            Spannable spannable = (Spannable) text;
            removeHiddenSpans(view, state);
            if (state.hiddenSpans == null) {
                state.hiddenSpans = new HashMap<>();
            }
            int textLen = text.length();
            for (Map.Entry<Integer, CharAnimData> entry : state.charStartTimes.entrySet()) {
                int idx = entry.getKey();
                CharAnimData data = entry.getValue();
                if (idx < 0 || idx >= textLen) {
                    continue;
                }
                int end = Math.min(textLen, idx + data.length);
                ForegroundColorSpan span = new ForegroundColorSpan(Color.TRANSPARENT);
                spannable.setSpan(span, idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                state.hiddenSpans.put(idx, span);
            }
        } catch (Throwable error) {
            AnimLog.report("updateHiddenSpans", error);
        }
    }

    void removeHiddenSpan(EditText view, AnimationState state, int idx) {
        if (state.hiddenSpans == null) {
            return;
        }
        Object span = state.hiddenSpans.remove(idx);
        CharSequence text = view.getText();
        if (span != null && text instanceof Spannable) {
            ((Spannable) text).removeSpan(span);
        }
    }

    void removeHiddenSpans(View view, AnimationState state) {
        try {
            if (!(view instanceof EditText) || state.hiddenSpans == null || state.hiddenSpans.isEmpty()) {
                return;
            }
            CharSequence text = ((EditText) view).getText();
            if (text instanceof Spannable) {
                Spannable spannable = (Spannable) text;
                for (Object span : state.hiddenSpans.values()) {
                    spannable.removeSpan(span);
                }
            }
            state.hiddenSpans.clear();
        } catch (Throwable error) {
            AnimLog.report("removeHiddenSpans", error);
        }
    }

    void drawAnimatedChars(EditText view, Canvas canvas, AnimationState state) {
        try {
            Paint paint = view.getPaint();
            Layout layout = view.getLayout();
            CharSequence content = view.getText();
            if (paint == null || layout == null || content == null) {
                return;
            }
            long now = System.currentTimeMillis();
            int textLen = content.length();
            int lastLineStart = helper.getLastLineStart(view);
            int padL = view.getPaddingLeft();
            float baseTop = helper.textTop(view);
            int origAlpha = paint.getAlpha();
            float textSize = paint.getTextSize();
            float sharpDelay = Math.min(0.99f, settings.blurTextDelay / 100.0f);
            boolean canShape = Build.VERSION.SDK_INT >= 23;
            if (state.sharedPaint == null) {
                state.sharedPaint = new Paint(paint);
            } else {
                state.sharedPaint.set(paint);
            }
            Paint glyphPaint = state.sharedPaint;
            int textColor = paint.getColor();
            boolean flash = settings.colorFlashEnabled && settings.colorFlashStrength > 0;
            int accent = flash ? helper.accentColor(view, textColor) : textColor;
            float flashStrength = settings.colorFlashStrength / 100.0f;

            for (Map.Entry<Integer, CharAnimData> entry : state.charStartTimes.entrySet()) {
                int idx = entry.getKey();
                CharAnimData data = entry.getValue();
                int end = idx + data.length;
                if (idx < 0 || end > textLen || (!settings.animateAllLines && idx < lastLineStart)) {
                    continue;
                }
                int line = layout.getLineForOffset(idx);
                boolean rtl = helper.isRtlGlyph(layout, idx);
                int contextStart = layout.getLineStart(line);
                int contextEnd = Math.min(textLen, layout.getLineEnd(line));
                boolean shaped = canShape && contextStart <= idx && end <= contextEnd;
                float x = padL + layout.getPrimaryHorizontal(idx);
                float charWidth = 0.0f;
                if (rtl || settings.scaleEnabled || settings.rotateEnabled) {
                    charWidth = shaped
                        ? paint.getRunAdvance(content, idx, end, contextStart, contextEnd, rtl, end)
                        : paint.measureText(data.text);
                }
                if (rtl) {
                    x -= charWidth;
                }
                float y = baseTop + layout.getLineBaseline(line);
                long elapsed = now - data.startTime;
                float rawProgress = Math.max(0.0f, Math.min(1.0f, elapsed / (float) Math.max(1, settings.duration)));
                float rawBlur = Math.max(0.0f, Math.min(1.0f, elapsed / (float) Math.max(1, settings.blurDuration)));
                float progress = Easing.applyEasing(settings.easingCurve, rawProgress);
                float blurProgress = Math.max(0.0f, Math.min(1.0f, Easing.applyEasing(settings.easingCurve, rawBlur)));
                float drawY = y + (settings.slideEnabled ? -helper.dp(view, settings.slideDist) * (1.0f - progress) : 0.0f);
                if (data.trigger == CharAnimData.TRIGGER_BOUNCE && rawProgress < 1.0f) {
                    drawY -= (float) Math.abs(Math.sin(rawProgress * Math.PI * 2.0)) * textSize * 0.30f * (1.0f - rawProgress);
                }

                if (flash) {
                    int hot = data.heat > 0.0f ? ViewHelper.mixRgb(accent, HEAT_COLOR, data.heat) : accent;
                    float glow = Math.min(1.0f, flashStrength * (1.0f + data.heat * 0.5f)) * (1.0f - rawProgress);
                    glyphPaint.setColor(ViewHelper.mixRgb(textColor, hot, glow));
                }

                canvas.save();
                applyGlyphTransform(canvas, x + charWidth / 2.0f, drawY - textSize / 3.0f, progress);
                if (settings.blurEnabled && settings.blurRadius > 0 && blurProgress < 1.0f) {
                    int blurAlpha = (int) ((1.0f - blurProgress) * origAlpha);
                    if (blurAlpha > 4) {
                        glyphPaint.setAlpha(blurAlpha);
                        glyphPaint.setMaskFilter(blurFilter(settings.blurRadius * (1.0f - blurProgress)));
                        drawGlyph(canvas, glyphPaint, content, data, idx, contextStart, contextEnd, rtl, shaped, x, drawY);
                        glyphPaint.setMaskFilter(null);
                    }
                }
                float visibleProgress = blurProgress <= sharpDelay ? 0.0f : (blurProgress - sharpDelay) / (1.0f - sharpDelay);
                int normalAlpha = (int) (visibleProgress * origAlpha);
                if (normalAlpha > 0) {
                    glyphPaint.setAlpha(normalAlpha);
                    drawGlyph(canvas, glyphPaint, content, data, idx, contextStart, contextEnd, rtl, shaped, x, drawY);
                }
                canvas.restore();
            }
        } catch (Throwable error) {
            AnimLog.report("draw chars", error);
        }
    }

    private void applyGlyphTransform(Canvas canvas, float cx, float cy, float progress) {
        if (!settings.scaleEnabled && !settings.rotateEnabled) {
            return;
        }
        canvas.translate(cx, cy);
        if (settings.scaleEnabled) {
            float scale = settings.scaleStart + (1.0f - settings.scaleStart) * progress;
            canvas.scale(scale, scale);
        }
        if (settings.rotateEnabled) {
            canvas.rotate(settings.rotateAngle * (1.0f - progress));
        }
        canvas.translate(-cx, -cy);
    }

    void addDeletedGhosts(EditText view, AnimationState state, Layout layout, int start, String deletedText, long now) {
        try {
            Paint paint = view.getPaint();
            if (layout == null || paint == null || deletedText == null) {
                return;
            }
            CharSequence layoutText = layout.getText();
            int layoutLimit = layoutText == null ? 0 : layoutText.length();
            int padL = view.getPaddingLeft();
            float baseTop = helper.textTop(view);
            int length = deletedText.length();
            clusters.setText(deletedText);
            int i = 0;
            while (i < length && state.ghosts.size() < MAX_GHOSTS) {
                int next = clusters.next(i, length);
                String text = deletedText.substring(i, next);
                if (!text.trim().isEmpty() && !clusters.isEmojiLike(null, 0, 0, text)) {
                    int offset = Math.max(0, Math.min(start + i, layoutLimit));
                    float width = paint.measureText(text);
                    float x = padL + layout.getPrimaryHorizontal(offset);
                    if (helper.isRtlGlyph(layout, offset)) {
                        x -= width;
                    }
                    float baseline = baseTop + layout.getLineBaseline(layout.getLineForOffset(offset));
                    state.ghosts.add(new GhostGlyph(text, x, baseline, width, now));
                }
                i = next;
            }
        } catch (Throwable error) {
            AnimLog.report("addDeletedGhosts", error);
        }
    }

    int ghostDuration() {
        return Math.max(80, settings.duration);
    }

    void drawGhosts(EditText view, Canvas canvas, AnimationState state) {
        try {
            Paint paint = view.getPaint();
            if (paint == null || state.ghosts.isEmpty()) {
                return;
            }
            if (state.sharedPaint == null) {
                state.sharedPaint = new Paint(paint);
            } else {
                state.sharedPaint.set(paint);
            }
            Paint glyphPaint = state.sharedPaint;
            long now = System.currentTimeMillis();
            int origAlpha = paint.getAlpha();
            float textSize = paint.getTextSize();
            float duration = ghostDuration();
            float slide = settings.slideEnabled ? helper.dp(view, settings.slideDist) : 0.0f;
            for (int i = 0; i < state.ghosts.size(); i++) {
                GhostGlyph ghost = state.ghosts.get(i);
                float raw = (now - ghost.startTime) / duration;
                if (raw >= 1.0f) {
                    continue;
                }
                raw = Math.max(0.0f, raw);
                float fade = 1.0f - raw;
                float progress = Easing.applyEasing(settings.easingCurve, fade);
                float visible = Math.max(0.0f, Math.min(1.0f, progress));
                float drawY = ghost.baseline - slide * (1.0f - progress);

                canvas.save();
                applyGlyphTransform(canvas, ghost.x + ghost.width / 2.0f, drawY - textSize / 3.0f, progress);
                float blurRadius = settings.blurEnabled ? settings.blurRadius * (1.0f - visible) : 0.0f;
                if (blurRadius >= 0.5f) {
                    int blurAlpha = (int) (Math.min(1.0f, (1.0f - visible) * 2.0f) * fade * origAlpha);
                    if (blurAlpha > 4) {
                        glyphPaint.setAlpha(blurAlpha);
                        glyphPaint.setMaskFilter(blurFilter(blurRadius));
                        canvas.drawText(ghost.text, ghost.x, drawY, glyphPaint);
                        glyphPaint.setMaskFilter(null);
                    }
                    fade *= visible;
                }
                int alpha = (int) (fade * origAlpha);
                if (alpha > 0) {
                    glyphPaint.setAlpha(alpha);
                    canvas.drawText(ghost.text, ghost.x, drawY, glyphPaint);
                }
                canvas.restore();
            }
        } catch (Throwable error) {
            AnimLog.report("draw ghosts", error);
        }
    }

    private void drawGlyph(Canvas canvas, Paint paint, CharSequence content, CharAnimData data, int idx, int contextStart, int contextEnd, boolean rtl, boolean shaped, float x, float y) {
        if (shaped) {
            canvas.drawTextRun(content, idx, idx + data.length, contextStart, contextEnd, x, y, rtl, paint);
        } else {
            canvas.drawText(data.text, x, y, paint);
        }
    }

    void clearBlurCache() {
        blurFilters.clear();
    }

    private BlurMaskFilter blurFilter(float radius) {
        int key = Math.max(1, Math.round(radius * 2.0f));
        BlurMaskFilter filter = blurFilters.get(key);
        if (filter == null) {
            filter = new BlurMaskFilter(key / 2.0f, BlurMaskFilter.Blur.NORMAL);
            blurFilters.put(key, filter);
        }
        return filter;
    }
}
