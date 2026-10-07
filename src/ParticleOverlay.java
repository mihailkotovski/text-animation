package com.textanimation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.lang.ref.WeakReference;

final class ParticleOverlay {
    private final Settings settings;
    private final ParticleRenderer particles;
    private final int[] location = new int[2];

    ParticleOverlay(Settings settings, ParticleRenderer particles) {
        this.settings = settings;
        this.particles = particles;
    }

    boolean sync(EditText view, AnimationState state) {
        try {
            if (!settings.particlesOverlayEnabled || state.particles.isEmpty() || view.getWindowToken() == null) {
                detach(state);
                return false;
            }
            View root = view.getRootView();
            ViewGroup host = state.overlayHost == null ? null : state.overlayHost.get();
            if (host != null && host == root && state.overlayDrawable != null) {
                return true;
            }
            detach(state);
            if (!(root instanceof ViewGroup) || root == view) {
                return false;
            }
            host = (ViewGroup) root;
            Layer layer = new Layer(view, state, host);
            layer.setBounds(0, 0, host.getWidth(), host.getHeight());
            host.getOverlay().add(layer);
            state.overlayHost = new WeakReference<>(host);
            state.overlayDrawable = layer;
            return true;
        } catch (Throwable error) {
            AnimLog.report("overlay sync", error);
            detach(state);
            return false;
        }
    }

    void invalidate(AnimationState state) {
        ViewGroup host = state.overlayHost == null ? null : state.overlayHost.get();
        if (host != null && state.overlayDrawable != null) {
            host.invalidate();
        }
    }

    void detach(AnimationState state) {
        Drawable drawable = state.overlayDrawable;
        ViewGroup host = state.overlayHost == null ? null : state.overlayHost.get();
        state.overlayDrawable = null;
        state.overlayHost = null;
        if (drawable == null || host == null) {
            return;
        }
        try {
            host.getOverlay().remove(drawable);
            host.invalidate();
        } catch (Throwable error) {
            AnimLog.report("overlay detach", error);
        }
    }

    private final class Layer extends Drawable {
        private final WeakReference<EditText> viewRef;
        private final WeakReference<ViewGroup> hostRef;
        private final AnimationState state;

        Layer(EditText view, AnimationState state, ViewGroup host) {
            this.viewRef = new WeakReference<>(view);
            this.hostRef = new WeakReference<>(host);
            this.state = state;
        }

        @Override
        public void draw(Canvas canvas) {
            try {
                EditText view = viewRef.get();
                ViewGroup host = hostRef.get();
                if (view == null || host == null || state.overlayDrawable != this
                    || state.particles.isEmpty() || view.getWindowToken() == null || !view.isShown()) {
                    return;
                }
                view.getLocationInWindow(location);
                float dx = location[0];
                float dy = location[1];
                host.getLocationInWindow(location);
                dx += host.getScrollX() - location[0] - view.getScrollX();
                dy += host.getScrollY() - location[1] - view.getScrollY();
                int saveCount = canvas.save();
                try {
                    canvas.translate(dx, dy);
                    particles.drawParticles(view, canvas, state);
                } finally {
                    canvas.restoreToCount(saveCount);
                }
            } catch (Throwable error) {
                AnimLog.report("overlay draw", error);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
