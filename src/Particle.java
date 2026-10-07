package com.textanimation;

import java.util.Random;

public class Particle {
    public static final int STYLE_DOTS = 0;
    public static final int STYLE_SPARKS = 1;
    public static final int STYLE_SNOW = 2;
    public static final int STYLE_SAKURA = 3;
    public static final int STYLE_TEXT = 4;
    public static final int STYLE_FALL = 5;

    public static final int MODE_BURST = 0;
    public static final int MODE_IMPLODE = 1;

    private static final Random RANDOM = new Random();
    private static final float TWO_PI = 6.2831855f;
    private static final int[] SAKURA_PALETTE = {
        0xFFD7E4,
        0xFFC2D6,
        0xFFAECB,
        0xFB98BC,
    };

    public float x;
    public float y;
    public float vx;
    public float vy;
    public float life = 1.0f;
    public int color;
    public float size;
    public float decay;
    public int style;
    public String text;
    public float rotation;
    public float angularVelocity;
    public float spinDamping = 1.0f;
    public float gravity;
    public float drag;
    public float wind;
    public float wobble;
    public float wobblePhase;
    public float wobbleSpeed = 0.08f;
    public float stretch = 1.0f;
    public float turbAmp;
    public float turbFreqX;
    public float turbFreqY;
    public float turbPhaseX;
    public float turbPhaseY;
    public float flickerSpeed;
    public float flickerPhase;
    public float time;

    public int mode = MODE_BURST;
    public float originX;
    public float originY;
    public float homeX;
    public float homeY;
    public float attractX;
    public float attractY;
    public float attractPull;
    public float attractRadius;
    public float delay;

    public boolean bounded;
    public float boundLeft;
    public float boundRight;
    public float boundBottom;
    public float restitution;

    public Particle(float x, float y, int color, float textSize, float speed, float sizeMultiplier, int style, String text, float spread) {
        this.x = x;
        this.y = y;
        this.originX = x;
        this.originY = y;
        this.color = color;
        this.style = style;
        this.text = text == null || text.length() == 0 ? "*" : text;
        this.rotation = RANDOM.nextFloat() * 360.0f;
        this.wobblePhase = RANDOM.nextFloat() * TWO_PI;
        this.flickerPhase = RANDOM.nextFloat() * TWO_PI;
        this.turbPhaseX = RANDOM.nextFloat() * TWO_PI;
        this.turbPhaseY = RANDOM.nextFloat() * TWO_PI;
        configure(textSize, Math.max(0.1f, speed), Math.max(0.1f, sizeMultiplier), Math.max(0.0f, spread));
    }

