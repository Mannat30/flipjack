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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Who or what a toggle is being evaluated for: an optional subject (user, account,
 * tenant) plus free-form attributes. Sources that support targeting read it through
 * {@link FeatureToggles#isEnabled(String, FeatureContext)}; the rest ignore it.
 * <p>
 * Immutable. {@link #EMPTY} means "no context", and decorators treat it exactly like the
 * single-argument {@link FeatureToggles#isEnabled(String)}.
 */
public final class FeatureContext {

    public static final FeatureContext EMPTY = new FeatureContext(null, Map.of());

    private final String subject;
    private final Map<String, String> attributes;

    private FeatureContext(String subject, Map<String, String> attributes) {
        this.subject = subject;
        this.attributes = attributes;
    }

    /**
     * A context for {@code subject}, such as a user or tenant id.
     */
    public static FeatureContext of(String subject) {
        return new FeatureContext(Objects.requireNonNull(subject, "subject"), Map.of());
    }

    /**
     * A copy with {@code key} set to {@code value}.
     */
    public FeatureContext with(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(attributes);
        copy.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return new FeatureContext(subject, Map.copyOf(copy));
    }

    public Optional<String> subject() {
        return Optional.ofNullable(subject);
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /**
     * True if there is neither a subject nor any attribute.
     */
    public boolean isEmpty() {
        return subject == null && attributes.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FeatureContext that
                && Objects.equals(subject, that.subject)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, attributes);
    }

    @Override
    public String toString() {
        return "FeatureContext{subject=" + subject + ", attributes=" + attributes + "}";
    }
}
