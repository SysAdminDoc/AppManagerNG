// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class AutomationReceiverTest {
    private final AutomationReceiver mReceiver = new AutomationReceiver();

    @Test
    public void getUsersRejectsNegativeScalarUser() {
        Intent intent = new Intent().putExtra(AutomationIntents.EXTRA_USER, -1);

        assertThrows(IllegalArgumentException.class, () -> mReceiver.getUsers(intent, 1));
    }

    @Test
    public void getUsersRejectsNegativeListEntry() {
        Intent intent = new Intent().putIntegerArrayListExtra(AutomationIntents.EXTRA_USERS,
                new ArrayList<>(Arrays.asList(0, -1)));

        assertThrows(IllegalArgumentException.class, () -> mReceiver.getUsers(intent, 2));
    }

    @Test
    public void getUsersReplicatesSingleValidUser() {
        Intent intent = new Intent().putExtra(AutomationIntents.EXTRA_USERS, new int[]{10});

        assertEquals(Arrays.asList(10, 10), mReceiver.getUsers(intent, 2));
    }
}
