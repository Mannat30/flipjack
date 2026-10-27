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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kamranzafar.flipjack.FeatureConsistency;
import org.kamranzafar.flipjack.FeatureConsistencyReport;
import org.kamranzafar.flipjack.FeatureToggles;
import org.kamranzafar.flipjack.Toggled;
import org.kamranzafar.flipjack.spring.FlipjackProperties.ConsistencyCheck;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runs {@link FeatureConsistency} once all singletons exist.
 *
 * <p>This ensures that every toggled bean has been created and that a polling
 * source has had an opportunity to perform its first load. The checker then
 * warns or fails startup according to
 * {@code flipjack.consistency-check}.</p>
 */
public class FeatureConsistencyChecker implements SmartInitializingSingleton {

    private static final Log LOG = LogFactory.getLog(FeatureConsistencyChecker.class);

    private final FlipjackProperties properties;
    private final ObjectProvider<FeatureToggles> toggles;
    private final ObjectProvider<Toggled> toggledBeans;
    private final BeanFactory beanFactory;

    /**
     * Creates a consistency checker without a bean factory.
     *
     * <p>Only explicitly configured inline features and directly discoverable
     * toggled beans are checked.</p>
     *
     * @param properties Flipjack configuration properties
     * @param toggles provider for the application's feature toggle source
     * @param toggledBeans provider for toggle-aware beans
     */
    public FeatureConsistencyChecker(
            FlipjackProperties properties,
            ObjectProvider<FeatureToggles> toggles,
            ObjectProvider<Toggled> toggledBeans) {
        this(properties, toggles, toggledBeans, null);
    }

    /**
     * Creates a consistency checker with an optional bean factory.
     *
     * <p>When a bean factory is supplied, toggled steps held in fields of
     * application beans can also be checked.</p>
     *
     * @param properties Flipjack configuration properties
     * @param toggles provider for the application's feature toggle source
     * @param toggledBeans provider for toggle-aware beans
     * @param beanFactory bean factory used to discover additional toggled features;
     *                    may be {@code null}
     */
    public FeatureConsistencyChecker(
            FlipjackProperties properties,
            ObjectProvider<FeatureToggles> toggles,
            ObjectProvider<Toggled> toggledBeans,
            BeanFactory beanFactory) {
        this.properties = properties;
        this.toggles = toggles;
        this.toggledBeans = toggledBeans;
        this.beanFactory = beanFactory;
    }

    /**
     * Performs the feature consistency check after all singleton beans
     * have been instantiated.
     *
     * <p>If consistency checking is disabled, this method returns immediately.
     * When the toggle source cannot list its features, the check is skipped.
     * Missing features either produce a warning or fail application startup,
     * depending on the configured {@link ConsistencyCheck} mode.</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        ConsistencyCheck mode = properties.getConsistencyCheck();

        if (mode == ConsistencyCheck.OFF) {
            return;
        }

        Set<String> used = new TreeSet<>(properties.getInlineFeatures());
        used.addAll(ApplicationFeatures.used(beanFactory, toggledBeans));

        Optional<FeatureConsistencyReport> result =
                FeatureConsistency.check(toggles.getObject(), List.of(), used);

        if (result.isEmpty()) {
            LOG.debug(
                    "Flipjack consistency check skipped: the toggle source can't list its features "
                            + "(or hasn't loaded yet)"
            );
            return;
        }

        FeatureConsistencyReport report = result.get();

        if (!report.unusedInStore().isEmpty()) {
            LOG.info(report.describeUnused());
        }

        if (!report.missingFromStore().isEmpty()) {
            if (mode == ConsistencyCheck.FAIL) {
                throw new IllegalStateException(report.describeMissing());
            }

            LOG.warn(report.describeMissing());
        }
    }
}
```
