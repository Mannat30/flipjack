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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe memoizing supplier.
 */
final class LazySupplier<T> implements Supplier<T> {

    private final Supplier<? extends T> source;
    private volatile T value;

    LazySupplier(Supplier<? extends T> source) {
        this.source = source;
    }

    @Override
    public T get() {
        T result = value;
        if (result == null) {
            synchronized (this) {
                result = value;
                if (result == null) {
                    result = Objects.requireNonNull(source.get(), "lazy value resolved to null");
                    value = result;
                }
            }
        }
        return result;
    }
}
