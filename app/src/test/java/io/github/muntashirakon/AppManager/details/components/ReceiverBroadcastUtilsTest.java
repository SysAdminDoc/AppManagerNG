// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ReceiverBroadcastUtilsTest {
    @Test
    public void buildBroadcastIntentTargetsExplicitReceiver() {
        Bundle extras = new Bundle();
        extras.putString("name", "value");

        Intent intent = ReceiverBroadcastUtils.buildBroadcastIntent("com.example", ".BootReceiver",
                "com.example.BOOT", Arrays.asList("android.intent.category.DEFAULT"), extras, true);

        assertEquals("com.example.BOOT", intent.getAction());
        assertEquals(new ComponentName("com.example", "com.example.BootReceiver"), intent.getComponent());
        assertTrue(intent.hasCategory("android.intent.category.DEFAULT"));
        assertEquals("value", intent.getStringExtra("name"));
        assertTrue((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);
    }

    @Test
    public void parseCategoriesDeduplicatesCommaAndLineInput() {
        List<String> categories = ReceiverBroadcastUtils.parseCategories("alpha, beta\nalpha\r\ngamma");

        assertEquals(Arrays.asList("alpha", "beta", "gamma"), categories);
    }

    @Test
    public void parseExtrasSupportsTypedValues() {
        Bundle extras = ReceiverBroadcastUtils.parseExtras("name=sync\ncount:int=3\nenabled:boolean=true\nratio:double=1.5");

        assertEquals("sync", extras.getString("name"));
        assertEquals(3, extras.getInt("count"));
        assertTrue(extras.getBoolean("enabled"));
        assertEquals(1.5d, extras.getDouble("ratio"), 0d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseExtrasRejectsMalformedLines() {
        ReceiverBroadcastUtils.parseExtras("missing_separator");
    }

    @Test
    public void protectedOrCrossUserBroadcastsNeedPrivilegedDispatch() {
        assertTrue(ReceiverBroadcastUtils.needsPrivilegedDispatch("android.intent.action.BATTERY_LOW",
                true, 0, 0));
        assertTrue(ReceiverBroadcastUtils.needsPrivilegedDispatch("com.example.SYNC", true, 10, 0));
        assertTrue(ReceiverBroadcastUtils.needsPrivilegedDispatch("com.example.SYNC", false, 0, 0));
        assertFalse(ReceiverBroadcastUtils.needsPrivilegedDispatch("com.example.SYNC", true, 0, 0));
    }

    @Test
    public void toQualifiedComponentNameHandlesManifestForms() {
        assertEquals("com.example.Relative", ReceiverBroadcastUtils.toQualifiedComponentName("com.example", ".Relative"));
        assertEquals("com.example.Nested", ReceiverBroadcastUtils.toQualifiedComponentName("com.example", "Nested"));
        assertEquals("org.other.Receiver", ReceiverBroadcastUtils.toQualifiedComponentName("com.example", "org.other.Receiver"));
    }
}
