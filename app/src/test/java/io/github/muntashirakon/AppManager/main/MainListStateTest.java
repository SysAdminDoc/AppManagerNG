// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;

import io.github.muntashirakon.AppManager.R;

/**
 * Upstream #2006: a frozen app showed only as a tinted UID, and a suspended one as a "°" after the
 * app type, while a screen reader heard "Installed" for both. These bind the real adapter into the
 * real row.
 */
@RunWith(RobolectricTestRunner.class)
public class MainListStateTest {
    private static final String DOT = " " + (char) 0x00B7 + " ";

    @Test
    public void aFrozenAppSaysSoAtTheFrontOfItsLine() {
        ApplicationItem item = item();
        item.isDisabled = true;

        FrameLayout row = bind(item);

        assertEquals("Frozen" + DOT + "UID 10123" + DOT + "v4.7.3" + DOT + "User" + DOT + "SDK 35", metadata(row));
        assertTrue(description(row), description(row).startsWith("Example, Frozen, package com.example.app, "));
    }

    @Test
    public void aSuspendedAppSaysSuspendedInsteadOfAMark() {
        ApplicationItem item = item();
        item.flags |= ApplicationInfo.FLAG_SUSPENDED;
        // Android counts a suspended app as frozen too
        item.isDisabled = true;
        generate(item);

        FrameLayout row = bind(item);

        assertEquals("Suspended" + DOT + "UID 10123" + DOT + "v4.7.3" + DOT + "User" + DOT + "SDK 35", metadata(row));
        assertFalse(metadata(row), metadata(row).contains("°"));
        assertTrue(description(row), description(row).startsWith("Example, Suspended, package "));
    }

    @Test
    @Config(sdk = 23)
    public void theSameBitOnAnOlderAndroidIsNotASuspension() {
        // Suspending came with Android 7. Before it the bit meant no suspension (on Android 5 it
        // marked a privileged system app, and Robolectric here starts at Android 6).
        ApplicationItem item = item();
        item.flags |= ApplicationInfo.FLAG_SUSPENDED;
        generate(item);

        FrameLayout row = bind(item);

        assertTrue(metadata(row), metadata(row).startsWith("UID 10123" + DOT));
        assertFalse(metadata(row), metadata(row).contains("°"));
        assertTrue(description(row), description(row).startsWith("Example, Installed, "));
    }

    @Test
    public void anAppThatRunsNormallyNamesNoState() {
        FrameLayout row = bind(item());

        assertTrue(metadata(row), metadata(row).startsWith("UID 10123" + DOT));
        assertTrue(description(row), description(row).startsWith("Example, Installed, "));
    }

    @Test
    public void anAppThatIsNotInstalledIsNotCalledFrozen() {
        // The loader marks a row with no installed copy as not enabled
        ApplicationItem item = item();
        item.isInstalled = false;
        item.isDisabled = true;
        item.userIds = new int[0];

        FrameLayout row = bind(item);

        assertFalse(metadata(row), metadata(row).contains("Frozen"));
        assertTrue(description(row), description(row).startsWith("Example, Not installed, "));
    }

    @Test
    public void aRowWithNoVersionDoesNotReadOutNull() {
        // An archived app or a backup-only row has no version; TalkBack read "null" there
        ApplicationItem item = item();
        item.isInstalled = false;
        item.isDisabled = true;
        item.userIds = new int[0];
        item.versionName = null;
        item.versionTag = null;

        FrameLayout row = bind(item);

        assertFalse(description(row), description(row).contains("null"));
        assertTrue(description(row), description(row).startsWith(
                "Example, Not installed, package com.example.app, -, SDK 35. "));
    }

