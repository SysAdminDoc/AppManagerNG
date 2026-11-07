// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import android.app.Application;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class WatchRuleMenuTest {
    private Application mApp;
    private Menu mMenu;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mMenu = new PopupMenu(mApp, new View(mApp)).getMenu();
    }

    @NonNull
    private List<String> titles() {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < mMenu.size(); ++i) {
            titles.add(mMenu.getItem(i).getTitle().toString());
        }
        return titles;
    }

    @NonNull
    private List<WatchRuleMenu.Extra> extras() {
        return Collections.singletonList(new WatchRuleMenu.Extra(R.string.set_app_op_mode, () -> {
        }));
    }

    @Test
    public void theRowsOwnItemComesFirstThenTheThreeStates() {
        WatchRuleMenu.populate(mMenu, extras(), true, null);

        assertEquals(Arrays.asList(mApp.getString(R.string.set_app_op_mode),
                mApp.getString(R.string.permission_watcher_always_allow),
                mApp.getString(R.string.permission_watcher_always_deny),
                mApp.getString(R.string.permission_watcher_ignore)), titles());
        MenuItem extra = mMenu.getItem(0);
        assertEquals(WatchRuleMenu.GROUP_EXTRAS, extra.getGroupId());
        assertEquals("the order is how a click finds its action", 0, extra.getOrder());
        for (int id : new int[]{WatchRuleMenu.ID_ALLOW, WatchRuleMenu.ID_DENY, WatchRuleMenu.ID_IGNORE}) {
            assertFalse(mMenu.findItem(id).isChecked());
            assertTrue(mMenu.findItem(id).isCheckable());
        }
        assertNull("nothing to stop watching yet", mMenu.findItem(WatchRuleMenu.ID_STOP));
    }

    @Test
    public void anExistingRuleIsCheckedAndCanBeStopped() {
        WatchRule existing = permission("com.example.app", 0, "android.permission.CAMERA", WatchRule.STATE_DENY);

        WatchRuleMenu.populate(mMenu, extras(), true, existing);

        assertTrue(mMenu.findItem(WatchRuleMenu.ID_DENY).isChecked());
        assertFalse(mMenu.findItem(WatchRuleMenu.ID_ALLOW).isChecked());
        assertFalse(mMenu.findItem(WatchRuleMenu.ID_IGNORE).isChecked());
        MenuItem stop = mMenu.findItem(WatchRuleMenu.ID_STOP);
        assertEquals(mApp.getString(R.string.permission_watcher_stop), stop.getTitle().toString());
        assertEquals(mMenu.size() - 1, indexOf(stop));
    }

    @Test
    public void aRowThatCantBeWatchedKeepsOnlyItsOwnItems() {
        WatchRuleMenu.populate(mMenu, extras(), false, null);

        assertEquals(Collections.singletonList(mApp.getString(R.string.set_app_op_mode)), titles());
    }

    @Test
    public void withNoOwnItemsTheStatesStandAlone() {
        WatchRuleMenu.populate(mMenu, Collections.emptyList(), true, null);

        assertEquals(3, mMenu.size());
        assertEquals(WatchRuleMenu.ID_ALLOW, mMenu.getItem(0).getItemId());
    }

    @Test
    public void eachStateItemMapsToItsState() {
        assertEquals(WatchRule.STATE_ALLOW, WatchRuleMenu.stateFor(WatchRuleMenu.ID_ALLOW));
        assertEquals(WatchRule.STATE_DENY, WatchRuleMenu.stateFor(WatchRuleMenu.ID_DENY));
        assertEquals(WatchRule.STATE_IGNORE, WatchRuleMenu.stateFor(WatchRuleMenu.ID_IGNORE));
        assertEquals(-1, WatchRuleMenu.stateFor(WatchRuleMenu.ID_STOP));
        assertEquals(-1, WatchRuleMenu.stateFor(Menu.NONE));
    }

    private int indexOf(@NonNull MenuItem item) {
        for (int i = 0; i < mMenu.size(); ++i) {
            if (mMenu.getItem(i) == item) {
                return i;
            }
        }
        return -1;
    }
}
