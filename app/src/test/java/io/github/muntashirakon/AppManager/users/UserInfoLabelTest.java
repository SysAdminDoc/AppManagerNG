// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.test.ResourceNameContext;

/**
 * Issue 31 item 6: an unnamed user showed as {@code This (0)} or {@code Other (10)} in every language, since
 * the words were typed into the code.
 */
@RunWith(RobolectricTestRunner.class)
public class UserInfoLabelTest {
    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void unnamedUsersAreLabelledFromResources() {
        Context named = new ResourceNameContext(mContext);

        String self = new UserInfo(pmUser(0, null)).toLocalizedString(named).toString();
        String other = new UserInfo(pmUser(10, null)).toLocalizedString(named).toString();

        assertFalse(self, self.contains("This"));
        assertFalse(other, other.contains("Other"));
        assertTrue(self, self.startsWith("{{"));
        assertTrue(other, other.startsWith("{{"));
        assertTrue(self, self.contains("(0)"));
        assertTrue(other, other.contains("(10)"));
    }

    @Test
    public void namedUsersKeepTheirOwnName() {
        String label = new UserInfo(pmUser(0, "Owner")).toLocalizedString(mContext).toString();

        assertTrue(label, label.startsWith("Owner (0)"));
    }

    private static android.content.pm.UserInfo pmUser(int id, @Nullable String name) {
        android.content.pm.UserInfo info = new android.content.pm.UserInfo();
        info.id = id;
        info.name = name;
        info.profileGroupId = android.content.pm.UserInfo.NO_PROFILE_GROUP_ID;
        return info;
    }
}
