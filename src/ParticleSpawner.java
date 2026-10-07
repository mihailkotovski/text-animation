package com.textanimation;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.text.Layout;
import android.widget.EditText;

final class ParticleSpawner {
    private static final java.util.Random SPAWN_RANDOM = new java.util.Random();
    private static final int MAX_PARTICLES = 350;
    private static final int SEND_BURST_BUDGET = 260;

    private final Settings settings;
    private final ViewHelper helper;
    private final GraphemeClusters clusters;

    ParticleSpawner(Settings settings, ViewHelper helper, GraphemeClusters clusters) {
        this.settings = settings;
        this.helper = helper;
        this.clusters = clusters;
    }

    Layout deletionLayout(EditText view, AnimationState state, int start, String deletedText) {
        Layout layout = view.getLayout();
        CharSequence remainingText = view.getText();
        int remaining = remainingText == null ? 0 : remainingText.length();
        if (deletedText.length() > 1 && (layout == null || start + deletedText.length() > remaining)) {
            Layout ghost = buildGhostLayout(view, state.prevText);
            if (ghost != null) {
                layout = ghost;
            }
        }
        return layout;
    }

    void spawnDeleteParticles(EditText view, AnimationState state, Layout layout, int start, String deletedText, boolean sendBurst) {
        if (deletedText == null || deletedText.length() == 0 || settings.particleCount <= 0 || layout == null) {
            return;
        }
        clusters.setText(deletedText);
        int deletedLength = deletedText.length();
        int charCount = 0;
        int scan = 0;
        while (scan < deletedLength) {
            int next = clusters.next(scan, deletedLength);
            if (!settings.ignoreSpaces || !deletedText.substring(scan, next).trim().isEmpty()) {
                charCount++;
            }
            scan = next;
        }
        if (charCount == 0) {
            return;
        }
        int perChar = settings.particleCount;
        float attractX = 0.0f;
        float attractY = 0.0f;
        float pull = 0.0f;
        if (sendBurst) {
            float power = Math.max(0.1f, settings.sendBurstPower / 100.0f);
            perChar = Math.max(1, Math.min(settings.particleCount, SEND_BURST_BUDGET / charCount));
            if (state.hasSendTarget) {
                attractX = state.sendTargetX;
                attractY = state.sendTargetY;
            } else {
                attractX = helper.isRtlView(view)
                    ? view.getScrollX() - helper.dp(view, 36.0f)
                    : view.getScrollX() + view.getWidth() + helper.dp(view, 36.0f);
                attractY = view.getScrollY() + view.getHeight() - helper.dp(view, 2.0f);
            }
            pull = 0.45f * power;
        }
        int i = 0;
        while (i < deletedLength) {
            int next = clusters.next(i, deletedLength);
            String text = deletedText.substring(i, next);
            if (!settings.ignoreSpaces || !text.trim().isEmpty()) {
                spawnDeleteParticlesAt(view, state, layout, start + i, text, perChar, attractX, attractY, pull);
            }
            i = next;
        }
    }

    private Layout buildGhostLayout(EditText view, String text) {
        try {
            if (text == null || text.length() == 0) {
                return null;
            }
            int width = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
            if (width <= 0) {
                return null;
            }
            return new android.text.StaticLayout(
                text,
                view.getPaint(),
                width,
                Layout.Alignment.ALIGN_NORMAL,
                view.getLineSpacingMultiplier(),
                view.getLineSpacingExtra(),
                false
            );
        } catch (Throwable error) {
            AnimLog.report("buildGhostLayout", error);
            return null;
        }
    }

