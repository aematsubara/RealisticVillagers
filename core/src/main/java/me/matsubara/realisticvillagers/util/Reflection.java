package me.matsubara.realisticvillagers.util;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;

public final class Reflection {

    private final static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static MethodHandle getFieldGetter(Class<?> clazz, String name) {
        return getField(clazz, name, true);
    }

    public static MethodHandle getFieldSetter(Class<?> clazz, String name) {
        return getField(clazz, name, false);
    }

    public static MethodHandle getField(Class<?> clazz, String name, boolean isGetter) {
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

    public static MethodHandle getConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static MethodHandle getMethod(Class<?> refc, String name, Class<?> parameterTypes) {
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

    public static void setFieldUsingUnsafe(final Field field, final Object object, final Object newValue) {
        try {
            field.setAccessible(true);
            int fieldModifiersMask = field.getModifiers();
            boolean isFinalModifierPresent = (fieldModifiersMask & Modifier.FINAL) == Modifier.FINAL;
            if (isFinalModifierPresent) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    try {
                        Unsafe unsafe = getUnsafe();
                        long offset = unsafe.objectFieldOffset(field);
                        setFieldUsingUnsafe(object, field.getType(), offset, newValue, unsafe);
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

    private static Unsafe getUnsafe() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setFieldUsingUnsafe(Object base, Class<?> type, long offset, Object newValue, Unsafe unsafe) {
        if (type == Integer.TYPE) {
            unsafe.putInt(base, offset, ((Integer) newValue));
        } else if (type == Short.TYPE) {
            unsafe.putShort(base, offset, ((Short) newValue));
        } else if (type == Long.TYPE) {
            unsafe.putLong(base, offset, ((Long) newValue));
        } else if (type == Byte.TYPE) {
            unsafe.putByte(base, offset, ((Byte) newValue));
        } else if (type == Boolean.TYPE) {
            unsafe.putBoolean(base, offset, ((Boolean) newValue));
        } else if (type == Float.TYPE) {
            unsafe.putFloat(base, offset, ((Float) newValue));
        } else if (type == Double.TYPE) {
            unsafe.putDouble(base, offset, ((Double) newValue));
        } else if (type == Character.TYPE) {
            unsafe.putChar(base, offset, ((Character) newValue));
        } else {
            unsafe.putObject(base, offset, newValue);
        }
    }
}