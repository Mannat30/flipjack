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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Decorator that lets features be forced on or off at runtime, for kill switches during
 * incidents, admin endpoints and tests. An override wins; otherwise the source decides.
 */
public final class OverridableFeatureToggles implements FeatureToggles {

    private final FeatureToggles delegate;
    private final ConcurrentMap<String, Boolean> overrides = new ConcurrentHashMap<>();

    public OverridableFeatureToggles(FeatureToggles delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean isEnabled(String feature) {
        Boolean override = overrides.get(feature);
        return override != null ? override : delegate.isEnabled(feature);
    }

    /**
     * Overrides are global: they win for every context.
     */
    @Override
    public boolean isEnabled(String feature, FeatureContext context) {
        Boolean override = overrides.get(feature);
        return override != null ? override : delegate.isEnabled(feature, context);
    }

    public OverridableFeatureToggles override(String feature, boolean enabled) {
        overrides.put(Objects.requireNonNull(feature, "feature"), enabled);
        return this;
    }

    /**
     * Hands {@code feature} back to the source.
     */
    public OverridableFeatureToggles clearOverride(String feature) {
        overrides.remove(feature);
        return this;
    }

    public OverridableFeatureToggles clearOverrides() {
        overrides.clear();
        return this;
    }

    public Map<String, Boolean> overrides() {
        return Map.copyOf(overrides);
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        return delegate.knownFeatures();
    }

    /**
     * Closes the wrapped toggles.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
