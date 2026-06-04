// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.details.info.DomainLinkConflictDetector;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;

public final class FinderRelevanceScorer {
    private static final int COMPONENT_MATCH_PENALTY = 5;
    private static final int NO_MATCH_SCORE = Integer.MAX_VALUE / 4;

    public static <T extends IFilterableAppInfo> void sort(@NonNull List<FilterItem.FilteredItemInfo<T>> items,
                                                           @NonNull FilterItem filterItem) {
        SearchTerms searchTerms = SearchTerms.from(filterItem);
        if (searchTerms.isEmpty()) {
            return;
        }
        Collections.sort(items, (left, right) -> {
            int leftScore = score(left, searchTerms);
            int rightScore = score(right, searchTerms);
            if (leftScore >= NO_MATCH_SCORE && rightScore >= NO_MATCH_SCORE) {
                return 0;
            }
            int scoreCompare = Integer.compare(leftScore, rightScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int packageCompare = left.info.getPackageName().compareToIgnoreCase(right.info.getPackageName());
            if (packageCompare != 0) {
                return packageCompare;
            }
            return Integer.compare(left.info.getUserId(), right.info.getUserId());
        });
    }

    @VisibleForTesting
    static int scoreText(@Nullable String candidate, @NonNull String query) {
        if (candidate == null) {
            return NO_MATCH_SCORE;
        }
        String normalizedCandidate = normalize(candidate);
        String normalizedQuery = normalize(query);
        if (normalizedCandidate.isEmpty() || normalizedQuery.isEmpty()) {
            return NO_MATCH_SCORE;
        }
        String simpleName = getSimpleName(normalizedCandidate);
        int distance = Math.min(levenshteinDistance(normalizedCandidate, normalizedQuery),
                levenshteinDistance(simpleName, normalizedQuery));
        for (String token : normalizedCandidate.split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                distance = Math.min(distance, levenshteinDistance(token, normalizedQuery));
            }
        }
        distance = Math.min(distance, bestWindowDistance(normalizedCandidate, normalizedQuery));
        int positionPenalty;
        if (normalizedCandidate.equals(normalizedQuery)) {
            positionPenalty = 0;
        } else if (simpleName.equals(normalizedQuery)) {
            positionPenalty = 5;
        } else if (normalizedCandidate.startsWith(normalizedQuery)) {
            positionPenalty = 10;
        } else if (simpleName.startsWith(normalizedQuery)) {
            positionPenalty = 20;
        } else if (startsWithToken(normalizedCandidate, normalizedQuery)) {
            positionPenalty = 30;
        } else if (normalizedCandidate.contains(normalizedQuery)) {
            positionPenalty = 40;
        } else {
            positionPenalty = 100;
        }
        return distance * 1000 + positionPenalty + Math.min(normalizedCandidate.length(), 255);
    }

