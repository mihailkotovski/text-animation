package com.textanimation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.EditText;

import java.util.Iterator;

final class ParticleRenderer {
    private final Settings settings;
    private final ViewHelper helper;
    private final TiltSensor tilt;

    ParticleRenderer(Settings settings, ViewHelper helper, TiltSensor tilt) {
        this.settings = settings;
        this.helper = helper;
        this.tilt = tilt;
    }

    void drawParticles(EditText view, Canvas canvas, AnimationState state) {
        if (state.particlePaint == null) {
            state.particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        if (state.particleGlowPaint == null) {
            state.particleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        if (state.particleStrokePaint == null) {
            state.particleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            state.particleStrokePaint.setStyle(Paint.Style.STROKE);
            state.particleStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        }
        if (state.particleTextPaint == null) {
            state.particleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            state.particleTextPaint.setTextAlign(Paint.Align.CENTER);
        }
        state.particleTextPaint.setTypeface(view.getPaint().getTypeface());
        tilt.ensure(view);
        long now = System.currentTimeMillis();
        boolean advance = state.lastParticleUpdateTime == 0L || now - state.lastParticleUpdateTime >= 4L;
        long dt = state.lastParticleUpdateTime == 0L ? 16L : Math.max(8L, Math.min(48L, now - state.lastParticleUpdateTime));
        if (advance) {
            state.lastParticleUpdateTime = now;
        }
        float boundLeft = view.getScrollX();
        float boundRight = boundLeft + view.getWidth();
        float boundBottom = view.getScrollY() + view.getHeight() - helper.dp(view, 1.0f);
        float gravityX = 0.0f;
        float gravityY = 1.0f;
        if (settings.tiltWindEnabled && tilt.tiltRegistered) {
            float weight = Math.min(1.5f, settings.tiltWindStrength / 100.0f);
            gravityX = tilt.tiltX * weight;
            gravityY = 1.0f + (tilt.tiltY - 1.0f) * Math.min(1.0f, weight);
        }
        boolean push = settings.cursorPushParticles && settings.cursorEnabled && state.cursorX >= 0.0f && state.cursorMotion > 0.6f;
        float pushRadius = push ? helper.dp(view, 30.0f) : 0.0f;
        float pushPower = push ? Math.min(2.4f, state.cursorMotion * 0.11f) : 0.0f;
        Iterator<Particle> iterator = state.particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            if (particle.delay <= 0.0f) {
                int alpha = Math.max(0, Math.min(255, (int) (particle.fade() * 255.0f)));
                if (alpha > 0) {
                    drawParticleShape(canvas, state, particle, alpha, particle.color);
                }
                if (push && advance) {
                    particle.applyPush(state.cursorX, state.cursorY, pushRadius, pushPower);
                }
            }
            if (!advance) {
                continue;
            }
            if (particle.bounded) {
                particle.setBounds(boundLeft, boundRight, boundBottom);
            }
            if (!particle.update(dt, gravityX, gravityY)) {
                iterator.remove();
            }
        }
        if (state.particles.isEmpty()) {
            state.lastParticleUpdateTime = 0L;
        }
    }

    private void drawParticleShape(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        if (particle.style == Particle.STYLE_SPARKS) {
            drawSparkParticle(canvas, state, particle, alpha, color);
        } else if (particle.style == Particle.STYLE_SNOW) {
            drawSnowParticle(canvas, state, particle, alpha, color);
        } else if (particle.style == Particle.STYLE_SAKURA) {
            drawSakuraParticle(canvas, state, particle, alpha, color);
        } else if (particle.style == Particle.STYLE_TEXT || particle.style == Particle.STYLE_FALL) {
            drawTextParticle(canvas, state, particle, alpha, color);
        } else {
            drawDustParticle(canvas, state, particle, alpha, color);
        }
    }

    private float flickerFactor(Particle particle) {
        if (particle.flickerSpeed <= 0.0f) {
            return 1.0f;
        }
        return 0.80f + 0.20f * (float) Math.sin(particle.time * particle.flickerSpeed + particle.flickerPhase);
    }

    private void setPaintColor(Paint paint, int color, int alpha) {
        paint.setARGB(
            alpha,
            (color >> 16) & 0xFF,
            (color >> 8) & 0xFF,
            color & 0xFF
        );
    }

    private void drawDustParticle(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        Paint paint = state.particlePaint;
        int a = (int) (alpha * flickerFactor(particle));
        float radius = Math.max(0.8f, particle.size * (0.50f + 0.40f * (1.0f - particle.life)) * 0.5f);
        setPaintColor(paint, lightenColor(color, 0.30f), Math.min(255, (int) (a * 0.32f)));
        canvas.drawCircle(particle.x, particle.y, radius * 2.0f, paint);
        setPaintColor(paint, color, (int) (a * 0.88f));
        canvas.drawCircle(particle.x, particle.y, radius, paint);
        if (particle.life > 0.5f) {
            int coreAlpha = Math.min(255, (int) (a * (particle.life - 0.5f) * 2.0f * 0.85f));
            setPaintColor(paint, lightenColor(color, 0.70f), coreAlpha);
            canvas.drawCircle(particle.x, particle.y, radius * 0.45f, paint);
        }
    }

    private void drawSparkParticle(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        int a = (int) (alpha * flickerFactor(particle));
        float size = Math.max(1.0f, particle.size * (0.45f + 0.55f * particle.life));
        float speed = (float) Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy);
        float dirX;
        float dirY;
        if (speed > 0.05f) {
            dirX = particle.vx / speed;
            dirY = particle.vy / speed;
        } else {
            double radians = Math.toRadians(particle.rotation);
            dirX = (float) Math.cos(radians);
            dirY = (float) Math.sin(radians);
        }
        float tail = Math.min(size * 7.0f, size * 1.6f + speed * 3.2f);
        Paint stroke = state.particleStrokePaint;
        setPaintColor(stroke, color, (int) (a * 0.45f));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.30f));
        canvas.drawLine(particle.x - dirX * tail, particle.y - dirY * tail, particle.x, particle.y, stroke);
        setPaintColor(stroke, lightenColor(color, 0.35f + 0.25f * particle.life), (int) (a * 0.85f));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.42f));
        canvas.drawLine(particle.x - dirX * tail * 0.45f, particle.y - dirY * tail * 0.45f, particle.x, particle.y, stroke);
        Paint fill = state.particlePaint;
        setPaintColor(fill, lightenColor(color, Math.min(0.9f, 0.55f + 0.35f * particle.life)), a);
        canvas.drawCircle(particle.x, particle.y, Math.max(1.0f, size * 0.46f), fill);
    }

    private void drawSnowParticle(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        int frosted = lightenColor(color, 0.60f);
        Paint fill = state.particlePaint;
        setPaintColor(fill, frosted, (int) (alpha * 0.16f));
        canvas.drawCircle(particle.x, particle.y, size * 0.85f, fill);
        Paint stroke = state.particleStrokePaint;
        setPaintColor(stroke, frosted, Math.max(32, alpha));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.13f));
        canvas.save();
        canvas.rotate(particle.rotation, particle.x, particle.y);
        float r = Math.max(2.0f, size * 0.5f);
        for (int i = 0; i < 3; i++) {
            canvas.rotate(60.0f, particle.x, particle.y);
            canvas.drawLine(particle.x - r, particle.y, particle.x + r, particle.y, stroke);
            float branch = r * 0.35f;
            canvas.drawLine(particle.x + r * 0.38f, particle.y, particle.x + r * 0.38f - branch, particle.y - branch, stroke);
            canvas.drawLine(particle.x + r * 0.38f, particle.y, particle.x + r * 0.38f - branch, particle.y + branch, stroke);
        }
        canvas.restore();
        setPaintColor(fill, lightenColor(color, 0.85f), (int) (alpha * 0.90f));
        canvas.drawCircle(particle.x, particle.y, Math.max(1.0f, size * 0.10f), fill);
    }

    private void drawSakuraParticle(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        Paint paint = state.particlePaint;
        int petalColor = color;
        float size = Math.max(2.0f, particle.size * (0.55f + 0.45f * particle.life));
        Path path = state.particlePath;
        if (path == null) {
            path = new Path();
            state.particlePath = path;
        } else {
            path.reset();
        }
        float w = Math.max(2.0f, size * 0.62f);
        float h = Math.max(3.0f, size * particle.stretch);
        path.moveTo(0.0f, -h * 0.5f);
        path.cubicTo(w * 0.95f, -h * 0.58f, w * 1.03f, h * 0.02f, w * 0.22f, h * 0.42f);
        path.cubicTo(w * 0.10f, h * 0.49f, w * 0.04f, h * 0.52f, 0.0f, h * 0.56f);
        path.cubicTo(-w * 0.04f, h * 0.52f, -w * 0.10f, h * 0.49f, -w * 0.22f, h * 0.42f);
        path.cubicTo(-w * 1.03f, h * 0.02f, -w * 0.95f, -h * 0.58f, 0.0f, -h * 0.5f);
        canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.save();
        canvas.scale(1.30f, 1.24f);
        setPaintColor(paint, lightenColor(petalColor, 0.45f), (int) (alpha * 0.22f));
        canvas.drawPath(path, paint);
        canvas.restore();
        setPaintColor(paint, petalColor, Math.max(24, alpha));
        canvas.drawPath(path, paint);
        canvas.save();
        canvas.scale(0.55f, 0.55f);
        canvas.translate(0.0f, -h * 0.10f);
        setPaintColor(paint, lightenColor(petalColor, 0.35f), (int) (alpha * 0.45f));
        canvas.drawPath(path, paint);
        canvas.restore();
        Paint stroke = state.particleStrokePaint;
        setPaintColor(stroke, mixColor(petalColor, 0xE0679A, 0.55f), Math.min(160, Math.max(28, alpha * 11 / 20)));
        stroke.setStrokeWidth(Math.max(1.0f, size * 0.08f));
        canvas.drawLine(0.0f, -h * 0.28f, 0.0f, h * 0.38f, stroke);
        canvas.restore();
    }

    private void drawTextParticle(Canvas canvas, AnimationState state, Particle particle, int alpha, int color) {
        Paint paint = state.particleTextPaint;
        float size = Math.max(6.0f, particle.size);
        float shrink = particle.style == Particle.STYLE_FALL ? 1.0f : 0.55f + 0.45f * particle.life;
        setPaintColor(paint, color, alpha);
        paint.setTextSize(size);
        canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.scale(shrink, shrink);
        canvas.drawText(particle.text, 0.0f, size * 0.35f, paint);
        canvas.restore();
    }

    private int lightenColor(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private int mixColor(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }
}
