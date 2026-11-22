// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.shadows.ShadowToast;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.settings.Ops;

@RunWith(RobolectricTestRunner.class)
public class NotificationHistoryActivityTest {
    private static final String CHAT = "com.example.chat";
    private static final String SHOP = "com.example.shop";

    private Application mApp;
    private NotificationHistoryDb mDb;
    private NotificationHistory mHistory;
    private ActivityController<NotificationHistoryActivity> mController;
    private boolean mAuthenticatedBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        shadowOf(mApp).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mDb = Room.inMemoryDatabaseBuilder(mApp, NotificationHistoryDb.class).allowMainThreadQueries().build();
        mHistory = new NotificationHistory(mDb.dao(), Runnable::run, () -> TimeUnit.DAYS.toMillis(30),
                System::currentTimeMillis);
        NotificationHistory.setInstanceForTesting(mHistory);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        NotificationHistory.setInstanceForTesting(null);
        NotificationPins.forgetAllForTesting();
        mDb.close();
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    private void record(long ageMillis, @NonNull String packageName, @NonNull String title, @NonNull String text) {
        mHistory.record(new NotificationHistoryEntry(System.currentTimeMillis() - ageMillis, packageName,
                "Label of " + packageName, new NotificationFields(title, text, ""), "SILENT", null, false));
    }

    @NonNull
    private NotificationHistoryActivity open() {
        mController = Robolectric.buildActivity(NotificationHistoryActivity.class).setup();
        ShadowLooper.idleMainLooper();
        return mController.get();
    }

    private static void waitFor(@NonNull BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!condition.getAsBoolean()) {
            assertTrue("timed out", System.currentTimeMillis() < deadline);
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static void waitForRows(@NonNull NotificationHistoryActivity activity, int rows) {
        waitFor(() -> activity.getVisibleEntries().size() == rows);
    }

    private static void layOut(@NonNull RecyclerView list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
    }

    @Nullable
    private StatusBarNotification shownPin() {
        NotificationManager manager = (NotificationManager) mApp.getSystemService(Context.NOTIFICATION_SERVICE);
        for (StatusBarNotification sbn : manager.getActiveNotifications()) {
            if (sbn.getTag() != null && sbn.getTag().startsWith(NotificationPins.PIN_TAG_PREFIX)) {
                return sbn;
            }
        }
        return null;
    }

    @NonNull
    private static MenuItem item(@NonNull PopupMenu popup, @NonNull String title) {
        for (int i = 0; i < popup.getMenu().size(); ++i) {
            MenuItem item = popup.getMenu().getItem(i);
            if (title.contentEquals(item.getTitle())) {
                return item;
            }
        }
        throw new AssertionError("No " + title + " in the menu");
    }

    @Test
    public void relatedWordsFindNotificationsByMeaning() {
        record(3_000L, CHAT, "Carol", "Wi-Fi calling is off");
        record(2_000L, SHOP, "Sale", "Your order shipped");
        record(1_000L, SHOP, "Sync", "Couldn't reach the network");
        NotificationHistoryActivity activity = open();
        waitForRows(activity, 3);
        AdvancedSearchView searchView = (AdvancedSearchView) shadowOf(activity).getOptionsMenu()
                .findItem(R.id.action_search).getActionView();
        assertTrue((searchView.getEnabledTypes() & AdvancedSearchView.SEARCH_TYPE_RELATED) != 0);
        assertTrue((searchView.getEnabledTypes() & AdvancedSearchView.SEARCH_TYPE_REGEX) != 0);

        activity.onQueryTextChange("internet", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
        waitForRows(activity, 0);

        activity.onQueryTextChange("internet", AdvancedSearchView.SEARCH_TYPE_RELATED);
        waitForRows(activity, 2);
        assertEquals("Couldn't reach the network", activity.getVisibleEntries().get(0).text);
        assertEquals("Wi-Fi calling is off", activity.getVisibleEntries().get(1).text);

        activity.onQueryTextChange("error", AdvancedSearchView.SEARCH_TYPE_RELATED);
        waitForRows(activity, 1);

        // Regex says exactly what to match.
        activity.onQueryTextChange("internet|error", AdvancedSearchView.SEARCH_TYPE_REGEX);
        waitForRows(activity, 0);
    }

    @Test
    public void aRowCanBePinnedToTheShade() {
        record(1_000L, CHAT, "Carol", "Gate 12, boarding at 9:40");
        NotificationHistoryActivity activity = open();
        waitForRows(activity, 1);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);
        View pin = list.getChildAt(0).findViewById(R.id.pin);
        assertNotNull(pin);
        assertEquals(View.VISIBLE, pin.getVisibility());
        assertEquals(mApp.getString(R.string.notification_history_pin), pin.getContentDescription().toString());

        pin.performClick();
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popup);
        assertEquals(3, popup.getMenu().size());
        item(popup, mApp.getString(R.string.notification_filter_pin_one_hour));
        item(popup, mApp.getString(R.string.notification_filter_pin_today));
        shadowOf(popup).getOnMenuItemClickListener()
                .onMenuItemClick(item(popup, mApp.getString(R.string.notification_filter_pin_until_unpinned)));

        waitFor(() -> shownPin() != null);
        Notification shown = shownPin().getNotification();
        assertEquals("Gate 12, boarding at 9:40", shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertTrue((shown.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        waitFor(() -> mApp.getString(R.string.notification_history_pinned).equals(ShadowToast.getTextOfLatestToast()));
    }

    @Test
    public void pinningWithNotificationsOffSaysSo() {
        record(1_000L, CHAT, "Carol", "Gate 12, boarding at 9:40");
        shadowOf(mApp).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationHistoryActivity activity = open();
        waitForRows(activity, 1);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        list.getChildAt(0).findViewById(R.id.pin).performClick();
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        shadowOf(popup).getOnMenuItemClickListener()
                .onMenuItemClick(item(popup, mApp.getString(R.string.notification_filter_pin_one_hour)));

        waitFor(() -> mApp.getString(R.string.notification_history_pin_needs_permission)
                .equals(ShadowToast.getTextOfLatestToast()));
        assertEquals(null, shownPin());
    }

    @Test
    public void theActionFilterListsTheNewActions() {
        mHistory.record(new NotificationHistoryEntry(System.currentTimeMillis() - 1_000L, CHAT, "Chat",
                new NotificationFields("Carol", "Pinned one", ""), "PIN", "Keep Carol", false));
        mHistory.record(new NotificationHistoryEntry(System.currentTimeMillis() - 2_000L, CHAT, "Chat",
                new NotificationFields("Carol", "Iconic one", ""), "KEEP_ICON", "Icon Carol", false));
        NotificationHistoryActivity activity = open();
        waitForRows(activity, 2);
        android.widget.AutoCompleteTextView filter = activity.findViewById(R.id.filter_action);
        android.widget.ListAdapter adapter = filter.getAdapter();
        boolean pin = false;
        boolean keepIcon = false;
        for (int i = 0; i < adapter.getCount(); ++i) {
            String label = String.valueOf(adapter.getItem(i));
            pin |= label.equals(mApp.getString(R.string.signer_pin_action_pin));
            keepIcon |= label.equals(mApp.getString(R.string.notification_filter_action_keep_icon));
        }
        assertTrue(pin);
        assertTrue(keepIcon);
    }
}
