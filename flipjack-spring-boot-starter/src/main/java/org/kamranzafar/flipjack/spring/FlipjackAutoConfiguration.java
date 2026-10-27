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

import org.kamranzafar.flipjack.FeatureToggles;
import org.kamranzafar.flipjack.Toggled;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@EnableConfigurationProperties(FlipjackProperties.class)
public class FlipjackAutoConfiguration {

    /**
     * Replace by defining your own FeatureToggles bean (any core implementation works).
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureToggles featureToggles(FlipjackProperties properties) {
        return new ConfigurationPropertiesFeatureToggles(properties);
    }

    @Bean
    public static ToggleablePrimaryPostProcessor toggleablePrimaryPostProcessor() {
        return new ToggleablePrimaryPostProcessor();
    }

    @Bean
    public static ToggleableBeanPostProcessor toggleableBeanPostProcessor(
            ObjectProvider<FeatureToggles> toggles, ObjectProvider<FeatureContextProvider> contextProvider) {
        return new ToggleableBeanPostProcessor(toggles, contextProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureConsistencyChecker flipjackConsistencyChecker(FlipjackProperties properties,
                                                                ObjectProvider<FeatureToggles> toggles,
                                                                ObjectProvider<Toggled> toggledBeans,
                                                                BeanFactory beanFactory) {
        return new FeatureConsistencyChecker(properties, toggles, toggledBeans, beanFactory);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    static class EndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint
        FlipjackEndpoint flipjackEndpoint(FlipjackProperties properties,
                                          FeatureToggles toggles,
                                          ObjectProvider<Toggled> toggledBeans,
                                          BeanFactory beanFactory) {
            return new FlipjackEndpoint(properties, toggles, toggledBeans, beanFactory);
        }
    }
}
