// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import android.app.usage.UsageEvents;
import android.content.Intent;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;

final class AssistTargetResolver {
    @VisibleForTesting
    static final String EXTRA_ASSIST_PACKAGE = "android.intent.extra.ASSIST_PACKAGE";
    @VisibleForTesting
    static final String EXTRA_ASSIST_UID = "android.intent.extra.ASSIST_UID";
    private static final long USAGE_LOOKBACK_MILLIS = 2 * 60 * 1000L;

    interface ActivityEventProvider {
        @NonNull
        List<ActivityEvent> query(long beginTime, long endTime, int userId);
    }

    static final class ActivityEvent {
        @NonNull
        final String packageName;
        final int eventType;
        final long timestamp;

        ActivityEvent(@NonNull String packageName, int eventType, long timestamp) {
            this.packageName = packageName;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }
    }

    static final class Target {
        @NonNull
        final String packageName;
        final int userId;

        Target(@NonNull String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
        }
    }

    private AssistTargetResolver() {
    }

    @Nullable
    static Target resolve(@NonNull Intent intent,
                          int fallbackUserId,
                          long now,
                          @NonNull ActivityEventProvider eventProvider) {
        Target target = resolveFromAssistExtras(intent, fallbackUserId);
        if (target != null) {
            return target;
        }
        return resolveFromRecentUsage(fallbackUserId, now, eventProvider);
    }

    @Nullable
    private static Target resolveFromAssistExtras(@NonNull Intent intent, int fallbackUserId) {
        String packageName = intent.getStringExtra(EXTRA_ASSIST_PACKAGE);
        if (isIgnoredPackage(packageName)) {
            return null;
        }
        int userId = fallbackUserId;
        int uid = intent.getIntExtra(EXTRA_ASSIST_UID, -1);
        if (uid >= 0) {
            userId = UserHandleHidden.getUserId(uid);
        }
        return new Target(packageName, userId);
    }

    @Nullable
    private static Target resolveFromRecentUsage(int userId,
                                                 long now,
                                                 @NonNull ActivityEventProvider eventProvider) {
        List<ActivityEvent> events = eventProvider.query(now - USAGE_LOOKBACK_MILLIS, now, userId);
        ActivityEvent bestEvent = null;
        for (ActivityEvent event : events) {
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED || isIgnoredPackage(event.packageName)) {
                continue;
            }
            if (bestEvent == null || event.timestamp > bestEvent.timestamp) {
                bestEvent = event;
            }
        }
        return bestEvent != null ? new Target(bestEvent.packageName, userId) : null;
    }

    private static boolean isIgnoredPackage(@Nullable String packageName) {
        return TextUtils.isEmpty(packageName) || BuildConfig.APPLICATION_ID.equals(packageName);
    }
}
