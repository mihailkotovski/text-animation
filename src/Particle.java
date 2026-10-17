package com.textanimation;

import java.util.Random;

final class Particle {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float SPREAD_X = 1.3f;
    private static final float SPREAD_Y = 1.25f;
    private static final int[] SAKURA = {0xFFFFD7E4, 0xFFFFC2D6, 0xFFFFAECB, 0xFFFB98BC};

    float x;
    float y;
    float vx;
    float vy;
    float life;
    float size;
    float rotation;
    float stretch;
    float flicker;
    float flickerPhase;
    float time;
    int color;
    int style;
    String text;

    private float decay;
    private float gravity;
    private float drag;
    private float wind;
    private float spin;
    private float spinDamping;
    private float wobble;
    private float wobblePhase;
    private float wobbleSpeed;
    private float turbulence;
    private float turbulenceFx;
    private float turbulenceFy;
    private float turbulencePx;
    private float turbulencePy;
    private float targetX;
    private float targetY;
    private float pull;
    private float absorb;
    private boolean bounded;
    private float minX;
    private float maxX;
    private float maxY;
    private float restitution;

    void init(Random random, int style, float x, float y, int color, float textSize, String text) {
        this.style = style;
        this.x = x;
        this.y = y;
        this.color = color;
        this.text = text;
        life = 1.0f;
        time = 0.0f;
        rotation = random.nextFloat() * 360.0f;
        stretch = 1.0f;
        flicker = 0.0f;
        flickerPhase = random.nextFloat() * TWO_PI;
        wind = 0.0f;
        spinDamping = 1.0f;
        wobble = 0.0f;
        wobblePhase = random.nextFloat() * TWO_PI;
        wobbleSpeed = 0.08f;
        turbulence = 0.0f;
        turbulencePx = random.nextFloat() * TWO_PI;
        turbulencePy = random.nextFloat() * TWO_PI;
        pull = 0.0f;
        bounded = false;
        float base = Math.max(1.0f, textSize);
        switch (style) {
            case Config.STYLE_SNOW:
                snow(random, base);
                break;
            case Config.STYLE_SAKURA:
                sakura(random, base);
                break;
            case Config.STYLE_LETTERS:
                letters(random, base);
                break;
            case Config.STYLE_FALL:
                fall(random, base);
                break;
            default:
                dust(random, base);
                break;
        }
    }

    void push(float outwardX, float outwardY) {
        vx += outwardX * 1.5f;
        vy += outwardY * 0.5f;
    }

    void bound(float left, float right, float bottom) {
        minX = left;
        maxX = right;
        maxY = bottom;
    }

    void attract(float x, float y, float force, float radius) {
        targetX = x;
        targetY = y;
        pull = force;
        absorb = radius;
        bounded = false;
        gravity *= 0.30f;
        decay *= 0.80f;
        float dx = x - this.x;
        float dy = y - this.y;
        float cruise = Math.max(1.0f, force / Math.max(0.01f, drag));
        float frames = Math.min(150.0f, (float) Math.sqrt(dx * dx + dy * dy) / cruise + 12.0f);
        decay = Math.min(decay, 0.5f / frames);
    }

    float alpha() {
        float value = Motion.clamp(life);
        if (style == Config.STYLE_FALL) {
            return Math.min(1.0f, value / 0.2f);
        }
        return value * value * (3.0f - 2.0f * value);
    }

