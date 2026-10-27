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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Source of truth for feature state, queried on every call to a toggled feature.
 * <p>
 * Implement this to back toggles with a database, a config service, Togglz, Unleash,
 * OpenFeature or anything else. Add cross-cutting behaviour by composing decorators
 * with the default methods, innermost first:
 * <pre>{@code
 * OverridableFeatureToggles toggles = new DatabaseFeatureToggles(dataSource)
 *         .cached(Duration.ofSeconds(30))   // fewer lookups
 *         .failSafe(false)                  // survive source outages
 *         .withOverrides();                 // runtime kill switches win over everything
 * }</pre>
 * Each method wraps the toggles it is called on and returns the new outer layer.
 * <p>
 * Toggles are {@link AutoCloseable}: closing a decorator closes what it wraps, so closing
 * the outermost layer releases a {@link PollingFeatureToggles} poller underneath. Spring
 * calls {@code close()} on a {@code FeatureToggles} bean automatically at shutdown.
 */
@FunctionalInterface
public interface FeatureToggles extends AutoCloseable {

    boolean isEnabled(String feature);

    /**
     * State of {@code feature} for {@code context}. Override this in sources that support
     * targeting; the default ignores the context. Decorators must override it too and pass
     * the context on, or it is silently dropped at that layer.
     */
    default boolean isEnabled(String feature, FeatureContext context) {
        return isEnabled(feature);
    }

    default boolean isDisabled(String feature) {
        return !isEnabled(feature);
    }

    /**
     * The features this source has explicit state for, if it can list them. Used by
     * {@link FeatureConsistency} to spot typos and stale toggles. Empty (the default) means
     * the source can't enumerate its features and the check is skipped.
     */
    default Optional<Set<String>> knownFeatures() {
        return Optional.empty();
    }

    /**
     * Releases resources such as background pollers. No-op by default; never throws a checked exception.
     */
    @Override
    default void close() {
    }

    /**
     * Caches each feature's state for {@code ttl}. See {@link CachingFeatureToggles}.
     */
    default CachingFeatureToggles cached(Duration ttl) {
        return new CachingFeatureToggles(this, ttl);
    }

    /**
     * Never throws: on failure returns the last known state, else {@code defaultEnabled}.
     * See {@link FailSafeFeatureToggles}.
     */
    default FailSafeFeatureToggles failSafe(boolean defaultEnabled) {
        return new FailSafeFeatureToggles(this, defaultEnabled);
    }

    /**
     * Adds a runtime override layer. See {@link OverridableFeatureToggles}.
     */
    default OverridableFeatureToggles withOverrides() {
        return new OverridableFeatureToggles(this);
    }

    /**
     * Reports every evaluation to {@code listener}. See {@link ObservedFeatureToggles}.
     */
    default ObservedFeatureToggles observed(FeatureToggleListener listener) {
        return new ObservedFeatureToggles(this, listener);
    }
}