    private void spawnDeleteParticlesAt(EditText view, AnimationState state, Layout layout, int offset, String text, int count, float attractX, float attractY, float pull) {
        try {
            Paint paint = view.getPaint();
            if (layout == null || paint == null) {
                return;
            }
            boolean fall = settings.particleStyle == Particle.STYLE_FALL;
            int budget = Math.min(fall ? 1 : count, MAX_PARTICLES - state.particles.size());
            if (budget <= 0) {
                return;
            }
            int safeOffset = Math.max(0, Math.min(offset, Math.max(0, layout.getText() == null ? 0 : layout.getText().length())));
            int line = layout.getLineForOffset(safeOffset);
            float x = view.getPaddingLeft() + layout.getPrimaryHorizontal(safeOffset);
            float baseline = helper.textTop(view) + layout.getLineBaseline(line);
            float textSize = paint.getTextSize();
            float textWidth = Math.max(1.0f, paint.measureText(text));
            if (helper.isRtlGlyph(layout, safeOffset)) {
                x -= textWidth;
            }
            int color = paint.getColor();
            float glyphTop = baseline - textSize * 0.78f;
            float glyphHeight = Math.max(1.0f, textSize * 0.82f);
            float centerX = x + textWidth * 0.5f;
            float centerY = glyphTop + glyphHeight * 0.55f;
            float[] outline = settings.glyphParticles && !fall ? sampleGlyphOutline(state, paint, text, x, baseline, budget) : null;
            float absorbRadius = helper.dp(view, 14.0f);
            for (int j = 0; j < budget; j++) {
                float px;
                float py;
                if (fall) {
                    px = centerX;
                    py = baseline - Math.max(6.0f, textSize * settings.particleSize) * 0.35f;
                } else if (outline != null) {
                    px = outline[j * 2];
                    py = outline[j * 2 + 1];
                } else {
                    float fx = (j + 0.5f) / Math.max(1, count);
                    px = x + textWidth * fx + (SPAWN_RANDOM.nextFloat() - 0.5f) * textWidth * 0.6f;
                    py = glyphTop + SPAWN_RANDOM.nextFloat() * glyphHeight;
                }
                Particle particle = new Particle(px, py, color, textSize, settings.particleSpeed, settings.particleSize, settings.particleStyle, text, settings.particleSpread);
                if (!fall) {
                    float outwardX = (px - centerX) / Math.max(1.0f, textWidth * 0.5f);
                    float outwardY = (py - centerY) / Math.max(1.0f, glyphHeight * 0.5f);
                    particle.vx += outwardX * (0.6f + settings.particleSpread * 0.9f) * settings.particleSpeed;
                    particle.vy += outwardY * 0.5f * settings.particleSpeed;
                }
                if (pull > 0.0f) {
                    particle.attractTo(attractX, attractY, pull, absorbRadius);
                    particle.bounded = false;
                    particle.gravity *= 0.30f;
                    particle.decay *= 0.80f;
                    float dx = attractX - px;
                    float dy = attractY - py;
                    float cruise = Math.max(1.0f, pull / Math.max(0.01f, particle.drag));
                    float frames = Math.min(150.0f, (float) Math.sqrt(dx * dx + dy * dy) / cruise + 12.0f);
                    particle.decay = Math.min(particle.decay, 0.5f / frames);
                }
                state.particles.add(particle);
            }
        } catch (Throwable error) {
            AnimLog.report("particle spawn", error);
        }
    }

    private float[] sampleGlyphOutline(AnimationState state, Paint paint, String text, float x, float baseline, int count) {
        try {
            if (count <= 0 || text == null || text.trim().isEmpty()) {
                return null;
            }
            Path path = state.glyphPath;
            if (path == null) {
                path = new Path();
                state.glyphPath = path;
            } else {
                path.reset();
            }
            paint.getTextPath(text, 0, text.length(), x, baseline, path);
            if (path.isEmpty()) {
                return null;
            }
            PathMeasure measure = state.pathMeasure;
            if (measure == null) {
                measure = new PathMeasure();
                state.pathMeasure = measure;
            }
            measure.setPath(path, false);
            float total = 0.0f;
            int contours = 0;
            do {
                total += measure.getLength();
                contours++;
            } while (contours < 16 && measure.nextContour());
            if (total < 1.0f) {
                return null;
            }
            float[] result = new float[count * 2];
            float[] position = new float[2];
            measure.setPath(path, false);
            int filled = 0;
            contours = 0;
            do {
                float length = measure.getLength();
                if (length > 0.5f) {
                    int share = Math.round(count * (length / total));
                    for (int i = 0; i < share && filled < count; i++) {
                        measure.getPosTan(SPAWN_RANDOM.nextFloat() * length, position, null);
                        result[filled * 2] = position[0];
                        result[filled * 2 + 1] = position[1];
                        filled++;
                    }
                }
                contours++;
            } while (filled < count && contours < 16 && measure.nextContour());
            if (filled == 0) {
                return null;
            }
            if (filled < count) {
                measure.setPath(path, false);
                float length = Math.max(1.0f, measure.getLength());
                while (filled < count) {
                    measure.getPosTan(SPAWN_RANDOM.nextFloat() * length, position, null);
                    result[filled * 2] = position[0];
                    result[filled * 2 + 1] = position[1];
                    filled++;
                }
            }
            return result;
        } catch (Throwable error) {
            AnimLog.report("sampleGlyphOutline", error);
            return null;
        }
    }