    boolean step(float scale) {
        time += scale;
        wobblePhase += wobbleSpeed * scale;
        vx += wind * scale;
        if (turbulence != 0.0f) {
            vx += (float) Math.sin(time * turbulenceFx + turbulencePx) * turbulence * scale;
            vy += (float) Math.cos(time * turbulenceFy + turbulencePy) * turbulence * 0.6f * scale;
        }
        if (pull > 0.0f) {
            float dx = targetX - x;
            float dy = targetY - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > 1.0f) {
                vx += dx / distance * pull * scale;
                vy += dy / distance * pull * scale;
            }
            if (distance < absorb) {
                life -= 0.12f * scale;
            }
        }
        float sway = wobble == 0.0f ? 0.0f : (float) Math.sin(wobblePhase) * wobble;
        x += (vx + sway) * scale;
        y += vy * scale;
        if (style == Config.STYLE_SAKURA && wobble != 0.0f) {
            y += (float) Math.cos(wobblePhase * 2.0f) * wobble * 0.18f * scale;
            rotation += (float) Math.cos(wobblePhase) * 1.9f * scale;
        }
        vy += gravity * scale;
        float damping = (float) Math.pow(1.0f - Math.min(0.5f, drag), scale);
        vx *= damping;
        vy *= 1.0f - (1.0f - damping) * 0.55f;
        rotation += spin * scale;
        if (spinDamping < 1.0f) {
            spin *= (float) Math.pow(spinDamping, scale);
        }
        if (bounded && maxX > minX) {
            collide(scale);
        }
        life -= decay * scale;
        return life > 0.0f;
    }

    private void collide(float scale) {
        float half = size * 0.32f;
        if (x - half < minX) {
            x = minX + half;
            if (vx < 0.0f) {
                vx = -vx * restitution;
            }
        } else if (x + half > maxX) {
            x = maxX - half;
            if (vx > 0.0f) {
                vx = -vx * restitution;
            }
        }
        if (y + half <= maxY) {
            return;
        }
        y = maxY - half;
        if (vy > 0.0f) {
            vy = vy < gravity * 2.5f ? 0.0f : -vy * restitution;
        }
        vx *= (float) Math.pow(0.965f, scale);
        float roll = (float) Math.toDegrees(vx / Math.max(1.0f, half));
        spin += (roll - spin) * Math.min(1.0f, 0.25f * scale);
    }

    private void dust(Random random, float base) {
        float angle = random.nextFloat() * TWO_PI;
        float force = range(random, 0.6f, 2.6f);
        vx = (float) Math.cos(angle) * force * SPREAD_X;
        vy = (float) Math.sin(angle) * force * SPREAD_Y * 0.6f - range(random, 0.5f, 1.3f);
        size = Math.max(1.5f, range(random, base / 11.0f, base / 5.5f));
        decay = range(random, 0.014f, 0.032f);
        gravity = 0.012f;
        drag = 0.050f;
        wind = range(random, -0.010f, 0.010f);
        turbulence = 0.055f;
        turbulenceFx = range(random, 0.11f, 0.23f);
        turbulenceFy = range(random, 0.09f, 0.19f);
        flicker = range(random, 0.25f, 0.50f);
        spin = range(random, -4.0f, 4.0f);
    }

    private void snow(Random random, float base) {
        vx = range(random, -0.8f, 0.8f) * SPREAD_X;
        vy = range(random, -0.6f, 0.25f) * SPREAD_Y;
        size = Math.max(2.0f, range(random, base / 9.0f, base / 5.5f));
        decay = range(random, 0.007f, 0.015f);
        gravity = 0.014f;
        drag = 0.025f;
        wind = range(random, 0.004f, 0.014f);
        wobble = range(random, 0.45f, 1.30f);
        wobbleSpeed = range(random, 0.050f, 0.090f);
        spin = range(random, -2.5f, 2.5f);
        spinDamping = 0.998f;
        turbulence = 0.012f;
        turbulenceFx = range(random, 0.06f, 0.14f);
        turbulenceFy = range(random, 0.05f, 0.12f);
    }

    private void sakura(Random random, float base) {
        color = SAKURA[random.nextInt(SAKURA.length)];
        vx = range(random, -0.9f, 1.2f) * SPREAD_X * 0.8f + range(random, 0.05f, 0.30f);
        vy = range(random, -1.0f, 0.15f) * SPREAD_Y * 0.55f;
        size = Math.max(2.6f, range(random, base / 6.8f, base / 4.4f));
        decay = range(random, 0.006f, 0.013f);
        gravity = 0.030f;
        drag = 0.030f;
        wind = range(random, 0.004f, 0.014f);
        wobble = range(random, 0.8f, 1.8f);
        wobbleSpeed = range(random, 0.045f, 0.075f);
        spin = range(random, -2.2f, 2.2f);
        spinDamping = 0.985f;
        stretch = range(random, 1.45f, 2.0f);
        turbulence = 0.008f;
        turbulenceFx = range(random, 0.05f, 0.12f);
        turbulenceFy = range(random, 0.04f, 0.10f);
    }

    private void letters(Random random, float base) {
        float angle = (float) (-Math.PI / 2.0) + range(random, -1.1f, 1.1f);
        float force = range(random, 1.6f, 3.8f);
        vx = (float) Math.cos(angle) * force * SPREAD_X;
        vy = (float) Math.sin(angle) * force * SPREAD_Y;
        size = Math.max(4.0f, base * range(random, 0.50f, 0.72f));
        decay = range(random, 0.014f, 0.028f);
        gravity = 0.09f;
        drag = 0.015f;
        wind = range(random, -0.010f, 0.010f);
        spin = range(random, -14.0f, 14.0f);
        spinDamping = 0.992f;
        turbulence = 0.008f;
        turbulenceFx = range(random, 0.08f, 0.16f);
        turbulenceFy = range(random, 0.07f, 0.14f);
    }

    private void fall(Random random, float base) {
        vx = range(random, -0.05f, 0.05f) * base * 1.4f;
        vy = -range(random, 0.09f, 0.18f) * base;
        size = Math.max(6.0f, base);
        decay = range(random, 0.0045f, 0.0065f);
        gravity = base * 0.011f;
        drag = 0.012f;
        rotation = 0.0f;
        spin = range(random, -5.0f, 5.0f);
        spinDamping = 0.995f;
        restitution = range(random, 0.35f, 0.50f);
        bounded = true;
    }

    private static float range(Random random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
