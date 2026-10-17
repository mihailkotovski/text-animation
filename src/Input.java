package com.textanimation;

import android.graphics.Canvas;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;

final class Input implements TextWatcher {
    private static final long SEND_WINDOW_MS = 1500L;
    private static final float EDGE_DP = 36.0f;
    private static final float SINK_DP = 2.0f;
    private static final String PREVIEW = "text_animation_preview";

    private final TextAnimationCore core;
    private final WeakReference<EditText> ref;
    private final Glyphs glyphs = new Glyphs();
    private final Ghosts ghosts = new Ghosts();
    private final Caret caret = new Caret();
    private final Particles particles = new Particles();
    private final ParticleLayer layer = new ParticleLayer(particles);
    private final ArrayList<Ghost> pending = new ArrayList<>();
    private final int[] location = new int[2];
    private String removed;
    private int removedAt;
    private int clip = -1;
    private boolean owned;
    private boolean wanted = true;
    private long sendTime;
    private float[] sendTarget;
    private long wake;

    Input(TextAnimationCore core, EditText view) {
        this.core = core;
        this.ref = new WeakReference<>(view);
        view.addTextChangedListener(this);
        refresh(view);
    }

    void refresh(EditText view) {
        Config config = core.config;
        if (config.caret) {
            own(view);
        } else {
            release(view);
        }
        if (!config.ghosts) {
            ghosts.clear();
        }
        if (!config.particles) {
            particles.clear();
            layer.detach();
        }
        view.invalidate();
    }

    void dispose(EditText view) {
        view.removeTextChangedListener(this);
        release(view);
        fail();
        pending.clear();
        removed = null;
        view.invalidate();
    }

    void fail() {
        glyphs.clear();
        ghosts.clear();
        particles.clear();
        layer.detach();
    }

    boolean owns() {
        return owned;
    }

    void want(boolean value) {
        wanted = value;
    }

    void arm(float[] target) {
        sendTime = AnimationUtils.currentAnimationTimeMillis();
        sendTarget = target;
    }

    void before(EditText view, Canvas canvas) {
        clip = -1;
        if (glyphs.measure(view, core.config, AnimationUtils.currentAnimationTimeMillis())) {
            clip = canvas.save();
            glyphs.clip(canvas);
        }
    }

