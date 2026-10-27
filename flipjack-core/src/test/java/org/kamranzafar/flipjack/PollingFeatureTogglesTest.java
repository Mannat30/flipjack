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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class PollingFeatureTogglesTest {

    /**
     * A store whose contents and availability the test controls.
     */
    static final class FakeStore {
        final AtomicReference<Map<String, ?>> contents = new AtomicReference<>(Map.of());
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final AtomicInteger loads = new AtomicInteger();

        Map<String, ?> load() throws Exception {
            loads.incrementAndGet();
            Exception error = failure.get();
            if (error != null) {
                throw error;
            }
            return contents.get();
        }
    }

    private static final Duration LONG = Duration.ofHours(1);

    @Test
    void loadsSynchronouslyOnBuild() {
        FakeStore store = new FakeStore();
        store.contents.set(Map.of("premium", true));
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(store::load).interval(LONG).build()) {
            assertTrue(toggles.isEnabled("premium"));
            assertFalse(toggles.isEnabled("unknown"));
            assertTrue(toggles.lastLoaded().isPresent());
        }
    }

    @Test
    void refreshPicksUpChangesAndReportsThem() {
        FakeStore store = new FakeStore();
        store.contents.set(Map.of("a", true, "b", false));
        List<String> changes = new ArrayList<>();
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(store::load)
                .interval(LONG)
                .onChange((feature, before, after) -> changes.add(feature + ":" + before + "->" + after))
                .build()) {
            changes.clear(); // ignore the initial load's notifications

            store.contents.set(Map.of("a", true, "b", true, "c", true));
            assertTrue(toggles.refresh());
            assertTrue(toggles.isEnabled("b"));
            assertEquals(List.of("b:false->true", "c:null->true"), sorted(changes));

            changes.clear();
            store.contents.set(Map.of("a", true));
            assertTrue(toggles.refresh());
            assertEquals(List.of("b:true->null", "c:true->null"), sorted(changes));
        }
    }

    @Test
    void failedLoadKeepsLastGoodSnapshot() {
        FakeStore store = new FakeStore();
        store.contents.set(Map.of("premium", true));
        List<Exception> errors = new ArrayList<>();
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(store::load)
                .interval(LONG).onLoadError(errors::add).build()) {

            store.failure.set(new java.io.IOException("store unavailable")); // checked exceptions are fine
            assertFalse(toggles.refresh());
            assertTrue(toggles.isEnabled("premium"));
            assertEquals(1, errors.size());

            store.failure.set(null);
            store.contents.set(new HashMap<>(Map.of("premium", false)));
            assertTrue(toggles.refresh());
            assertFalse(toggles.isEnabled("premium"));
        }
    }

    @Test
    void acceptsBooleansAndBooleanStrings() {
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(
                () -> Map.of("a", true, "b", " TRUE ", "c", "false")).interval(LONG).build()) {
            assertEquals(Map.of("a", true, "b", true, "c", false), toggles.snapshot());
        }
    }

    @Test
    void invalidValuesAreRejectedAsAWhole() {
        Map<String, String> raw = new HashMap<>(Map.of("a", "true"));
        List<Exception> errors = new ArrayList<>();
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(() -> raw)
                .interval(LONG).onLoadError(errors::add).build()) {
            assertTrue(toggles.isEnabled("a"));

            raw.put("a", "false");
            raw.put("b", "ture"); // typo
            assertFalse(toggles.refresh());
            assertTrue(toggles.isEnabled("a")); // whole load rejected, snapshot unchanged
            assertTrue(errors.get(0) instanceof IllegalArgumentException);
        }
    }

    @Test
    void nullMapOrValuesAreFailures() {
        AtomicReference<Map<String, ?>> result = new AtomicReference<>(Map.of("a", true));
        try (PollingFeatureToggles toggles = PollingFeatureToggles.builder(result::get)
                .interval(LONG).onLoadError(e -> {
                }).build()) {
            result.set(null);
            assertFalse(toggles.refresh());
            Map<String, Boolean> withNull = new HashMap<>();
            withNull.put("a", null);
            result.set(withNull);
            assertFalse(toggles.refresh());
            assertTrue(toggles.isEnabled("a"));
        }
    }

    @Test
    void initialLoadFailureIsLenientByDefaultOrFailsWhenRequired() {
        FakeStore store = new FakeStore();
        store.failure.set(new IllegalStateException("down"));

        try (PollingFeatureToggles lenient = PollingFeatureToggles.builder(store::load)
                .interval(LONG).defaultEnabled(false).onLoadError(e -> {
                }).build()) {
            assertFalse(lenient.isEnabled("premium"));
            assertFalse(lenient.lastLoaded().isPresent());
        }

        assertThrows(IllegalStateException.class, () -> PollingFeatureToggles.builder(store::load)
                .interval(LONG).onLoadError(e -> {
                }).requireInitialLoad().build());
    }

    @Test
    void pollsInTheBackgroundAndStopsOnClose() throws InterruptedException {
        FakeStore store = new FakeStore();
        store.contents.set(Map.of("premium", false));
        PollingFeatureToggles toggles = PollingFeatureToggles.builder(store::load)
                .interval(Duration.ofMillis(20)).build();

        store.contents.set(Map.of("premium", true));
        assertTrue(eventually(() -> toggles.isEnabled("premium")), "background poll picked up the change");

        toggles.close();
        Thread.sleep(50); // let any in-flight poll finish
        int loadsAfterClose = store.loads.get();
        Thread.sleep(150);
        assertEquals(loadsAfterClose, store.loads.get());
        assertTrue(toggles.isEnabled("premium")); // last snapshot still answers
        assertFalse(toggles.refresh());
    }

    @Test
    void closingTheOutermostDecoratorStopsThePoller() throws InterruptedException {
        FakeStore store = new FakeStore();
        PollingFeatureToggles poller = PollingFeatureToggles.builder(store::load)
                .interval(Duration.ofMillis(20)).build();
        FeatureToggles chain = poller.withOverrides().observed((feature, enabled) -> {
        });

        assertTrue(eventually(() -> store.loads.get() >= 3), "polling started");
        chain.close();
        Thread.sleep(50);
        int loadsAfterClose = store.loads.get();
        Thread.sleep(150);
        assertEquals(loadsAfterClose, store.loads.get());
    }

    private static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(null);
        return copy;
    }
}
