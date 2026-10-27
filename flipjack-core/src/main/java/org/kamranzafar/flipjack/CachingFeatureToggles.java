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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Decorator that caches each feature's state for a fixed time, so a slow or remote
 * source is not queried on every call. Failed lookups are not cached; wrap with
 * {@link FailSafeFeatureToggles} to handle them.
 */
public final class CachingFeatureToggles implements FeatureToggles {

    private record Entry(boolean enabled, long expiresAtNanos) {
    }

    private final FeatureToggles delegate;
    private final long ttlNanos;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingFeatureToggles(FeatureToggles delegate, Duration ttl) {
        this(delegate, ttl, System::nanoTime);
    }

    CachingFeatureToggles(FeatureToggles delegate, Duration ttl, LongSupplier nanoClock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must not be negative");
        }
        this.ttlNanos = ttl.toNanos();
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean isEnabled(String feature) {
        long now = nanoClock.getAsLong();
        Entry entry = cache.get(feature);
        if (entry != null && now - entry.expiresAtNanos() < 0) {
            return entry.enabled();
        }
        boolean enabled = delegate.isEnabled(feature);
        cache.put(feature, new Entry(enabled, now + ttlNanos));
        return enabled;
    }

    /**
     * Only context-free lookups are cached; a non-empty context always goes to the source,
     * so one subject's answer is never served to another and the cache stays bounded.
     */
    @Override
    public boolean isEnabled(String feature, FeatureContext context) {
        return context.isEmpty() ? isEnabled(feature) : delegate.isEnabled(feature, context);
    }

    /**
     * Forces the next lookup of {@code feature} to go to the source.
     */
    public void invalidate(String feature) {
        cache.remove(feature);
    }

    public void invalidateAll() {
        cache.clear();
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
