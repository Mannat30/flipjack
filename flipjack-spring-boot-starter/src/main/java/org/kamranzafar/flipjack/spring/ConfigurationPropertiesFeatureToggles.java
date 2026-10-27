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

import org.kamranzafar.flipjack.FeatureToggles;

import java.util.Optional;
import java.util.Set;

/**
 * Default {@link FeatureToggles} for Spring Boot: reads {@link FlipjackProperties} on every call.
 */
public class ConfigurationPropertiesFeatureToggles implements FeatureToggles {

    private final FlipjackProperties properties;

    public ConfigurationPropertiesFeatureToggles(FlipjackProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isEnabled(String feature) {
        Boolean value = properties.getToggles().get(feature);
        return value != null ? value : properties.isDefaultEnabled();
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        return Optional.of(Set.copyOf(properties.getToggles().keySet()));
    }
}
