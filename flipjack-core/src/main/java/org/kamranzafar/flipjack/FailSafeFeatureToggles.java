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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Decorator that never lets a failing source break a request. When the source throws,
 * it returns the feature's last known state, or {@code defaultEnabled} if it has none,
 * and reports the error.
 */
public final class FailSafeFeatureToggles implements FeatureToggles {

    /**
     * Receives source failures.
     */
    @FunctionalInterface
    public interface ErrorHandler {
        void onError(String feature, RuntimeException error);
    }

    private static final System.Logger LOG = System.getLogger(FailSafeFeatureToggles.class.getName());

    private final FeatureToggles delegate;
    private final boolean defaultEnabled;
    private final ErrorHandler errorHandler;
    private final ConcurrentMap<String, Boolean> lastKnown = new ConcurrentHashMap<>();

    /**
     * Logs failures as warnings through {@link System.Logger}.
     */
    public FailSafeFeatureToggles(FeatureToggles delegate, boolean defaultEnabled) {
        this(delegate, defaultEnabled, (feature, error) -> LOG.log(System.Logger.Level.WARNING,
                "Toggle lookup failed for '" + feature + "'; using last known or default state", error));
    }

    public FailSafeFeatureToggles(FeatureToggles delegate, boolean defaultEnabled, ErrorHandler errorHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.defaultEnabled = defaultEnabled;
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    @Override
    public boolean isEnabled(String feature) {
        try {
            boolean enabled = delegate.isEnabled(feature);
            lastKnown.put(feature, enabled);
            return enabled;
        } catch (RuntimeException error) {
            report(feature, error);
            return lastKnown.getOrDefault(feature, defaultEnabled);
        }
    }

    /**
     * Last known state is only kept for context-free lookups; on failure a non-empty
     * context gets {@code defaultEnabled}, never another subject's last answer.
     */
    @Override
    public boolean isEnabled(String feature, FeatureContext context) {
        if (context.isEmpty()) {
            return isEnabled(feature);
        }
        try {
            return delegate.isEnabled(feature, context);
        } catch (RuntimeException error) {
            report(feature, error);
            return defaultEnabled;
        }
    }

    private void report(String feature, RuntimeException error) {
        try {
            errorHandler.onError(feature, error);
        } catch (RuntimeException ignored) {
            // a broken error handler must not break the request either
        }
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        try {
            return delegate.knownFeatures();
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    /**
     * Closes the wrapped toggles.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