    void after(EditText view, Canvas canvas) {
        if (clip >= 0) {
            canvas.restoreToCount(clip);
            clip = -1;
        }
        Config config = core.config;
        long now = AnimationUtils.currentAnimationTimeMillis();
        boolean active;
        long delay = Caret.IDLE;
        int save = canvas.save();
        try {
            active = ghosts.draw(view, canvas, config, now);
            active |= glyphs.draw(view, canvas, config, now);
            if (owned && wanted && view.isCursorVisible()) {
                boolean focused = view.isFocused() || PREVIEW.equals(view.getTag());
                delay = caret.draw(view, canvas, config, core.telegram.caretColor(view), focused, now);
            }
        } finally {
            canvas.restoreToCount(save);
        }
        if (active || delay == Caret.NEXT_FRAME) {
            view.postInvalidateOnAnimation();
        } else if (delay > 0L && (wake <= now || wake > now + delay)) {
            wake = now + delay;
            view.postInvalidateDelayed(delay);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        removed = null;
        pending.clear();
        EditText view = ref.get();
        if (view == null || hidden(view) || start < 0 || count < 0 || start + count > text.length()) {
            return;
        }
        removed = text.subSequence(start, start + count).toString();
        removedAt = start;
        Config config = core.config;
        if (count > 0 && (config.ghosts || config.particles) && view.isShown()) {
            capture(view, text, start, start + count);
        }
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        String old = removed;
        removed = null;
        EditText view = ref.get();
        if (view == null) {
            return;
        }
        if (old == null || start != removedAt || before != old.length() || start + count > text.length()) {
            glyphs.clear();
        } else {
            commit(view, text, start, old, count);
        }
        pending.clear();
    }

    @Override
    public void afterTextChanged(Editable text) {
    }

    private void commit(EditText view, CharSequence content, int start, String old, int count) {
        String text = content.toString();
        Clusters clusters = core.clusters;
        clusters.reset(text);
        int limit = Math.min(old.length(), count);
        int prefix = 0;
        while (prefix < limit && old.charAt(prefix) == text.charAt(start + prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < limit - prefix && old.charAt(old.length() - 1 - suffix) == text.charAt(start + count - 1 - suffix)) {
            suffix++;
        }
        int from = clusters.floor(start + prefix);
        int to = clusters.ceil(start + count - suffix);
        int removedTo = to - count + old.length();
        int erased = removedTo - from;
        int typed = to - from;
        if (erased == 0 && typed == 0) {
            return;
        }
        long now = AnimationUtils.currentAnimationTimeMillis();
        glyphs.shift(from, erased, typed);
        if (erased > 0) {
            vanish(view, from, removedTo, text.isEmpty() && sent(now), typed > 0, now);
        }
        if (typed > 0) {
            glyphs.add(view, clusters, text, from, to, now);
            caret.typed(text, from, to, now);
        } else {
            caret.erased(now);
        }
        caret.wake(now);
        view.invalidate();
    }

    private void vanish(EditText view, int from, int to, boolean send, boolean replaced, long now) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            int offset = pending.get(i).offset;
            if (offset < from || offset >= to) {
                pending.remove(i);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        Config config = core.config;
        boolean falling = config.particles && config.style == Config.STYLE_FALL;
        if (config.ghosts && !send && !replaced && !falling) {
            for (int i = 0; i < pending.size(); i++) {
                Ghost ghost = pending.get(i);
                if (!ghost.emoji) {
                    ghosts.add(ghost, now);
                }
            }
        }
        if (!config.particles) {
            return;
        }
        float[] target = send && config.burst ? (sendTarget != null ? sendTarget : edge(view)) : null;
        if (particles.burst(view, config, pending, target)) {
            layer.attach(view);
            if (config.haptic) {
                haptic(view, target != null);
            }
        }
    }

    private void capture(EditText view, CharSequence content, int start, int end) {
        Layout layout = view.getLayout();
        if (layout == null) {
            return;
        }
        String text = content.toString();
        Clusters clusters = core.clusters;
        clusters.reset(text);
        TextPaint paint = view.getPaint();
        float left = view.getCompoundPaddingLeft();
        int top = view.getTotalPaddingTop();
        int scroll = view.getScrollY();
        int first = layout.getLineForVertical(scroll - top);
        int last = layout.getLineForVertical(scroll + view.getHeight() - top);
        int from = Math.max(clusters.floor(start), layout.getLineStart(first));
        int to = Math.min(end, layout.getLineEnd(last));
        for (int i = from; i < to && pending.size() < Ghosts.LIMIT; ) {
            int next = clusters.next(i, to);
            if (!Clusters.blank(text, i, next)) {
                float width = paint.measureText(text, i, next);
                float x = left + layout.getPrimaryHorizontal(i);
                if (layout.isRtlCharAt(i)) {
                    x -= width;
                }
                float baseline = top + layout.getLineBaseline(layout.getLineForOffset(i));
                pending.add(new Ghost(i, text.substring(i, next), x, baseline, width, Clusters.emoji(content, i, next)));
            }
            i = next;
        }
    }

    private boolean sent(long now) {
        boolean armed = sendTime != 0L && now - sendTime < SEND_WINDOW_MS;
        sendTime = 0L;
        return armed;
    }

    private float[] edge(EditText view) {
        view.getLocationInWindow(location);
        float density = view.getResources().getDisplayMetrics().density;
        boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float x = rtl ? location[0] - EDGE_DP * density : location[0] + view.getWidth() + EDGE_DP * density;
        return new float[]{x, location[1] + view.getHeight() - SINK_DP * density};
    }

    private void own(EditText view) {
        Field field = core.telegram.allowCursorField;
        if (owned || field == null) {
            return;
        }
        try {
            wanted = field.getBoolean(view);
            field.setBoolean(view, false);
            owned = true;
        } catch (IllegalAccessException error) {
            Logs.error("own caret", error);
        }
    }

    private void release(EditText view) {
        Field field = core.telegram.allowCursorField;
        if (!owned || field == null) {
            return;
        }
        owned = false;
        caret.reset();
        try {
            field.setBoolean(view, wanted);
        } catch (IllegalAccessException error) {
            Logs.error("release caret", error);
        }
    }

    private static boolean hidden(EditText view) {
        return view.getTransformationMethod() instanceof PasswordTransformationMethod;
    }

    private static void haptic(View view, boolean strong) {
        int type = !strong
            ? HapticFeedbackConstants.KEYBOARD_TAP
            : Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS;
        view.performHapticFeedback(type);
    }
}
