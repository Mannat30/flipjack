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

import java.util.HashMap;
import java.util.Map;

/**
 * Strict conversion of loaded values into toggle state.
 */
final class FeatureValues {

    private FeatureValues() {
    }

    static Map<String, Boolean> parse(Map<String, ?> raw) {
        Map<String, Boolean> result = new HashMap<>();
        raw.forEach((feature, value) -> {
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("Loaded a blank or null feature name");
            }
            result.put(feature, parseBoolean(feature, value));
        });
        return result;
    }

    private static boolean parseBoolean(String feature, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        throw new IllegalArgumentException(
                "Feature '" + feature + "' has invalid value '" + value + "'; expected true or false");
    }
}
