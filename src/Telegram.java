package com.textanimation;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.view.View;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class Telegram {
    private static final int CARET_COLOR = 0xFF54A1DB;

    final Method onDraw;
    final Method allowCursor;
    final Field allowCursorField;
    final List<Method> sends = new ArrayList<>();

    private final Field shape;
    private final Field gradient;
    private final Field message;
    private final Field button;
    private final Field container;
    private final int[] location = new int[2];

    Telegram() throws ReflectiveOperationException {
        Class<?> edit = Class.forName("org.telegram.ui.Components.EditTextBoldCursor");
        onDraw = edit.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);

        Method allow = method(edit, "setAllowDrawCursor", boolean.class);
        Field allowField = field(edit, "allowDrawCursor");
        boolean cursor = allow != null && allowField != null && allowField.getType() == boolean.class;
        allowCursor = cursor ? allow : null;
        allowCursorField = cursor ? allowField : null;
        shape = field(edit, "cursorDrawable");
        gradient = field(edit, "gradientDrawable");

        Class<?> enter = find("org.telegram.ui.Components.ChatActivityEnterView");
        message = field(enter, "messageEditText");
        button = field(enter, "sendButton");
        container = field(enter, "sendButtonContainer");
        if (enter != null && message != null) {
            for (Method method : enter.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("sendMessageInternal") || (name.equals("sendMessage") && method.getParameterTypes().length == 0)) {
                    method.setAccessible(true);
                    sends.add(method);
                }
            }
        }
    }

    EditText field(Object enterView) throws IllegalAccessException {
        Object value = message.get(enterView);
        return value instanceof EditText ? (EditText) value : null;
    }

    float[] target(Object enterView) throws IllegalAccessException {
        View view = shown(button, enterView);
        if (view == null) {
            view = shown(container, enterView);
        }
        if (view == null) {
            return null;
        }
        view.getLocationInWindow(location);
        return new float[]{location[0] + view.getWidth() / 2.0f, location[1] + view.getHeight() / 2.0f};
    }

    int caretColor(EditText view) {
        try {
            Object drawable = gradient == null ? null : gradient.get(view);
            if (drawable instanceof GradientDrawable && Build.VERSION.SDK_INT >= 24) {
                ColorStateList colors = ((GradientDrawable) drawable).getColor();
                return colors != null ? colors.getDefaultColor() : CARET_COLOR;
            }
            drawable = shape == null ? null : shape.get(view);
            if (drawable instanceof ShapeDrawable) {
                return ((ShapeDrawable) drawable).getPaint().getColor();
            }
        } catch (IllegalAccessException error) {
            Logs.error("caret color", error);
        }
        return CARET_COLOR;
    }

    private static View shown(Field field, Object owner) throws IllegalAccessException {
        Object value = field == null ? null : field.get(owner);
        if (!(value instanceof View)) {
            return null;
        }
        View view = (View) value;
        return view.isShown() && view.getWidth() > 0 && view.getHeight() > 0 ? view : null;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            Logs.error("class " + name, error);
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            Logs.error("field " + name, error);
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... types) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException error) {
            Logs.error("method " + name, error);
            return null;
        }
    }
}
