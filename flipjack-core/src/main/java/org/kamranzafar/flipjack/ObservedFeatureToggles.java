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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decorator that reports every evaluation to a {@link FeatureToggleListener}. Put it
 * outermost to observe the final decision. Listener failures are logged, never thrown.
 */
public final class ObservedFeatureToggles implements FeatureToggles {

    private static final System.Logger LOG = System.getLogger(ObservedFeatureToggles.class.getName());

    private final FeatureToggles delegate;
    private final FeatureToggleListener listener;

    public ObservedFeatureToggles(FeatureToggles delegate, FeatureToggleListener listener) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public boolean isEnabled(String feature) {
        boolean enabled = delegate.isEnabled(feature);
        notify(feature, FeatureContext.EMPTY, enabled);
        return enabled;
    }

    @Override
    public boolean isEnabled(String feature, FeatureContext context) {
        boolean enabled = delegate.isEnabled(feature, context);
        notify(feature, context, enabled);
        return enabled;
    }

    private void notify(String feature, FeatureContext context, boolean enabled) {
        try {
            listener.onEvaluated(feature, context, enabled);
        } catch (RuntimeException error) {
            LOG.log(System.Logger.Level.WARNING, "Feature toggle listener failed for '" + feature + "'", error);
        }
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        return delegate.knownFeatures();
    }

    /**
     * Closes the wrapped toggles.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
