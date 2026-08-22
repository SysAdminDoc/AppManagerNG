// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.util.AdapterUtils;

@RunWith(RobolectricTestRunner.class)
public class AdapterUtilsTest {
    @Test
    public void identicalMapsDispatchNoUpdates() {
        SimpleArrayMap<String, String> base = mapOf("alpha", "one", "beta", "two");
        SimpleArrayMap<String, String> next = mapOf("alpha", "one", "beta", "two");
        RecordingAdapter adapter = new RecordingAdapter();
        RecordingObserver observer = new RecordingObserver();
        adapter.registerAdapterDataObserver(observer);

        AdapterUtils.notifyDataSetChanged(adapter, base, next);

        assertEquals(0, observer.updateCount);
        assertMapEquals(next, base);
        assertNull(observer.payload);
    }

    @Test
    public void changedValueDispatchesOnePayloadChange() {
        SimpleArrayMap<String, String> base = mapOf("alpha", "one", "beta", "two");
        SimpleArrayMap<String, String> next = mapOf("alpha", "updated", "beta", "two");
        RecordingAdapter adapter = new RecordingAdapter();
        RecordingObserver observer = new RecordingObserver();
        adapter.registerAdapterDataObserver(observer);

        AdapterUtils.notifyDataSetChanged(adapter, base, next);

        assertEquals(1, observer.updateCount);
        assertEquals(AdapterUtils.STUB, observer.payload);
        assertMapEquals(next, base);
    }

    private static SimpleArrayMap<String, String> mapOf(String firstKey, String firstValue,
                                                         String secondKey, String secondValue) {
        SimpleArrayMap<String, String> map = new SimpleArrayMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }

    private static void assertMapEquals(SimpleArrayMap<String, String> expected,
                                        SimpleArrayMap<String, String> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); ++i) {
            assertEquals(expected.keyAt(i), actual.keyAt(i));
            assertEquals(expected.valueAt(i), actual.valueAt(i));
        }
    }

    private static final class RecordingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            throw new AssertionError("View creation is not part of this test");
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            throw new AssertionError("View binding is not part of this test");
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private static final class RecordingObserver extends RecyclerView.AdapterDataObserver {
        int updateCount;
        Object payload;

        @Override
        public void onChanged() {
            updateCount++;
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            updateCount += itemCount;
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            this.payload = payload;
            super.onItemRangeChanged(positionStart, itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            updateCount += itemCount;
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            updateCount += itemCount;
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            updateCount += itemCount;
        }
    }
}
