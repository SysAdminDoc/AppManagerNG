// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;

import android.app.NotificationManager;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.NotificationListenerService.RankingMap;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class NotificationFilterServiceTest {
    private static final String KEY = "0|com.termux|1337|null|10212";

    /**
     * #32: by the time a post is handled, the newest ranking can already be one without it. Its importance then went
     * missing, and a quiet ongoing notification was snoozed for 12 hours instead of 30 minutes. The ranking that came
     * with the post always lists it.
     */
    @Test
    public void importanceComesFromThePostsOwnRankingFirst() throws Exception {
        RankingMap withIt = rankingMap(ranking(KEY, NotificationManager.IMPORTANCE_LOW));
        RankingMap newerWithoutIt = rankingMap(ranking("0|other|1|null|10300", NotificationManager.IMPORTANCE_HIGH));

        assertEquals(NotificationManager.IMPORTANCE_LOW,
                NotificationFilterService.importanceIn(KEY, withIt, newerWithoutIt));
        // Outside of a post only the newest one is there.
        assertEquals(NotificationManager.IMPORTANCE_LOW, NotificationFilterService.importanceIn(KEY, null, withIt));
        assertEquals(-1, NotificationFilterService.importanceIn(KEY, null, newerWithoutIt));
        assertEquals(-1, NotificationFilterService.importanceIn(KEY, null, null));
    }

    @NonNull
    private static Ranking ranking(@NonNull String key, int importance) throws Exception {
        Ranking ranking = new Ranking();
        set(ranking, "mKey", key);
        set(ranking, "mImportance", importance);
        return ranking;
    }

    @NonNull
    private static RankingMap rankingMap(@NonNull Ranking... rankings) throws Exception {
        // Android's own tests build them this way. The constructor isn't in the public SDK.
        Constructor<RankingMap> constructor = RankingMap.class.getDeclaredConstructor(Ranking[].class);
        constructor.setAccessible(true);
        return constructor.newInstance((Object) rankings);
    }

    private static void set(@NonNull Ranking ranking, @NonNull String name, @NonNull Object value) throws Exception {
        Field field = Ranking.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ranking, value);
    }
}
