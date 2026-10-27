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

import org.kamranzafar.flipjack.FeatureContext;
import org.kamranzafar.flipjack.FeatureToggles;
import org.kamranzafar.flipjack.ToggledFeature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.SingletonSupplier;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Wraps toggleable beans with the core library's {@link ToggledFeature}. The core does all
 * annotation reading, validation and routing; this class only supplies Spring's pieces:
 * the bean, its real class, the toggle source, the bean factory for fallbacks and the
 * optional {@link FeatureContextProvider}.
 * Runs last so it wraps any other proxies (transactions, caching) already applied.
 */
public class ToggleableBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware,
        BeanClassLoaderAware, SmartInitializingSingleton, Ordered {

    private static final FeatureContextProvider NO_CONTEXT = () -> FeatureContext.EMPTY;

    private final Supplier<FeatureToggles> toggles;
    private final Supplier<FeatureContextProvider> contextProvider;
    private BeanFactory beanFactory;
    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    public ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles) {
        this(toggles, () -> NO_CONTEXT);
    }

    public ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles,
                                       ObjectProvider<FeatureContextProvider> contextProviders) {
        this(toggles, () -> contextProviders.getIfAvailable(() -> NO_CONTEXT));
    }

    private ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles,
                                        Supplier<FeatureContextProvider> contextProvider) {
        // resolved lazily so this post-processor doesn't force early bean creation
        this.toggles = SingletonSupplier.of(toggles::getObject);
        this.contextProvider = SingletonSupplier.of(contextProvider);
    }

    /**
     * Resolves the context provider before traffic, so two candidate beans fail startup, not a request.
     */
    @Override
    public void afterSingletonsInstantiated() {
        contextProvider.get();
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (!ToggledFeature.isToggleable(targetClass)) {
            return bean;
        }

        Class<?>[] interfaces = serviceInterfaces(targetClass);
        if (interfaces.length == 0) {
            throw new BeanCreationException(beanName,
                    "@Toggleable bean " + targetClass.getName() + " must implement at least one interface");
        }

        // fallbackMethods must run on the real object, not on an AOP proxy around it
        Object rawTarget = AopProxyUtils.getSingletonTarget(bean);

        try {
            return ToggledFeature.builder((Class) interfaces[0])
                    .alsoImplements(Arrays.copyOfRange(interfaces, 1, interfaces.length))
                    .enabled(bean)
                    .annotatedType(targetClass)
                    .fallbackMethodTarget(rawTarget != null ? rawTarget : bean)
                    .lazyToggles(toggles)
                    .context(() -> contextProvider.get().currentContext())
                    .lazyFallbackResolver(type -> beanFactory.getBean(type))
                    .classLoader(beanClassLoader)
                    .build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BeanCreationException(beanName, e.getMessage(), e);
        }
    }

    /**
     * The bean's own interfaces, excluding Spring and java.io infrastructure ones.
     */
    private static Class<?>[] serviceInterfaces(Class<?> type) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(type))
                .filter(i -> !i.getName().startsWith("org.springframework.")
                        && !i.getName().startsWith("java.io."))
                .toArray(Class<?>[]::new);
    }
}