    @Test
    public void theWordCarriesTheStateAndTheTintOnlyBacksItUp() {
        Context context = RuntimeEnvironment.getApplication();
        ApplicationItem item = item();
        item.isDisabled = true;
        // How the loader records this user's frozen copy, which also tints the UID
        item.disabledUserIds = new int[]{0};

        Spanned line = (Spanned) ((TextView) bind(item).findViewById(R.id.metadata)).getText();

        ForegroundColorSpan[] spans = line.getSpans(0, line.length(), ForegroundColorSpan.class);
        assertEquals(2, spans.length);
        int warning = ContextCompat.getColor(context, R.color.premium_warning_content);
        for (ForegroundColorSpan span : spans) {
            assertEquals(warning, span.getForegroundColor());
            String tinted = line.subSequence(line.getSpanStart(span), line.getSpanEnd(span)).toString();
            assertTrue(tinted, tinted.equals("Frozen") || tinted.equals("UID 10123"));
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(fontScale = 2f, qualifiers = "w320dp")
    public void atTheLargestFontTheStateIsWholeAndTheRowKeepsItsHeight() {
        ApplicationItem frozen = item();
        frozen.isDisabled = true;
        FrameLayout frozenRow = layOut(bind(frozen), 320);
        FrameLayout normalRow = layOut(bind(item()), 320);

        TextView metadata = frozenRow.findViewById(R.id.metadata);
        Layout layout = metadata.getLayout();
        TextView normalMetadata = normalRow.findViewById(R.id.metadata);
        String lines = "frozen " + layout.getLineCount() + " lines, ordinary " + normalMetadata.getLayout().getLineCount();
        // The line keeps its cap, and the ellipsis cuts from the end, so the state at the front is never cut
        assertTrue(lines, layout.getLineCount() <= metadata.getMaxLines());
        assertTrue(lines, layout.getLineEnd(0) >= "Frozen".length());
        int ellipsis = layout.getEllipsisCount(0) == 0 ? layout.getLineEnd(0) : layout.getEllipsisStart(0);
        assertTrue("cut at " + ellipsis, ellipsis >= "Frozen".length());
        // No pill and no extra line: the row is as tall as an ordinary one, and the package name as wide
        assertEquals(lines, normalRow.getHeight(), frozenRow.getHeight());
        TextView frozenPackage = frozenRow.findViewById(R.id.packageName);
        TextView normalPackage = normalRow.findViewById(R.id.packageName);
        assertEquals(normalPackage.getWidth(), frozenPackage.getWidth());
        assertEquals(normalPackage.getLayout().getLineCount(), frozenPackage.getLayout().getLineCount());
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp")
    public void onACommonPhoneTheStateAddsNoLineToTheRow() {
        // 360 dp is the most common phone width. On the narrowest, 320 dp, the line can wrap once,
        // within the two lines it has for long translations.
        ApplicationItem frozen = item();
        frozen.isDisabled = true;
        FrameLayout frozenRow = layOut(bind(frozen), 360);
        FrameLayout normalRow = layOut(bind(item()), 360);

        TextView metadata = frozenRow.findViewById(R.id.metadata);
        assertEquals(1, metadata.getLayout().getLineCount());
        assertEquals(0, metadata.getLayout().getEllipsisCount(0));
        assertEquals(normalRow.getHeight(), frozenRow.getHeight());
    }

    @NonNull
    private static String metadata(@NonNull FrameLayout row) {
        return ((TextView) row.findViewById(R.id.metadata)).getText().toString();
    }

    @NonNull
    private static String description(@NonNull FrameLayout row) {
        return row.getContentDescription().toString();
    }

    @NonNull
    private static FrameLayout layOut(@NonNull FrameLayout row, int widthDp) {
        Context context = row.getContext();
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        return row;
    }

    @NonNull
    private static FrameLayout bind(@NonNull ApplicationItem item) {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
        activity.setTheme(R.style.AppTheme_V2);
        activity.viewModel = new MainViewModel(activity.getApplication());
        MainRecyclerAdapter adapter = new MainRecyclerAdapter(activity);
        adapter.setDefaultList(Collections.singletonList(item));
        MainRecyclerAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(activity), 0);
        adapter.onBindViewHolder(holder, 0);
        return (FrameLayout) holder.itemView;
    }

    /**
     * What the loader works out from the flags, with no runtime checks.
     */
    private static void generate(@NonNull ApplicationItem item) {
        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                return -1;
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                return false;
            }

            @Override
            public boolean checkCrossUserPermission(int userId) {
                return false;
            }
        });
    }

    @NonNull
    private static ApplicationItem item() {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.app";
        item.label = "Example";
        item.uid = 10123;
        item.userIds = new int[]{0};
        // An ordinary app, so the flags add no marks of their own
        item.flags = ApplicationInfo.FLAG_HAS_CODE | ApplicationInfo.FLAG_HARDWARE_ACCELERATED;
        item.versionName = "v4.7.3";
        item.versionTag = "v4.7.3";
        item.targetSdk = 35;
        item.sdkString = "SDK 35";
        return item;
    }
}
