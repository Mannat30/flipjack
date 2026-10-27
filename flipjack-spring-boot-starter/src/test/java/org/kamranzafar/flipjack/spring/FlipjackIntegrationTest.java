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
package org.kamranzafar.flipjack.spring;

import org.junit.jupiter.api.Test;
import org.kamranzafar.flipjack.FeatureDisabledException;
import org.kamranzafar.flipjack.Toggleable;
import org.kamranzafar.flipjack.Toggled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = FlipjackIntegrationTest.TestApp.class)
class FlipjackIntegrationTest {

    // class level: decorator with fallback bean
    public interface Feature {
        String execute();
    }

    public static class BasicFeature implements Feature {
        @Override
        public String execute() {
            return "Basic Feature";
        }
    }

    @Toggleable(value = "premium", fallback = BasicFeature.class)
    public static class PremiumFeatureDecorator implements Feature {
        private final Feature delegate;

        public PremiumFeatureDecorator(BasicFeature delegate) {
            this.delegate = delegate;
        }

        @Override
        public String execute() {
            return delegate.execute() + " + Premium Feature";
        }
    }

    // class level: no fallback
    public interface Exporter {
        String export();
    }

    @Toggleable("beta-export")
    public static class BetaExporter implements Exporter {
        @Override
        public String export() {
            return "exported";
        }
    }

    // method level: fallbackMethod and fallback bean
    public interface Pricing {
        String quote();

        String discount();
    }

    public static class LegacyPricing implements Pricing {
        @Override
        public String quote() {
            return "legacy quote";
        }

        @Override
        public String discount() {
            return "legacy discount";
        }
    }

    public static class NewPricing implements Pricing {
        @Override
        @Toggleable(value = "new-quote", fallback = LegacyPricing.class)
        public String quote() {
            return "new quote";
        }

        @Override
        @Toggleable(value = "dynamic-discount", fallbackMethod = "flatDiscount")
        public String discount() {
            return "dynamic discount";
        }

        String flatDiscount() {
            return "flat discount";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({BasicFeature.class, PremiumFeatureDecorator.class, BetaExporter.class,
            LegacyPricing.class, NewPricing.class})
    static class TestApp {
    }

    @Autowired
    Feature feature;
    @Autowired
    Exporter exporter;
    @Autowired
    Pricing pricing;
    @Autowired
    FlipjackProperties properties; // mutated to simulate a config refresh

    @Test
    void classLevelRoutesWithoutRestart() {
        properties.getToggles().put("premium", false);
        assertThat(feature.execute()).isEqualTo("Basic Feature");

        properties.getToggles().put("premium", true);
        assertThat(feature.execute()).isEqualTo("Basic Feature + Premium Feature");
    }

    @Test
    void classLevelThrowsWithNoFallback() {
        properties.getToggles().put("beta-export", false);
        assertThatThrownBy(exporter::export).isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    void methodLevelTogglesAreIndependent() {
        properties.getToggles().put("new-quote", false);
        properties.getToggles().put("dynamic-discount", true);
        assertThat(pricing.quote()).isEqualTo("legacy quote");
        assertThat(pricing.discount()).isEqualTo("dynamic discount");

        properties.getToggles().put("new-quote", true);
        properties.getToggles().put("dynamic-discount", false);
        assertThat(pricing.quote()).isEqualTo("new quote");
        assertThat(pricing.discount()).isEqualTo("flat discount");

        assertThat(((Toggled) pricing).toggledFeatures())
                .containsExactlyInAnyOrder("new-quote", "dynamic-discount");
    }
}
