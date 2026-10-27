```java
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
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Provides a read-only {@code /actuator/flipjack} endpoint.
 *
 * <p>The endpoint exposes every feature that is configured or checked by a
 * toggled bean, including class-level and method-level toggles and toggled
 * steps held in bean fields, together with its effective state.</p>
 */
@Endpoint(id = "flipjack")
public class FlipjackEndpoint {

    private final FlipjackProperties properties;
    private final FeatureToggles toggles;
    private final ObjectProvider<Toggled> toggledBeans;
    private final BeanFactory beanFactory;

    /**
     * Creates a Flipjack actuator endpoint without a bean factory.
     *
     * @param properties Flipjack configuration properties
     * @param toggles feature toggle source
     * @param toggledBeans provider for toggle-aware beans
     */
    public FlipjackEndpoint(
            FlipjackProperties properties,
            FeatureToggles toggles,
            ObjectProvider<Toggled> toggledBeans) {
        this(properties, toggles, toggledBeans, null);
    }

    /**
     * Creates a Flipjack actuator endpoint.
     *
     * <p>When a bean factory is supplied, toggled steps held in fields of
     * application beans are included in the endpoint output.</p>
     *
     * @param properties Flipjack configuration properties
     * @param toggles feature toggle source
     * @param toggledBeans provider for toggle-aware beans
     * @param beanFactory bean factory used to discover additional toggled features;
     *                    may be {@code null}
     */
    public FlipjackEndpoint(
            FlipjackProperties properties,
            FeatureToggles toggles,
            ObjectProvider<Toggled> toggledBeans,
            BeanFactory beanFactory) {
        this.properties = properties;
        this.toggles = toggles;
        this.toggledBeans = toggledBeans;
        this.beanFactory = beanFactory;
    }

    /**
     * Returns the effective enabled state of all known features.
     *
     * <p>The returned map contains configured features as well as features
     * discovered from toggle-aware beans.</p>
     *
     * @return a map containing feature names and their effective enabled state
     */
    @ReadOperation
    public Map<String, Boolean> features() {
        TreeSet<String> names = new TreeSet<>(properties.getToggles().keySet());
        names.addAll(ApplicationFeatures.used(beanFactory, toggledBeans));

        Map<String, Boolean> result = new TreeMap<>();
        names.forEach(name -> result.put(name, toggles.isEnabled(name)));

        return result;
    }

    /**
     * Returns the effective enabled state of a specific feature.
     *
     * @param name feature name to inspect
     * @return a map containing the feature name and its effective enabled state
     */
    @ReadOperation
    public Map<String, Object> feature(@Selector String name) {
        return Map.of(
                "feature",
                name,
                "enabled",
                toggles.isEnabled(name)
        );
    }
}
```
