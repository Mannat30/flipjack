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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Toggles provided by the application through a {@link FeatureToggleLoader} and refreshed
 * on a background schedule. Flipjack doesn't know or care where they come from.
 * <pre>{@code
 * PollingFeatureToggles toggles = PollingFeatureToggles.builder(configService::loadFeatureToggles)
 *         .interval(Duration.ofSeconds(30))
 *         .build();
 * }</pre>
 * <ul>
 *   <li>The loader returns <em>all</em> toggles in one call, so the backing store sees one
 *       request per interval per instance regardless of how many features or calls there are.</li>
 *   <li>{@link #isEnabled} reads an in-memory snapshot: no I/O, and it never throws, so
 *       wrapping this source in {@link FailSafeFeatureToggles} or {@link CachingFeatureToggles}
 *       is unnecessary.</li>
 *   <li>If a load fails (store unreachable, invalid value, null map), the last good snapshot
 *       is kept and the error is reported; the next scheduled load retries.</li>
 *   <li>The first load runs synchronously in {@link Builder#build()}, so toggles are ready
 *       before the service takes traffic.</li>
 * </ul>
 * Call {@link #close()} (or close the outermost decorator) to stop polling.
 */
public final class PollingFeatureToggles implements FeatureToggles {

    /**
     * Notified for each feature whose state changed in a load; {@code null} means absent.
     */
    @FunctionalInterface
    public interface ChangeListener {
        void onChange(String feature, Boolean previous, Boolean current);
    }

    private static final System.Logger LOG = System.getLogger(PollingFeatureToggles.class.getName());

    private final FeatureToggleLoader loader;
    private final boolean defaultEnabled;
    private final Consumer<Exception> errorHandler;
    private final ChangeListener changeListener;
    private final ScheduledExecutorService executor;
    private final boolean ownsExecutor;
    private final ScheduledFuture<?> task;

    private volatile Map<String, Boolean> snapshot = Map.of();
    private volatile Instant lastLoaded;
    private volatile boolean closed;

    private PollingFeatureToggles(Builder builder) {
        this.loader = builder.loader;
        this.defaultEnabled = builder.defaultEnabled;
        this.errorHandler = builder.errorHandler;
        this.changeListener = builder.changeListener;

        Exception initialError = load();
        if (initialError != null && builder.requireInitialLoad) {
            throw new IllegalStateException("Initial feature toggle load failed", initialError);
        }

        this.ownsExecutor = builder.executor == null;
        this.executor = ownsExecutor ? Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "flipjack-poller");
            thread.setDaemon(true);
            return thread;
        }) : builder.executor;
        long intervalNanos = builder.interval.toNanos();
        this.task = executor.scheduleWithFixedDelay(this::poll, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    }

    public static Builder builder(FeatureToggleLoader loader) {
        return new Builder(loader);
    }

    @Override
    public boolean isEnabled(String feature) {
        Boolean value = snapshot.get(feature);
        return value != null ? value : defaultEnabled;
    }

    /**
     * Loads now instead of waiting for the next interval, for example from an admin
     * endpoint or a change notification.
     *
     * @return {@code true} if the load succeeded
     */
    public boolean refresh() {
        return !closed && load() == null;
    }

    /**
     * The current toggle state as last loaded (immutable).
     */
    public Map<String, Boolean> snapshot() {
        return snapshot;
    }

    /**
     * Features in the last successful load; empty until one has succeeded.
     */
    @Override
    public Optional<Set<String>> knownFeatures() {
        return lastLoaded == null ? Optional.empty() : Optional.of(snapshot.keySet());
    }

    /**
     * When the last successful load completed; empty if none has. Useful for health checks.
     */
    public Optional<Instant> lastLoaded() {
        return Optional.ofNullable(lastLoaded);
    }

    /**
     * Stops polling. The last snapshot keeps answering {@link #isEnabled}. Idempotent.
     */
    @Override
    public void close() {
        closed = true;
        task.cancel(false);
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    private void poll() {
        try {
            load();
        } catch (Throwable unexpected) {
            // never let an exception cancel the schedule
            LOG.log(System.Logger.Level.ERROR, "Unexpected error while polling feature toggles", unexpected);
        }
    }

    /**
     * @return {@code null} on success, otherwise the failure
     */
    private synchronized Exception load() {
        try {
            Map<String, ?> loaded = loader.load();
            if (loaded == null) {
                throw new IllegalStateException("Feature toggle loader returned null");
            }
            Map<String, Boolean> previous = snapshot;
            Map<String, Boolean> next = Map.copyOf(FeatureValues.parse(loaded));
            snapshot = next;
            lastLoaded = Instant.now();
            notifyChanges(previous, next);
            return null;
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            try {
                errorHandler.accept(error);
            } catch (RuntimeException ignored) {
                // a broken error handler must not break polling
            }
            return error;
        }
    }

    private void notifyChanges(Map<String, Boolean> previous, Map<String, Boolean> next) {
        if (changeListener == null) {
            return;
        }
        Set<String> features = new HashSet<>(previous.keySet());
        features.addAll(next.keySet());
        for (String feature : features) {
            Boolean before = previous.get(feature);
            Boolean after = next.get(feature);
            if (!Objects.equals(before, after)) {
                try {
                    changeListener.onChange(feature, before, after);
                } catch (RuntimeException error) {
                    LOG.log(System.Logger.Level.WARNING, "Feature toggle change listener failed for '" + feature + "'", error);
                }
            }
        }
    }

    public static final class Builder {

        private final FeatureToggleLoader loader;
        private Duration interval = Duration.ofSeconds(30);
        private boolean defaultEnabled;
        private boolean requireInitialLoad;
        private Consumer<Exception> errorHandler = error -> LOG.log(System.Logger.Level.WARNING,
                "Feature toggle load failed; keeping the last good snapshot", error);
        private ChangeListener changeListener;
        private ScheduledExecutorService executor;

        private Builder(FeatureToggleLoader loader) {
            this.loader = Objects.requireNonNull(loader, "loader");
        }

        /**
         * Delay between loads (default 30 seconds).
         */
        public Builder interval(Duration interval) {
            if (interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("interval must be positive");
            }
            this.interval = interval;
            return this;
        }

        /**
         * State for features absent from the store (default {@code false}).
         */
        public Builder defaultEnabled(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
            return this;
        }

        /**
         * Fail {@link #build()} if the first load fails. By default the service starts with
         * defaults and keeps retrying, which suits defaults that are the safe, old behaviour.
         */
        public Builder requireInitialLoad() {
            this.requireInitialLoad = true;
            return this;
        }

        /**
         * Receives load failures (default: logged as a warning).
         */
        public Builder onLoadError(Consumer<Exception> errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        /**
         * Receives per-feature changes, including the initial load; useful for audit logs.
         */
        public Builder onChange(ChangeListener changeListener) {
            this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
            return this;
        }

        /**
         * Polls on this executor instead of a private daemon thread; it is not shut down on close.
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Loads once synchronously, then starts polling.
         */
        public PollingFeatureToggles build() {
            return new PollingFeatureToggles(this);
        }
    }
}
