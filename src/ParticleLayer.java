package com.textanimation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import java.lang.ref.WeakReference;

final class ParticleLayer extends Drawable {
    private final Particles particles;
    private final int[] location = new int[2];
    private final Runnable sweep = this::sweep;
    private WeakReference<ViewGroup> host;
    private long last;

    ParticleLayer(Particles particles) {
        this.particles = particles;
    }

    void attach(View view) {
        View root = view.getRootView();
        if (root == host()) {
            invalidateSelf();
            return;
        }
        detach();
        if (!(root instanceof ViewGroup) || view.getWindowToken() == null) {
            particles.clear();
            return;
        }
        ViewGroup group = (ViewGroup) root;
        host = new WeakReference<>(group);
        last = 0L;
        setBounds(0, 0, group.getWidth(), group.getHeight());
        group.getOverlay().add(this);
    }

    void detach() {
        ViewGroup current = host();
        host = null;
        if (current != null) {
            current.removeCallbacks(sweep);
            current.getOverlay().remove(this);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        ViewGroup current = host();
        if (current == null) {
            return;
        }
        int save = canvas.save();
        try {
            long now = AnimationUtils.currentAnimationTimeMillis();
            if (last != 0L) {
                particles.step(now - last);
            }
            last = now;
            Rect bounds = getBounds();
            if (bounds.right != current.getWidth() || bounds.bottom != current.getHeight()) {
                setBounds(0, 0, current.getWidth(), current.getHeight());
            }
            current.getLocationInWindow(location);
            canvas.translate(-location[0], -location[1]);
            particles.draw(canvas);
        } catch (Throwable error) {
            Logs.error("particles", error);
            particles.clear();
        } finally {
            canvas.restoreToCount(save);
        }
        if (particles.isEmpty()) {
            current.post(sweep);
        } else {
            invalidateSelf();
        }
    }

    private ViewGroup host() {
        return host == null ? null : host.get();
    }

    private void sweep() {
        if (particles.isEmpty()) {
            detach();
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
