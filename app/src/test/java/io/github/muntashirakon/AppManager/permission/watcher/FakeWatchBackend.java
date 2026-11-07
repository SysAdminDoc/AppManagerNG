// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers from a map and writes down every read and change, so a test sees exactly what the watcher did. */
final class FakeWatchBackend implements PermissionWatcher.Backend {
    final Map<String, Boolean> allowed = new HashMap<>();
    final Set<String> refused = new HashSet<>();
    final List<String> reads = new ArrayList<>();
    final List<String> applied = new ArrayList<>();
    boolean privileged;

    @NonNull
    static String key(@NonNull WatchRule rule) {
        return rule.packageName + "/" + rule.userId + " " + rule.name;
    }

    @NonNull
    FakeWatchBackend set(@NonNull WatchRule rule, boolean isAllowed) {
        allowed.put(key(rule), isAllowed);
        return this;
    }

    @Nullable
    @Override
    public Boolean isAllowed(@NonNull WatchRule rule) {
        reads.add(key(rule));
        return allowed.get(key(rule));
    }

    @Override
    public boolean canEnforce(@NonNull WatchRule rule) {
        return privileged;
    }

    @Override
    public void apply(@NonNull WatchRule rule, boolean allow) throws Exception {
        if (refused.contains(key(rule))) {
            throw new IllegalStateException("refused " + rule.name);
        }
        applied.add((allow ? "grant " : "revoke ") + key(rule));
        allowed.put(key(rule), allow);
    }
}
