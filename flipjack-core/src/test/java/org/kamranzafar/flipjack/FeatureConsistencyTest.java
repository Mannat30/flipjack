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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureConsistencyTest {

    static final class Features {
        static final String NEW_PRICING = "new-pricing";
        static final String TYPO = "new-pricng";
    }

    public interface Pricing {
        String quote();
    }

    public static class LegacyPricing implements Pricing {
        @Override
        public String quote() {
            return "legacy";
        }
    }

    @Toggleable(value = Features.TYPO, fallback = LegacyPricing.class)
    public static class MisspeltPricing implements Pricing {
        @Override
        public String quote() {
            return "new";
        }
    }

    @Toggleable(value = Features.NEW_PRICING, fallback = LegacyPricing.class)
    public static class NewPricing implements Pricing {
        @Override
        public String quote() {
            return "new";
        }
    }

    private static Pricing toggled(Pricing implementation, FeatureToggles toggles) {
        return ToggledFeature.builder(Pricing.class)
                .enabled(implementation).fallback(new LegacyPricing()).toggles(toggles).build();
    }

    @Test
    void reportsTyposAsMissingAndUnused() {
        InMemoryFeatureToggles store = new InMemoryFeatureToggles().enable(Features.NEW_PRICING);
        Pricing pricing = toggled(new MisspeltPricing(), store);

        FeatureConsistencyReport report = FeatureConsistency.check(store, List.of(pricing)).orElseThrow();
        assertFalse(report.isConsistent());
        assertEquals(Set.of("new-pricng"), report.missingFromStore());
        assertEquals(Set.of("new-pricing"), report.unusedInStore());
        report.logIfInconsistent();
    }

    @Test
    void consistentWhenNamesMatchAndInlineFeaturesAreDeclared() {
        InMemoryFeatureToggles store = new InMemoryFeatureToggles().enable(Features.NEW_PRICING).disable("audit-log");
        Pricing pricing = toggled(new NewPricing(), store);

        FeatureConsistencyReport withoutInline = FeatureConsistency.check(store, List.of(pricing)).orElseThrow();
        assertEquals(Set.of("audit-log"), withoutInline.unusedInStore());

        FeatureConsistencyReport report = FeatureConsistency
                .check(store, List.of(pricing, "not a toggled component"), Set.of("audit-log")).orElseThrow();
        assertTrue(report.isConsistent());
    }

    @Test
    void skippedWhenTheSourceCannotListItsFeatures() {
        FeatureToggles opaque = feature -> true;
        assertTrue(FeatureConsistency.check(opaque, List.of(toggled(new NewPricing(), opaque))).isEmpty());
    }

    @Test
    void knownFeaturesFlowThroughDecoratorChains() {
        InMemoryFeatureToggles store = new InMemoryFeatureToggles().enable("a").disable("b");
        FeatureToggles chain = store.cached(Duration.ofMinutes(1)).failSafe(false).withOverrides()
                .observed((feature, enabled) -> {
                });
        assertEquals(Optional.of(Set.of("a", "b")), chain.knownFeatures());

        FeatureToggles broken = new FeatureToggles() {
            public boolean isEnabled(String feature) {
                return false;
            }

            public Optional<Set<String>> knownFeatures() {
                throw new IllegalStateException("down");
            }
        };
        assertEquals(Optional.empty(), broken.failSafe(false).knownFeatures());
    }

    @Test
    void pollingSourceIsUnknownUntilItHasLoaded() {
        boolean[] fail = {true};
        try (PollingFeatureToggles polling = PollingFeatureToggles.builder(() -> {
            if (fail[0]) {
                throw new IllegalStateException("down");
            }
            return Map.of("a", true);
        }).interval(Duration.ofHours(1)).onLoadError(e -> {
        }).build()) {
            assertEquals(Optional.empty(), polling.knownFeatures()); // no noise before a load succeeds
            fail[0] = false;
            assertTrue(polling.refresh());
            assertEquals(Optional.of(Set.of("a")), polling.knownFeatures());
        }
    }
}
