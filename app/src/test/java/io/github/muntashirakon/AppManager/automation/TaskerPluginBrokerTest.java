// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TaskerPluginBrokerTest {
    private static final String SECRET = "fixed-test-secret";

    @Test
    public void buildEditResultIntentReturnsLocaleBundleAndBlurb() throws Exception {
        Intent result = TaskerPluginBroker.buildEditResultIntent(
                "am://profile/nightly/run?state=on", SECRET);

        Bundle bundle = result.getBundleExtra(TaskerPluginBroker.EXTRA_BUNDLE);

        assertEquals("Profile: nightly", result.getStringExtra(TaskerPluginBroker.EXTRA_STRING_BLURB));
        assertNotNull(bundle);
        assertEquals("am://profile/nightly/run?state=on", TaskerPluginBroker.getUri(bundle));
        assertTrue(TaskerPluginBroker.isBundleSigned(bundle, SECRET));
    }

    @Test
    public void signedBundleBuildsInternalAutomationReceiverIntent() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Bundle bundle = TaskerPluginBroker.buildPluginBundle(
                "am://freeze/com.example.app?user=10", SECRET);

        Intent intent = TaskerPluginBroker.getSignedAutomationIntent(context, bundle, SECRET);

        assertNotNull(intent);
        assertEquals(AutomationIntents.ACTION_FREEZE, intent.getAction());
        assertEquals("com.example.app", intent.getStringExtra(AutomationIntents.EXTRA_PACKAGE));
        assertEquals(10, intent.getIntExtra(AutomationIntents.EXTRA_USER, -1));
        assertEquals(AutomationReceiver.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void tamperedBundleDoesNotBuildAutomationIntent() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Bundle bundle = TaskerPluginBroker.buildPluginBundle(
                "am://freeze/com.example.app", SECRET);
        bundle.putString("io.github.sysadmindoc.AppManagerNG.tasker.URI", "am://unfreeze/com.example.app");

        assertNull(TaskerPluginBroker.getSignedAutomationIntent(context, bundle, SECRET));
    }

    @Test
    public void profileUriWithPackageOverrideMapsToProfileReceiverIntent() throws Exception {
        AutomationRequest request = AutomationRequest.fromUri(android.net.Uri.parse(
                "am://profile/nightly/run?state=off&package=com.example.app"));

        Intent intent = TaskerPluginBroker.toAutomationIntent(request);

        assertEquals(AutomationIntents.ACTION_RUN_PROFILE, intent.getAction());
        assertEquals("nightly", intent.getStringExtra(AutomationIntents.EXTRA_PROFILE_ID));
        assertEquals("off", intent.getStringExtra(AutomationIntents.EXTRA_PROFILE_STATE));
        assertTrue(intent.getStringExtra(AutomationIntents.EXTRA_PROFILE_OVERRIDES)
                .contains("com.example.app"));
    }
}
