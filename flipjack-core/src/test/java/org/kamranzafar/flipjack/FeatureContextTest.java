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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FeatureContextTest {

    /**
     * Enables each feature for the listed subjects only; records every context it sees.
     */
    static final class TargetedToggles implements FeatureToggles {
        final Map<String, Set<String>> subjects;
        final List<FeatureContext> seen = new ArrayList<>();
        final AtomicInteger lookups = new AtomicInteger();
        final AtomicBoolean failing = new AtomicBoolean();

        TargetedToggles(Map<String, Set<String>> subjects) {
            this.subjects = subjects;
        }

        @Override
        public boolean isEnabled(String feature) {
            return isEnabled(feature, FeatureContext.EMPTY);
        }

        @Override
        public boolean isEnabled(String feature, FeatureContext context) {
            lookups.incrementAndGet();
            seen.add(context);
            if (failing.get()) {
                throw new IllegalStateException("down");
            }
            return context.subject().map(subjects.getOrDefault(feature, Set.of())::contains).orElse(false);
        }
    }

    private static final FeatureContext ALICE = FeatureContext.of("alice");
    private static final FeatureContext BOB = FeatureContext.of("bob");

    // ---- FeatureContext --------------------------------------------------------

    @Test
    void contextIsAnImmutableValue() {
        FeatureContext base = FeatureContext.of("alice");
        FeatureContext withTenant = base.with("tenant", "acme");

        assertEquals(Optional.of("alice"), withTenant.subject());
        assertEquals(Optional.of("acme"), withTenant.attribute("tenant"));
        assertEquals(Map.of(), base.attributes());
        assertEquals(FeatureContext.of("alice").with("tenant", "acme"), withTenant);
        assertTrue(FeatureContext.EMPTY.isEmpty());
        assertFalse(base.isEmpty());
        assertFalse(FeatureContext.EMPTY.with("region", "eu").isEmpty());
    }

    @Test
    void sourcesWithoutTargetingIgnoreTheContext() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles().enable("x");
        assertTrue(toggles.isEnabled("x", ALICE));
        assertFalse(toggles.isEnabled("y", ALICE));
    }

    // ---- decorators ------------------------------------------------------------

    @Test
    void everyDecoratorPassesTheContextThrough() {
        TargetedToggles source = new TargetedToggles(Map.of("beta", Set.of("alice")));
        List<FeatureContext> observed = new ArrayList<>();
        FeatureToggles chain = source
                .cached(Duration.ofMinutes(1))
                .failSafe(false)
                .withOverrides()
                .observed(new FeatureToggleListener() {
                    @Override
                    public void onEvaluated(String feature, boolean enabled) {
                    }

                    @Override
                    public void onEvaluated(String feature, FeatureContext context, boolean enabled) {
                        observed.add(context);
                    }
                });

        assertTrue(chain.isEnabled("beta", ALICE));
        assertFalse(chain.isEnabled("beta", BOB));
        assertEquals(List.of(ALICE, BOB), source.seen);
        assertEquals(List.of(ALICE, BOB), observed);
    }

    @Test
    void cacheNeverSharesAnswersAcrossContexts() {
        TargetedToggles source = new TargetedToggles(Map.of("beta", Set.of("alice")));
        CachingFeatureToggles cached = source.cached(Duration.ofMinutes(1));

        assertTrue(cached.isEnabled("beta", ALICE));
        assertFalse(cached.isEnabled("beta", BOB));
        assertTrue(cached.isEnabled("beta", ALICE));
        assertEquals(3, source.lookups.get()); // contextual lookups always reach the source

        cached.isEnabled("beta");
        cached.isEnabled("beta", FeatureContext.EMPTY);
        assertEquals(4, source.lookups.get()); // the empty context shares the plain cache entry
    }

    @Test
    void failSafeUsesTheDefaultForContextualFailures() {
        TargetedToggles source = new TargetedToggles(Map.of("beta", Set.of("alice")));
        List<String> errors = new ArrayList<>();
        FailSafeFeatureToggles safe = new FailSafeFeatureToggles(source, false, (feature, e) -> errors.add(feature));

        assertTrue(safe.isEnabled("beta", ALICE));
        source.failing.set(true);
        assertFalse(safe.isEnabled("beta", BOB)); // not alice's last answer
        assertFalse(safe.isEnabled("beta", ALICE)); // contextual answers aren't remembered
        assertEquals(List.of("beta", "beta"), errors);
    }

    @Test
    void overridesWinForEveryContext() {
        TargetedToggles source = new TargetedToggles(Map.of("beta", Set.of("alice")));
        OverridableFeatureToggles toggles = source.withOverrides();

        toggles.override("beta", false);
        assertFalse(toggles.isEnabled("beta", ALICE));
        toggles.clearOverride("beta");
        assertTrue(toggles.isEnabled("beta", ALICE));
        assertFalse(toggles.isEnabled("beta", BOB));
    }

    @Test
    void contextFreeListenersStillHearEveryEvaluation() {
        List<String> heard = new ArrayList<>();
        FeatureToggles observed = new InMemoryFeatureToggles().enable("x")
                .observed((feature, enabled) -> heard.add(feature + "=" + enabled));

        observed.isEnabled("x");
        observed.isEnabled("x", ALICE);
        assertEquals(List.of("x=true", "x=true"), heard);
    }

    // ---- proxy -----------------------------------------------------------------

    @Test
    void proxyEvaluatesEveryCheckForTheCurrentContext() {
        TargetedToggles source = new TargetedToggles(Map.of(
                "checkout-v2", Set.of("alice", "bob"),
                "express-shipping", Set.of("alice")));
        AtomicReference<FeatureContext> current = new AtomicReference<>();
        AtomicInteger contextReads = new AtomicInteger();
        ToggledFeatureTest.Checkout checkout = ToggledFeature.builder(ToggledFeatureTest.Checkout.class)
                .enabled(new ToggledFeatureTest.CheckoutV2())
                .fallback(new ToggledFeatureTest.CheckoutV1())
                .toggles(source)
                .context(() -> {
                    contextReads.incrementAndGet();
                    return current.get();
                })
                .build();

        current.set(ALICE);
        assertEquals("v2 express", checkout.ship());
        assertEquals(1, contextReads.get()); // read once for both checks
        assertEquals(List.of(ALICE, ALICE), source.seen);

        current.set(BOB);
        assertEquals("v2 standard", checkout.ship());

        current.set(FeatureContext.of("carol"));
        assertEquals("v1 ship", checkout.ship());

        current.set(null); // treated as empty
        assertEquals("v1 pay", checkout.pay());
        assertEquals(FeatureContext.EMPTY, source.seen.get(source.seen.size() - 1));
    }

    @Test
    void proxyWithoutAContextSupplierUsesTheEmptyContext() {
        TargetedToggles source = new TargetedToggles(Map.of());
        ToggledFeatureTest.Checkout checkout = ToggledFeature.of(ToggledFeatureTest.Checkout.class,
                new ToggledFeatureTest.CheckoutV2(), new ToggledFeatureTest.CheckoutV1(), source);

        assertEquals("v1 pay", checkout.pay());
        assertEquals(List.of(FeatureContext.EMPTY), source.seen);
    }
}
