package me.matsubara.realisticvillagers.util;

import com.cryptomorin.xseries.reflection.XReflection;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

    public static @Nullable Object getFieldValue(MethodHandle handle) {
        try {
            return handle.invoke();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

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
        return getConstructor(true, clazz, parameterTypes);
    }

    public static @Nullable MethodHandle getConstructor(boolean printStackTrace, @NotNull Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, Class<?>... parameterTypes) {
        return getMethod(refc, name, true, parameterTypes);
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, boolean printStackTrace, Class<?>... parameterTypes) {
        try {
            Method method = refc.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            if (printStackTrace) exception.printStackTrace();
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

    public static @Nullable MethodHandle getMethod(Class<?> refc, String name, MethodType type, boolean isStatic, boolean printStackTrace, String... extraNames) {
        try {
            if (isStatic) return LOOKUP.findStatic(refc, name, type);
            if (XReflection.MINOR_NUMBER > 17) {
                Method method = refc.getMethod(name, type.parameterArray());
                if (!method.getReturnType().isAssignableFrom(type.returnType())) {
                    throw new NoSuchMethodException();
                }
                return LOOKUP.unreflect(method);
            }
            return LOOKUP.findVirtual(refc, name, type);
        } catch (ReflectiveOperationException exception) {
            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) {
                    return getMethod(refc, extraNames[0], type, isStatic, printStackTrace);
                }
                for (String extra : extraNames) {
                    int index = ArrayUtils.indexOf(extraNames, extra);
                    String[] rest = ArrayUtils.remove(extraNames, index);
                    return getMethod(refc, extra, type, isStatic, printStackTrace, rest);
                }
            }
            if (printStackTrace) exception.printStackTrace();
            return null;
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

    public static @Nullable MethodHandle getField(Class<?> refc, Class<?> instc, String name, boolean isGetter, String... extraNames) {
        try {
            Field temp = getFieldHandleRaw(refc, instc, name);
            MethodHandle handle = temp != null ? (isGetter ? LOOKUP.unreflectGetter(temp) : LOOKUP.unreflectSetter(temp)) : null;

            if (handle != null) return handle;

            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) return getField(refc, instc, extraNames[0], isGetter);
                return getField(refc, instc, extraNames[0], isGetter, removeFirst(extraNames));
            }
        } catch (IllegalAccessException exception) {
            exception.printStackTrace();
        }

        return null;
    }

    private static @NotNull String[] removeFirst(@NotNull String[] array) {
        int length = array.length;

        String[] result = new String[length - 1];
        System.arraycopy(array, 1, result, 0, length - 1);

        return result;
    }

    public static @Nullable Field getFieldRaw(Class<?> refc, Class<?> instc, String name, String... extraNames) {
        Field handle = getFieldHandleRaw(refc, instc, name);
        if (handle != null) return handle;

        if (extraNames != null && extraNames.length > 0) {
            if (extraNames.length == 1) return getFieldRaw(refc, instc, extraNames[0]);
            return getFieldRaw(refc, instc, extraNames[0], removeFirst(extraNames));
        }

        return null;
    }

    private static @Nullable Field getFieldHandleRaw(@NotNull Class<?> refc, Class<?> inscofc, String name) {
        for (Field field : refc.getDeclaredFields()) {
            field.setAccessible(true);

            if (!field.getName().equalsIgnoreCase(name)) continue;

            if (field.getType().isInstance(inscofc) || field.getType().isAssignableFrom(inscofc)) {
                return field;
            }
        }
        return null;
    }
}