    void spawnImplodeParticles(EditText view, AnimationState state, int offset, String text, long delayMs) {
        try {
            Layout layout = view.getLayout();
            Paint paint = view.getPaint();
            if (layout == null || paint == null) {
                return;
            }
            int budget = Math.min(settings.implodeCount, MAX_PARTICLES - state.particles.size());
            if (budget <= 0) {
                return;
            }
            CharSequence content = view.getText();
            int safeOffset = Math.max(0, Math.min(offset, content == null ? 0 : content.length()));
            int line = layout.getLineForOffset(safeOffset);
            float x = view.getPaddingLeft() + layout.getPrimaryHorizontal(safeOffset);
            float baseline = helper.textTop(view) + layout.getLineBaseline(line);
            float textSize = paint.getTextSize();
            float textWidth = Math.max(1.0f, paint.measureText(text));
            if (helper.isRtlGlyph(layout, safeOffset)) {
                x -= textWidth;
            }
            float centerX = x + textWidth * 0.5f;
            float centerY = baseline - textSize * 0.32f;
            int color = paint.getColor();
            float radius = Math.max(textSize * 0.95f, helper.dp(view, 13.0f)) * (0.7f + settings.particleSpread * 0.6f);
            float delayFrames = delayMs / 16.0f;
            int style = settings.particleStyle == Particle.STYLE_FALL ? Particle.STYLE_TEXT : settings.particleStyle;
            for (int j = 0; j < budget; j++) {
                double angle = (j / (double) budget) * Math.PI * 2.0 + SPAWN_RANDOM.nextFloat() * 0.8;
                float distance = radius * (0.7f + SPAWN_RANDOM.nextFloat() * 0.8f);
                float px = centerX + (float) Math.cos(angle) * distance;
                float py = centerY + (float) Math.sin(angle) * distance * 0.78f;
                Particle particle = new Particle(px, py, color, textSize, settings.particleSpeed, settings.particleSize * 0.75f, style, text, settings.particleSpread);
                particle.becomeImplode(centerX, centerY, Math.max(140, settings.duration), delayFrames);
                state.particles.add(particle);
            }
        } catch (Throwable error) {
            AnimLog.report("implode spawn", error);
        }
    }

    void spawnStyledParticles(EditText view, AnimationState state, int offset, String text, int count, int style, float sizeScale) {
        try {
            Layout layout = view.getLayout();
            Paint paint = view.getPaint();
            if (layout == null || paint == null) {
                return;
            }
            int budget = Math.min(count, MAX_PARTICLES - state.particles.size());
            if (budget <= 0) {
                return;
            }
            CharSequence content = view.getText();
            int safeOffset = Math.max(0, Math.min(offset, content == null ? 0 : content.length()));
            int line = layout.getLineForOffset(safeOffset);
            float x = view.getPaddingLeft() + layout.getPrimaryHorizontal(safeOffset);
            float baseline = helper.textTop(view) + layout.getLineBaseline(line);
            float textSize = paint.getTextSize();
            float textWidth = Math.max(1.0f, paint.measureText(text));
            if (helper.isRtlGlyph(layout, safeOffset)) {
                x -= textWidth;
            }
            int color = paint.getColor();
            for (int j = 0; j < budget; j++) {
                float px = x + textWidth * SPAWN_RANDOM.nextFloat();
                float py = baseline - textSize * (0.20f + SPAWN_RANDOM.nextFloat() * 0.60f);
                state.particles.add(new Particle(px, py, color, textSize, settings.particleSpeed, settings.particleSize * sizeScale, style, text, settings.particleSpread));
            }
        } catch (Throwable error) {
            AnimLog.report("styled particle spawn", error);
        }
    }
}
