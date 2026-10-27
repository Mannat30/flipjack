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

import org.kamranzafar.flipjack.ToggledFeature;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Marks bean definitions with class- or method-level {@code @Toggleable} as primary, so
 * injecting the service interface gets the toggle-routing proxy rather than a fallback.
 * Uses the core library's detection, so Spring and plain Java agree on what is toggleable.
 */
public class ToggleablePrimaryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            String className = definition.getBeanClassName();
            if (className == null
                    || definition.getFactoryMethodName() != null
                    || definition.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE
                    || className.startsWith("org.springframework.")) {
                continue;
            }
            try {
                if (ToggledFeature.isToggleable(ClassUtils.forName(className, classLoader))) {
                    definition.setPrimary(true);
                }
            } catch (Exception | LinkageError e) {
                // class not inspectable here (optional dependency missing); it can't be toggleable
            }
        }
    }
}
