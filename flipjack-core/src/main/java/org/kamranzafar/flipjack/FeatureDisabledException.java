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

import java.lang.reflect.Method;

/**
 * Thrown when a disabled feature with no fallback is called.
 */
public class FeatureDisabledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String feature;

    public FeatureDisabledException(String feature, Method method) {
        super("Feature '" + feature + "' is disabled; cannot call "
                + method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()");
        this.feature = feature;
    }

    public String getFeature() {
        return feature;
    }
}
