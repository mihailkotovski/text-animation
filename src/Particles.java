package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.text.TextPaint;
import android.widget.EditText;

import java.util.List;
import java.util.Random;

final class Particles {
    private static final int LIMIT = 350;
    private static final int BUDGET = 260;
    private static final int CONTOURS = 16;
    private static final float PULL = 0.45f;
    private static final float ABSORB_DP = 14.0f;
    private static final int RIB = 0xFFE0679A;

    private final Particle[] pool = new Particle[LIMIT];
    private final Random random = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path petal = petal();
    private final Path outline = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] points = new float[Config.MAX_AMOUNT * 2];
    private final float[] position = new float[2];
    private final int[] location = new int[2];
    private int count;

    Particles() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        letter.setTextAlign(Paint.Align.CENTER);
    }

    boolean isEmpty() {
        return count == 0;
    }

    void clear() {
        for (int i = 0; i < count; i++) {
            pool[i].text = null;
        }
        count = 0;
    }

    boolean burst(EditText view, Config config, List<Ghost> cells, float[] target) {
        if (cells.isEmpty() || count >= LIMIT) {
            return false;
        }
        TextPaint paint = view.getPaint();
        letter.setTypeface(paint.getTypeface());
        view.getLocationInWindow(location);
        float density = view.getResources().getDisplayMetrics().density;
        float ox = location[0] - view.getScrollX();
        float oy = location[1] - view.getScrollY();
        float minX = location[0];
        float maxX = location[0] + view.getWidth();
        float maxY = location[1] + view.getHeight() - density;
        float absorb = ABSORB_DP * density;
        float textSize = paint.getTextSize();
        int color = paint.getColor();
        boolean fall = config.style == Config.STYLE_FALL;
        int per = fall ? 1 : target != null ? Math.max(1, Math.min(config.amount, BUDGET / cells.size())) : config.amount;
        int before = count;
        for (int c = 0; c < cells.size() && count < LIMIT; c++) {
            Ghost cell = cells.get(c);
            int budget = Math.min(per, LIMIT - count);
            float width = Math.max(1.0f, cell.width);
            float top = cell.baseline - textSize * 0.78f;
            float height = Math.max(1.0f, textSize * 0.82f);
            float cx = cell.x + width * 0.5f;
            float cy = top + height * 0.55f;
            int sampled = fall ? 0 : sample(paint, cell, budget);
            for (int j = 0; j < budget; j++) {
                float px;
                float py;
                if (fall) {
                    px = cx;
                    py = cell.baseline - textSize * 0.35f;
                } else if (j < sampled) {
                    px = points[j * 2];
                    py = points[j * 2 + 1];
                } else {
                    px = cell.x + width * (j + 0.5f) / budget + (random.nextFloat() - 0.5f) * width * 0.6f;
                    py = top + random.nextFloat() * height;
                }
                Particle particle = acquire();
                particle.init(random, config.style, px + ox, py + oy, color, textSize, cell.text);
                if (fall) {
                    particle.bound(minX, maxX, maxY);
                } else {
                    particle.push((px - cx) / Math.max(1.0f, width * 0.5f), (py - cy) / Math.max(1.0f, height * 0.5f));
                }
                if (target != null) {
                    particle.attract(target[0], target[1], PULL, absorb);
                }
            }
        }
        return count > before;
    }

    void step(long elapsed) {
        if (elapsed <= 0) {
            return;
        }
        float scale = Math.max(0.25f, Math.min(3.0f, elapsed / 16.0f));
        for (int i = count - 1; i >= 0; i--) {
            if (!pool[i].step(scale)) {
                release(i);
            }
        }
    }

    void draw(Canvas canvas) {
        for (int i = 0; i < count; i++) {
            Particle particle = pool[i];
            int alpha = Math.round(particle.alpha() * 255.0f);
            if (alpha <= 0) {
                continue;
            }
            switch (particle.style) {
                case Config.STYLE_SNOW:
                    snow(canvas, particle, alpha);
                    break;
                case Config.STYLE_SAKURA:
                    sakura(canvas, particle, alpha);
                    break;
                case Config.STYLE_LETTERS:
                case Config.STYLE_FALL:
                    letter(canvas, particle, alpha);
                    break;
                default:
                    dust(canvas, particle, alpha);
                    break;
            }
        }
    }

    private Particle acquire() {
        Particle particle = pool[count];
        if (particle == null) {
            particle = new Particle();
            pool[count] = particle;
        }
        count++;
        return particle;
    }

    private void release(int index) {
        Particle particle = pool[index];
        particle.text = null;
        count--;
        pool[index] = pool[count];
        pool[count] = particle;
    }

    private int sample(TextPaint paint, Ghost cell, int wanted) {
        outline.reset();
        paint.getTextPath(cell.text, 0, cell.text.length(), cell.x, cell.baseline, outline);
        if (outline.isEmpty()) {
            return 0;
        }
        measure.setPath(outline, false);
        float total = 0.0f;
        int contours = 0;
        do {
            total += measure.getLength();
            contours++;
        } while (contours < CONTOURS && measure.nextContour());
        if (total < 1.0f) {
            return 0;
        }
        measure.setPath(outline, false);
        int filled = 0;
        contours = 0;
        do {
            float length = measure.getLength();
            int share = Math.round(wanted * length / total);
            for (int i = 0; i < share && filled < wanted; i++) {
                put(filled++, random.nextFloat() * length);
            }
            contours++;
        } while (filled < wanted && contours < CONTOURS && measure.nextContour());
        if (filled == 0) {
            return 0;
        }
        if (filled < wanted) {
            measure.setPath(outline, false);
            float length = measure.getLength();
            while (filled < wanted) {
                put(filled++, random.nextFloat() * length);
            }
        }
        return filled;
    }

    private void put(int index, float distance) {
        measure.getPosTan(distance, position, null);
        points[index * 2] = position[0];
        points[index * 2 + 1] = position[1];
    }

    private void dust(Canvas canvas, Particle particle, int alpha) {
        int a = Math.round(alpha * flicker(particle));
        float radius = Math.max(0.8f, particle.size * (0.50f + 0.40f * (1.0f - particle.life)) * 0.5f);
        tint(fill, lighten(particle.color, 0.30f), a * 0.32f);
        canvas.drawCircle(particle.x, particle.y, radius * 2.0f, fill);
        tint(fill, particle.color, a * 0.88f);
        canvas.drawCircle(particle.x, particle.y, radius, fill);
        if (particle.life > 0.5f) {
            tint(fill, lighten(particle.color, 0.70f), a * (particle.life - 0.5f) * 1.7f);
            canvas.drawCircle(particle.x, particle.y, radius * 0.45f, fill);
        }
    }

    private void snow(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        int frost = lighten(particle.color, 0.60f);
        tint(fill, frost, alpha * 0.16f);
        canvas.drawCircle(particle.x, particle.y, size * 0.85f, fill);
        tint(stroke, frost, Math.max(32, alpha));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.13f));
        float radius = Math.max(2.0f, size * 0.5f);
        float branch = radius * 0.35f;
        float knot = radius * 0.38f;
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        for (int i = 0; i < 3; i++) {
            canvas.rotate(60.0f);
            canvas.drawLine(-radius, 0.0f, radius, 0.0f, stroke);
            canvas.drawLine(knot, 0.0f, knot - branch, -branch, stroke);
            canvas.drawLine(knot, 0.0f, knot - branch, branch, stroke);
        }
        canvas.restoreToCount(save);
        tint(fill, lighten(particle.color, 0.85f), alpha * 0.90f);
        canvas.drawCircle(particle.x, particle.y, Math.max(1.0f, size * 0.10f), fill);
    }

    private void sakura(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        float w = Math.max(2.0f, size * 0.62f);
        float h = Math.max(3.0f, size * particle.stretch);
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        tint(fill, lighten(particle.color, 0.45f), alpha * 0.22f);
        drawPetal(canvas, w * 1.30f, h * 1.24f, 0.0f);
        tint(fill, particle.color, Math.max(24, alpha));
        drawPetal(canvas, w, h, 0.0f);
        tint(fill, lighten(particle.color, 0.35f), alpha * 0.45f);
        drawPetal(canvas, w * 0.55f, h * 0.55f, -h * 0.055f);
        tint(stroke, mix(particle.color, RIB, 0.55f), Math.min(160, Math.max(28, alpha * 0.55f)));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.08f));
        canvas.drawLine(0.0f, -h * 0.28f, 0.0f, h * 0.38f, stroke);
        canvas.restoreToCount(save);
    }

    private void drawPetal(Canvas canvas, float width, float height, float offset) {
        int save = canvas.save();
        canvas.translate(0.0f, offset);
        canvas.scale(width, height);
        canvas.drawPath(petal, fill);
        canvas.restoreToCount(save);
    }

    private void letter(Canvas canvas, Particle particle, int alpha) {
        float size = Math.max(6.0f, particle.size);
        float shrink = particle.style == Config.STYLE_FALL ? 1.0f : 0.55f + 0.45f * particle.life;
        tint(letter, particle.color, alpha);
        letter.setTextSize(size);
        int save = canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.scale(shrink, shrink);
        canvas.drawText(particle.text, 0.0f, size * 0.35f, letter);
        canvas.restoreToCount(save);
    }

    private static float flicker(Particle particle) {
        if (particle.flicker <= 0.0f) {
            return 1.0f;
        }
        return 0.80f + 0.20f * (float) Math.sin(particle.time * particle.flicker + particle.flickerPhase);
    }

    private static void tint(Paint paint, int color, float alpha) {
        paint.setColor(color);
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha))));
    }

    private static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    private static int mix(int from, int to, float amount) {
        int r = (from >> 16) & 0xFF;
        int g = (from >> 8) & 0xFF;
        int b = from & 0xFF;
        r += Math.round((((to >> 16) & 0xFF) - r) * amount);
        g += Math.round((((to >> 8) & 0xFF) - g) * amount);
        b += Math.round(((to & 0xFF) - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static Path petal() {
        Path path = new Path();
        path.moveTo(0.0f, -0.5f);
        path.cubicTo(0.95f, -0.58f, 1.03f, 0.02f, 0.22f, 0.42f);
        path.cubicTo(0.10f, 0.49f, 0.04f, 0.52f, 0.0f, 0.56f);
        path.cubicTo(-0.04f, 0.52f, -0.10f, 0.49f, -0.22f, 0.42f);
        path.cubicTo(-1.03f, 0.02f, -0.95f, -0.58f, 0.0f, -0.5f);
        path.close();
        return path;
    }
}