    @VisibleForTesting
    static int levenshteinDistance(@NonNull String left, @NonNull String right) {
        if (left.equals(right)) {
            return 0;
        }
        if (left.isEmpty()) {
            return right.length();
        }
        if (right.isEmpty()) {
            return left.length();
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); ++j) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); ++i) {
            current[0] = i;
            for (int j = 1; j <= right.length(); ++j) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static <T extends IFilterableAppInfo> int score(@NonNull FilterItem.FilteredItemInfo<T> item,
                                                           @NonNull SearchTerms searchTerms) {
        int packageScore = bestScore(item.info.getPackageName(), searchTerms.packageTerms);
        int componentScore = bestComponentScore(item, searchTerms.componentTerms);
        if (componentScore < NO_MATCH_SCORE) {
            componentScore += COMPONENT_MATCH_PENALTY;
        }
        int domainScore = bestDomainScore(item.info, searchTerms.domainTerms);
        return Math.min(Math.min(packageScore, componentScore), domainScore);
    }

    private static int bestScore(@Nullable String candidate, @NonNull List<String> queries) {
        int bestScore = NO_MATCH_SCORE;
        for (String query : queries) {
            bestScore = Math.min(bestScore, scoreText(candidate, query));
        }
        return bestScore;
    }

    private static <T extends IFilterableAppInfo> int bestComponentScore(@NonNull FilterItem.FilteredItemInfo<T> item,
                                                                        @NonNull List<String> queries) {
        if (queries.isEmpty()) {
            return NO_MATCH_SCORE;
        }
        int bestScore = NO_MATCH_SCORE;
        bestScore = Math.min(bestScore, bestComponentScore(item.result.getMatchedComponents(), queries));
        bestScore = Math.min(bestScore, bestComponentScore(item.result.getMatchedTrackers(), queries));
        return bestScore;
    }

    private static int bestComponentScore(@Nullable Map<ComponentInfo, Integer> components,
                                          @NonNull List<String> queries) {
        if (components == null || components.isEmpty()) {
            return NO_MATCH_SCORE;
        }
        int bestScore = NO_MATCH_SCORE;
        for (ComponentInfo component : components.keySet()) {
            bestScore = Math.min(bestScore, bestScore(component.name, queries));
        }
        return bestScore;
    }

    private static int bestDomainScore(@NonNull IFilterableAppInfo info, @NonNull List<String> queries) {
        if (queries.isEmpty()) {
            return NO_MATCH_SCORE;
        }
        int bestScore = NO_MATCH_SCORE;
        for (String host : info.getDomainVerificationHosts().keySet()) {
            bestScore = Math.min(bestScore, bestScore(host, queries));
        }
        for (List<DomainLinkConflictDetector.Conflict> conflicts : info.getDomainLinkConflicts().values()) {
            for (DomainLinkConflictDetector.Conflict conflict : conflicts) {
                bestScore = Math.min(bestScore, bestScore(conflict.packageName, queries));
                bestScore = Math.min(bestScore, bestScore(conflict.label, queries));
            }
        }
        return bestScore;
    }

    private static int bestWindowDistance(@NonNull String candidate, @NonNull String query) {
        if (query.length() >= candidate.length()) {
            return levenshteinDistance(candidate, query);
        }
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i <= candidate.length() - query.length(); ++i) {
            bestDistance = Math.min(bestDistance,
                    levenshteinDistance(candidate.substring(i, i + query.length()), query));
        }
        return bestDistance;
    }

    private static boolean startsWithToken(@NonNull String candidate, @NonNull String query) {
        for (String token : candidate.split("[^a-z0-9]+")) {
            if (token.startsWith(query)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String getSimpleName(@NonNull String candidate) {
        int lastDot = candidate.lastIndexOf('.');
        return lastDot >= 0 && lastDot < candidate.length() - 1
                ? candidate.substring(lastDot + 1)
                : candidate;
    }

    @NonNull
    private static String normalize(@NonNull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class SearchTerms {
        @NonNull
        final List<String> packageTerms = new ArrayList<>();
        @NonNull
        final List<String> componentTerms = new ArrayList<>();
        @NonNull
        final List<String> domainTerms = new ArrayList<>();

        boolean isEmpty() {
            return packageTerms.isEmpty() && componentTerms.isEmpty() && domainTerms.isEmpty();
        }

        @NonNull
        static SearchTerms from(@NonNull FilterItem filterItem) {
            SearchTerms searchTerms = new SearchTerms();
            for (FilterOption option : filterItem.getFilterOptionsSnapshot()) {
                String value = option.getValue();
                if (value == null) {
                    continue;
                }
                String key = option.getKey();
                if (option.type.equals("pkg_name") && isPackageNameSearchKey(key)) {
                    addTerms(searchTerms.packageTerms, value);
                } else if (option.type.equals("components") && isComponentNameSearchKey(key)) {
                    addTerms(searchTerms.componentTerms, value);
                } else if (option.type.equals("trackers") && isTrackerNameSearchKey(key)) {
                    addTerms(searchTerms.componentTerms, value);
                } else if (option.type.equals("domain_links") && isDomainSearchKey(key)) {
                    addTerms(searchTerms.domainTerms, value);
                }
            }
            return searchTerms;
        }

        private static boolean isPackageNameSearchKey(@NonNull String key) {
            return key.equals("eq") || key.equals("eq_any") || key.equals("contains")
                    || key.equals("starts_with") || key.equals("ends_with");
        }

        private static boolean isComponentNameSearchKey(@NonNull String key) {
            return key.equals("eq") || key.equals("contains")
                    || key.equals("starts_with") || key.equals("ends_with");
        }

        private static boolean isTrackerNameSearchKey(@NonNull String key) {
            return key.equals("name_eq") || key.equals("name_contains")
                    || key.equals("name_starts_with") || key.equals("name_ends_with");
        }

        private static boolean isDomainSearchKey(@NonNull String key) {
            return key.equals("host_eq") || key.equals("host_contains")
                    || key.equals("conflict_package_contains");
        }

        private static void addTerms(@NonNull List<String> terms, @NonNull String rawValue) {
            for (String term : rawValue.split("\\n")) {
                String normalizedTerm = normalize(term);
                if (!normalizedTerm.isEmpty()) {
                    terms.add(normalizedTerm);
                }
            }
        }
    }
}
