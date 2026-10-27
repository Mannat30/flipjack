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
 * Wraps toggleable Spring beans with the core library's {@link ToggledFeature}.
 *
 * <p>The core library handles annotation reading, validation and feature routing.
 * This post-processor supplies the Spring-specific components: the bean, its
 * target class, the feature toggle source, the bean factory for fallback
 * resolution and the optional {@link FeatureContextProvider}.</p>
 *
 * <p>The processor runs last so that it wraps any other proxies, such as
 * transactions or caching proxies, that have already been applied.</p>
 */
public class ToggleableBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware,
        BeanClassLoaderAware, SmartInitializingSingleton, Ordered {

    private static final FeatureContextProvider NO_CONTEXT = () -> FeatureContext.EMPTY;

    private final Supplier<FeatureToggles> toggles;
    private final Supplier<FeatureContextProvider> contextProvider;
    private BeanFactory beanFactory;
    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    /**
     * Creates a post-processor using the available feature toggle source
     * and a default context provider that returns an empty context.
     *
     * @param toggles provider for the application's {@link FeatureToggles} instance
     */
    public ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles) {
        this(toggles, () -> NO_CONTEXT);
    }

    /**
     * Creates a post-processor using the supplied feature toggle source
     * and context provider.
     *
     * @param toggles provider for the application's {@link FeatureToggles} instance
     * @param contextProviders provider for the application's
     *                         {@link FeatureContextProvider}
     */
    public ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles,
                                       ObjectProvider<FeatureContextProvider> contextProviders) {
        this(toggles, () -> contextProviders.getIfAvailable(() -> NO_CONTEXT));
    }

    private ToggleableBeanPostProcessor(ObjectProvider<FeatureToggles> toggles,
                                        Supplier<FeatureContextProvider> contextProvider) {
        // Resolved lazily so this post-processor does not force early bean creation.
        this.toggles = SingletonSupplier.of(toggles::getObject);
        this.contextProvider = SingletonSupplier.of(contextProvider);
    }

    /**
     * Resolves the context provider before application traffic begins.
     *
     * <p>This ensures that multiple candidate context providers fail during
     * application startup rather than during a request.</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        contextProvider.get();
    }

    /**
     * Supplies the Spring {@link BeanFactory} used to resolve fallback beans.
     *
     * @param beanFactory the owning bean factory
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Supplies the class loader used when creating feature-toggle proxies.
     *
     * @param classLoader the class loader to use
     */
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    /**
     * Returns the order in which this post-processor is applied.
     *
     * <p>The lowest precedence is used so that this processor wraps other
     * Spring-generated proxies after they have been created.</p>
     *
     * @return {@link Ordered#LOWEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Wraps an initialized toggleable bean with the core feature-toggle proxy.
     *
     * <p>Beans that are not annotated as toggleable are returned unchanged.
     * Toggleable beans must implement at least one non-infrastructure interface,
     * which is used as the primary proxy interface.</p>
     *
     * @param bean initialized Spring bean
     * @param beanName name of the Spring bean
     * @return the original bean when it is not toggleable, otherwise a
     *         feature-toggle proxy around the bean
     * @throws BeansException if the bean cannot be wrapped
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        if (!ToggledFeature.isToggleable(targetClass)) {
            return bean;
        }

        Class<?>[] interfaces = serviceInterfaces(targetClass);

        if (interfaces.length == 0) {
            throw new BeanCreationException(
                    beanName,
                    "@Toggleable bean " + targetClass.getName()
                            + " must implement at least one interface"
            );
        }

        // fallbackMethods must run on the real object, not on an AOP proxy around it.
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
     * Returns the service interfaces implemented by the supplied class,
     * excluding Spring and {@code java.io} infrastructure interfaces.
     *
     * @param type class whose interfaces should be inspected
     * @return service interfaces implemented by the class
     */
    private static Class<?>[] serviceInterfaces(Class<?> type) {
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(type))
                .filter(i -> !i.getName().startsWith("org.springframework.")
                        && !i.getName().startsWith("java.io."))
                .toArray(Class<?>[]::new);
    }
}
```
