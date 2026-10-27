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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, mutable toggles held in memory. Useful for tests, admin endpoints and simple apps.
 */
public final class InMemoryFeatureToggles implements FeatureToggles {

    private final Map<String, Boolean> toggles = new ConcurrentHashMap<>();
    private final boolean defaultEnabled;

    public InMemoryFeatureToggles() {
        this(false);
    }

    public InMemoryFeatureToggles(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public InMemoryFeatureToggles(Map<String, Boolean> initial, boolean defaultEnabled) {
        this(defaultEnabled);
        toggles.putAll(initial);
    }

    @Override
    public boolean isEnabled(String feature) {
        return toggles.getOrDefault(feature, defaultEnabled);
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        return Optional.of(Set.copyOf(toggles.keySet()));
    }

    public InMemoryFeatureToggles set(String feature, boolean enabled) {
        toggles.put(feature, enabled);
        return this;
    }

    public InMemoryFeatureToggles enable(String feature) {
        return set(feature, true);
    }

    public InMemoryFeatureToggles disable(String feature) {
        return set(feature, false);
    }

    /**
     * Removes the explicit setting so the feature falls back to the default.
     */
    public InMemoryFeatureToggles reset(String feature) {
        toggles.remove(feature);
        return this;
    }

    /**
     * Snapshot of explicitly set toggles.
     */
    public Map<String, Boolean> snapshot() {
        return Map.copyOf(toggles);
    }
}
