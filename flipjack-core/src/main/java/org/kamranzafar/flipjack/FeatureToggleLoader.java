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

import java.util.Map;

/**
 * Supplies the current state of all toggles from wherever the application keeps them
 * (SSM Parameter Store, Vault, a config service, a database...). Implemented by the
 * application, typically as a method reference to one of its own methods:
 * <pre>{@code
 * PollingFeatureToggles.builder(configService::loadFeatureToggles).build();
 * }</pre>
 * Return every toggle in one call, keyed by feature name. Values may be {@link Boolean}s
 * or the strings {@code "true"} / {@code "false"} (case-insensitive, trimmed); anything
 * else rejects the whole load. Throwing, including checked exceptions, also rejects the
 * load, and the last good state is kept.
 */
@FunctionalInterface
public interface FeatureToggleLoader {

    Map<String, ?> load() throws Exception;
}
