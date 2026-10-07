package com.textanimation;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class TextAnimationCore {
    private static final Object LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final long WORD_TRIGGER_COOLDOWN = 900L;
    private static final String[] WORD_TRIGGERS = {
        "снег", "snow",
        "сакура", "sakura",
        "ура", "hooray", "wow", "огонь", "fire",
    };
    private static final int[] WORD_TRIGGER_STYLES = {
        Particle.STYLE_SNOW, Particle.STYLE_SNOW,
        Particle.STYLE_SAKURA, Particle.STYLE_SAKURA,
        Particle.STYLE_SPARKS, Particle.STYLE_SPARKS, Particle.STYLE_SPARKS, Particle.STYLE_SPARKS, Particle.STYLE_SPARKS,
    };
    private static TextAnimationCore instance;

    private volatile boolean initialized = false;

    private final WeakHashMap<View, AnimationState> states = new WeakHashMap<>();
    private final ArrayList<XC_MethodHook.Unhook> hooks = new ArrayList<>();

    private boolean sendHookInstalled = false;
    private Field chatEnterViewEditTextField;
    private Field sendButtonField;
    private final int[] locationA = new int[2];
    private final int[] locationB = new int[2];

    private final Settings settings = new Settings();
    private final TelegramReflection telegram = new TelegramReflection();
    private final ViewHelper helper = new ViewHelper(settings, telegram);
    private final GraphemeClusters clusters = new GraphemeClusters();
    private final TiltSensor tilt = new TiltSensor(settings);
    private final PowerMode power = new PowerMode(settings, helper);
    private final ParticleSpawner spawner = new ParticleSpawner(settings, helper, clusters);
    private final ParticleRenderer particles = new ParticleRenderer(settings, helper, tilt);
    private final ParticleOverlay overlay = new ParticleOverlay(settings, particles);
    private final CursorRenderer cursor = new CursorRenderer(settings, telegram, helper);
    private final CharAnimator chars = new CharAnimator(settings, helper, clusters, power, spawner);

    public static void start() {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                getInstance().startInternal();
            }
        });
    }

    public static void updateSettings(final String settingsJson) {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                getInstance().updateSettingsInternal(settingsJson);
            }
        });
    }

    public static void unload() {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (instance != null) {
                        instance.cleanup();
                        instance = null;
                    }
                }
            }
        });
    }

    private static void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN_HANDLER.post(action);
        }
    }

    public static TextAnimationCore getInstance() {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new TextAnimationCore();
            }
            return instance;
        }
    }

    private void startInternal() {
        if (initialized) {
            return;
        }
        try {
            telegram.resolve();
            Class<?> targetClass = telegram.editTextClass();
            hookOnDraw(targetClass);
            hookFocusChanged(targetClass);
            hookTouchEvent(targetClass);
            hookSendMessage();
            initialized = true;
            AnimLog.log("TextAnimationCore started");
        } catch (Throwable error) {
            AnimLog.log("Start error: " + error);
            cleanup();
        }
    }

    private void updateSettingsInternal(String settingsJson) {
        try {
            settings.applyJson(settingsJson);
            AnimLog.debugLogging = settings.debugMode;
            if (!settings.tiltWindEnabled) {
                tilt.release();
            }
            invalidateTrackedViews();
        } catch (Throwable error) {
            AnimLog.log("Settings error: " + error);
        }
    }

    private void cleanup() {
        tilt.release();
        sendHookInstalled = false;
        for (XC_MethodHook.Unhook hook : new ArrayList<>(hooks)) {
            try {
                hook.unhook();
            } catch (Throwable error) {
                AnimLog.report("cleanup", error);
            }
        }
        hooks.clear();
        for (Map.Entry<View, AnimationState> entry : new ArrayList<>(states.entrySet())) {
            View view = entry.getKey();
            AnimationState state = entry.getValue();
            if (view == null || state == null) {
                continue;
            }
            cursor.restoreSystemCursor(view, state);
            chars.removeHiddenSpans(view, state);
            overlay.detach(state);
            state.ghosts.clear();
            if (view instanceof EditText && state.textWatcher != null) {
                ((EditText) view).removeTextChangedListener(state.textWatcher);
            }
            state.textWatcher = null;
            state.charStartTimes.clear();
            state.particles.clear();
            state.hasAnimatingChars = false;
            view.invalidate();
        }
        states.clear();
        chars.clearBlurCache();
        initialized = false;
        AnimLog.log("TextAnimationCore unloaded");
    }

    private void hookOnDraw(Class<?> cls) {
        try {
            final Method onDraw = telegram.findMethodInHierarchy(cls, "onDraw", Canvas.class);
            if (onDraw == null) {
                return;
            }
            XC_MethodHook.Unhook hook = XposedBridge.hookMethod(onDraw, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof EditText) {
                        handleBeforeDraw(param);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof EditText) {
                        handleAfterDraw(param);
                    }
                }
            });
            hooks.add(hook);
        } catch (Throwable error) {
            AnimLog.log("onDraw hook error: " + error);
        }
    }

    private void hookFocusChanged(Class<?> cls) {
        try {
            Method method = telegram.findNamedMethod(cls, "onFocusChanged", 3);
            if (method == null) {
                return;
            }
            XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof EditText) {
                        View view = (View) param.thisObject;
                        AnimationState state = getState(view);
                        if (!view.isFocused()) {
                            state.cursorX = -1.0f;
                            state.cursorY = -1.0f;
                            state.cursorBlinkAnchor = 0L;
                            cursor.resetCursorEffects(state);
                        }
                        view.invalidate();
                    }
                }
            });
            hooks.add(hook);
        } catch (Throwable error) {
            AnimLog.log("focus hook error: " + error);
        }
    }

    private void hookTouchEvent(Class<?> cls) {
        try {
            Method method = telegram.findNamedMethod(cls, "onTouchEvent", 1);
            if (method == null || method.getParameterTypes()[0] != MotionEvent.class) {
                return;
            }
            XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof EditText) {
                        View view = (View) param.thisObject;
                        getState(view).cursorBlinkAnchor = System.currentTimeMillis();
                        view.invalidate();
                    }
                }
            });
            hooks.add(hook);
        } catch (Throwable error) {
            AnimLog.log("touch hook error: " + error);
        }
    }

    private void hookSendMessage() {
        try {
            Class<?> enterViewClass = telegram.loadClassSafely("org.telegram.ui.Components.ChatActivityEnterView");
            if (enterViewClass == null) {
                return;
            }
            chatEnterViewEditTextField = telegram.findFieldInHierarchy(enterViewClass, "messageEditText");
            sendButtonField = telegram.findFieldInHierarchy(enterViewClass, "sendButtonContainer");
            if (sendButtonField == null) {
                sendButtonField = telegram.findFieldInHierarchy(enterViewClass, "sendButton");
            }
            Method method = telegram.findNamedMethod(enterViewClass, "sendMessage", 0);
            if (chatEnterViewEditTextField == null || method == null) {
                return;
            }
            XC_MethodHook.Unhook hook = XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object field = chatEnterViewEditTextField.get(param.thisObject);
                        if (field instanceof View) {
                            AnimationState state = getState((View) field);
                            state.sendPendingTime = System.currentTimeMillis();
                            captureSendTarget((View) field, state, param.thisObject);
                        }
                    } catch (Throwable error) {
                        AnimLog.report("hookSendMessage", error);
                    }
                }
            });
            hooks.add(hook);
            sendHookInstalled = true;
        } catch (Throwable error) {
            AnimLog.log("send hook error: " + error);
        }
    }

    private void captureSendTarget(View field, AnimationState state, Object enterView) {
        state.hasSendTarget = false;
        try {
            Object button = sendButtonField == null ? null : sendButtonField.get(enterView);
            if (!(button instanceof View)) {
                return;
            }
            View target = (View) button;
            if (!target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0) {
                return;
            }
            target.getLocationInWindow(locationA);
            field.getLocationInWindow(locationB);
            state.sendTargetX = locationA[0] - locationB[0] + target.getWidth() / 2.0f + field.getScrollX();
            state.sendTargetY = locationA[1] - locationB[1] + target.getHeight() / 2.0f + field.getScrollY();
            state.hasSendTarget = true;
        } catch (Throwable error) {
            AnimLog.report("captureSendTarget", error);
        }
    }

    private AnimationState getState(View view) {
        AnimationState state = states.get(view);
        if (state == null) {
            state = new AnimationState();
            states.put(view, state);
            if (view instanceof EditText) {
                state.textWatcher = new DirtyWatcher(state);
                ((EditText) view).addTextChangedListener(state.textWatcher);
            }
        }
        return state;
    }

    private static final class DirtyWatcher implements TextWatcher {
        private final AnimationState state;

        DirtyWatcher(AnimationState state) {
            this.state = state;
        }

        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
            state.textDirty = true;
        }

        @Override
        public void afterTextChanged(Editable text) {
        }
    }

    private boolean isSensitiveInput(EditText editText) {
        return editText != null && editText.getTransformationMethod() instanceof PasswordTransformationMethod;
    }

    private void handleBeforeDraw(XC_MethodHook.MethodHookParam param) {
        EditText view = (EditText) param.thisObject;
        AnimationState state = getState(view);
        state.lastDrawTime = System.currentTimeMillis();
        state.drawingDepth++;
        if (state.drawingDepth == 1) {
            state.shakeSaveCount = -1;
            power.applyShake(view, state, param);
        }
        try {
            if (settings.cursorEnabled) {
                cursor.hideSystemCursor(view, state);
            } else {
                cursor.restoreSystemCursor(view, state);
            }

            int lastLineStart = helper.getLastLineStart(view);
            boolean lineMoved = !settings.animateAllLines && lastLineStart != state.prevLastLineStart;
            if (!state.textDirty && !lineMoved) {
                return;
            }
            state.textDirty = false;
            CharSequence editable = view.getText();
            if (editable == null) {
                return;
            }
            String curText = editable.toString();
            int curLen = curText.length();

            if (lineMoved || isSensitiveInput(view)) {
                resetAnimationState(view, state, curText, curLen, lastLineStart);
                return;
            }

            int commonPrefix = 0;
            int limit = Math.min(curLen, state.prevLen);
            while (commonPrefix < limit && curText.charAt(commonPrefix) == state.prevText.charAt(commonPrefix)) {
                commonPrefix++;
            }

            int commonSuffix = 0;
            while (commonSuffix < curLen - commonPrefix
                && commonSuffix < state.prevLen - commonPrefix
                && curText.charAt(curLen - 1 - commonSuffix) == state.prevText.charAt(state.prevLen - 1 - commonSuffix)) {
                commonSuffix++;
            }

            int deletedStart = commonPrefix;
            int deletedEnd = state.prevLen - commonSuffix;
            int insertedStart = commonPrefix;
            int insertedEnd = curLen - commonSuffix;
            int deletedCount = deletedEnd - deletedStart;
            int insertedCount = insertedEnd - insertedStart;
            boolean isChange = deletedCount > 0 || insertedCount > 0;
            long now = System.currentTimeMillis();

            if (deletedCount > 0 && (settings.deleteAnimEnabled || settings.deleteGhostEnabled)) {
                boolean sendClear = curLen == 0
                    && insertedCount == 0
                    && deletedCount == state.prevLen
                    && isSendClear(state, now);
                boolean sendBurst = settings.sendBurstEnabled && sendClear;
                String deletedText = state.prevText.substring(deletedStart, deletedEnd);
                if (!settings.ignoreSpaces || !deletedText.trim().isEmpty()) {
                    Layout deletionLayout = spawner.deletionLayout(view, state, deletedStart, deletedText);
                    if (settings.deleteAnimEnabled) {
                        spawner.spawnDeleteParticles(view, state, deletionLayout, deletedStart, deletedText, sendBurst);
                        helper.haptic(view, sendBurst);
                    }
                    boolean lettersFall = settings.deleteAnimEnabled
                        && settings.particleCount > 0
                        && settings.particleStyle == Particle.STYLE_FALL;
                    if (settings.deleteGhostEnabled && !sendClear && !lettersFall && insertedCount == 0) {
                        chars.addDeletedGhosts(view, state, deletionLayout, deletedStart, deletedText, now);
                    }
                }
            }

            if (isChange && !state.charStartTimes.isEmpty()) {
                chars.shiftExistingAnimations(state, curLen, state.prevLen, commonPrefix, commonSuffix, insertedCount - deletedCount);
            }

            if (!settings.animateAllLines && !state.charStartTimes.isEmpty()) {
                Iterator<Integer> iterator = state.charStartTimes.keySet().iterator();
                while (iterator.hasNext()) {
                    if (iterator.next() < lastLineStart) {
                        iterator.remove();
                    }
                }
            }

            if (insertedCount > 0) {
                power.registerTypingCombo(view, state, insertedCount, now);
                chars.addInsertedAnimations(view, state, curText, insertedStart, insertedEnd, lastLineStart, now);
                cursor.triggerSpaceCursorJump(view, state, curText, insertedStart, insertedEnd);
                cursor.triggerEnterCursorDive(view, state, curText, insertedStart, insertedEnd);
                if (settings.charTriggersEnabled) {
                    checkWordTriggers(view, state, curText, insertedEnd, now);
                }
            } else if (deletedCount > 0) {
                cursor.triggerBackspaceCursorPulse(view, state);
            }

            if (isChange) {
                state.cursorBlinkAnchor = now;
                if (!state.charStartTimes.isEmpty() || !state.particles.isEmpty() || !state.ghosts.isEmpty()) {
                    state.hasAnimatingChars = true;
                    chars.updateHiddenSpans(view, state);
                    startAnimationLoop(view, state);
                } else {
                    state.hasAnimatingChars = false;
                    chars.removeHiddenSpans(view, state);
                }
            }

            state.prevText = curText;
            state.prevLen = curLen;
            state.prevLastLineStart = lastLineStart;
        } catch (Throwable error) {
            AnimLog.report("before draw", error);
        }
    }

    private void handleAfterDraw(XC_MethodHook.MethodHookParam param) {
        View rawView = (View) param.thisObject;
        AnimationState state = getState(rawView);
        state.drawingDepth = Math.max(0, state.drawingDepth - 1);
        if (state.drawingDepth > 0) {
            return;
        }
        Canvas canvas = param.args != null && param.args.length > 0 && param.args[0] instanceof Canvas
            ? (Canvas) param.args[0]
            : null;
        try {
            EditText view = (EditText) rawView;
            if (canvas == null) {
                return;
            }
            if (!state.ghosts.isEmpty()) {
                chars.drawGhosts(view, canvas, state);
            }
            if (state.hasAnimatingChars) {
                chars.drawAnimatedChars(view, canvas, state);
            }
            if (!overlay.sync(view, state) && !state.particles.isEmpty()) {
                particles.drawParticles(view, canvas, state);
            }
            if (settings.cursorEnabled) {
                cursor.drawCursor(view, canvas, state);
            }
            power.drawComboLabel(view, canvas, state, System.currentTimeMillis());
        } catch (Throwable error) {
            AnimLog.report("after draw", error);
        } finally {
            if (canvas != null && state.shakeSaveCount >= 0) {
                try {
                    canvas.restoreToCount(state.shakeSaveCount);
                } catch (Throwable error) {
                    AnimLog.report("handleAfterDraw", error);
                }
            }
            state.shakeSaveCount = -1;
        }
    }

    private void resetAnimationState(EditText view, AnimationState state, String curText, int curLen, int lastLineStart) {
        state.charStartTimes.clear();
        state.particles.clear();
        state.ghosts.clear();
        overlay.detach(state);
        state.lastParticleUpdateTime = 0L;
        cursor.resetCursorEffects(state);
        state.hasAnimatingChars = false;
        state.animationRunning = false;
        chars.removeHiddenSpans(view, state);
        state.prevText = curText;
        state.prevLen = curLen;
        state.prevLastLineStart = lastLineStart;
    }

    private void checkWordTriggers(EditText view, AnimationState state, String curText, int end, long now) {
        try {
            if (now - state.lastWordTriggerTime < WORD_TRIGGER_COOLDOWN) {
                return;
            }
            int safeEnd = Math.max(0, Math.min(end, curText.length()));
            if (safeEnd <= 0) {
                return;
            }
            int from = Math.max(0, safeEnd - 12);
            String tail = curText.substring(from, safeEnd).toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < WORD_TRIGGERS.length && i < WORD_TRIGGER_STYLES.length; i++) {
                String word = WORD_TRIGGERS[i];
                if (!tail.endsWith(word)) {
                    continue;
                }
                int wordStart = safeEnd - word.length();
                if (wordStart < 0) {
                    continue;
                }
                if (wordStart > 0 && Character.isLetterOrDigit(curText.charAt(wordStart - 1))) {
                    continue;
                }
                state.lastWordTriggerTime = now;
                int perChar = Math.max(1, Math.min(4, 16 / Math.max(1, word.length())));
                for (int offset = wordStart; offset < safeEnd; offset++) {
                    spawner.spawnStyledParticles(view, state, offset, String.valueOf(curText.charAt(offset)), perChar, WORD_TRIGGER_STYLES[i], 1.0f);
                }
                helper.haptic(view, false);
                return;
            }
        } catch (Throwable error) {
            AnimLog.report("word trigger", error);
        }
    }

    private void startAnimationLoop(final EditText view, final AnimationState state) {
        if (state.animationRunning) {
            return;
        }
        state.animationRunning = true;
        view.postOnAnimationDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    if (!initialized || view.getWindowToken() == null) {
                        state.animationRunning = false;
                        state.hasAnimatingChars = false;
                        state.charStartTimes.clear();
                        state.particles.clear();
                        state.ghosts.clear();
                        overlay.detach(state);
                        chars.removeHiddenSpans(view, state);
                        tilt.release();
                        return;
                    }
                    if (!state.particles.isEmpty() && state.lastDrawTime > 0L && now - state.lastDrawTime > 2000L) {
                        state.animationRunning = false;
                        state.hasAnimatingChars = false;
                        state.charStartTimes.clear();
                        state.particles.clear();
                        state.ghosts.clear();
                        overlay.detach(state);
                        chars.removeHiddenSpans(view, state);
                        tilt.release();
                        return;
                    }
                    int maxDuration = Math.max(settings.duration, settings.blurDuration);
                    Iterator<Map.Entry<Integer, CharAnimData>> iterator = state.charStartTimes.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<Integer, CharAnimData> entry = iterator.next();
                        if (now - entry.getValue().startTime >= maxDuration) {
                            iterator.remove();
                            chars.removeHiddenSpan(view, state, entry.getKey());
                        }
                    }
                    int ghostDuration = chars.ghostDuration();
                    Iterator<GhostGlyph> ghosts = state.ghosts.iterator();
                    while (ghosts.hasNext()) {
                        if (now - ghosts.next().startTime >= ghostDuration) {
                            ghosts.remove();
                        }
                    }
                    view.invalidate();
                    if (state.particles.isEmpty()) {
                        overlay.detach(state);
                    } else {
                        overlay.invalidate(state);
                    }
                    if (!state.charStartTimes.isEmpty() || !state.particles.isEmpty() || !state.ghosts.isEmpty()) {
                        view.postOnAnimationDelayed(this, 16);
                    } else {
                        state.animationRunning = false;
                        state.hasAnimatingChars = false;
                        chars.removeHiddenSpans(view, state);
                        tilt.release();
                    }
                } catch (Throwable error) {
                    AnimLog.report("startAnimationLoop", error);
                    state.animationRunning = false;
                    state.hasAnimatingChars = false;
                    state.ghosts.clear();
                    overlay.detach(state);
                    chars.removeHiddenSpans(view, state);
                }
            }
        }, 16);
    }

    private void invalidateTrackedViews() {
        for (View view : new ArrayList<>(states.keySet())) {
            try {
                if (view != null) {
                    view.invalidate();
                }
            } catch (Throwable error) {
                AnimLog.report("invalidateTrackedViews", error);
            }
        }
    }

    private boolean isSendClear(AnimationState state, long now) {
        if (state.sendPendingTime > 0L && now - state.sendPendingTime < 1500L) {
            state.sendPendingTime = 0L;
            return true;
        }
        if (!sendHookInstalled) {
            return true;
        }
        return false;
    }
}
