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
import org.kamranzafar.flipjack.Toggleable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureConsistencyCheckerTest {

    public interface Pricing {
        String quote();
    }

    public static class LegacyPricing implements Pricing {
        @Override
        public String quote() {
            return "legacy";
        }
    }

    @Toggleable(value = "new-pricng", fallback = LegacyPricing.class) // typo
    public static class NewPricing implements Pricing {
        @Override
        public String quote() {
            return "new";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({LegacyPricing.class, NewPricing.class})
    static class Components {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlipjackAutoConfiguration.class))
            .withUserConfiguration(Components.class)
            .withPropertyValues("flipjack.toggles.new-pricing=true");

    @Test
    void warnModeStartsDespiteMismatch() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failModeRejectsAMisspeltFeature() {
        runner.withPropertyValues("flipjack.consistency-check=fail")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
                            .hasMessageContaining("new-pricng");
                });
    }

    @Test
    void failModePassesWhenNamesMatch() {
        runner.withPropertyValues("flipjack.consistency-check=fail", "flipjack.toggles.new-pricng=true",
                        "flipjack.inline-features=new-pricing")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
