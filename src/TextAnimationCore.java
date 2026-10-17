package com.textanimation;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class TextAnimationCore {
    public static final int VERSION_CODE = 12;
    public static final String VERSION_NAME = "12.0";
    public static final int BRIDGE_API = 1;

    private static final long TIMEOUT_MS = 3000L;

    private static TextAnimationCore instance;

    final Telegram telegram;
    final Clusters clusters = new Clusters();
    Config config = Config.DEFAULT;

    private final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private final WeakHashMap<EditText, Input> inputs = new WeakHashMap<>();

    private TextAnimationCore(Telegram telegram) {
        this.telegram = telegram;
    }

    public static void startBridge() {
        onMain(() -> {
            if (instance != null) {
                return;
            }
            TextAnimationCore core = new TextAnimationCore(new Telegram());
            core.install();
            instance = core;
            Logs.info("started " + VERSION_NAME);
        });
    }

    public static void updateSettingsBridge(String json) {
        Config next = Config.parse(json);
        onMain(() -> {
            if (instance != null) {
                instance.apply(next);
            }
        });
    }

    public static void onUnloadBridge() {
        onMain(() -> {
            TextAnimationCore core = instance;
            instance = null;
            if (core != null) {
                core.uninstall();
                Logs.info("stopped");
            }
        });
    }

    Input input(EditText view) {
        Input input = inputs.get(view);
        if (input == null) {
            input = new Input(this, view);
            inputs.put(view, input);
        }
        return input;
    }

    private void install() {
        try {
            hooks.add(XposedBridge.hookMethod(telegram.onDraw, new DrawHook()));
            if (telegram.allowCursor != null) {
                hooks.add(XposedBridge.hookMethod(telegram.allowCursor, new CursorHook()));
            }
            for (Method method : telegram.sends) {
                hooks.add(XposedBridge.hookMethod(method, new SendHook()));
            }
        } catch (Throwable error) {
            uninstall();
            throw new IllegalStateException("hook install failed", error);
        }
    }

    private void uninstall() {
        for (XC_MethodHook.Unhook hook : hooks) {
            try {
                hook.unhook();
            } catch (Throwable error) {
                Logs.error("unhook", error);
            }
        }
        hooks.clear();
        for (Map.Entry<EditText, Input> entry : new ArrayList<>(inputs.entrySet())) {
            EditText view = entry.getKey();
            if (view == null) {
                continue;
            }
            try {
                entry.getValue().dispose(view);
            } catch (Throwable error) {
                Logs.error("dispose", error);
            }
        }
        inputs.clear();
    }

    private void apply(Config next) {
        config = next;
        for (Map.Entry<EditText, Input> entry : new ArrayList<>(inputs.entrySet())) {
            EditText view = entry.getKey();
            if (view != null) {
                entry.getValue().refresh(view);
            }
        }
    }

    private interface Task {
        void run() throws Exception;
    }

    private static void onMain(Task task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            execute(task);
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                execute(task);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("main thread timeout");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
        Throwable error = failure.get();
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        if (error != null) {
            throw new IllegalStateException(error);
        }
    }

    private static void execute(Task task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private final class DrawHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (!(param.thisObject instanceof EditText) || !(param.args[0] instanceof Canvas)) {
                return;
            }
            EditText view = (EditText) param.thisObject;
            try {
                input(view).before(view, (Canvas) param.args[0]);
            } catch (Throwable error) {
                Logs.error("before draw", error);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Input input = inputs.get(param.thisObject);
            if (input == null || !(param.args[0] instanceof Canvas)) {
                return;
            }
            try {
                input.after((EditText) param.thisObject, (Canvas) param.args[0]);
            } catch (Throwable error) {
                Logs.error("after draw", error);
                input.fail();
            }
        }
    }

    private final class CursorHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            Input input = inputs.get(param.thisObject);
            if (input != null && input.owns() && param.args[0] instanceof Boolean) {
                input.want((Boolean) param.args[0]);
                param.args[0] = Boolean.FALSE;
            }
        }
    }

    private final class SendHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                EditText field = telegram.field(param.thisObject);
                if (field != null) {
                    input(field).arm(telegram.target(param.thisObject));
                }
            } catch (Throwable error) {
                Logs.error("send", error);
            }
        }
    }
}
