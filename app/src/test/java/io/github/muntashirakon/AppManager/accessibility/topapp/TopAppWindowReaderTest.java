// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAccessibilityService;
import org.robolectric.shadows.ShadowAccessibilityWindowInfo;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class TopAppWindowReaderTest {
    public static class StubService extends AccessibilityService {
        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
        }

        @Override
        public void onInterrupt() {
        }
    }

    @NonNull
    private static AccessibilityWindowInfo window(int type, int layer, @Nullable String packageName) {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo shadow = Shadow.extract(window);
        shadow.setType(type);
        shadow.setLayer(layer);
        if (packageName != null) {
            AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
            root.setPackageName(packageName);
            shadow.setRoot(root);
        }
        return window;
    }

    @NonNull
    private static List<TopAppTracker.Window> read(@NonNull AccessibilityWindowInfo... windows) {
        StubService service = Robolectric.buildService(StubService.class).create().get();
        ShadowAccessibilityService shadow = Shadow.extract(service);
        shadow.setWindows(Arrays.asList(windows));
        return TopAppWindowReader.read(service);
    }

    @Test
    public void windowsComeBackTopmostFirst() {
        List<TopAppTracker.Window> windows = read(
                window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, "com.example.app"),
                window(AccessibilityWindowInfo.TYPE_SYSTEM, 5, "com.example.ad"),
                window(AccessibilityWindowInfo.TYPE_SYSTEM, 3, "com.example.edge"));

        assertEquals(3, windows.size());
        assertEquals("com.example.ad", windows.get(0).packageName);
        assertEquals("com.example.edge", windows.get(1).packageName);
        assertEquals("com.example.app", windows.get(2).packageName);
    }

    @Test
    public void anOverlayIsToldApartFromAnApp() {
        List<TopAppTracker.Window> windows = read(
                window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, "com.example.app"),
                window(AccessibilityWindowInfo.TYPE_SYSTEM, 2, "com.example.ad"));

        assertFalse(windows.get(0).application);
        assertTrue(windows.get(1).application);
    }

    @Test
    public void theKeyboardAndOtherHelpersAreLeftOut() {
        List<TopAppTracker.Window> windows = read(
                window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, 9, "com.example.keyboard"),
                window(AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, 8, "com.example.helper"),
                window(AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER, 7, "com.example.divider"),
                window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, "com.example.app"));

        assertEquals(1, windows.size());
        assertEquals("com.example.app", windows.get(0).packageName);
    }

    @Test
    public void aWindowWithNoReadableRootIsSkipped() {
        List<TopAppTracker.Window> windows = read(
                window(AccessibilityWindowInfo.TYPE_SYSTEM, 2, null),
                window(AccessibilityWindowInfo.TYPE_APPLICATION, 1, "com.example.app"));

        assertEquals(1, windows.size());
        assertEquals("com.example.app", windows.get(0).packageName);
    }
}
