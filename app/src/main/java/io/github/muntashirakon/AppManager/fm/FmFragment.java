// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static io.github.muntashirakon.AppManager.fm.FmTasks.FmTask.TYPE_CUT;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.core.provider.DocumentsContractCompat;
import androidx.core.util.Pair;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.fm.dialogs.FilePropertiesDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewFileDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewFolderDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.NewSymbolicLinkDialogFragment;
import io.github.muntashirakon.AppManager.fm.dialogs.RenameDialogFragment;
import io.github.muntashirakon.AppManager.oneclickops.ApkDuplicateOperations;
import io.github.muntashirakon.AppManager.oneclickops.ApkDuplicateSelector;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.FloatingActionButtonGroup;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class FmFragment extends Fragment implements MenuProvider, SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener, SpeedDialView.OnActionSelectedListener,
        MultiSelectionActionsView.OnItemSelectedListener,
        MultiSelectionView.OnSelectionModeChangeListener {
    public static final String TAG = FmFragment.class.getSimpleName();

    private static final String ARG_URI = "uri";
    public static final String ARG_OPTIONS = "opt";
    public static final String ARG_POSITION = "pos";
    private static final long SEARCH_DEBOUNCE_MILLIS = 250;
    private static final int BATCH_RENAME_PREVIEW_LIMIT = 30;

    @NonNull
    public static FmFragment getNewInstance(@NonNull FmActivity.Options options,
                                            @Nullable Integer position) {
        FmFragment fragment = new FmFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_OPTIONS, options);
        if (position != null) {
            args.putInt(ARG_POSITION, position);
        }
        fragment.setArguments(args);
        return fragment;
    }

    private FmViewModel mModel;
    @Nullable
    private RecyclerView mRecyclerView;
    private LinearLayoutCompat mEmptyView;
    private ImageView mEmptyViewIcon;
    private TextView mEmptyViewTitle;
    private TextView mEmptyViewSummary;
    private TextView mEmptyViewDetails;
    private MaterialButton mEmptyViewAction;
    @Nullable
    private FmAdapter mAdapter;
    @Nullable
    private SwipeRefreshLayout mSwipeRefresh;
    @Nullable
    private MultiSelectionView mMultiSelectionView;
    private FloatingActionButtonGroup mFabGroup;
    private FmPathListAdapter mPathListAdapter;
    private FmActivity mActivity;
    private View mSearchFilterContainer;
    private Chip mSearchFilterChip;
    @Nullable
    private SearchView mSearchView;
    @Nullable
    private MenuItem mSearchMenuItem;
    @Nullable
    private Uri mVolumeScanWarningAcceptedUri;
    @NonNull
    private String mPendingSearchQuery = "";
    private boolean mVolumeScanWarningShowing;

    @Nullable
    private FolderShortInfo mFolderShortInfo;

    private final Runnable mSearchDebounceRunnable = () -> applySearchQueryWithWarning(mPendingSearchQuery);

    private final ViewTreeObserver.OnGlobalLayoutListener mMultiSelectionViewChangeListener = () -> {
        if (mFabGroup != null && getActivity() != null) {
            int defaultMargin = UiUtils.dpToPx(requireContext(), 16);
            int newMargin;
            if (mMultiSelectionView.getVisibility() == View.VISIBLE) {
                newMargin = defaultMargin + mMultiSelectionView.getHeight();
            } else newMargin = defaultMargin;
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) mFabGroup.getLayoutParams();
            if (marginLayoutParams.bottomMargin != newMargin) {
                marginLayoutParams.bottomMargin = newMargin;
                mFabGroup.setLayoutParams(marginLayoutParams);
            }
        }
    };

    private final OnBackPressedCallback mExitSelectionBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mAdapter != null && mMultiSelectionView != null && mAdapter.isInSelectionMode()) {
                mMultiSelectionView.cancel();
                return;
            }
            setEnabled(false);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    };
    private final OnBackPressedCallback mGoUpBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mPathListAdapter != null && mPathListAdapter.getCurrentPosition() > 0) {
                mModel.loadFiles(mPathListAdapter.calculateUri(mPathListAdapter.getCurrentPosition() - 1));
                return;
            }
            setEnabled(false);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(FmViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FmActivity.Options options = null;
        Uri uri = null;
        AtomicInteger scrollPosition = new AtomicInteger(RecyclerView.NO_POSITION);
        if (savedInstanceState != null) {
            uri = BundleCompat.getParcelable(savedInstanceState, ARG_URI, Uri.class);
            options = BundleCompat.getParcelable(savedInstanceState, ARG_OPTIONS, FmActivity.Options.class);
            scrollPosition.set(savedInstanceState.getInt(ARG_POSITION, RecyclerView.NO_POSITION));
        }
        if (options == null) {
            options = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_OPTIONS, FmActivity.Options.class));
            if (uri == null) {
                uri = options.getInitUriForVfs();
            }
            if (requireArguments().containsKey(ARG_POSITION)) {
                scrollPosition.set(requireArguments().getInt(ARG_POSITION, RecyclerView.NO_POSITION));
            }
        }
        mActivity = (FmActivity) requireActivity();
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        UiUtils.applyWindowInsetsAsPadding(view.findViewById(R.id.path_container), false, true);
        RecyclerView pathListView = view.findViewById(R.id.path_list);
        pathListView.setLayoutManager(new LinearLayoutManager(mActivity, RecyclerView.HORIZONTAL, false));
        mPathListAdapter = new FmPathListAdapter(mModel);
        mPathListAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                pathListView.setSelection(mPathListAdapter.getCurrentPosition());
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }
        });
        pathListView.setAdapter(mPathListAdapter);
        MaterialButton pathEditButton = view.findViewById(R.id.uri_edit);
        pathEditButton.setOnClickListener(v -> {
            Uri currentUri = mModel.getCurrentUri();
            String path = currentUri != null ? FmUtils.getDisplayablePath(currentUri) : null;
            new TextInputDialogBuilder(mActivity, null)
                    .setTitle(R.string.go_to_path)
                    .setInputText(path)
                    .setPositiveButton(R.string.go, (dialog, which, inputText, isChecked) -> {
                        if (TextUtils.isEmpty(inputText)) {
                            return;
                        }
                        goToRawPath(inputText.toString().trim());
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        });
        mFabGroup = view.findViewById(R.id.fab);
        mFabGroup.inflate(R.menu.fragment_fm_speed_dial);
        mFabGroup.setOnActionSelectedListener(this);
        mFabGroup.setContentDescription(getString(R.string.add));
        UiUtils.applyWindowInsetsAsMargin(view.findViewById(R.id.fab_holder));
        mEmptyView = view.findViewById(android.R.id.empty);
        mEmptyViewIcon = view.findViewById(R.id.icon);
        mEmptyViewTitle = view.findViewById(R.id.title);
        mEmptyViewSummary = view.findViewById(R.id.summary);
        mEmptyViewDetails = view.findViewById(R.id.message);
        mEmptyViewAction = view.findViewById(R.id.empty_action);
        setEmptyViewRefreshAction();
        mSearchFilterContainer = view.findViewById(R.id.fm_search_filter_container);
        mSearchFilterChip = view.findViewById(R.id.fm_search_filter_chip);
        mSearchFilterChip.setOnClickListener(v -> clearSearchQuery());
        mSearchFilterChip.setOnCloseIconClickListener(v -> clearSearchQuery());
        mRecyclerView = view.findViewById(R.id.list_item);
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(mActivity));
        mAdapter = new FmAdapter(mModel, mActivity);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataChangedObserver() {
            @Override
            public void onChanged() {
                if (mAdapter.isInSelectionMode()) {
                    // Avoid setting a selection in selection mode (directory cannot be changed
                    // in selection mode anyway).
                    return;
                }
                if (scrollPosition.get() != RecyclerView.NO_POSITION) {
                    // Update scroll position
                    mRecyclerView.setSelection(scrollPosition.get());
                    scrollPosition.set(RecyclerView.NO_POSITION);
                } else {
                    mRecyclerView.setSelection(mModel.getCurrentScrollPosition());
                }
            }
        });
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                if (mFolderShortInfo == null) {
                    return;
                }
                if (dy < 0 && mFolderShortInfo.canWrite && !mFabGroup.isShown()) {
                    mFabGroup.show();
                } else if (dy > 0 && mFabGroup.isShown()) {
                    mFabGroup.hide();
                }
            }
        });
        mMultiSelectionView = view.findViewById(R.id.selection_view);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setOnSelectionModeChangeListener(this);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.updateCounter(true);
        mMultiSelectionView.getViewTreeObserver().addOnGlobalLayoutListener(mMultiSelectionViewChangeListener);
        BatchOpsHandler batchOpsHandler = new BatchOpsHandler(mMultiSelectionView);
        mMultiSelectionView.setOnSelectionChangeListener(batchOpsHandler);
        mActivity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // Set observer
        mModel.getLastUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            // force disable empty view
            if (mEmptyView.isShown()) {
                mEmptyView.setVisibility(View.GONE);
            }
            // Reset subtitle
            Optional.ofNullable(mActivity.getSupportActionBar()).ifPresent(actionBar ->
                    actionBar.setSubtitle(R.string.loading));
            if (uri1 == null) {
                return;
            }
            if (mRecyclerView != null) {
                View v = mRecyclerView.getChildAt(0);
                if (v != null) {
                    mModel.setScrollPosition(uri1, mRecyclerView.getChildAdapterPosition(v));
                }
                mAdapter.setFmList(Collections.emptyList());
            }
            if (mMultiSelectionView.isShown()) {
                mMultiSelectionView.cancel();
            }
        });
        mModel.getFmItemsLiveData().observe(getViewLifecycleOwner(), fmItems -> {
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(false);
            }
            mAdapter.setFmList(fmItems);
            updateSearchFilterChip();
            if (fmItems.isEmpty()) {
                if (mModel.hasQueryString()) {
                    handleSearchEmptyView();
                } else {
                    handleEmptyView(R.drawable.ic_file, getString(R.string.fm_empty_folder_title),
                            getText(R.string.fm_empty_folder_message), null);
                }
            } else if (mEmptyView.isShown()) {
                mEmptyView.setVisibility(View.GONE);
            }
        });
        mModel.getFmErrorLiveData().observe(getViewLifecycleOwner(), throwable -> {
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(false);
            }
            CharSequence title = getFolderErrorDisplayTitle(throwable, getText(R.string.error));
            handleEmptyView(io.github.muntashirakon.ui.R.drawable.ic_caution, title,
                    getText(R.string.fm_folder_error_message), throwable);
        });
        mModel.getUriLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            FmActivity.Options options1 = mModel.getOptions();
            String alternativeRootName = options1.isVfs() ? options1.uri.getLastPathSegment() : null;
            Optional.ofNullable(mActivity.getSupportActionBar()).ifPresent(actionBar -> {
                String title = uri1.getLastPathSegment();
                if (TextUtils.isEmpty(title)) {
                    title = alternativeRootName != null ? alternativeRootName : "Root";
                }
                actionBar.setTitle(title);
            });
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(true);
            }
            mPathListAdapter.setCurrentUri(uri1);
            mPathListAdapter.setAlternativeRootName(alternativeRootName);
            mGoUpBackPressedCallback.setEnabled(mPathListAdapter.getCurrentPosition() > 0);
            if (!Objects.equals(uri1, mVolumeScanWarningAcceptedUri)) {
                mVolumeScanWarningAcceptedUri = null;
            }
        });
        mModel.getFolderShortInfoLiveData().observe(getViewLifecycleOwner(), folderShortInfo -> {
            mFolderShortInfo = folderShortInfo;
            StringBuilder subtitle = new StringBuilder();
            // 1. Size
            if (folderShortInfo.size > 0) {
                subtitle.append(Formatter.formatShortFileSize(requireContext(), folderShortInfo.size)).append(" • ");
            }
            // 2. Folders and files
            if (folderShortInfo.folderCount > 0 && folderShortInfo.fileCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.folder_count, folderShortInfo.folderCount,
                                folderShortInfo.folderCount))
                        .append(", ")
                        .append(getResources().getQuantityString(R.plurals.file_count, folderShortInfo.fileCount,
                                folderShortInfo.fileCount));
            } else if (folderShortInfo.folderCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.folder_count, folderShortInfo.folderCount,
                        folderShortInfo.folderCount));
            } else if (folderShortInfo.fileCount > 0) {
                subtitle.append(getResources().getQuantityString(R.plurals.file_count, folderShortInfo.fileCount,
                        folderShortInfo.fileCount));
            } else {
                subtitle.append(getString(R.string.empty_folder));
            }
            // 3. Mode
            if (folderShortInfo.canRead || folderShortInfo.canWrite) {
                subtitle.append(" • ");
                if (folderShortInfo.canRead) {
                    subtitle.append("R");
                }
                if (folderShortInfo.canWrite) {
                    subtitle.append("W");
                }
            }
            if (!folderShortInfo.canWrite) {
                if (mFabGroup.isShown()) {
                    mFabGroup.hide();
                }
            } else {
                if (!mFabGroup.isShown()) {
                    mFabGroup.show();
                }
            }
            Optional.ofNullable(mActivity.getSupportActionBar()).ifPresent(actionBar ->
                    actionBar.setSubtitle(subtitle)
            );
        });
        mModel.getDisplayPropertiesLiveData().observe(getViewLifecycleOwner(), uri1 -> {
            FilePropertiesDialogFragment dialogFragment = FilePropertiesDialogFragment.getInstance(uri1);
            dialogFragment.show(mActivity.getSupportFragmentManager(), FilePropertiesDialogFragment.TAG);
        });
        mModel.getShortcutCreatorLiveData().observe(getViewLifecycleOwner(), pathBitmapPair -> {
            Path path = pathBitmapPair.first;
            Bitmap icon = pathBitmapPair.second;
            FmShortcutInfo shortcutInfo = new FmShortcutInfo(path, null);
            if (icon != null) {
                shortcutInfo.setIcon(icon);
            } else {
                Drawable drawable = Objects.requireNonNull(ContextCompat.getDrawable(requireContext(),
                        path.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file));
                shortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(drawable));
            }
            CreateShortcutDialogFragment dialog = CreateShortcutDialogFragment.getInstance(shortcutInfo);
            dialog.show(getChildFragmentManager(), CreateShortcutDialogFragment.TAG);
        });
        mModel.getSharableItemsLiveData().observe(getViewLifecycleOwner(), sharableItems ->
                mActivity.startActivity(sharableItems.toSharableIntent()));
        mModel.getExtractArchiveLiveData().observe(getViewLifecycleOwner(), this::promptExtractArchive);
        mModel.setOptions(options, uri);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mModel != null && mRecyclerView != null) {
            Prefs.FileManager.setLastOpenedPath(mModel.getOptions(), mModel.getCurrentUri(), getRecyclerViewFirstChildPosition());
        }
    }

    @Override
    public void onDestroyView() {
        ThreadUtils.getUiThreadHandler().removeCallbacks(mSearchDebounceRunnable);
        if (mMultiSelectionView != null) {
            mMultiSelectionView.getViewTreeObserver().removeOnGlobalLayoutListener(mMultiSelectionViewChangeListener);
        }
        mSearchView = null;
        mSearchMenuItem = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mModel != null) {
            outState.putParcelable(ARG_URI, mModel.getCurrentUri());
            outState.putParcelable(ARG_OPTIONS, mModel.getOptions());
        }
        if (mRecyclerView != null) {
            View v = mRecyclerView.getChildAt(0);
            if (v != null) {
                outState.putInt(ARG_POSITION, mRecyclerView.getChildAdapterPosition(v));
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Handle back press: The order MUST be kept same
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mGoUpBackPressedCallback);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mExitSelectionBackPressedCallback);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.activity_fm_actions, menu);
        mSearchMenuItem = menu.findItem(R.id.action_search);
        if (mSearchMenuItem != null) {
            mSearchView = (SearchView) mSearchMenuItem.getActionView();
            if (mSearchView != null) {
                mSearchView.setQueryHint(getString(R.string.fm_search_hint));
                mSearchView.setOnQueryTextListener(this);
                String query = mModel.getQueryString();
                if (!TextUtils.isEmpty(query)) {
                    mSearchMenuItem.expandActionView();
                    mSearchView.setQuery(query, false);
                    mSearchView.clearFocus();
                }
            }
        }
        updateSearchFilterChip();
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuItem pasteMenu = menu.findItem(R.id.action_paste);
        if (pasteMenu != null) {
            FmTasks.FmTask fmTask = FmTasks.getInstance().peek();
            pasteMenu.setEnabled(mFolderShortInfo != null && fmTask != null && mFolderShortInfo.canWrite && fmTask.canPaste());
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            mModel.reload();
            return true;
        } else if (id == R.id.action_shortcut) {
            Uri uri = mPathListAdapter.getCurrentUri();
            if (uri != null) {
                mModel.createShortcut(uri);
            }
            return true;
        } else if (id == R.id.action_list_options) {
            FmListOptions listOptions = new FmListOptions();
            listOptions.setListOptionActions(mModel);
            listOptions.show(getChildFragmentManager(), FmListOptions.TAG);
            return true;
        } else if (id == R.id.action_paste) {
            FmTasks.FmTask task = FmTasks.getInstance().dequeue();
            if (task != null) {
                startBatchPaste(task);
            }
            return true;
        } else if (id == R.id.action_new_window) {
            Intent intent = new Intent(mActivity, FmActivity.class);
            if (!mModel.getOptions().isVfs()) {
                intent.setDataAndType(mModel.getCurrentUri(), DocumentsContract.Document.MIME_TYPE_DIR);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_add_to_favorites) {
            Uri uri = mPathListAdapter.getCurrentUri();
            if (uri != null) {
                mModel.addToFavorite(Paths.get(uri), mModel.getOptions());
            }
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = SettingsActivity.getSettingsIntent(requireContext(), "files_prefs");
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public boolean onActionSelected(@NonNull SpeedDialActionItem actionItem) {
        int id = actionItem.getId();
        if (id == R.id.action_file) {
            NewFileDialogFragment dialog = NewFileDialogFragment.getInstance(this::createNewFile);
            dialog.show(getChildFragmentManager(), NewFileDialogFragment.TAG);
        } else if (id == R.id.action_folder) {
            NewFolderDialogFragment dialog = NewFolderDialogFragment.getInstance(this::createNewFolder);
            dialog.show(getChildFragmentManager(), NewFolderDialogFragment.TAG);
        } else if (id == R.id.action_symbolic_link) {
            Uri uri = mPathListAdapter.getCurrentUri();
            if (uri == null) {
                return false;
            }
            Path path = Paths.get(uri);
            if (path.getFile() == null) {
                UIUtils.displayLongToast(R.string.symbolic_link_not_supported);
                return false;
            }
            NewSymbolicLinkDialogFragment dialog = NewSymbolicLinkDialogFragment.getInstance(this::createNewSymbolicLink);
            dialog.show(getChildFragmentManager(), NewSymbolicLinkDialogFragment.TAG);
        }
        return false;
    }

    @Override
    public void onSelectionModeEnabled() {
        mExitSelectionBackPressedCallback.setEnabled(true);
    }

    @Override
    public void onSelectionModeDisabled() {
        mExitSelectionBackPressedCallback.setEnabled(false);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        List<Path> selectedFiles = mModel.getSelectedItems();
        if (selectedFiles.isEmpty()) {
            // Do nothing on empty list
            return false;
        }
        if (id == R.id.action_share) {
            mModel.shareFiles(selectedFiles);
        } else if (id == R.id.action_install_selected_apks) {
            startBatchApkInstall(selectedFiles);
        } else if (id == R.id.action_find_duplicate_apks) {
            startSelectedApkDuplicateScan(selectedFiles);
        } else if (id == R.id.action_rename) {
            RenameDialogFragment dialog = RenameDialogFragment.getInstance(null, (prefix, extension) ->
                    showBatchRenamePreview(new ArrayList<>(selectedFiles), prefix, extension));
            dialog.show(getChildFragmentManager(), RenameDialogFragment.TAG);
        } else if (id == R.id.action_delete) {
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.title_confirm_deletion)
                    .setMessage(getResources().getQuantityString(R.plurals.file_deletion_confirmation,
                            selectedFiles.size(), selectedFiles.size()))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm_file_deletion, (dialog, which) -> startBatchDeletion(selectedFiles))
                    .show();
        } else if (id == R.id.action_cut) {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(TYPE_CUT, selectedFiles);
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
        } else if (id == R.id.action_copy) {
            FmTasks.FmTask fmTask = new FmTasks.FmTask(FmTasks.FmTask.TYPE_COPY, selectedFiles);
            FmTasks.getInstance().enqueue(fmTask);
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
        } else if (id == R.id.action_create_archive) {
            promptCreateArchive(selectedFiles);
        } else if (id == R.id.action_extract_archive) {
            if (selectedFiles.size() == 1) {
                promptExtractArchive(selectedFiles.get(0));
            }
        } else if (id == R.id.action_copy_path) {
            Utils.copyToClipboard(mActivity, "Paths", FmUtils.getClipboardPaths(selectedFiles));
        }
        return false;
    }

    private void startBatchApkInstall(@NonNull List<Path> selectedFiles) {
        if (!FmBatchApkInstallUtils.canOfferInstall(selectedFiles)) {
            UIUtils.displayShortToast(R.string.no_installable_apks_selected);
            return;
        }
        startActivity(FmBatchApkInstallUtils.getInstallIntent(requireContext(), selectedFiles));
        if (mMultiSelectionView != null) {
            mMultiSelectionView.cancel();
        }
    }

    private void startSelectedApkDuplicateScan(@NonNull List<Path> selectedFiles) {
        if (!FmDuplicateApkSelectionUtils.canOfferDuplicateScan(selectedFiles)) {
            UIUtils.displayShortToast(R.string.no_duplicate_apk_files_selected);
            return;
        }
        List<File> apkFiles = FmDuplicateApkSelectionUtils.toLocalFiles(selectedFiles);
        if (mMultiSelectionView != null) {
            mMultiSelectionView.cancel();
        }
        PackageManager packageManager = requireContext().getPackageManager();
        File cacheDir = requireContext().getCacheDir();
        AtomicReference<Future<?>> scanThread = new AtomicReference<>();
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.find_duplicate_apks)
                .setMessage(R.string.scan_selected_duplicate_apks)
                .setPositiveButton(R.string.action_stop_service, (dialog, which) -> {
                    Future<?> future = scanThread.get();
                    if (future != null) {
                        future.cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        scanThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            List<ApkDuplicateSelector.DuplicateGroup> groups = Collections.emptyList();
            boolean cancelled = false;
            try {
                List<ApkDuplicateSelector.Candidate> candidates = new ArrayList<>(apkFiles.size());
                for (File apkFile : apkFiles) {
                    if (ThreadUtils.isInterrupted()) {
                        cancelled = true;
                        break;
                    }
                    ApkDuplicateSelector.Candidate candidate = ApkDuplicateOperations.buildCandidate(
                            packageManager, cacheDir, apkFile);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                if (!cancelled && !ThreadUtils.isInterrupted()) {
                    groups = ApkDuplicateSelector.selectDuplicates(candidates,
                            ApkDuplicateSelector.KeepStrategy.LARGEST);
                } else {
                    cancelled = true;
                }
            } finally {
                List<ApkDuplicateSelector.DuplicateGroup> finalGroups = groups;
                boolean finalCancelled = cancelled || ThreadUtils.isInterrupted();
                ThreadUtils.postOnMainThread(() -> {
                    progressDialog.dismiss();
                    if (!isAdded()) {
                        return;
                    }
                    if (!finalCancelled) {
                        reviewSelectedApkDuplicates(finalGroups);
                    }
                });
            }
        }));
    }

    private void reviewSelectedApkDuplicates(@Nullable List<ApkDuplicateSelector.DuplicateGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            showSelectedApkDuplicateInfo(R.string.find_duplicate_apks, R.string.no_duplicate_apks_found_message);
            return;
        }
        final ArrayList<ApkDuplicateSelector.Candidate> drops = new ArrayList<>();
        final ArrayList<Integer> indices = new ArrayList<>();
        final List<CharSequence> labels = new ArrayList<>();
        for (ApkDuplicateSelector.DuplicateGroup group : groups) {
            for (ApkDuplicateSelector.Candidate drop : group.drop) {
                indices.add(drops.size());
                drops.add(drop);
                String dropName = FmUtils.getFileDisplayName(drop.path);
                String keeperName = FmUtils.getFileDisplayName(group.keeper.path);
                labels.add(new SpannableStringBuilder(dropName)
                        .append("\n").append(getSmallerText(drop.packageName + " v" + drop.versionCode
                                + " · " + Formatter.formatShortFileSize(requireContext(), drop.sizeBytes)
                                + " · " + getString(R.string.apk_duplicate_keeping, keeperName))));
            }
        }
        new SearchableMultiChoiceDialogBuilder<>(requireContext(), indices, labels)
                .addSelections(indices)
                .setTitle(R.string.duplicate_apks_review_title)
                .setPositiveButton(R.string.delete, (dialog, which, selectedIndices) -> {
                    if (selectedIndices.isEmpty()) {
                        UIUtils.displayShortToast(R.string.no_duplicate_apk_delete_selection);
                        return;
                    }
                    List<ApkDuplicateSelector.Candidate> selected = new ArrayList<>(selectedIndices.size());
                    for (Integer index : selectedIndices) {
                        selected.add(drops.get(index));
                    }
                    confirmDeleteSelectedApkDuplicates(selected);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteSelectedApkDuplicates(@NonNull List<ApkDuplicateSelector.Candidate> dropFiles) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.find_duplicate_apks)
                .setMessage(R.string.delete_duplicate_apks_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_clear_data,
                                () -> deleteSelectedApkDuplicates(dropFiles)))
                .show();
    }

    private void deleteSelectedApkDuplicates(@NonNull List<ApkDuplicateSelector.Candidate> dropFiles) {
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.find_duplicate_apks)
                .setMessage(R.string.loading)
                .setCancelable(false)
                .show();
        ThreadUtils.postOnBackgroundThread(() -> {
            Pair<Integer, Long> result = ApkDuplicateOperations.deleteCandidates(dropFiles);
            ThreadUtils.postOnMainThread(() -> {
                progressDialog.dismiss();
                if (!isAdded()) {
                    return;
                }
                UIUtils.displayLongToast(getString(R.string.duplicate_apks_deleted, result.first,
                        Formatter.formatShortFileSize(requireContext(), result.second)));
                mModel.reload();
            });
        });
    }

    private void showSelectedApkDuplicateInfo(@StringRes int titleRes, @StringRes int messageRes) {
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_information_circle)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        ThreadUtils.getUiThreadHandler().removeCallbacks(mSearchDebounceRunnable);
        applySearchQueryWithWarning(query);
        if (mSearchView != null) {
            mSearchView.clearFocus();
            UiUtils.hideKeyboard(mSearchView);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mPendingSearchQuery = newText != null ? newText : "";
        ThreadUtils.getUiThreadHandler().removeCallbacks(mSearchDebounceRunnable);
        ThreadUtils.postOnMainThreadDelayed(mSearchDebounceRunnable, SEARCH_DEBOUNCE_MILLIS);
        return true;
    }

    @Override
    public void onRefresh() {
        if (mModel != null) mModel.reload();
    }

    public int getRecyclerViewFirstChildPosition() {
        if (mRecyclerView != null && mRecyclerView.getChildCount() > 0) {
            View v = mRecyclerView.getChildAt(0);
            if (v != null) {
                return mRecyclerView.getChildAdapterPosition(v);
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private void goToRawPath(@NonNull String p) {
        Uri uncheckedUri = Uri.parse(p);
        if (uncheckedUri.getScheme() != null) {
            Uri checkedUri = FmUtils.sanitizeContentInput(uncheckedUri);
            if (checkedUri != null) {
                // Valid path
                mModel.loadFiles(checkedUri);
            } // else bad URI
            return;
        }
        // Bad Uri, consider it to be a file://
        if (p.startsWith(Paths.PATH_SEPARATOR)) {
            // absolute file
            Uri checkedUri = FmUtils.sanitizeContentInput(uncheckedUri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build());
            if (checkedUri != null) {
                mModel.loadFiles(checkedUri);
            } // else bad file
            return;
        }
        // Relative path
        String goodPath = Paths.sanitize(p, false);
        if (goodPath == null || goodPath.equals(Paths.PATH_SEPARATOR)) {
            // No relative path means current path which is already loaded
            return;
        }
        Uri currentUri = mModel.getCurrentUri();
        if (DocumentsContractCompat.isDocumentUri(requireContext(), currentUri)) {
            List<String> pathSegments = currentUri.getPathSegments();
            if (pathSegments.size() == 4) {
                // For a tree URI, the 3rd index is the path
                String lastPathSegment = pathSegments.get(3) + Paths.PATH_SEPARATOR + goodPath;
                Uri.Builder b = new Uri.Builder()
                        .scheme(currentUri.getScheme())
                        .authority(currentUri.getAuthority())
                        .appendPath(pathSegments.get(0))
                        .appendPath(pathSegments.get(1))
                        .appendPath(pathSegments.get(2))
                        .appendPath(lastPathSegment);
                mModel.loadFiles(b.build());
            }
            // Other document Uris don't support navigation nor do they support folders/trees
            return;
        }
        // For others, simply append path segments at the end
        String[] segments = goodPath.split(Paths.PATH_SEPARATOR);
        Uri.Builder b = currentUri.buildUpon();
        for (String segment : segments) {
            b.appendPath(segment);
        }
        mModel.loadFiles(b.build());
    }

    private void applySearchQueryWithWarning(@Nullable String query) {
        Uri currentUri = mModel.getCurrentUri();
        if (!TextUtils.isEmpty(query)
                && !Objects.equals(currentUri, mVolumeScanWarningAcceptedUri)
                && FmVolumeScanWarning.shouldWarnBeforeRecursiveSearch(currentUri, query)) {
            showVolumeScanWarning(currentUri, query);
            return;
        }
        applySearchQueryNow(query);
    }

    private void applySearchQueryNow(@Nullable String query) {
        mModel.setQueryString(query);
        updateSearchFilterChip();
    }

    private void showVolumeScanWarning(@NonNull Uri currentUri, @NonNull String query) {
        if (mVolumeScanWarningShowing) {
            return;
        }
        mVolumeScanWarningShowing = true;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.fm_volume_scan_warning_title)
                .setMessage(FmVolumeScanWarning.buildWarningMessage(requireContext(), currentUri))
                .setNegativeButton(R.string.cancel, (dialog, which) -> clearSearchQuery())
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    mVolumeScanWarningAcceptedUri = currentUri;
                    applySearchQueryNow(query);
                })
                .setOnDismissListener(dialog -> mVolumeScanWarningShowing = false)
                .show();
    }

    private void clearSearchQuery() {
        mPendingSearchQuery = "";
        ThreadUtils.getUiThreadHandler().removeCallbacks(mSearchDebounceRunnable);
        if (mSearchView != null) {
            mSearchView.setQuery("", false);
            mSearchView.clearFocus();
            UiUtils.hideKeyboard(mSearchView);
        }
        if (mSearchMenuItem != null) {
            mSearchMenuItem.collapseActionView();
        }
        applySearchQueryNow(null);
    }

    private void updateSearchFilterChip() {
        if (mSearchFilterContainer == null || mSearchFilterChip == null || mModel == null) {
            return;
        }
        String query = mModel.getQueryString();
        boolean hasQuery = !TextUtils.isEmpty(query);
        mSearchFilterContainer.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
        if (hasQuery) {
            mSearchFilterChip.setText(getString(R.string.fm_search_active_filter, getSearchDisplayQuery(query)));
        }
    }

    private void setEmptyViewRefreshAction() {
        mEmptyViewAction.setText(R.string.refresh);
        mEmptyViewAction.setIconResource(R.drawable.ic_refresh);
        mEmptyViewAction.setOnClickListener(v -> {
            if (mSwipeRefresh != null) {
                mSwipeRefresh.setRefreshing(true);
            }
            onRefresh();
        });
    }

    private void handleSearchEmptyView() {
        String query = mModel.getQueryString();
        handleEmptyView(io.github.muntashirakon.ui.R.drawable.ic_search, getString(R.string.fm_search_empty_title),
                getString(R.string.fm_search_empty_message, getSearchDisplayQuery(query)), null);
        mEmptyViewAction.setText(R.string.main_empty_action_clear_search);
        mEmptyViewAction.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_clear);
        mEmptyViewAction.setOnClickListener(v -> clearSearchQuery());
    }

    @VisibleForTesting
    @NonNull
    static String getSearchDisplayQuery(@Nullable String query) {
        return FmUtils.getDisplayName(query, "");
    }

    private void handleEmptyView(@DrawableRes int icon, @Nullable CharSequence title,
                                 @Nullable CharSequence summary, @Nullable Throwable th) {
        setEmptyViewRefreshAction();
        if (!mEmptyView.isShown()) {
            mEmptyView.setVisibility(View.VISIBLE);
        }
        mEmptyViewIcon.setImageResource(icon);
        mEmptyViewTitle.setText(title);
        mEmptyViewSummary.setText(summary);
        mEmptyViewSummary.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
        mEmptyViewAction.setVisibility(View.VISIBLE);
        if (th == null) {
            mEmptyViewDetails.setVisibility(View.GONE);
            return;
        }
        mEmptyViewDetails.setVisibility(View.VISIBLE);
        mEmptyViewDetails.setText(formatEmptyViewDetails(th));
    }

    @VisibleForTesting
    @NonNull
    static String formatEmptyViewDetails(@NonNull Throwable th) {
        StackTraceElement[] arr = th.getStackTrace();
        StringBuilder report = new StringBuilder(th + "\n");
        int i = 0;
        for (StackTraceElement traceElement : arr) {
            if (i == 3) break;
            report.append("    at ").append(traceElement.toString()).append("\n");
            ++i;
        }
        Throwable cause = th;
        while ((cause = cause.getCause()) != null) {
            report.append(" Caused by: ").append(cause).append("\n");
            arr = cause.getStackTrace();
            i = 0;
            for (StackTraceElement stackTraceElement : arr) {
                if (i == 3) break;
                report.append("   at ").append(stackTraceElement.toString()).append("\n");
                ++i;
            }
        }
        return ExportTextUtils.toPlainTextReport(report.toString());
    }

    private void createNewFolder(String name) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path path = Paths.get(uri);
        String displayName = findNextBestDisplayName(path, name, null);
        try {
            Path newDir = path.createNewDirectory(displayName);
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(newDir.getName());
        } catch (IOException e) {
            e.printStackTrace();
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void createNewFile(String prefix, @Nullable String extension, String template) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path path = Paths.get(uri);
        String displayName = findNextBestDisplayName(path, prefix, extension);
        try {
            Path newFile = path.createNewFile(displayName, null);
            FileUtils.copyFromAsset(requireContext(), "blanks/" + template, newFile);
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(newFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void createNewSymbolicLink(String prefix, @Nullable String extension, String targetPath) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path basePath = Paths.get(uri);
        String displayName = findNextBestDisplayName(basePath, prefix, extension);
        Path sourcePath = Paths.build(basePath, displayName);
        if (sourcePath != null && sourcePath.createNewSymbolicLink(targetPath)) {
            UIUtils.displayShortToast(R.string.done);
            mModel.reload(sourcePath.getName());
        } else {
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void promptCreateArchive(@NonNull List<Path> selectedFiles) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path basePath = Paths.get(uri);
        String defaultName = getDefaultArchiveName(selectedFiles);
        new TextInputDialogBuilder(mActivity, null)
                .setTitle(R.string.create_zip_archive)
                .setInputText(defaultName)
                .setPositiveButton(R.string.create, (dialog, which, inputText, isChecked) -> {
                    String displayName = getZipDisplayName(inputText, defaultName);
                    String archiveName = findNextBestDisplayName(basePath, Paths.trimPathExtension(displayName),
                            Paths.getPathExtension(displayName));
                    try {
                        Path archivePath = basePath.createNewFile(archiveName, null);
                        startArchiveCreation(selectedFiles, archivePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showArchiveErrorDialog(R.string.failed_to_create_archive, e);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptExtractArchive(@NonNull Path archivePath) {
        if (!FmArchiveUtils.isSupportedZip(archivePath)) {
            UIUtils.displayShortToast(R.string.failed_to_extract_archive);
            return;
        }
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        Path basePath = Paths.get(uri);
        String defaultName = getDirectoryDisplayName(Paths.trimPathExtension(archivePath.getName()), "archive");
        new TextInputDialogBuilder(mActivity, null)
                .setTitle(R.string.extract_archive)
                .setInputText(defaultName)
                .setPositiveButton(R.string.extract, (dialog, which, inputText, isChecked) -> {
                    String displayName = getDirectoryDisplayName(inputText, defaultName);
                    try {
                        Path destinationPath = getArchiveDestination(basePath, displayName);
                        startArchiveExtraction(archivePath, destinationPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                        showArchiveErrorDialog(R.string.failed_to_extract_archive, e);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startArchiveCreation(@NonNull List<Path> selectedFiles, @NonNull Path archivePath) {
        AtomicReference<Future<?>> archiveThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, selectedFiles.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_zip_archive)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (archiveThread.get() != null) {
                        archiveThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        archiveThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            boolean success = false;
            Throwable failure = null;
            try {
                FmArchiveUtils.createZipArchive(selectedFiles, archivePath,
                        (labelText, done, total) -> updateArchiveProgress(progressRef, labelRef, counterRef,
                                labelText, done, total));
                success = true;
            } catch (InterruptedIOException e) {
                failure = e;
                archivePath.delete();
            } catch (IOException e) {
                failure = e;
                archivePath.delete();
            } finally {
                boolean finalSuccess = success;
                Throwable finalFailure = failure;
                AlertDialog d = dialogRef.get();
                ThreadUtils.postOnMainThread(() -> {
                    if (d != null) {
                        d.dismiss();
                    }
                    if (finalSuccess) {
                        UIUtils.displayShortToast(R.string.archive_created_successfully);
                        mModel.reload(archivePath.getName());
                    } else {
                        mModel.reload();
                        if (!(finalFailure instanceof InterruptedIOException) && finalFailure != null) {
                            showArchiveErrorDialog(R.string.failed_to_create_archive, finalFailure);
                        }
                    }
                });
            }
        }));
    }

    private void startArchiveExtraction(@NonNull Path archivePath, @NonNull Path destinationPath) {
        AtomicReference<Future<?>> extractThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, 0));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.extract_archive)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (extractThread.get() != null) {
                        extractThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        extractThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            boolean success = false;
            Throwable failure = null;
            try {
                FmArchiveUtils.extractZipArchive(archivePath, destinationPath, this::resolveArchiveConflict,
                        (labelText, done, total) -> updateArchiveProgress(progressRef, labelRef, counterRef,
                                labelText, done, total));
                success = true;
            } catch (InterruptedIOException e) {
                failure = e;
            } catch (IOException e) {
                failure = e;
            } finally {
                boolean finalSuccess = success;
                Throwable finalFailure = failure;
                AlertDialog d = dialogRef.get();
                ThreadUtils.postOnMainThread(() -> {
                    if (d != null) {
                        d.dismiss();
                    }
                    if (finalSuccess) {
                        UIUtils.displayShortToast(R.string.archive_extracted_successfully);
                        mModel.reload(destinationPath.getName());
                    } else {
                        mModel.reload();
                        if (!(finalFailure instanceof InterruptedIOException) && finalFailure != null) {
                            showArchiveErrorDialog(R.string.failed_to_extract_archive, finalFailure);
                        }
                    }
                });
            }
        }));
    }

    private void startBatchDeletion(@NonNull List<Path> paths) {
        // TODO: 27/6/23 Ideally, these should be done in a bound service
        AtomicReference<Future<?>> deletionThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, paths.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (deletionThread.get() != null) {
                        deletionThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        deletionThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(paths.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                int i = 1;
                for (Path path : paths) {
                    // Update label
                    TextView l = labelRef.get();
                    if (l != null) {
                        ThreadUtils.postOnMainThread(() -> l.setText(FmUtils.getPathDisplayName(path)));
                    }
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    // Delete, progress
                    path.delete();
                    TextView c = counterRef.get();
                    int finalI = i;
                    ThreadUtils.postOnMainThread(() -> {
                        if (c != null) {
                            c.setText(String.format(Locale.getDefault(), "%d/%d", finalI, paths.size()));
                        }
                        if (p != null) {
                            p.setProgress(finalI);
                        }
                    });
                    ++i;
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                }
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        UIUtils.displayShortToast(R.string.deleted_successfully);
                        mModel.reload();
                    });
                }
            }
        }));
    }

    private void showBatchRenamePreview(@NonNull List<Path> paths, @NonNull String prefix, @Nullable String extension) {
        FmBatchRenameUtils.Plan plan = FmBatchRenameUtils.createPlan(paths, prefix, extension);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.batch_rename_preview_title)
                .setMessage(getBatchRenamePreviewMessage(plan));
        if (plan.canExecute()) {
            builder.setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.rename, (dialog, which) -> startBatchRenaming(plan));
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.show();
    }

    @NonNull
    private CharSequence getBatchRenamePreviewMessage(@NonNull FmBatchRenameUtils.Plan plan) {
        StringBuilder builder = new StringBuilder();
        if (!plan.issues.isEmpty()) {
            builder.append(getString(R.string.batch_rename_cannot_start));
            for (FmBatchRenameUtils.Issue issue : plan.issues) {
                builder.append("\n").append(getBatchRenameIssueMessage(issue));
            }
            return builder;
        }
        builder.append(getResources().getQuantityString(R.plurals.batch_rename_preview_count,
                plan.entries.size(), plan.entries.size()));
        if (plan.resolvedConflictCount > 0) {
            builder.append("\n").append(getResources().getQuantityString(R.plurals.batch_rename_conflict_count,
                    plan.resolvedConflictCount, plan.resolvedConflictCount));
        }
        int previewCount = Math.min(plan.entries.size(), BATCH_RENAME_PREVIEW_LIMIT);
        for (int i = 0; i < previewCount; ++i) {
            FmBatchRenameUtils.Entry entry = plan.entries.get(i);
            builder.append("\n").append(getString(R.string.batch_rename_preview_row,
                    FmBatchRenameUtils.getDisplayName(entry.sourceName),
                    FmBatchRenameUtils.getDisplayName(entry.targetName)));
        }
        int remainingCount = plan.entries.size() - previewCount;
        if (remainingCount > 0) {
            builder.append("\n").append(getResources().getQuantityString(R.plurals.and_n_more,
                    remainingCount, remainingCount));
        }
        return builder;
    }

    @NonNull
    private CharSequence getBatchRenameIssueMessage(@NonNull FmBatchRenameUtils.Issue issue) {
        switch (issue.type) {
            case EMPTY_TARGET_NAME:
                return getString(R.string.batch_rename_empty_target);
            case INVALID_TARGET_NAME:
                return getString(R.string.batch_rename_invalid_target,
                        FmBatchRenameUtils.getDisplayName(issue.targetName));
            case MISSING_PARENT:
                return getString(R.string.batch_rename_missing_parent,
                        FmBatchRenameUtils.getDisplayName(issue.sourceName));
            case NO_AVAILABLE_TARGET_NAME:
            default:
                return getString(R.string.batch_rename_no_available_target,
                        FmBatchRenameUtils.getDisplayName(issue.sourceName));
        }
    }

    private void startBatchRenaming(@NonNull FmBatchRenameUtils.Plan plan) {
        AtomicReference<Future<?>> renameThread = new AtomicReference<>();
        AtomicReference<FmBatchRenameUtils.BatchResult> resultRef = new AtomicReference<>(
                new FmBatchRenameUtils.BatchResult(Collections.emptyList(), false));
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, plan.entries.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rename)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (renameThread.get() != null) {
                        renameThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        renameThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(plan.entries.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                resultRef.set(FmBatchRenameUtils.execute(plan, new FmBatchRenameUtils.ProgressListener() {
                    @Override
                    public void onEntryStarted(@NonNull FmBatchRenameUtils.Entry entry, int index, int total) {
                        TextView l = labelRef.get();
                        if (l != null) {
                            ThreadUtils.postOnMainThread(() ->
                                    l.setText(FmBatchRenameUtils.getDisplayName(entry.sourceName)));
                        }
                    }

                    @Override
                    public void onEntryFinished(@NonNull FmBatchRenameUtils.Entry entry, int completed, int total) {
                        TextView c = counterRef.get();
                        ThreadUtils.postOnMainThread(() -> {
                            if (c != null) {
                                c.setText(String.format(Locale.getDefault(), "%d/%d", completed, total));
                            }
                            if (p != null) {
                                p.setProgress(completed);
                            }
                        });
                    }
                }));
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        if (isAdded()) {
                            showBatchRenameResult(resultRef.get());
                        }
                        mModel.reload();
                    });
                }
            }
        }));
    }

    private void showBatchRenameResult(@NonNull FmBatchRenameUtils.BatchResult result) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.batch_rename_result_title)
                .setMessage(getBatchRenameResultMessage(result))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @NonNull
    private CharSequence getBatchRenameResultMessage(@NonNull FmBatchRenameUtils.BatchResult result) {
        StringBuilder builder = new StringBuilder(getString(R.string.batch_rename_result_summary,
                result.getSuccessCount(), result.results.size()));
        if (result.interrupted) {
            builder.append("\n").append(getString(R.string.batch_rename_interrupted));
        }
        for (FmBatchRenameUtils.Result item : result.results) {
            builder.append("\n");
            if (item.success) {
                builder.append(getString(R.string.batch_rename_result_success_row,
                        FmBatchRenameUtils.getDisplayName(item.entry.sourceName),
                        FmBatchRenameUtils.getDisplayName(item.entry.targetName)));
            } else if (TextUtils.isEmpty(item.failureMessage)) {
                builder.append(getString(R.string.batch_rename_result_failed_row,
                        FmBatchRenameUtils.getDisplayName(item.entry.sourceName),
                        FmBatchRenameUtils.getDisplayName(item.entry.targetName)));
            } else {
                builder.append(getString(R.string.batch_rename_result_failed_row_with_reason,
                        FmBatchRenameUtils.getDisplayName(item.entry.sourceName),
                        FmBatchRenameUtils.getDisplayName(item.entry.targetName), item.failureMessage));
            }
        }
        return builder;
    }

    private void startBatchPaste(@NonNull FmTasks.FmTask task) {
        Uri uri = mPathListAdapter.getCurrentUri();
        if (uri == null) {
            return;
        }
        AtomicReference<Future<?>> pasteThread = new AtomicReference<>();
        View view = View.inflate(requireContext(), R.layout.dialog_progress, null);
        LinearProgressIndicator progress = view.findViewById(R.id.progress_linear);
        TextView label = view.findViewById(android.R.id.text1);
        TextView counter = view.findViewById(android.R.id.text2);
        counter.setText(String.format(Locale.getDefault(), "%d/%d", 0, task.files.size()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.paste)
                .setView(view)
                .setPositiveButton(R.string.action_stop_service, (dialog1, which) -> {
                    if (pasteThread.get() != null) {
                        pasteThread.get().cancel(true);
                    }
                })
                .setCancelable(false)
                .show();
        pasteThread.set(ThreadUtils.postOnBackgroundThread(() -> {
            WeakReference<LinearProgressIndicator> progressRef = new WeakReference<>(progress);
            WeakReference<TextView> labelRef = new WeakReference<>(label);
            WeakReference<TextView> counterRef = new WeakReference<>(counter);
            WeakReference<AlertDialog> dialogRef = new WeakReference<>(dialog);
            Path targetPath = Paths.get(uri);
            try {
                LinearProgressIndicator p = progressRef.get();
                if (p != null) {
                    p.setMax(task.files.size());
                    p.setProgress(0);
                    p.setIndeterminate(false);
                }
                int i = 1;
                for (Path sourcePath : task.files) {
                    // Update label
                    TextView l = labelRef.get();
                    if (l != null) {
                        ThreadUtils.postOnMainThread(() -> l.setText(FmUtils.getPathDisplayName(sourcePath)));
                    }
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                    // Copy, progress
                    if (!copy(sourcePath, targetPath)) {
                        // Failed to copy, abort
                        ThreadUtils.postOnMainThread(() -> {
                            if (!isAdded()) return;  // fragment gone (e.g. rotation mid-paste)
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.failed_to_copy_specified_file,
                                            FmUtils.getPathDisplayName(sourcePath)))
                                    .setPositiveButton(R.string.close, null)
                                    .show();
                        });
                        return;
                    }
                    if (task.type == TYPE_CUT) {
                        if (!sourcePath.delete()) {
                            // Failed to move, abort
                            ThreadUtils.postOnMainThread(() -> {
                                if (!isAdded()) return;
                                new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(R.string.error)
                                        .setMessage(getString(R.string.failed_to_delete_specified_file_after_copying,
                                                FmUtils.getPathDisplayName(sourcePath)))
                                        .setPositiveButton(R.string.close, null)
                                        .show();
                            });
                            return;
                        }
                    }
                    TextView c = counterRef.get();
                    int finalI = i;
                    ThreadUtils.postOnMainThread(() -> {
                        if (c != null) {
                            c.setText(String.format(Locale.getDefault(), "%d/%d", finalI, task.files.size()));
                        }
                        if (p != null) {
                            p.setProgress(finalI);
                        }
                    });
                    ++i;
                    if (ThreadUtils.isInterrupted()) {
                        break;
                    }
                }
                UIUtils.displayShortToast(task.type == TYPE_CUT ? R.string.moved_successfully : R.string.copied_successfully);
            } finally {
                AlertDialog d = dialogRef.get();
                if (d != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        d.dismiss();
                        mModel.reload();
                    });
                }
            }
        }));
    }

    @WorkerThread
    private boolean copy(Path source, Path dest) {
        String name = source.getName();
        if (dest.hasFile(name)) {
            // Duplicate found. Ask user for what to do.
            String displayName = FmUtils.getPathDisplayName(source);
            CountDownLatch waitForUser = new CountDownLatch(1);
            AtomicReference<Boolean> keepBoth = new AtomicReference<>(null);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) {
                    // Fragment detached before we could ask: abort this file and release the
                    // worker, which would otherwise block on await() forever.
                    keepBoth.set(null);
                    waitForUser.countDown();
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.conflict_detected_while_copying)
                        .setMessage(getString(R.string.conflict_detected_while_copying_message, displayName))
                        .setCancelable(false)
                        .setOnDismissListener(dialog -> waitForUser.countDown())
                        .setPositiveButton(R.string.replace, (dialog, which) -> keepBoth.set(false))
                        .setNegativeButton(R.string.action_stop_service, (dialog, which) -> keepBoth.set(null))
                        .setNeutralButton(R.string.copy_keep_both_file, (dialog, which) -> keepBoth.set(true))
                        .show();
            });
            try {
                waitForUser.await();
            } catch (InterruptedException ignore) {
            }
            if (keepBoth.get() == null) {
                // Abort copying
                return false;
            }
            if (keepBoth.get()) {
                // Keep both
                String prefix;
                String extension;
                if (!source.isDirectory()) {
                    prefix = Paths.trimPathExtension(name);
                    extension = Paths.getPathExtension(name);
                } else {
                    prefix = name;
                    extension = null;
                }
                String newName = findNextBestDisplayName(dest, prefix, extension);
                try {
                    Path newPath = source.isDirectory() ? dest.createNewDirectory(newName) : dest.createNewFile(newName, null);
                    // Need to create that path again
                    newPath.delete();
                    return source.copyTo(newPath) != null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                // Overwrite
                return source.copyTo(dest, true) != null;
            }
        }
        // Simply copy
        return source.copyTo(dest, false) != null;
    }

    @NonNull
    private String getDefaultArchiveName(@NonNull List<Path> selectedFiles) {
        String prefix = selectedFiles.size() == 1 ? Paths.trimPathExtension(selectedFiles.get(0).getName()) : "archive";
        return getZipDisplayName(prefix, "archive.zip");
    }

    @NonNull
    private String getZipDisplayName(@Nullable CharSequence inputText, @NonNull String fallback) {
        String displayName = inputText != null ? inputText.toString().trim() : null;
        displayName = Paths.sanitizeFilename(displayName, null,
                Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED);
        if (TextUtils.isEmpty(displayName)) {
            displayName = fallback;
        }
        if (!displayName.toLowerCase(Locale.ROOT).endsWith("." + FmArchiveUtils.ZIP_EXTENSION)) {
            displayName += "." + FmArchiveUtils.ZIP_EXTENSION;
        }
        return displayName;
    }

    @NonNull
    private String getDirectoryDisplayName(@Nullable CharSequence inputText, @NonNull String fallback) {
        String displayName = inputText != null ? inputText.toString().trim() : null;
        displayName = Paths.sanitizeFilename(displayName, null,
                Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED);
        if (TextUtils.isEmpty(displayName)) {
            displayName = fallback;
        }
        return displayName;
    }

    @NonNull
    private Path getArchiveDestination(@NonNull Path basePath, @NonNull String displayName) throws IOException {
        if (basePath.hasFile(displayName)) {
            Path existingPath = basePath.findFile(displayName);
            if (existingPath.isDirectory()) {
                return existingPath;
            }
            displayName = findNextBestDisplayName(basePath, displayName, null);
        }
        return basePath.createNewDirectory(displayName);
    }

    @WorkerThread
    @NonNull
    private FmArchiveUtils.ConflictAction resolveArchiveConflict(@NonNull String entryName) throws IOException {
        String displayName = FmUtils.getArchiveEntryDisplayName(entryName);
        CountDownLatch waitForUser = new CountDownLatch(1);
        AtomicReference<FmArchiveUtils.ConflictAction> selectedAction =
                new AtomicReference<>(FmArchiveUtils.ConflictAction.ABORT);
        ThreadUtils.postOnMainThread(() -> {
            if (!isAdded()) {
                // Detached before we could ask: keep the ABORT default and release the worker.
                waitForUser.countDown();
                return;
            }
            CharSequence[] actions = new CharSequence[]{
                    getString(R.string.replace),
                    getString(R.string.copy_keep_both_file),
                    getString(R.string.skip),
                    getString(R.string.action_stop_service),
            };
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.conflict_detected_while_copying)
                    .setMessage(getString(R.string.conflict_detected_while_extracting_message, displayName))
                    .setCancelable(false)
                    .setItems(actions, (dialog, which) -> {
                        if (which == 0) {
                            selectedAction.set(FmArchiveUtils.ConflictAction.REPLACE);
                        } else if (which == 1) {
                            selectedAction.set(FmArchiveUtils.ConflictAction.KEEP_BOTH);
                        } else if (which == 2) {
                            selectedAction.set(FmArchiveUtils.ConflictAction.SKIP);
                        } else {
                            selectedAction.set(FmArchiveUtils.ConflictAction.ABORT);
                        }
                    })
                    .setOnDismissListener(dialog -> waitForUser.countDown())
                    .show();
        });
        try {
            waitForUser.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
        return selectedAction.get();
    }

    private void updateArchiveProgress(@NonNull WeakReference<LinearProgressIndicator> progressRef,
                                       @NonNull WeakReference<TextView> labelRef,
                                       @NonNull WeakReference<TextView> counterRef,
                                       @NonNull String labelText, int done, int total) {
        ThreadUtils.postOnMainThread(() -> {
            LinearProgressIndicator progress = progressRef.get();
            TextView label = labelRef.get();
            TextView counter = counterRef.get();
            if (label != null) {
                label.setText(FmUtils.getArchiveEntryDisplayName(labelText));
            }
            if (counter != null) {
                counter.setText(String.format(Locale.getDefault(), "%d/%d", done, total));
            }
            if (progress != null) {
                int max = Math.max(total, 1);
                progress.setMax(max);
                progress.setProgress(Math.min(done, max));
                progress.setIndeterminate(false);
            }
        });
    }

    private void showArchiveErrorDialog(int titleRes, @NonNull Throwable throwable) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setPositiveButton(R.string.close, null);
        String message = formatArchiveErrorMessage(throwable);
        if (!TextUtils.isEmpty(message)) {
            builder.setMessage(message);
        }
        builder.show();
    }

    @VisibleForTesting
    @NonNull
    static String formatArchiveErrorMessage(@NonNull Throwable throwable) {
        return ExportTextUtils.toPlainTextReport(throwable.getLocalizedMessage()).trim();
    }

    @VisibleForTesting
    @NonNull
    static CharSequence getFolderErrorDisplayTitle(@NonNull Throwable throwable, @NonNull CharSequence fallback) {
        String title = FmUtils.getDisplayName(throwable.getMessage(), "");
        return !TextUtils.isEmpty(title) ? title : fallback;
    }

    private String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix, @Nullable String extension) {
        return findNextBestDisplayName(basePath, prefix, extension, 1);
    }

    private String findNextBestDisplayName(@NonNull Path basePath, @NonNull String prefix, @Nullable String extension, int startIndex) {
        if (TextUtils.isEmpty(extension)) {
            extension = "";
        } else extension = "." + extension;
        String displayName = prefix + extension;
        int i = startIndex;
        // We need to find the next best file name if current exists
        while (basePath.hasFile(displayName)) {
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, extension);
            ++i;
        }
        return displayName;
    }

    private class BatchOpsHandler implements MultiSelectionView.OnSelectionChangeListener {
        private final MenuItem mShareMenu;
        private final MenuItem mInstallApksMenu;
        private final MenuItem mFindDuplicateApksMenu;
        private final MenuItem mRenameMenu;
        private final MenuItem mDeleteMenu;
        private final MenuItem mCutMenu;
        private final MenuItem mCopyMenu;
        private final MenuItem mCreateArchiveMenu;
        private final MenuItem mExtractArchiveMenu;
        private final MenuItem mCopyPathsMenu;

        public BatchOpsHandler(@NonNull MultiSelectionView multiSelectionView) {
            Menu menu = multiSelectionView.getMenu();
            mShareMenu = menu.findItem(R.id.action_share);
            mInstallApksMenu = menu.findItem(R.id.action_install_selected_apks);
            mFindDuplicateApksMenu = menu.findItem(R.id.action_find_duplicate_apks);
            mRenameMenu = menu.findItem(R.id.action_rename);
            mDeleteMenu = menu.findItem(R.id.action_delete);
            mCutMenu = menu.findItem(R.id.action_cut);
            mCopyMenu = menu.findItem(R.id.action_copy);
            mCreateArchiveMenu = menu.findItem(R.id.action_create_archive);
            mExtractArchiveMenu = menu.findItem(R.id.action_extract_archive);
            mCopyPathsMenu = menu.findItem(R.id.action_copy_path);
        }

        @Override
        public boolean onSelectionChange(int selectionCount) {
            boolean nonZeroSelection = selectionCount > 0;
            boolean canRead = mFolderShortInfo != null && mFolderShortInfo.canRead;
            boolean canWrite = mFolderShortInfo != null && mFolderShortInfo.canWrite;
            List<Path> selectedItems = nonZeroSelection ? mModel.getSelectedItems() : Collections.emptyList();
            boolean allSelectedReadable = true;
            for (Path selectedItem : selectedItems) {
                if (!selectedItem.canRead()) {
                    allSelectedReadable = false;
                    break;
                }
            }
            boolean canExtractArchive = selectedItems.size() == 1
                    && allSelectedReadable
                    && canWrite
                    && FmArchiveUtils.isSupportedZip(selectedItems.get(0));
            boolean canInstallApks = canRead && FmBatchApkInstallUtils.canOfferInstall(selectedItems);
            boolean canFindDuplicateApks = canRead && canWrite
                    && FmDuplicateApkSelectionUtils.canOfferDuplicateScan(selectedItems);
            mShareMenu.setEnabled(nonZeroSelection && canRead);
            mInstallApksMenu.setEnabled(canInstallApks);
            mFindDuplicateApksMenu.setEnabled(canFindDuplicateApks);
            mRenameMenu.setEnabled(nonZeroSelection && canWrite);
            mDeleteMenu.setEnabled(nonZeroSelection && canWrite);
            mCutMenu.setEnabled(nonZeroSelection && canWrite);
            mCopyMenu.setEnabled(nonZeroSelection && canRead);
            mCreateArchiveMenu.setEnabled(nonZeroSelection && canRead && canWrite && allSelectedReadable);
            mExtractArchiveMenu.setEnabled(canExtractArchive);
            mCopyPathsMenu.setEnabled(nonZeroSelection);
            return false;
        }
    }
}
