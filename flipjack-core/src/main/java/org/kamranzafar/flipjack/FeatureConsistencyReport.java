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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Result of comparing the features code uses with the features a source holds.
 *
 * @param missingFromStore used by code but absent from the store: usually a typo or a toggle
 *                         not yet configured. These calls run with the default state.
 * @param unusedInStore    in the store but not used by any toggled component or declared inline
 *                         feature: usually a stale toggle that can be deleted.
 */
public record FeatureConsistencyReport(Set<String> missingFromStore, Set<String> unusedInStore) {

    private static final System.Logger LOG = System.getLogger(FeatureConsistencyReport.class.getName());

    public FeatureConsistencyReport {
        missingFromStore = Collections.unmodifiableSet(new TreeSet<>(missingFromStore));
        unusedInStore = Collections.unmodifiableSet(new TreeSet<>(unusedInStore));
    }

    public boolean isConsistent() {
        return missingFromStore.isEmpty() && unusedInStore.isEmpty();
    }

    public String describeMissing() {
        return "Features used by code but missing from the toggle store (running with defaults; "
                + "check for typos): " + missingFromStore;
    }

    public String describeUnused() {
        return "Features in the toggle store that no code uses (stale toggles, or inline checks "
                + "not declared): " + unusedInStore;
    }

    /**
     * Logs missing features as warnings and unused ones as info, through {@link System.Logger}.
     */
    public void logIfInconsistent() {
        if (!missingFromStore.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, describeMissing());
        }
        if (!unusedInStore.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, describeUnused());
        }
    }
}
