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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;

/**
 * Toggles read from a {@code .properties} file and reloaded when the file changes,
 * so features can be flipped at runtime without a framework.
 * <pre>
 * premium=true
 * new-pricing=false
 * </pre>
 * The file's modification time is checked at most once per reload interval. If the
 * file is missing, every feature uses the default; if a reload fails, the last good
 * values are kept.
 */
public final class PropertiesFileFeatureToggles implements FeatureToggles {

    private final Path file;
    private final String prefix;
    private final boolean defaultEnabled;
    private final long reloadIntervalNanos;

    private volatile Map<String, Boolean> snapshot = Map.of();
    private volatile FileTime loadedModifiedTime;
    private volatile long lastCheckNanos;

    public PropertiesFileFeatureToggles(Path file) {
        this(file, "", false, Duration.ofSeconds(5));
    }

    /**
     * @param prefix         only keys starting with this are read, with the prefix
     *                       stripped (for example {@code "features."}); use {@code ""} for all keys
     * @param reloadInterval how often to check the file for changes
     */
    public PropertiesFileFeatureToggles(Path file, String prefix, boolean defaultEnabled, Duration reloadInterval) {
        this.file = Objects.requireNonNull(file, "file");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.defaultEnabled = defaultEnabled;
        this.reloadIntervalNanos = reloadInterval.toNanos();
        reload();
        this.lastCheckNanos = System.nanoTime();
    }

    @Override
    public boolean isEnabled(String feature) {
        reloadIfDue();
        Boolean value = snapshot.get(feature);
        return value != null ? value : defaultEnabled;
    }

    @Override
    public Optional<Set<String>> knownFeatures() {
        reloadIfDue();
        return Optional.of(snapshot.keySet());
    }

    private void reloadIfDue() {
        if (System.nanoTime() - lastCheckNanos < reloadIntervalNanos) {
            return;
        }
        synchronized (this) {
            long now = System.nanoTime();
            if (now - lastCheckNanos < reloadIntervalNanos) {
                return;
            }
            lastCheckNanos = now;
            reload();
        }
    }

    private void reload() {
        try {
            if (!Files.exists(file)) {
                snapshot = Map.of();
                loadedModifiedTime = null;
                return;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(loadedModifiedTime)) {
                return;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
            Map<String, Boolean> values = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    values.put(key.substring(prefix.length()),
                            Boolean.parseBoolean(properties.getProperty(key).trim()));
                }
            }
            snapshot = Map.copyOf(values);
            loadedModifiedTime = modified;
        } catch (IOException e) {
            // keep the last good snapshot
        }
    }
}
