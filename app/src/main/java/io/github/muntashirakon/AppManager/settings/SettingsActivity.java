// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.WindowWidthSizeClass;
import io.github.muntashirakon.AppManager.self.SelfUriManager;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String TAG = SettingsActivity.class.getSimpleName();

    private static final String SAVED_KEYS = "saved_keys";

    @NonNull
    public static Intent getSettingsIntent(@NonNull Context context, @Nullable String... paths) {
        Intent intent = new Intent(context, SettingsActivity.class);
        if (paths != null) {
            intent.setData(getSettingUri(paths));
        }
        return intent;
    }

    @NonNull
    private static Uri getSettingUri(@NonNull String... pathSegments) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SelfUriManager.APP_MANAGER_SCHEME)
                .authority(SelfUriManager.SETTINGS_HOST);
        for (String pathSegment : pathSegments) {
            builder.appendPath(pathSegment);
        }
        return builder.build();
    }

    public LinearProgressIndicator progressIndicator;
    @Nullable
    public SearchView searchView;
    @NonNull
    private final SettingsKeyPath mSavedKeys = new SettingsKeyPath();
    /**
     * While authentication is pending: the pages to open once it finishes, from a link that came
     * meanwhile or the path a recreate brought. A recreate before then has to save these.
     */
    @Nullable
    private ArrayList<String> mPathBeforeAuthentication;
    private int mLevel = 0;
    /** The page that fills an empty right pane, which the user never picked, so it isn't saved. */
    @Nullable
    private String mUnsavedDefaultKey;
    private boolean mDualPaneMode;
    @Nullable
    private MaterialToolbar mSecondaryToolbar;
    @Nullable
    private View mMainPane;
    @Nullable
    private RecyclerView mSearchResultsView;
    @Nullable
    private View mSearchEmptyView;
    @Nullable
    private SettingsSearchAdapter mSearchAdapter;
    private LinearProgressIndicator mSearchProgressIndicator;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        mDualPaneMode = shouldUseDualPane(getResources().getConfiguration().screenWidthDp);
        setContentView(mDualPaneMode ? R.layout.activity_settings_dual_pane : R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        mSecondaryToolbar = findViewById(R.id.toolbar2);
        FragmentContainerView secondaryContainer = findViewById(R.id.secondary_layout);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();
        mSearchProgressIndicator = findViewById(R.id.settings_search_progress);
        if (mSearchProgressIndicator == null) {
            mSearchProgressIndicator = progressIndicator;
        } else {
            mSearchProgressIndicator.setVisibilityAfterHide(View.GONE);
            mSearchProgressIndicator.hide();
        }
        mMainPane = findViewById(R.id.main_layout);
        mSearchResultsView = findViewById(R.id.settings_search_results);
        mSearchEmptyView = findViewById(R.id.settings_search_empty);
        if (mSearchResultsView != null) {
            mSearchAdapter = new SettingsSearchAdapter();
            mSearchAdapter.setListener(this::navigateToSearchResult);
            mSearchResultsView.setLayoutManager(new LinearLayoutManager(this));
            mSearchResultsView.setAdapter(mSearchAdapter);
            mSearchResultsView.setHasFixedSize(true);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupSearchView(actionBar, new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    onQueryTextChange(query);
                    if (searchView != null) {
                        searchView.clearFocus();
                    }
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    applySettingsSearch(newText);
                    return true;
                }
            });
            searchView.setQueryHint(getString(R.string.search_settings));
        }
        // Apply necessary padding: ignore start
        if (mSecondaryToolbar != null) {
            UiUtils.applyWindowInsetsAsPadding(mSecondaryToolbar, true, false, false, true);
        }
        if (secondaryContainer != null) {
            UiUtils.applyWindowInsetsAsPadding(secondaryContainer, false, true, false, true);
        }

        if (savedInstanceState != null) {
            clearBackStack();
        }
        mSavedKeys.restore(mPathBeforeAuthentication != null ? mPathBeforeAuthentication
                : getPathToOpen(savedInstanceState));
        mPathBeforeAuthentication = null;

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            // Pages count, dialogs don't: pages show theirs on this manager too, and the back
            // stack never undoes a dialog's step, so the next page saved one level too deep
            if (!(fragment instanceof MainPreferences) && !(fragment instanceof DialogFragment)) {
                ++mLevel;
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            mLevel = getSupportFragmentManager().getBackStackEntryCount();
            Log.d(TAG, "Backstack changed. Level: %d", mLevel);
            mSavedKeys.onBackStackChanged(mLevel);
        });

        String defaultPref = getKey(mLevel);
        if (defaultPref == null && mDualPaneMode) {
            defaultPref = mUnsavedDefaultKey = "custom_locale";
        }
        FragmentTransaction initialTransaction = MotionUtils.maybeSetDefaultFragmentAnimations(this,
                getSupportFragmentManager().beginTransaction());
        initialTransaction.replace(R.id.main_layout, MainPreferences.getInstance(defaultPref, mDualPaneMode))
                .commit();
    }

    static boolean shouldUseDualPane(int screenWidthDp) {
        return WindowWidthSizeClass.requiresTwoPane(screenWidthDp);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        List<String> link = getLinkPath(intent);
        if (link != null) {
            if (isAuthenticationPending()) {
                // Opened once authentication finishes. onAuthenticated reads the first intent.
                mPathBeforeAuthentication = new ArrayList<>(link);
                return;
            }
            // Clear old items. Popping the back stack drops saved keys, so the link goes in after.
            clearBackStack();
            mSavedKeys.restore(link);
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout);
            if (fragment instanceof MainPreferences) {
                ((MainPreferences) fragment).setPrefKey(getKey(mLevel = 0));
                Log.d(TAG, "Selected pref: %s", fragment.getClass().getName());
            }
        }
    }

    @Override
    public void setTitle(int titleId) {
        if (mDualPaneMode) {
            Objects.requireNonNull(mSecondaryToolbar).setTitle(titleId);
        } else super.setTitle(titleId);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        if (pref.getFragment() == null) {
            return false;
        }
        boolean fromList = caller instanceof MainPreferences;
        if (fromList) {
            // A pick in the list starts a new path. Dual pane has no back stack to bring the
            // level back down, so without this every pick there was saved one level deeper
            mLevel = 0;
        }
        boolean shownByDefault = fromList && Objects.equals(pref.getKey(), mUnsavedDefaultKey);
        mUnsavedDefaultKey = null;
        // Opening a saved or linked path, the page gets the next key on it. A tap by the user starts
        // a new path, or tapping a parent page again went straight back into its child
        boolean followingPath = caller instanceof PreferenceFragment
                && ((PreferenceFragment) caller).isOpeningPrefKey();
        FragmentManager fragmentManager = getSupportFragmentManager();
        Bundle args = pref.getExtras();
        Fragment fragment = fragmentManager.getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        if (fragment instanceof PreferenceFragment) {
            // Inject dual pane mode
            args.putBoolean(PreferenceFragment.PREF_SECONDARY, mDualPaneMode);
            // Inject subKey to the arguments
            String subKey = followingPath ? getKey(mLevel + 1) : null;
            if (subKey != null && Objects.equals(pref.getKey(), getKey(mLevel))) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
            }
        }
        // Every page saves its key, plain fragments too (Mode of operation, the language list),
        // or a recreate reopens the page before it
        if (!shownByDefault) {
            Log.d(TAG, "Save level: %d, Key: %s", mLevel, pref.getKey());
            if (followingPath) {
                mSavedKeys.save(mLevel, pref.getKey());
            } else {
                mSavedKeys.pick(mLevel, pref.getKey());
            }
        }
        fragment.setArguments(args);
        // The line below is kept because this is how it is handled in AndroidX library
        fragment.setTargetFragment(caller, 0);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (!mDualPaneMode) {
            MotionUtils.maybeSetDefaultFragmentAnimations(this, transaction)
                    .addToBackStack(null);
        }
        transaction
                .replace(mDualPaneMode ? R.id.secondary_layout : R.id.main_layout, fragment)
                .commit();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (!isAuthenticationPending()) {
            outState.putStringArrayList(SAVED_KEYS, mSavedKeys.toList());
        } else {
            // Nothing is open yet. Saving the empty path lost the link when the lock screen or
            // the startup dialog was rotated, so hand on the pages still to open.
            List<String> path = mPathBeforeAuthentication != null ? mPathBeforeAuthentication
                    : getLinkPath(getIntent());
            if (path != null) {
                outState.putStringArrayList(SAVED_KEYS, new ArrayList<>(path));
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (isAuthenticationPending()) {
            // Kept for onSaveInstanceState, in case it's recreated again before authentication
            List<String> path = getPathToOpen(savedInstanceState);
            mPathBeforeAuthentication = path != null ? new ArrayList<>(path) : null;
        }
    }

    /**
     * The pages to open: the path a recreate saved, else the link this activity was opened with.
     * A saved path wins even when it's shorter than the link, as the user went back from those.
     */
    @Nullable
    private List<String> getPathToOpen(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVED_KEYS)) {
            return savedInstanceState.getStringArrayList(SAVED_KEYS);
        }
        return getLinkPath(getIntent());
    }

    @Nullable
    private String getKey(int level) {
        return mSavedKeys.get(level);
    }

    /** The pages a settings link opens, or null when the intent isn't a settings link. */
    @Nullable
    private static List<String> getLinkPath(@NonNull Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && SelfUriManager.APP_MANAGER_SCHEME.equals(uri.getScheme())
                && SelfUriManager.SETTINGS_HOST.equals(uri.getHost()) && uri.getPath() != null) {
            return uri.getPathSegments();
        }
        return null;
    }

    /**
     * Filter the {@link SettingsSearchIndex} against {@code query} and toggle the
     * results overlay. Empty query restores the normal main pane.
     */
    private void applySettingsSearch(@Nullable String query) {
        if (mSearchResultsView == null || mSearchAdapter == null || mSearchEmptyView == null
                || mMainPane == null) {
            return;
        }
        final String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            mSearchResultsView.setVisibility(View.GONE);
            mSearchEmptyView.setVisibility(View.GONE);
            mMainPane.setVisibility(View.VISIBLE);
            mSearchAdapter.submit(Collections.emptyList());
            mSearchProgressIndicator.hide();
            return;
        }
        mMainPane.setVisibility(View.GONE);
        mSearchEmptyView.setVisibility(View.GONE);
        mSearchProgressIndicator.show();
        Context appContext = getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            List<SettingsSearchIndex.Entry> matches = SettingsSearchIndex.get(appContext).search(text);
            ThreadUtils.postOnMainThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (searchView == null) return;
                CharSequence current = searchView.getQuery();
                if (current == null || !text.equals(current.toString().trim())) {
                    // Result is stale for the current query
                    return;
                }
                mSearchProgressIndicator.hide();
                mSearchAdapter.submit(matches);
                boolean hasMatches = !matches.isEmpty();
                mSearchResultsView.setVisibility(hasMatches ? View.VISIBLE : View.GONE);
                mSearchEmptyView.setVisibility(hasMatches ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void navigateToSearchResult(@NonNull SettingsSearchIndex.Entry entry) {
        // Clear search state so onNewIntent navigation reveals the target fragment
        if (searchView != null) {
            searchView.setQuery("", false);
            searchView.clearFocus();
        }
        applySettingsSearch(null);
        String[] keys = entry.targetKey == null
                ? new String[] { entry.parentKey }
                : new String[] { entry.parentKey, entry.targetKey };
        onNewIntent(getSettingsIntent(this, keys));
    }
}
