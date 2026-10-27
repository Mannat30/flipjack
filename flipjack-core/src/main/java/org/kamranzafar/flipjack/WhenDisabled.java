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

/**
 * Behaviour for a disabled feature that has no fallback.
 */
public enum WhenDisabled {

    /**
     * Throw {@link FeatureDisabledException}.
     */
    THROW,

    /**
     * Do nothing: return {@code null}, {@code Optional.empty()}, or the default
     * value for primitive return types.
     */
    NO_OP
}
