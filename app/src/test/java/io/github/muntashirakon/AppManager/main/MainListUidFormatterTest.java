// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import io.github.muntashirakon.AppManager.R;

/**
 * Issue 19: the compact row dropped the UID, which automation and sandbox work read from the list.
 * These bind the real adapter into the real row, so the tests see what the row shows.
 */
@RunWith(RobolectricTestRunner.class)
public class MainListUidFormatterTest {
    private static final String DOT = " " + (char) 0x00B7 + " ";

    @Test
    public void aSingleUserRowLeadsItsMetadataLineWithTheUid() {
        ApplicationItem item = item(10123, new int[]{0});

        TextView metadata = bind(item).findViewById(R.id.metadata);

        assertEquals("UID 10123" + DOT + "v4.7.3" + DOT + metadataAppType(metadata) + DOT + "SDK 35",
                metadata.getText().toString());
    }

    @Test
    public void aRowOfSeveralUsersShowsTheUserCountAndAppId() {
        ApplicationItem item = item(1010123, new int[]{0, 10});

        TextView metadata = bind(item).findViewById(R.id.metadata);

        assertTrue(metadata.getText().toString().startsWith("UID 2+10123" + DOT));
    }

    @Test
    public void theTokenComesFromTheItemNotTheStringWorkedOutBeforeAnotherUserJoinedTheRow() {
        ApplicationItem item = item(1010123, new int[]{0, 10});
        item.uidOrAppIds = "10123";

        assertEquals("2+10123", MainListUidFormatter.valueFor(item));
    }

    @Test
    public void anAppThatIsNotInstalledShowsNoUid() {
        ApplicationItem backupOnly = item(10123, new int[]{0});
        backupOnly.isInstalled = false;
        ApplicationItem noUser = item(10123, new int[0]);

        for (ApplicationItem item : new ApplicationItem[]{backupOnly, noUser}) {
            FrameLayout row = bind(item);
            String metadata = ((TextView) row.findViewById(R.id.metadata)).getText().toString();
            assertTrue(metadata, metadata.startsWith("v4.7.3" + DOT));
            assertFalse(metadata, metadata.contains("UID"));
            assertFalse(rowDescription(row), rowDescription(row).contains("UID"));
        }
    }

    @Test
    public void aSharedUidIsTintedAndOnlyTheTokenIs() {
        ApplicationItem item = item(1000, new int[]{0});
        item.sharedUserId = "android.uid.system";
        Context context = RuntimeEnvironment.getApplication();

        Spanned line = (Spanned) ((TextView) bind(item).findViewById(R.id.metadata)).getText();

        ForegroundColorSpan[] spans = line.getSpans(0, line.length(), ForegroundColorSpan.class);
        assertEquals(1, spans.length);
        assertEquals(ContextCompat.getColor(context, R.color.premium_warning_content), spans[0].getForegroundColor());
        assertEquals(0, line.getSpanStart(spans[0]));
        assertEquals("UID 1000".length(), line.getSpanEnd(spans[0]));
    }

    @Test
    public void aPackageWhoseStateDiffersBetweenUsersIsTinted() {
        ApplicationItem item = item(1010123, new int[]{0, 10});
        item.disabledUserIds = new int[]{10};

        CharSequence line = ((TextView) bind(item).findViewById(R.id.metadata)).getText();

        assertEquals(1, ((Spanned) line).getSpans(0, line.length(), ForegroundColorSpan.class).length);
    }

    @Test
    public void anOrdinaryRowIsNotTinted() {
        CharSequence line = ((TextView) bind(item(10123, new int[]{0})).findViewById(R.id.metadata)).getText();

        assertEquals(0, ((Spanned) line).getSpans(0, line.length(), ForegroundColorSpan.class).length);
    }

    @Test
    public void aScreenReaderHearsTheUidAndWhatItMeans() {
        ApplicationItem single = item(10123, new int[]{0});
        assertTrue(rowDescription(bind(single)).endsWith(" UID 10123."));

        ApplicationItem shared = item(1000, new int[]{0});
        shared.sharedUserId = "android.uid.system";
        assertTrue(rowDescription(bind(shared)).endsWith(" UID 1000. Shared user android.uid.system."));

        ApplicationItem several = item(1010123, new int[]{0, 10});
        assertTrue(rowDescription(bind(several)).endsWith(" App ID 10123, installed for 2 users."));
    }

    @Test
    public void theLineStaysOneLineSoANarrowRowOrBigFontCannotGrowTheRow() {
        TextView metadata = bind(item(10123, new int[]{0})).findViewById(R.id.metadata);

        assertEquals(1, metadata.getMaxLines());
        // Cut from the end, the UID at the front is the part that stays
        assertEquals(TextUtils.TruncateAt.END, metadata.getEllipsize());
        assertEquals(0, metadata.getText().toString().indexOf("UID 10123"));
    }

    @NonNull
    private static String metadataAppType(@NonNull TextView metadata) {
        return metadata.getText().toString().split(DOT)[2];
    }

    @NonNull
    private static String rowDescription(@NonNull FrameLayout row) {
        return row.getContentDescription().toString();
    }

    /**
     * The row after the real adapter has bound the item into it.
     */
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

    @NonNull
    private static ApplicationItem item(int uid, @NonNull int[] userIds) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.app";
        item.label = "Example";
        item.uid = uid;
        item.userIds = userIds;
        item.versionTag = "v4.7.3";
        item.sdkString = "SDK 35";
        return item;
    }
}
