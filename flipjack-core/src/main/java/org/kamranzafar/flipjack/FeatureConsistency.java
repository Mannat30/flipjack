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

import java.util.*;

/**
 * Compares the features your code uses with the features the toggle store holds, to catch
 * misspelt names (which would otherwise silently run with the default state) and stale
 * toggles. Run it once at startup, after the first load:
 * <pre>{@code
 * FeatureConsistency.check(toggles, List.of(pricing, orders), Set.of("inline-only-feature"))
 *         .ifPresent(FeatureConsistencyReport::logIfInconsistent);
 * }</pre>
 * Features used by toggled components, and by toggled proxies held in a component's fields
 * (such as {@link ToggledFeature#either}), are discovered automatically.
 * Features checked inline with {@code toggles.isEnabled(...)} can't be discovered, so
 * declare them in {@code inlineFeatures} or they'll be reported as unused.
 */
public final class FeatureConsistency {

    private FeatureConsistency() {
    }

    public static Optional<FeatureConsistencyReport> check(FeatureToggles toggles, Iterable<?> components) {
        return check(toggles, components, List.of());
    }

    /**
     * @param components objects to inspect; see {@link ToggledFeature#featuresUsedBy(Object)}
     * @return empty if the source can't list its features (see {@link FeatureToggles#knownFeatures()})
     */
    public static Optional<FeatureConsistencyReport> check(FeatureToggles toggles, Iterable<?> components,
                                                           Collection<String> inlineFeatures) {
        Optional<Set<String>> known = toggles.knownFeatures();
        if (known.isEmpty()) {
            return Optional.empty();
        }
        Set<String> used = new TreeSet<>(inlineFeatures);
        for (Object component : components) {
            used.addAll(ToggledFeature.featuresUsedBy(component));
        }
        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(known.get());
        Set<String> unused = new TreeSet<>(known.get());
        unused.removeAll(used);
        return Optional.of(new FeatureConsistencyReport(missing, unused));
    }
}
