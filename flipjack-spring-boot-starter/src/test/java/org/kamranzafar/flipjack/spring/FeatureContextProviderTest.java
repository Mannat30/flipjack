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
import org.kamranzafar.flipjack.FeatureContext;
import org.kamranzafar.flipjack.FeatureToggles;
import org.kamranzafar.flipjack.Toggleable;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureContextProviderTest {

    public interface Greeting {
        String greet();
    }

    public static class BasicGreeting implements Greeting {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Toggleable(value = "beta", fallback = BasicGreeting.class)
    public static class BetaGreeting implements Greeting {
        @Override
        public String greet() {
            return "hello, beta tester";
        }
    }

    /**
     * "beta" is on for alice only; without a subject it is off.
     */
    static final class BetaForAlice implements FeatureToggles {
        @Override
        public boolean isEnabled(String feature) {
            return false;
        }

        @Override
        public boolean isEnabled(String feature, FeatureContext context) {
            return context.subject().filter(Set.of("alice")::contains).isPresent();
        }
    }

    /**
     * Stands in for a request or security context.
     */
    static final ThreadLocal<FeatureContext> CURRENT = ThreadLocal.withInitial(() -> FeatureContext.EMPTY);

    @Configuration(proxyBeanMethods = false)
    @Import({BasicGreeting.class, BetaGreeting.class})
    static class Components {
        @Bean
        FeatureToggles featureToggles() {
            return new BetaForAlice();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Provider {
        @Bean
        FeatureContextProvider featureContext() {
            return CURRENT::get;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SecondProvider {
        @Bean
        FeatureContextProvider anotherFeatureContext() {
            return () -> FeatureContext.EMPTY;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlipjackAutoConfiguration.class))
            .withUserConfiguration(Components.class);

    @Test
    void routesEachCallForTheProvidedContext() {
        runner.withUserConfiguration(Provider.class).run(context -> {
            Greeting greeting = context.getBean(Greeting.class);
            try {
                CURRENT.set(FeatureContext.of("alice"));
                assertThat(greeting.greet()).isEqualTo("hello, beta tester");

                CURRENT.set(FeatureContext.of("bob"));
                assertThat(greeting.greet()).isEqualTo("hello");
            } finally {
                CURRENT.remove();
            }
        });
    }

    @Test
    void withoutAProviderEveryCallUsesTheEmptyContext() {
        runner.run(context -> {
            CURRENT.set(FeatureContext.of("alice")); // ignored: no provider bean
            try {
                assertThat(context.getBean(Greeting.class).greet()).isEqualTo("hello");
            } finally {
                CURRENT.remove();
            }
        });
    }

    @Test
    void twoProvidersFailStartup() {
        runner.withUserConfiguration(Provider.class, SecondProvider.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
                    .isInstanceOf(NoUniqueBeanDefinitionException.class);
        });
    }
}
