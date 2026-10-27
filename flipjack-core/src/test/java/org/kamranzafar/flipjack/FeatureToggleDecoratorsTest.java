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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FeatureToggleDecoratorsTest {

    @Test
    void cachesUntilTtlExpires() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicBoolean state = new AtomicBoolean(false);
        FeatureToggles source = feature -> {
            lookups.incrementAndGet();
            return state.get();
        };
        AtomicLong clock = new AtomicLong();
        CachingFeatureToggles cached = new CachingFeatureToggles(source, Duration.ofSeconds(10), clock::get);

        assertFalse(cached.isEnabled("x"));
        state.set(true);
        assertFalse(cached.isEnabled("x")); // still cached
        assertEquals(1, lookups.get());

        clock.addAndGet(Duration.ofSeconds(10).toNanos());
        assertTrue(cached.isEnabled("x")); // expired, reloaded
        assertEquals(2, lookups.get());

        state.set(false);
        cached.invalidate("x");
        assertFalse(cached.isEnabled("x"));
    }

    @Test
    void cacheDoesNotStoreFailures() {
        AtomicBoolean failing = new AtomicBoolean(true);
        FeatureToggles source = feature -> {
            if (failing.get()) {
                throw new IllegalStateException("down");
            }
            return true;
        };
        CachingFeatureToggles cached = new CachingFeatureToggles(source, Duration.ofMinutes(1));
        assertThrows(IllegalStateException.class, () -> cached.isEnabled("x"));
        failing.set(false);
        assertTrue(cached.isEnabled("x"));
    }

    @Test
    void failSafeServesLastKnownThenDefault() {
        AtomicBoolean failing = new AtomicBoolean(false);
        FeatureToggles source = feature -> {
            if (failing.get()) {
                throw new IllegalStateException("down");
            }
            return true;
        };
        List<String> errors = new ArrayList<>();
        FailSafeFeatureToggles safe = new FailSafeFeatureToggles(source, false, (feature, e) -> errors.add(feature));

        assertTrue(safe.isEnabled("a"));
        failing.set(true);
        assertTrue(safe.isEnabled("a"));   // last known
        assertFalse(safe.isEnabled("b"));  // never seen: default
        assertEquals(List.of("a", "b"), errors);
    }

    @Test
    void overridesWinUntilCleared() {
        OverridableFeatureToggles toggles = new InMemoryFeatureToggles().enable("x").withOverrides();
        assertTrue(toggles.isEnabled("x"));
        toggles.override("x", false);
        assertFalse(toggles.isEnabled("x"));
        assertEquals(Map.of("x", false), toggles.overrides());
        toggles.clearOverride("x");
        assertTrue(toggles.isEnabled("x"));
    }

    @Test
    void observerSeesEveryEvaluationAndCannotBreakIt() {
        List<String> seen = new ArrayList<>();
        FeatureToggles observed = new InMemoryFeatureToggles().enable("x")
                .observed((feature, enabled) -> seen.add(feature + "=" + enabled));
        observed.isEnabled("x");
        observed.isEnabled("y");
        assertEquals(List.of("x=true", "y=false"), seen);

        FeatureToggles broken = new InMemoryFeatureToggles().enable("x")
                .observed((feature, enabled) -> {
                    throw new IllegalStateException("listener bug");
                });
        assertTrue(broken.isEnabled("x"));
    }

    public interface Feature {
        String execute();
    }

    @Test
    void composedChainDrivesToggledFeatures() {
        AtomicBoolean sourceDown = new AtomicBoolean(false);
        InMemoryFeatureToggles store = new InMemoryFeatureToggles().enable("premium");
        FeatureToggles source = feature -> {
            if (sourceDown.get()) {
                throw new IllegalStateException("down");
            }
            return store.isEnabled(feature);
        };
        List<String> seen = new ArrayList<>();
        OverridableFeatureToggles toggles = source
                .cached(Duration.ZERO)
                .failSafe(false)
                .withOverrides();
        FeatureToggles observed = toggles.observed((feature, enabled) -> seen.add(feature + "=" + enabled));

        Feature basic = () -> "basic";
        Feature feature = ToggledFeature.builder(Feature.class)
                .feature("premium").enabled(() -> "premium").fallback(basic).toggles(observed).build();

        assertEquals("premium", feature.execute());
        sourceDown.set(true);
        assertEquals("premium", feature.execute()); // fail-safe: last known state
        toggles.override("premium", false);
        assertEquals("basic", feature.execute());   // kill switch
        assertEquals(List.of("premium=true", "premium=true", "premium=false"), seen);
    }
}
