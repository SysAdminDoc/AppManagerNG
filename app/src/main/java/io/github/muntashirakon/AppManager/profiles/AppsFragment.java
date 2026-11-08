// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, MenuProvider,
        AdvancedSearchView.OnQueryTextListener {
    public static class AppsFragmentItem {
        @NonNull
        public final String packageName;
        @Nullable
        public CharSequence label;
        @Nullable
        public ApplicationInfo applicationInfo;
        @Nullable
        public IFilterableAppInfo filterableAppInfo;
        public long firstInstallTime;
        public long lastUpdateTime;

        public AppsFragmentItem(@NonNull String packageName) {
            this.packageName = packageName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof String) {
                return Objects.equals(packageName, o);
            }
            if (!(o instanceof AppsFragmentItem)) return false;
            AppsFragmentItem that = (AppsFragmentItem) o;
            return Objects.equals(packageName, that.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName);
        }
    }

    private AppsBaseProfileActivity mActivity;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearProgressIndicator mProgressIndicator;
    private AppsProfileViewModel mModel;
    private TextView mEmptyTitle;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (AppsBaseProfileActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(mActivity));
        View emptyView = view.findViewById(android.R.id.empty);
        mEmptyTitle = emptyView.findViewById(R.id.empty_state_title);
        mEmptyTitle.setText(R.string.no_apps);
        emptyView.findViewById(R.id.empty_state_summary).setVisibility(View.GONE);
        emptyView.findViewById(R.id.empty_state_action).setVisibility(View.GONE);
        recyclerView.setEmptyView(emptyView);
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        view.findViewById(R.id.alert_text).setVisibility(View.GONE);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        mModel = mActivity.model;
        AppsRecyclerAdapter adapter = new AppsRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        mModel.getDisplayedPackages().observe(getViewLifecycleOwner(), packages -> {
            mProgressIndicator.hide();
            mEmptyTitle.setText(TextUtils.isEmpty(mModel.getAppsQuery()) ? R.string.no_apps
                    : R.string.no_matching_package_found);
            adapter.setList(packages);
        });
        // Only while this tab is the one showing, so the other tabs keep their own toolbar
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_apps_profile_actions, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        AdvancedSearchView searchView = (AdvancedSearchView) searchItem.getActionView();
        if (searchView == null) {
            return;
        }
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                mModel.setAppsQuery(null, AdvancedSearchView.SEARCH_TYPE_CONTAINS);
                return true;
            }
        });
        String query = mModel.getAppsQuery();
        if (!TextUtils.isEmpty(query)) {
            // Back on this tab or after a rotation: show the search that's still filtering the list
            searchItem.expandActionView();
            searchView.setQuery(query, false);
        }
        searchView.setOnQueryTextListener(this);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_sort) {
            ProfileAppsListOptions listOptions = new ProfileAppsListOptions();
            listOptions.setListOptionActions(mModel);
            listOptions.show(getChildFragmentManager(), ProfileAppsListOptions.TAG);
            return true;
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(@NonNull String newText, @AdvancedSearchView.SearchType int type) {
        mModel.setAppsQuery(newText, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(@NonNull String query, @AdvancedSearchView.SearchType int type) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(mModel.getPreviewTitleString());
        }
        mActivity.fab.show();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        mModel.loadPackages();
    }

    public class AppsRecyclerAdapter extends RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder> {
        final PackageManager pm;
        final ArrayList<AppsFragmentItem> packages = new ArrayList<>();
        final ImageLoader.DefaultImageDrawable defaultImage;

        private AppsRecyclerAdapter() {
            pm = mActivity.getPackageManager();
            defaultImage = new ImageLoader.DefaultImageDrawable("android_default_icon", pm.getDefaultActivityIcon());
        }

        void setList(List<AppsFragmentItem> list) {
            AdapterUtils.notifyDataSetChanged(this, packages, list);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppsFragmentItem fragmentItem = packages.get(position);
            holder.icon.setTag(fragmentItem);
            if (fragmentItem.applicationInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, fragmentItem.applicationInfo, holder.icon);
            } else if (fragmentItem.filterableAppInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, holder.icon, tag ->
                        new ImageLoader.ImageFetcherResult(tag, UIUtils.getBitmapFromDrawable(fragmentItem.filterableAppInfo.getAppIcon()), true, true, defaultImage));
            } else {
                holder.icon.setImageBitmap(defaultImage.getImage());
            }
            CharSequence label = fragmentItem.label;
            holder.title.setText(label != null ? label : fragmentItem.packageName);
            if (label != null) {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(fragmentItem.packageName);
            } else {
                holder.subtitle.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> {
            });
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(mActivity, holder.itemView);
                popupMenu.setForceShowIcon(true);
                popupMenu.getMenu().add(R.string.delete).setIcon(R.drawable.ic_trash_can)
                        .setOnMenuItemClickListener(item -> {
                            mModel.deletePackage(fragmentItem.packageName);
                            return true;
                        });
                popupMenu.show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return packages.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title;
            TextView subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(android.R.id.icon);
                icon.setContentDescription(itemView.getContext().getString(R.string.icon));
                title = itemView.findViewById(android.R.id.title);
                subtitle = itemView.findViewById(android.R.id.summary);
            }
        }
    }
}
