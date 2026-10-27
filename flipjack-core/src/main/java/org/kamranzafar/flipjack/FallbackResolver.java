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

import java.util.List;

/**
 * Supplies the instance for a fallback class named in {@link Toggleable#fallback()}.
 * DI integrations implement this with their container (Spring uses the bean factory);
 * in plain Java use {@link #of(Object...)}.
 */
@FunctionalInterface
public interface FallbackResolver {

    Object resolve(Class<?> type);

    /**
     * Resolves to the first given instance of the requested type.
     */
    static FallbackResolver of(Object... instances) {
        List<Object> candidates = List.of(instances);
        return type -> candidates.stream()
                .filter(type::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No fallback instance of type " + type.getName()));
    }
}