    private float rand(float min, float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    private void configure(float textSize, float speed, float sizeMultiplier, float spread) {
        float base = Math.max(1.0f, textSize);
        float spreadX = 0.35f + spread * 0.95f;
        float spreadY = 0.55f + spread * 0.70f;
        if (style == STYLE_SPARKS) {
            float angle = RANDOM.nextFloat() * TWO_PI;
            float force = rand(2.6f, 7.0f) * speed;
            vx = (float) Math.cos(angle) * force * spreadX;
            vy = (float) Math.sin(angle) * force * spreadY * 0.85f - rand(1.2f, 2.4f) * speed;
            size = Math.max(1.0f, rand(base / 12.0f, base / 7.5f) * sizeMultiplier);
            decay = RANDOM.nextFloat() < 0.3f ? rand(0.045f, 0.075f) : rand(0.020f, 0.040f);
            gravity = 0.11f;
            drag = 0.055f;
            wind = rand(-0.012f, 0.012f) * speed;
            turbAmp = 0.020f * speed;
            turbFreqX = rand(0.14f, 0.30f);
            turbFreqY = rand(0.12f, 0.26f);
            flickerSpeed = rand(0.55f, 1.10f);
            angularVelocity = rand(-12.0f, 12.0f);
        } else if (style == STYLE_SNOW) {
            vx = rand(-0.8f, 0.8f) * speed * spreadX;
            vy = rand(-0.6f, 0.25f) * speed * spreadY;
            size = Math.max(2.0f, rand(base / 9.0f, base / 5.5f) * sizeMultiplier);
            decay = rand(0.007f, 0.015f);
            gravity = 0.014f;
            drag = 0.025f;
            wind = rand(0.004f, 0.014f) * speed;
            wobble = rand(0.45f, 1.30f);
            wobbleSpeed = rand(0.050f, 0.090f);
            angularVelocity = rand(-2.5f, 2.5f);
            spinDamping = 0.998f;
            turbAmp = 0.012f * speed;
            turbFreqX = rand(0.06f, 0.14f);
            turbFreqY = rand(0.05f, 0.12f);
        } else if (style == STYLE_SAKURA) {
            color = SAKURA_PALETTE[RANDOM.nextInt(SAKURA_PALETTE.length)];
            vx = rand(-0.9f, 1.2f) * speed * spreadX * 0.8f + rand(0.05f, 0.30f) * speed;
            vy = rand(-1.0f, 0.15f) * speed * spreadY * 0.55f;
            size = Math.max(2.6f, rand(base / 6.8f, base / 4.4f) * sizeMultiplier);
            decay = rand(0.006f, 0.013f);
            gravity = 0.030f;
            drag = 0.030f;
            wind = rand(0.004f, 0.014f) * speed * (0.55f + spread * 0.45f);
            wobble = rand(0.8f, 1.8f) * (0.6f + spread * 0.4f);
            wobbleSpeed = rand(0.045f, 0.075f);
            angularVelocity = rand(-2.2f, 2.2f);
            spinDamping = 0.985f;
            stretch = rand(1.45f, 2.0f);
            turbAmp = 0.008f * speed;
            turbFreqX = rand(0.05f, 0.12f);
            turbFreqY = rand(0.04f, 0.10f);
        } else if (style == STYLE_TEXT) {
            float angle = -1.5707964f + rand(-1.1f, 1.1f);
            float force = rand(1.6f, 3.8f) * speed;
            vx = (float) Math.cos(angle) * force * spreadX;
            vy = (float) Math.sin(angle) * force * spreadY;
            size = Math.max(4.0f, base * rand(0.50f, 0.72f) * sizeMultiplier);
            decay = rand(0.014f, 0.028f);
            gravity = 0.09f;
            drag = 0.015f;
            wind = rand(-0.010f, 0.010f) * speed;
            angularVelocity = rand(-14.0f, 14.0f);
            spinDamping = 0.992f;
            turbAmp = 0.008f * speed;
            turbFreqX = rand(0.08f, 0.16f);
            turbFreqY = rand(0.07f, 0.14f);
        } else if (style == STYLE_FALL) {
            vx = rand(-0.05f, 0.05f) * base * speed * (0.4f + spread);
            vy = -rand(0.09f, 0.18f) * base * speed;
            size = Math.max(6.0f, base * sizeMultiplier);
            decay = rand(0.0045f, 0.0065f);
            gravity = base * 0.011f;
            drag = 0.012f;
            rotation = 0.0f;
            angularVelocity = rand(-5.0f, 5.0f);
            spinDamping = 0.995f;
            restitution = rand(0.35f, 0.50f);
            bounded = true;
        } else {
            float angle = RANDOM.nextFloat() * TWO_PI;
            float force = rand(0.6f, 2.6f) * speed;
            vx = (float) Math.cos(angle) * force * spreadX;
            vy = (float) Math.sin(angle) * force * spreadY * 0.6f - rand(0.5f, 1.3f) * speed;
            size = Math.max(1.5f, rand(base / 11.0f, base / 5.5f) * sizeMultiplier);
            decay = rand(0.014f, 0.032f);
            gravity = 0.012f;
            drag = 0.050f;
            wind = rand(-0.010f, 0.010f) * speed;
            turbAmp = 0.055f * speed;
            turbFreqX = rand(0.11f, 0.23f);
            turbFreqY = rand(0.09f, 0.19f);
            flickerSpeed = rand(0.25f, 0.50f);
            angularVelocity = rand(-4.0f, 4.0f);
        }
    }

    public void becomeImplode(float targetX, float targetY, int durationMs, float delayFrames) {
        mode = MODE_IMPLODE;
        homeX = targetX;
        homeY = targetY;
        originX = x;
        originY = y;
        delay = Math.max(0.0f, delayFrames);
        decay = 16.0f / Math.max(80.0f, durationMs);
        angularVelocity = rand(-9.0f, 9.0f);
    }

    public void attractTo(float targetX, float targetY, float pull, float absorbRadius) {
        attractX = targetX;
        attractY = targetY;
        attractPull = Math.max(0.0f, pull);
        attractRadius = Math.max(0.0f, absorbRadius);
    }

    public void setBounds(float left, float right, float bottom) {
        boundLeft = left;
        boundRight = right;
        boundBottom = bottom;
    }

    private void collide(float scale) {
        float half = size * 0.32f;
        if (x - half < boundLeft) {
            x = boundLeft + half;
            if (vx < 0.0f) {
                vx = -vx * restitution;
            }
        } else if (x + half > boundRight) {
            x = boundRight - half;
            if (vx > 0.0f) {
                vx = -vx * restitution;
            }
        }
        if (y + half <= boundBottom) {
            return;
        }
        y = boundBottom - half;
        if (vy > 0.0f) {
            vy = vy < gravity * 2.5f ? 0.0f : -vy * restitution;
        }
        vx *= (float) Math.pow(0.965f, scale);
        float roll = vx / Math.max(1.0f, half) * 57.29578f;
        angularVelocity += (roll - angularVelocity) * Math.min(1.0f, 0.25f * scale);
    }

    public void applyPush(float px, float py, float radius, float power) {
        if (mode == MODE_IMPLODE || radius <= 0.0f || delay > 0.0f) {
            return;
        }
        float dx = x - px;
        float dy = y - py;
        float distanceSq = dx * dx + dy * dy;
        if (distanceSq > radius * radius || distanceSq < 0.01f) {
            return;
        }
        float distance = (float) Math.sqrt(distanceSq);
        float falloff = 1.0f - distance / radius;
        vx += dx / distance * falloff * power;
        vy += dy / distance * falloff * power * 0.6f;
    }

    public float fade() {
        float lifeValue = Math.max(0.0f, Math.min(1.0f, life));
        if (mode == MODE_IMPLODE) {
            return (float) Math.sin((1.0f - lifeValue) * Math.PI);
        }
        if (style == STYLE_FALL) {
            return Math.min(1.0f, lifeValue / 0.2f);
        }
        return lifeValue * lifeValue * (3.0f - 2.0f * lifeValue);
    }

    public boolean update(long dt, float gravityX, float gravityY) {
        float scale = Math.max(0.25f, Math.min(3.0f, dt / 16.0f));
        if (delay > 0.0f) {
            delay -= scale;
            return true;
        }
        time += scale;
        if (mode == MODE_IMPLODE) {
            life -= decay * scale;
            float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - life));
            float inv = 1.0f - progress;
            float eased = 1.0f - inv * inv * inv;
            x = originX + (homeX - originX) * eased;
            y = originY + (homeY - originY) * eased;
            rotation += angularVelocity * scale * inv;
            return life > 0.0f;
        }
        wobblePhase += wobbleSpeed * scale;
        vx += wind * scale;
        if (turbAmp != 0.0f) {
            vx += (float) Math.sin(time * turbFreqX + turbPhaseX) * turbAmp * scale;
            vy += (float) Math.cos(time * turbFreqY + turbPhaseY) * turbAmp * 0.6f * scale;
        }
        if (attractPull > 0.0f) {
            float dx = attractX - x;
            float dy = attractY - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > 1.0f) {
                vx += dx / distance * attractPull * scale;
                vy += dy / distance * attractPull * scale;
            }
            if (distance < attractRadius) {
                life -= 0.12f * scale;
            }
        }
        float swayX = wobble == 0.0f ? 0.0f : (float) Math.sin(wobblePhase) * wobble;
        x += (vx + swayX) * scale;
        y += vy * scale;
        if (style == STYLE_SAKURA && wobble != 0.0f) {
            y += (float) Math.cos(wobblePhase * 2.0f) * wobble * 0.18f * scale;
            rotation += (float) Math.cos(wobblePhase) * 1.9f * scale;
        }
        vx += gravity * gravityX * scale;
        vy += gravity * gravityY * scale;
        float dragFactor = (float) Math.pow(1.0f - Math.min(0.5f, drag), scale);
        vx *= dragFactor;
        vy *= 1.0f - (1.0f - dragFactor) * 0.55f;
        rotation += angularVelocity * scale;
        if (spinDamping < 1.0f) {
            angularVelocity *= (float) Math.pow(spinDamping, scale);
        }
        if (bounded && boundRight > boundLeft) {
            collide(scale);
        }
        life -= decay * scale;
        return life > 0.0f;
    }
}
