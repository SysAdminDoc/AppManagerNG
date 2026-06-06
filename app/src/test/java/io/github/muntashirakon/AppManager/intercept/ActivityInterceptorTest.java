// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ActivityInterceptorTest {
    @Test
    public void getLaunchIntent_carriesBuilderMetadata() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle extras = new Bundle();
        extras.putString("answer", "42");

        Intent intent = ActivityInterceptor.getLaunchIntent(context, "com.example",
                "com.example.MainActivity", 10, true, Intent.ACTION_VIEW,
                Intent.FLAG_ACTIVITY_CLEAR_TOP, extras);

        assertEquals(new ComponentName(context, ActivityInterceptor.class), intent.getComponent());
        assertEquals("com.example", intent.getStringExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME));
        assertEquals("com.example.MainActivity", intent.getStringExtra(ActivityInterceptor.EXTRA_CLASS_NAME));
        assertEquals(10, intent.getIntExtra(ActivityInterceptor.EXTRA_USER_HANDLE, -1));
        assertTrue(intent.getBooleanExtra(ActivityInterceptor.EXTRA_ROOT, false));
        assertEquals(Intent.ACTION_VIEW, intent.getStringExtra(ActivityInterceptor.EXTRA_ACTION));
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP,
                intent.getIntExtra(ActivityInterceptor.EXTRA_INTENT_FLAGS, 0));
        Bundle targetExtras = intent.getBundleExtra(ActivityInterceptor.EXTRA_INTENT_EXTRAS);
        assertNotNull(targetExtras);
        assertEquals("42", targetExtras.getString("answer"));
    }

    @Test
    public void formatLaunchResult_includesRouteAndRawCode() {
        assertEquals("privileged component (0)",
                ActivityInterceptor.formatLaunchResult("privileged component", 0));
        assertEquals("default (-1)", ActivityInterceptor.formatLaunchResult(null, -1));
    }

    @Test
    public void getPastedRootHeaderValueHandlesMalformedLines() {
        assertNull(ActivityInterceptor.getPastedRootHeaderValue("ROOT"));
        assertNull(ActivityInterceptor.getPastedRootHeaderValue("ACTION\tandroid.intent.action.VIEW"));
        assertTrue(ActivityInterceptor.getPastedRootHeaderValue("ROOT\ttrue"));
        assertFalse(ActivityInterceptor.getPastedRootHeaderValue("ROOT\t"));
    }

    @Test
    public void getPastedUserHeaderValueHandlesMalformedLines() {
        assertNull(ActivityInterceptor.getPastedUserHeaderValue("USER"));
        assertNull(ActivityInterceptor.getPastedUserHeaderValue("USER\tbad"));
        assertNull(ActivityInterceptor.getPastedUserHeaderValue("ACTION\tandroid.intent.action.VIEW"));
        assertEquals(Integer.valueOf(10), ActivityInterceptor.getPastedUserHeaderValue("USER\t10"));
        assertEquals(Integer.valueOf(16), ActivityInterceptor.getPastedUserHeaderValue("USER\t0x10"));
    }
}
