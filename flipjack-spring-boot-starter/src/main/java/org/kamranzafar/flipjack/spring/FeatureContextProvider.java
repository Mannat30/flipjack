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

/**
 * Supplies the {@link FeatureContext} that toggleable beans evaluate their features for.
 * Define one bean of this type and every {@code @Toggleable} proxy reads it once per call,
 * typically from the current request or security context:
 * <pre>{@code
 * @Bean
 * FeatureContextProvider featureContext() {
 *     return () -> {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         return auth == null ? FeatureContext.EMPTY : FeatureContext.of(auth.getName());
 *     };
 * }
 * }</pre>
 * It is called on the caller's thread, also outside requests (scheduled jobs, startup), so
 * return {@link FeatureContext#EMPTY} rather than throwing when there is no context.
 * Without a provider bean, every call is evaluated with the empty context.
 */
@FunctionalInterface
public interface FeatureContextProvider {

    FeatureContext currentContext();
}
