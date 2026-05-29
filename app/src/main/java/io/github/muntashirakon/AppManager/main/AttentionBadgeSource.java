// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.db.entity.App;

/**
 * Single-source-of-truth adapter that derives an
 * {@link AttentionBadgeCalculator.Signals} from the existing
 * {@link App} cache row, plus an optional caller-supplied OS-revert count.
 *
 * <p>The T21-G calculator is intentionally fed by plain ints so it stays
 * JVM-unit-testable. This adapter is the documented integration point for
 * the main-list adapter: derive the calculator inputs in <em>one</em>
 * place so two different rows on the screen cannot disagree about a
 * package's badge state.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>{@code dangerousPermissionsRequestedNotGranted} =
 *       {@code max(0, app.dangerousPermTotal - app.dangerousPermGranted)}.
 *       The {@code App} cache already maintains these as part of every
 *       row refresh, so no extra query is needed.</li>
 *   <li>{@code userDisabledComponentCount} =
 *       {@code max(0, app.rulesCount)}. The cache exposes the rule
 *       <em>count</em> rather than a separate disabled-component count;
 *       in practice a populated rule set always implies that AppManagerNG
 *       (or another tool) has flipped at least one component off, which
 *       is what the badge wants to flag.</li>
 *   <li>{@code recentOsRevertCount} - cache does not track this today, so
 *       the no-OS-revert overload defaults to {@code 0}. Call the
 *       three-arg overload to pass an explicit count when an external
 *       store (e.g. a future {@code OsRevertMonitor.countRecent}) is
 *       wired up.</li>
 * </ul>
 *
 * <p>A {@code null} {@link App} reference produces a zero-signal
 * input ({@code Badge.none()} after calculator pass) rather than
 * throwing - mirroring the cache layer's tolerance for partially-loaded
 * rows.
 */
public final class AttentionBadgeSource {

    private AttentionBadgeSource() {
    }

    /**
     * Derive calculator inputs from an {@link App} row only. Equivalent to
     * {@code forApp(app, 0)}.
     */
    @NonNull
    public static AttentionBadgeCalculator.Signals forApp(@Nullable App app) {
        return forApp(app, 0);
    }

    /**
     * Derive calculator inputs from an {@link App} row and an explicit
     * recent-OS-revert count (typically maintained by a future
     * {@code OsRevertMonitor} counter keyed by package).
     */
    @NonNull
    public static AttentionBadgeCalculator.Signals forApp(@Nullable App app,
                                                          int recentOsRevertCount) {
        if (app == null) {
            return new AttentionBadgeCalculator.Signals(0, 0, Math.max(0, recentOsRevertCount));
        }
        int ungranted = Math.max(0, app.dangerousPermTotal - app.dangerousPermGranted);
        int disabled = Math.max(0, app.rulesCount);
        return new AttentionBadgeCalculator.Signals(ungranted, disabled,
                Math.max(0, recentOsRevertCount));
    }

    /**
     * Convenience pipeline used by the main-list adapter (T21-G UI slice):
     * derive signals and immediately resolve a {@link AttentionBadgeCalculator.Badge}.
     */
    @NonNull
    public static AttentionBadgeCalculator.Badge badgeFor(@Nullable App app,
                                                          int recentOsRevertCount) {
        return AttentionBadgeCalculator.compute(forApp(app, recentOsRevertCount));
    }

    /**
     * Main-list integration overload: the adapter binds {@link ApplicationItem}
     * (not the raw {@link App} cache row), which already carries the same three
     * counts populated during the {@code MainViewModel} refresh. Mapping mirrors
     * {@link #forApp(App, int)} so a row and its detail view agree on the badge.
     */
    @NonNull
    public static AttentionBadgeCalculator.Signals forItem(@Nullable ApplicationItem item,
                                                           int recentOsRevertCount) {
        if (item == null) {
            return new AttentionBadgeCalculator.Signals(0, 0, Math.max(0, recentOsRevertCount));
        }
        int total = item.dangerousPermTotal != null ? item.dangerousPermTotal : 0;
        int granted = item.dangerousPermGranted != null ? item.dangerousPermGranted : 0;
        int ungranted = Math.max(0, total - granted);
        int disabled = item.blockedCount != null ? Math.max(0, item.blockedCount) : 0;
        return new AttentionBadgeCalculator.Signals(ungranted, disabled, Math.max(0, recentOsRevertCount));
    }

    @NonNull
    public static AttentionBadgeCalculator.Badge badgeFor(@Nullable ApplicationItem item,
                                                          int recentOsRevertCount) {
        return AttentionBadgeCalculator.compute(forItem(item, recentOsRevertCount));
    }
}
