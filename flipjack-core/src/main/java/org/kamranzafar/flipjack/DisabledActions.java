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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The {@link DisabledAction} strategies.
 */
final class DisabledActions {

    private DisabledActions() {
    }

    /**
     * Call the same interface method on another object (class- or method-level {@code fallback}).
     */
    static DisabledAction delegateTo(Supplier<?> target) {
        return (method, args) -> Invocations.invoke(target.get(), method, args);
    }

    /**
     * Call a different method on the feature's own object ({@code fallbackMethod}).
     */
    static DisabledAction callMethod(Object target, Method fallbackMethod) {
        return (method, args) -> Invocations.invoke(target, fallbackMethod, args);
    }

    /**
     * No fallback: throw or do nothing.
     */
    static DisabledAction of(String feature, WhenDisabled whenDisabled) {
        return switch (whenDisabled) {
            case THROW -> (method, args) -> {
                throw new FeatureDisabledException(feature, method);
            };
            case NO_OP -> (method, args) -> emptyValue(method.getReturnType());
        };
    }

    private static Object emptyValue(Class<?> type) {
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type.isPrimitive() && type != void.class) {
            return Array.get(Array.newInstance(type, 1), 0); // 0, false, '\0'
        }
        return null;
    }
}
