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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toggling one step inside a component with {@link ToggledFeature#either}, and discovering it.
 */
class StepToggleTest {

    public interface TaxRule {
        long tax(long amount);
    }

    /**
     * A plain component: not toggled itself, one toggled step held in a field.
     */
    static class CheckoutService {
        private final TaxRule tax;

        CheckoutService(FeatureToggles toggles) {
            this.tax = ToggledFeature.either(TaxRule.class, "regional-tax",
                    this::regionalTax, this::flatTax, toggles);
        }

        long total(long amount) {
            return amount + tax.tax(amount);
        }

        private long regionalTax(long amount) {
            return amount / 5;
        }

        private long flatTax(long amount) {
            return amount / 10;
        }
    }

    public interface Checkout {
        long total(long amount);
    }

    /**
     * A toggled component that also holds a toggled step.
     */
    @Toggleable("checkout-v2")
    static class CheckoutV2 implements Checkout {
        private final TaxRule tax;

        CheckoutV2(FeatureToggles toggles) {
            this.tax = ToggledFeature.either(TaxRule.class, "regional-tax", a -> a / 5, a -> a / 10, toggles);
        }

        @Override
        public long total(long amount) {
            return amount + tax.tax(amount);
        }
    }

    @Test
    void routesTheStepWhileTheCallerStaysUnconditional() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        CheckoutService checkout = new CheckoutService(toggles);

        assertEquals(110, checkout.total(100));
        toggles.enable("regional-tax");
        assertEquals(120, checkout.total(100));
    }

    @Test
    void findsStepsHeldInFieldsOfPlainComponents() {
        CheckoutService checkout = new CheckoutService(new InMemoryFeatureToggles());
        assertEquals(Set.of("regional-tax"), ToggledFeature.featuresUsedBy(checkout));
    }

    @Test
    void findsStepsInsideToggledComponents() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        Checkout checkout = ToggledFeature.builder(Checkout.class)
                .enabled(new CheckoutV2(toggles)).whenDisabled(WhenDisabled.NO_OP).toggles(toggles).build();

        assertEquals(Set.of("checkout-v2", "regional-tax"), ToggledFeature.featuresUsedBy(checkout));
    }

    @Test
    void ignoresComponentsWithoutToggles() {
        assertTrue(ToggledFeature.featuresUsedBy(new Object()).isEmpty());
        assertTrue(ToggledFeature.featuresUsedBy(List.of("x")).isEmpty());
    }

    @Test
    void consistencyCheckSeesStepsWithoutDeclaringThemInline() {
        InMemoryFeatureToggles store = new InMemoryFeatureToggles().enable("regional-tax");
        CheckoutService checkout = new CheckoutService(store);

        FeatureConsistencyReport report = FeatureConsistency.check(store, List.of(checkout)).orElseThrow();
        assertTrue(report.isConsistent(), report.toString());
    }
}
