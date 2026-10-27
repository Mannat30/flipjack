/*
 * Copyright 2026 Kamran Zafar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kamranzafar.flipjack;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * All {@link Toggleable} lookups, so every integration reads annotations the same way.
 */
final class ToggleAnnotations {

    private ToggleAnnotations() {
    }

    /**
     * Class-level annotation on the type or a superclass (so subclass proxies still match).
     */
    static Toggleable onType(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Toggleable toggleable = c.getAnnotation(Toggleable.class);
            if (toggleable != null) {
                return toggleable;
            }
        }
        return null;
    }

    /**
     * Method-level annotation for an interface method: the implementation's method
     * (searching superclasses) wins over the interface method.
     */
    static Toggleable onMethod(Class<?> type, Method interfaceMethod) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method impl = c.getDeclaredMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                Toggleable toggleable = impl.getAnnotation(Toggleable.class);
                if (toggleable != null) {
                    return toggleable;
                }
            } catch (NoSuchMethodException ignored) {
                // not declared here; keep looking
            }
        }
        return interfaceMethod.getAnnotation(Toggleable.class);
    }

    /**
     * True if the type, any of its methods, or any of its interfaces' methods carry the annotation.
     */
    static boolean isToggleable(Class<?> type) {
        if (onType(type) != null) {
            return true;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Toggleable.class)) {
                    return true;
                }
            }
        }
        for (Class<?> iface : allInterfaces(type)) {
            for (Method m : iface.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Toggleable.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Annotated methods declared on the class hierarchy itself (not on interfaces).
     */
    static Set<Method> annotatedClassMethods(Class<?> type) {
        Set<Method> result = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Toggleable.class) && !m.isBridge() && !m.isSynthetic()) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    static Set<Class<?>> allInterfaces(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                collect(iface, result);
            }
        }
        return result;
    }

    private static void collect(Class<?> iface, Set<Class<?>> result) {
        if (result.add(iface)) {
            for (Class<?> parent : iface.getInterfaces()) {
                collect(parent, result);
            }
        }
    }
}
