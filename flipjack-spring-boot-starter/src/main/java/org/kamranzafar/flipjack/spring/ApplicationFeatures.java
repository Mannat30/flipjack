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

import org.kamranzafar.flipjack.Toggled;
import org.kamranzafar.flipjack.ToggledFeature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Set;
import java.util.TreeSet;

/**
 * Collects every feature the application's beans check: toggled beans, plus toggled proxies
 * held in any bean's fields (such as {@link ToggledFeature#either} steps). Only singletons
 * that already exist are inspected, so nothing is created early.
 */
final class ApplicationFeatures {

    private ApplicationFeatures() {
    }

    static Set<String> used(BeanFactory beanFactory, ObjectProvider<Toggled> toggledBeans) {
        Set<String> features = new TreeSet<>();
        if (!(beanFactory instanceof ConfigurableListableBeanFactory factory)) {
            toggledBeans.stream().forEach(bean -> features.addAll(bean.toggledFeatures()));
            return features;
        }
        for (String name : factory.getSingletonNames()) {
            Object bean = factory.getSingleton(name);
            if (bean == null || (!(bean instanceof Toggled) && isInfrastructure(bean.getClass()))) {
                continue;
            }
            features.addAll(ToggledFeature.featuresUsedBy(bean));
            // a Spring AOP proxy doesn't hold the target's fields; inspect the target too
            Object target = AopProxyUtils.getSingletonTarget(bean);
            if (target != null) {
                features.addAll(ToggledFeature.featuresUsedBy(target));
            }
        }
        return features;
    }

    private static boolean isInfrastructure(Class<?> type) {
        String name = type.getName();
        return name.startsWith("org.springframework.") || name.startsWith("java.")
                || name.startsWith("javax.") || name.startsWith("jakarta.") || name.startsWith("jdk.");
    }
}
