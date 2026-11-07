// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TopAppWindowReader {
    private TopAppWindowReader() {
    }

    /**
     * Lists the windows an app could have put up, topmost first. Reading a window's root is a call into the process
     * that owns it and can wait on a hung app for seconds, so this never runs on the main thread.
     */
    @WorkerThread
    @NonNull
    @SuppressWarnings("deprecation")
    public static List<TopAppTracker.Window> read(@NonNull AccessibilityService service) {
        List<AccessibilityWindowInfo> windows = new ArrayList<>(service.getWindows());
        Collections.sort(windows, (a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
        List<TopAppTracker.Window> out = new ArrayList<>(windows.size());
        for (AccessibilityWindowInfo window : windows) {
            int type = window.getType();
            // An app's overlay shows up as a system window. The keyboard and dividers are never what's being hunted.
            if (type != AccessibilityWindowInfo.TYPE_APPLICATION && type != AccessibilityWindowInfo.TYPE_SYSTEM) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            CharSequence packageName = root.getPackageName();
            root.recycle();
            if (packageName != null) {
                out.add(new TopAppTracker.Window(packageName.toString(),
                        type == AccessibilityWindowInfo.TYPE_APPLICATION));
            }
        }
        return out;
    }
}
