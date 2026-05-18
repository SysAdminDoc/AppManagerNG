// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.usage.UsageEvents;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.BuildConfig;

@RunWith(RobolectricTestRunner.class)
public class AssistTargetResolverTest {
    @Test
    public void resolve_prefersAssistPackageExtra() {
        Intent intent = new Intent(Intent.ACTION_ASSIST)
                .putExtra(AssistTargetResolver.EXTRA_ASSIST_PACKAGE, "com.example.target");

        AssistTargetResolver.Target target = AssistTargetResolver.resolve(intent, 0, 1_000L,
                (beginTime, endTime, userId) -> Collections.singletonList(event("com.other", 900L)));

        assertEquals("com.example.target", target.packageName);
        assertEquals(0, target.userId);
    }

    @Test
    public void resolve_ignoresSelfAssistPackageAndUsesRecentForegroundEvent() {
        Intent intent = new Intent(Intent.ACTION_ASSIST)
                .putExtra(AssistTargetResolver.EXTRA_ASSIST_PACKAGE, BuildConfig.APPLICATION_ID);

        AssistTargetResolver.Target target = AssistTargetResolver.resolve(intent, 0, 1_000L,
                (beginTime, endTime, userId) -> Arrays.asList(
                        event("com.old", 100L),
                        event(BuildConfig.APPLICATION_ID, 950L),
                        event("com.recent", 900L)));

        assertEquals("com.recent", target.packageName);
    }

    @Test
    public void resolve_ignoresNonResumedEvents() {
        Intent intent = new Intent(Intent.ACTION_ASSIST);

        AssistTargetResolver.Target target = AssistTargetResolver.resolve(intent, 0, 1_000L,
                (beginTime, endTime, userId) -> Collections.singletonList(
                        new AssistTargetResolver.ActivityEvent("com.paused",
                                UsageEvents.Event.ACTIVITY_PAUSED, 900L)));

        assertNull(target);
    }

    @Test
    public void resolve_returnsNullWhenNoTargetExists() {
        AssistTargetResolver.Target target = AssistTargetResolver.resolve(new Intent(Intent.ACTION_ASSIST),
                0, 1_000L, (beginTime, endTime, userId) -> Collections.emptyList());

        assertNull(target);
    }

    private static AssistTargetResolver.ActivityEvent event(String packageName, long timestamp) {
        return new AssistTargetResolver.ActivityEvent(packageName, UsageEvents.Event.ACTIVITY_RESUMED, timestamp);
    }
}
