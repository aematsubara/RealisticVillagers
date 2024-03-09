package me.matsubara.realisticvillagers.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;

// NOTE: Warning ("Usage of API marked for removal") disabled in IDEA.
public final class Reflection {

    private static final Unsafe UNSAFE = getUnsafe();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static MethodHandle getFieldGetter(Class<?> clazz, String name) {
        return getField(clazz, name, true);
    }

    public static MethodHandle getFieldSetter(Class<?> clazz, String name) {
        return getField(clazz, name, false);
    }

    public static @Nullable MethodHandle getField(@NotNull Class<?> clazz, String name, boolean isGetter) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);

            if (isGetter) return LOOKUP.unreflectGetter(field);
            return LOOKUP.unreflectSetter(field);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getConstructor(@NotNull Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, Class<?>... parameterTypes) {
        MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        try {
            Method method = refc.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static void setFieldUsingUnsafe(@NotNull Field field, Object object, Object newValue) {
        try {
            field.setAccessible(true);
            int fieldModifiersMask = field.getModifiers();
            boolean isFinalModifierPresent = (fieldModifiersMask & Modifier.FINAL) == Modifier.FINAL;
            if (isFinalModifierPresent) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    try {
                        long offset = UNSAFE.objectFieldOffset(field);
                        setFieldUsingUnsafe(object, field.getType(), offset, newValue);
                        return null;
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });
            } else {
                try {
                    field.set(object, newValue);
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException(exception);
                }
            }
        } catch (SecurityException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void setFieldUsingUnsafe(Object base, Class<?> type, long offset, Object newValue) {
        if (type == Integer.TYPE) {
            UNSAFE.putInt(base, offset, ((Integer) newValue));
        } else if (type == Short.TYPE) {
            UNSAFE.putShort(base, offset, ((Short) newValue));
        } else if (type == Long.TYPE) {
            UNSAFE.putLong(base, offset, ((Long) newValue));
        } else if (type == Byte.TYPE) {
            UNSAFE.putByte(base, offset, ((Byte) newValue));
        } else if (type == Boolean.TYPE) {
            UNSAFE.putBoolean(base, offset, ((Boolean) newValue));
        } else if (type == Float.TYPE) {
            UNSAFE.putFloat(base, offset, ((Float) newValue));
        } else if (type == Double.TYPE) {
            UNSAFE.putDouble(base, offset, ((Double) newValue));
        } else if (type == Character.TYPE) {
            UNSAFE.putChar(base, offset, ((Character) newValue));
        } else {
            UNSAFE.putObject(base, offset, newValue);
        }
    }
}