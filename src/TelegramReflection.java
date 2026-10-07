package com.textanimation;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class TelegramReflection {
    private Class<?> editTextEffectsClass;
    Class<?> editTextBoldCursorClass;
    private Class<?> editTextCaptionClass;
    private Class<?> chatActivityEditTextCaptionClass;
    private Field fieldResourcesProvider;
    private Class<?> themeClass;
    private Method themeGetColorMethod;
    private Method themeGetColorWithProviderMethod;
    private Method textViewGetVerticalOffsetMethod;
    Method setAllowDrawCursorMethod;
    Field allowDrawCursorField;
    Integer keyChatMessagePanelCursor;

    void resolve() {
        editTextEffectsClass = loadClassSafely("org.telegram.ui.Components.EditTextEffects");
        editTextBoldCursorClass = loadClassSafely("org.telegram.ui.Components.EditTextBoldCursor");
        editTextCaptionClass = loadClassSafely("org.telegram.ui.Components.EditTextCaption");
        chatActivityEditTextCaptionClass = loadClassSafely("org.telegram.ui.Components.ChatActivityEnterView$ChatActivityEditTextCaption");
        findCursorDrawMembers();
        findThemeMembers();
    }

    Class<?> editTextClass() {
        if (editTextBoldCursorClass != null) {
            return editTextBoldCursorClass;
        }
        return editTextEffectsClass != null ? editTextEffectsClass : EditText.class;
    }

    Class<?> loadClassSafely(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    Field findFieldInHierarchy(Class<?> cls, String fieldName) {
        Class<?> current = cls;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    Method findMethodInHierarchy(Class<?> cls, String methodName, Class<?>... parameterTypes) {
        Class<?> current = cls;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    Method findNamedMethod(Class<?> cls, String name, int parameterCount) {
        Class<?> current = cls;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void findCursorDrawMembers() {
        if (editTextBoldCursorClass == null) {
            return;
        }
        try {
            setAllowDrawCursorMethod = editTextBoldCursorClass.getMethod("setAllowDrawCursor", boolean.class);
        } catch (Throwable ignored) {
        }
        allowDrawCursorField = findFieldInHierarchy(editTextBoldCursorClass, "allowDrawCursor");
    }

    private void findThemeMembers() {
        fieldResourcesProvider = findFieldInHierarchy(chatActivityEditTextCaptionClass, "resourcesProvider");
        if (fieldResourcesProvider == null) {
            fieldResourcesProvider = findFieldInHierarchy(editTextCaptionClass, "resourcesProvider");
        }
        try {
            textViewGetVerticalOffsetMethod = TextView.class.getDeclaredMethod("getVerticalOffset", boolean.class);
            textViewGetVerticalOffsetMethod.setAccessible(true);
        } catch (Throwable error) {
            AnimLog.report("findThemeMembers", error);
        }
        try {
            themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            Field keyField = themeClass.getField("key_chat_messagePanelCursor");
            keyChatMessagePanelCursor = (Integer) keyField.get(null);
            themeGetColorMethod = themeClass.getMethod("getColor", int.class);
            Class<?> providerClass = Class.forName("org.telegram.ui.ActionBar.Theme$ResourcesProvider");
            themeGetColorWithProviderMethod = themeClass.getMethod("getColor", int.class, providerClass);
        } catch (Throwable error) {
            AnimLog.log("Theme lookup error: " + error);
        }
    }

    float getVerticalOffset(TextView view) {
        try {
            if (textViewGetVerticalOffsetMethod != null) {
                Object result = textViewGetVerticalOffsetMethod.invoke(view, Boolean.FALSE);
                if (result instanceof Integer) {
                    return ((Integer) result).floatValue();
                }
            }
        } catch (Throwable error) {
            AnimLog.report("getVerticalOffset", error);
        }
        return 0.0f;
    }

    int getThemeColorForView(View view, Integer key, int fallback) {
        if (key == null || themeClass == null) {
            return fallback;
        }
        try {
            Object provider = null;
            if (fieldResourcesProvider != null && view != null) {
                provider = fieldResourcesProvider.get(view);
            }
            if (provider != null && themeGetColorWithProviderMethod != null) {
                Object result = themeGetColorWithProviderMethod.invoke(null, key.intValue(), provider);
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            }
            if (themeGetColorMethod != null) {
                Object result = themeGetColorMethod.invoke(null, key.intValue());
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            }
        } catch (Throwable error) {
            AnimLog.report("getThemeColorForView", error);
        }
        return fallback;
    }
}
