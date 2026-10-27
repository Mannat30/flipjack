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
package org.kamranzafar.flipjack;

import java.lang.annotation.*;

/**
 * Declares a toggleable feature on a class or on a method.
 *
 * <h2>Class level</h2>
 * Gates every interface method of the class. When the feature is off, calls go to
 * {@link #fallback()} or are handled per {@link #whenDisabled()}.
 *
 * <h2>Method level</h2>
 * Gates one method. Place it on the implementation's method or on the interface
 * method. When the feature is off, the call goes to {@link #fallbackMethod()} on
 * the same object, to the same method on {@link #fallback()}, or is handled per
 * {@link #whenDisabled()}.
 *
 * <h2>Both</h2>
 * The class toggle is checked first; if it is off, the class-level fallback handles
 * the call. If it is on, the method toggle is checked next.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Toggleable {

    /**
     * The feature name looked up in {@link FeatureToggles}.
     */
    String value();

    /**
     * Class of the object to route to when the feature is off. At class level it must
     * implement all proxied interfaces; at method level, the method's interface.
     * Instances come from {@code ToggledFeature.Builder.fallback(...)} or a
     * {@link FallbackResolver}. {@code Void.class} means none.
     */
    Class<?> fallback() default Void.class;

    /**
     * Method level only: name of a method on the same class, with the same parameter
     * types and a compatible return type, called when the feature is off. It may be
     * private. Cannot be combined with {@link #fallback()}.
     */
    String fallbackMethod() default "";

    /**
     * What to do when the feature is off and no fallback is configured. Combining
     * {@code NO_OP} with {@link #fallback()} or {@link #fallbackMethod()} is rejected when
     * the proxy is built, since the fallback would always win.
     */
    WhenDisabled whenDisabled() default WhenDisabled.THROW;
}
