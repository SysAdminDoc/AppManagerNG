// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DomainLinkConflictDetector {
    private DomainLinkConflictDetector() {
    }

    @NonNull
    public static Map<String, Map<String, List<Conflict>>> findConflictsByPackageUser(
            @NonNull List<AppDomainClaims> claims) {
        Map<String, List<AppDomainClaims>> hostClaims = new LinkedHashMap<>();
        for (AppDomainClaims claim : claims) {
            for (String host : claim.hostStates.keySet()) {
                String key = claim.userId + "\n" + normalizeHost(host);
                hostClaims.computeIfAbsent(key, unused -> new ArrayList<>()).add(claim);
            }
        }
        Map<String, Map<String, List<Conflict>>> conflictsByPackageUser = new LinkedHashMap<>();
        for (AppDomainClaims claim : claims) {
            Map<String, List<Conflict>> conflictsByHost = new LinkedHashMap<>();
            for (String host : claim.hostStates.keySet()) {
                String normalizedHost = normalizeHost(host);
                List<AppDomainClaims> conflictingClaims = hostClaims.get(claim.userId + "\n" + normalizedHost);
                if (conflictingClaims == null || conflictingClaims.size() < 2) {
                    continue;
                }
                List<Conflict> conflicts = new ArrayList<>(conflictingClaims.size() - 1);
                for (AppDomainClaims conflictingClaim : conflictingClaims) {
                    if (conflictingClaim.packageName.equals(claim.packageName)) {
                        continue;
                    }
                    Integer state = conflictingClaim.hostStates.get(normalizedHost);
                    if (state == null) {
                        state = conflictingClaim.hostStates.get(host);
                    }
                    conflicts.add(new Conflict(conflictingClaim.packageName, conflictingClaim.label,
                            conflictingClaim.userId, state != null ? state : 0));
                }
                if (!conflicts.isEmpty()) {
                    conflictsByHost.put(normalizedHost, conflicts);
                }
            }
            if (!conflictsByHost.isEmpty()) {
                conflictsByPackageUser.put(packageUserKey(claim.packageName, claim.userId), conflictsByHost);
            }
        }
        return conflictsByPackageUser;
    }

    @NonNull
    public static AppDomainClaims claim(@NonNull String packageName, @Nullable String label, int userId,
                                        @NonNull Map<String, Integer> hostStates) {
        Map<String, Integer> normalizedHosts = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : hostStates.entrySet()) {
            String host = normalizeHost(entry.getKey());
            if (!host.isEmpty()) {
                normalizedHosts.put(host, entry.getValue());
            }
        }
        return new AppDomainClaims(packageName, label != null ? label : packageName, userId, normalizedHosts);
    }

    @NonNull
    public static String packageUserKey(@NonNull String packageName, int userId) {
        return packageName + ':' + userId;
    }

    @NonNull
    private static String normalizeHost(@Nullable String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static final class AppDomainClaims {
        @NonNull
        public final String packageName;
        @NonNull
        public final String label;
        public final int userId;
        @NonNull
        public final Map<String, Integer> hostStates;

        private AppDomainClaims(@NonNull String packageName, @NonNull String label, int userId,
                                @NonNull Map<String, Integer> hostStates) {
            this.packageName = packageName;
            this.label = label;
            this.userId = userId;
            this.hostStates = Collections.unmodifiableMap(new LinkedHashMap<>(hostStates));
        }
    }

    public static final class Conflict {
        @NonNull
        public final String packageName;
        @NonNull
        public final String label;
        public final int userId;
        public final int state;

        private Conflict(@NonNull String packageName, @NonNull String label, int userId, int state) {
            this.packageName = packageName;
            this.label = label;
            this.userId = userId;
            this.state = state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Conflict)) return false;
            Conflict conflict = (Conflict) o;
            return userId == conflict.userId
                    && state == conflict.state
                    && packageName.equals(conflict.packageName)
                    && label.equals(conflict.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, label, userId, state);
        }
    }
}
