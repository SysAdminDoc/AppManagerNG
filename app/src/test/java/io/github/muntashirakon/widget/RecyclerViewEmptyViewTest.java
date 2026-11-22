// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * An App Details tab showed "No requested permissions" (or its tab's empty message) for a moment every time it
 * opened, because the list is empty until the first load lands and the empty view followed the item count alone.
 * While a tab is loading, the empty view has to stay hidden, and it comes back once loading ends with no rows.
 */
@RunWith(RobolectricTestRunner.class)
public class RecyclerViewEmptyViewTest {
    private RecyclerView mList;
    private View mEmpty;
    private Rows mAdapter;

    @Before
    public void setUp() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme_V2);
        mList = new RecyclerView(context);
        mEmpty = new View(context);
        mAdapter = new Rows();
        mList.setAdapter(mAdapter);
        mList.setEmptyView(mEmpty);
    }

    @Test
    public void anEmptyListShowsTheEmptyView() {
        assertEquals(View.VISIBLE, mEmpty.getVisibility());
    }

    @Test
    public void theEmptyViewStaysHiddenWhileLoading() {
        mList.setEmptyViewSuppressed(true);
        assertEquals(View.GONE, mEmpty.getVisibility());
        // An adapter change during the load (a search reset, a sort) mustn't bring it back either.
        mAdapter.notifyDataSetChanged();
        assertEquals(View.GONE, mEmpty.getVisibility());
    }

    @Test
    public void aLoadThatEndsWithNoRowsShowsTheEmptyView() {
        mList.setEmptyViewSuppressed(true);
        mList.setEmptyViewSuppressed(false);
        assertEquals(View.VISIBLE, mEmpty.getVisibility());
    }

    @Test
    public void aLoadThatEndsWithRowsShowsTheList() {
        mList.setEmptyViewSuppressed(true);
        mAdapter.set(3);
        mList.setEmptyViewSuppressed(false);
        assertEquals(View.GONE, mEmpty.getVisibility());
        assertEquals(View.VISIBLE, mList.getVisibility());
    }

    private static final class Rows extends androidx.recyclerview.widget.RecyclerView.Adapter<Rows.Holder> {
        private final List<Integer> mItems = new ArrayList<>();

        void set(int count) {
            mItems.clear();
            for (int i = 0; i < count; ++i) {
                mItems.add(i);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(new View(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static final class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            Holder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
