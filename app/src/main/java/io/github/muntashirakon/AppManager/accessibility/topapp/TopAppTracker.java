// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which app the top-app bubble names and which apps it keeps within reach. It keeps no Android state, so the
 * caller feeds it from whichever source is available.
 * <p>
 * Ignored packages (this app, System UI, the launcher, the keyboard) never replace the current app. An ad that closes
 * back to the home screen therefore stays named by the bubble, which is what makes it possible to catch.
 */
public final class TopAppTracker {
    /** Rows the bubble can show at once, so its window stays small. */
    public static final int MAX_RECENTS = 5;

    /** One on-screen window. */
    public static final class Window {
        @NonNull
        public final String packageName;
        /** False for an overlay, which accessibility reports as a system window. */
        public final boolean application;

        public Window(@NonNull String packageName, boolean application) {
            this.packageName = packageName;
            this.application = application;
        }
    }

    @NonNull
    private final Set<String> mIgnored;
    /** Newest first. The first row is the current app. */
    @NonNull
    private final List<String> mRecents = new ArrayList<>(MAX_RECENTS + 1);
    /** Overlays already on screen at the first walk, such as a Samsung edge handle. They'd otherwise always win. */
    @NonNull
    private final Set<String> mSteadyOverlays = new HashSet<>();
    @Nullable
    private Set<String> mLastOverlays;
    @Nullable
    private String mLastTopApplication;

    public TopAppTracker(@NonNull Collection<String> ignoredPackages) {
        mIgnored = new HashSet<>(ignoredPackages);
    }

    @Nullable
    public String getCurrent() {
        return mRecents.isEmpty() ? null : mRecents.get(0);
    }

    /** Newest first, starting with the current app. */
    @NonNull
    public List<String> getRecents() {
        return Collections.unmodifiableList(new ArrayList<>(mRecents));
    }

    /**
     * @return {@code true} if this changed the current app
     */
    public boolean onTopApp(@Nullable String packageName) {
        if (packageName == null || packageName.isEmpty() || mIgnored.contains(packageName)) {
            return false;
        }
        if (packageName.equals(getCurrent())) {
            return false;
        }
        mRecents.remove(packageName);
        mRecents.add(0, packageName);
        if (mRecents.size() > MAX_RECENTS) {
            mRecents.remove(mRecents.size() - 1);
        }
        return true;
    }

    /**
     * Takes one walk of the on-screen windows, topmost first. This is how an ad drawn as an overlay is found, since a
     * window that never takes focus sends no window-state event. An overlay counts only when it first appears, so one
     * that went away stays named until another app takes over.
     * <p>
     * Overlays already up at the first walk are never named over the top app afterwards, or a permanent one would hold
     * the bubble forever. They still go in the recents, since one of them may be the ad the bubble was turned on for.
     *
     * @return {@code true} if this changed the current app
     */
    public boolean onWindows(@NonNull List<Window> windowsTopFirst) {
        Set<String> lastOverlays = mLastOverlays;
        boolean firstWalk = lastOverlays == null;
        Set<String> overlays = new LinkedHashSet<>();
        String topApplication = null;
        String newOverlay = null;
        for (Window window : windowsTopFirst) {
            if (window.application) {
                if (topApplication == null) {
                    topApplication = window.packageName;
                }
            } else if (!mIgnored.contains(window.packageName)) {
                overlays.add(window.packageName);
                if (!firstWalk && newOverlay == null && !mSteadyOverlays.contains(window.packageName)
                        && !lastOverlays.contains(window.packageName)) {
                    newOverlay = window.packageName;
                }
            }
        }
        boolean changed = false;
        if (firstWalk) {
            mSteadyOverlays.addAll(overlays);
            List<String> topLast = new ArrayList<>(overlays);
            Collections.reverse(topLast);
            for (String packageName : topLast) {
                changed |= onTopApp(packageName);
            }
        }
        mLastOverlays = overlays;
        if (topApplication != null && !topApplication.equals(mLastTopApplication)) {
            mLastTopApplication = topApplication;
            changed |= onTopApp(topApplication);
        }
        // Recorded last, so that an overlay arriving with a new app is the one the bubble names.
        changed |= onTopApp(newOverlay);
        return changed;
    }
}
