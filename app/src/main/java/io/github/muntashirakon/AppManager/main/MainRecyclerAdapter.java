// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayShortToast;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.revert.OsRevertCountTracker;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AccessibilityUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

public class MainRecyclerAdapter extends MultiSelectionView.Adapter<MainRecyclerAdapter.ViewHolder>
        implements SectionIndexer {
    private static final String sSections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int STATE_STROKE_ALPHA = 0x80;
    private static final int BADGE_BACKGROUND_ALPHA = 0x2B;
    private static final int BADGE_STROKE_ALPHA = 0x7A;

    private final MainActivity mActivity;
    private String mSearchQuery;
    @Nullable
    private RecyclerView mRecyclerView;
    @Nullable
    private Parcelable mPreFilterScrollState;
    @GuardedBy("mAdapterList")
    private final List<ApplicationItem> mAdapterList = new ArrayList<>();

    private final int mColorGreen;
    private final int mColorOrange;
    private final int mColorPrimary;
    private final int mColorSecondary;
    private final int mColorError;
    private final int mColorTertiary;
    private final int mColorSelectedStroke;
    private final int mColorRegularStroke;
    private final int mQueryStringHighlight;

    MainRecyclerAdapter(@NonNull MainActivity activity) {
        super();
        mActivity = activity;
        mColorGreen = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.stopped);
        mColorOrange = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.orange);
        mColorPrimary = getThemeColor(activity, "colorOnSurface",
                ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.textColorPrimary));
        mColorSecondary = getThemeColor(activity, "colorOnSurfaceVariant",
                ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.textColorSecondary));
        mColorError = getThemeColor(activity, "colorError", Color.RED);
        mColorTertiary = getThemeColor(activity, "colorTertiary", mColorOrange);
        mColorSelectedStroke = getThemeColor(activity, "colorPrimary", mColorOrange);
        mColorRegularStroke = getThemeColor(activity, "colorOutlineVariant", Color.TRANSPARENT);
        mQueryStringHighlight = ColorCodes.getQueryStringHighlightColor(activity);
    }

    private static int getThemeColor(@NonNull Context context, @NonNull String attrName, int fallbackColor) {
        int attrId = context.getResources().getIdentifier(attrName, "attr", context.getPackageName());
        return attrId != 0 ? MaterialColors.getColor(context, attrId, fallbackColor) : fallbackColor;
    }

    @GuardedBy("mAdapterList")
    @UiThread
    void setDefaultList(List<ApplicationItem> list) {
        if (mActivity.viewModel == null) return;
        String oldSearchQuery = mSearchQuery;
        mSearchQuery = mActivity.viewModel.getSearchQuery();
        RecyclerView.LayoutManager layoutManager = mRecyclerView != null ? mRecyclerView.getLayoutManager() : null;
        boolean saveState = AdapterUtils.isStartingSearch(oldSearchQuery, mSearchQuery);
        boolean restoreState = AdapterUtils.isClearingSearch(oldSearchQuery, mSearchQuery);
        if (saveState && layoutManager != null) {
            mPreFilterScrollState = layoutManager.onSaveInstanceState();
        }
        boolean wasAtTop = isAtTop(layoutManager);
        synchronized (mAdapterList) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
            notifySelectionChange();
        }
        restoreScrollAfterFilter(layoutManager, wasAtTop, restoreState);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mRecyclerView = null;
    }

    @GuardedBy("mAdapterList")
    @Override
    public void cancelSelection() {
        super.cancelSelection();
        mActivity.viewModel.cancelSelection();
    }

    @Override
    public int getSelectedItemCount() {
        if (mActivity.viewModel == null) return 0;
        return mActivity.viewModel.getSelectedPackages().size();
    }

    @Override
    protected int getTotalItemCount() {
        if (mActivity.viewModel == null) return 0;
        return mActivity.viewModel.getApplicationItemCount();
    }

    @GuardedBy("mAdapterList")
    @Override
    protected boolean isSelected(int position) {
        synchronized (mAdapterList) {
            return mAdapterList.get(position).isSelected;
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    protected boolean select(int position) {
        synchronized (mAdapterList) {
            mAdapterList.set(position, mActivity.viewModel.select(mAdapterList.get(position)));
            return true;
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    protected boolean deselect(int position) {
        synchronized (mAdapterList) {
            mAdapterList.set(position, mActivity.viewModel.deselect(mAdapterList.get(position)));
            return true;
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void toggleSelection(int position) {
        synchronized (mAdapterList) {
            super.toggleSelection(position);
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void selectAll() {
        synchronized (mAdapterList) {
            mActivity.viewModel.selectOnlyApplicationItems(mAdapterList);
            notifySelectionChange();
            if (!mAdapterList.isEmpty()) {
                notifyItemRangeChanged(0, mAdapterList.size(), AdapterUtils.STUB);
            }
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void selectRange(int firstPosition, int secondPosition) {
        synchronized (mAdapterList) {
            super.selectRange(firstPosition, secondPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_main_v2, parent, false);
        return new ViewHolder(view);
    }

    @GuardedBy("mAdapterList")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ApplicationItem item;
        synchronized (mAdapterList) {
            item = mAdapterList.get(position);
        }
        MaterialCardView cardView = holder.itemView;
        Context context = cardView.getContext();
        // Add click listeners
        cardView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            ApplicationItem currentItem = getItemAtPosition(currentPosition);
            if (currentItem == null) {
                return;
            }
            // If selection mode is on, select/deselect the current item instead of the default behaviour
            if (isInSelectionMode()) {
                toggleSelection(currentPosition);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
                return;
            }
            handleClick(currentItem);
        });
        cardView.setOnLongClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) {
                return false;
            }
            // Long click listener: Select/deselect an app.
            // 1) Turn selection mode on if this is the first item in the selection list
            // 2) Select between last selection position and this position (inclusive) if selection mode is on
            synchronized (mAdapterList) {
                ApplicationItem lastSelectedItem = mActivity.viewModel.getLastSelectedPackage();
                int lastSelectedItemPosition = lastSelectedItem == null ? -1 : mAdapterList.indexOf(lastSelectedItem);
                if (lastSelectedItemPosition >= 0) {
                    // Select from last selection to this selection
                    selectRange(lastSelectedItemPosition, currentPosition);
                } else {
                    toggleSelection(currentPosition);
                    AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
                }
            }
            return true;
        });
        // Icon tap:
        //   - in selection mode: toggle selection (preserves the learned multi-select gesture)
        //   - out of selection mode: launch the app if it has a launcher activity, otherwise
        //     fall back to selection (so the gesture still does *something* useful for
        //     services-only/no-launcher packages instead of looking broken).
        // Long-pressing the card body still enters selection mode regardless.
        holder.icon.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;
            if (isInSelectionMode()) {
                toggleSelection(currentPosition);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
                return;
            }
            ApplicationItem currentItem = getItemAtPosition(currentPosition);
            if (currentItem == null) return;
            if (currentItem.isInstalled && !currentItem.isDisabled) {
                int userId = currentItem.userIds != null && currentItem.userIds.length > 0
                        ? currentItem.userIds[0]
                        : UserHandleHidden.myUserId();
                Intent launchIntent = io.github.muntashirakon.AppManager.compat.PackageManagerCompat
                        .getLaunchIntentForPackage(currentItem.packageName, userId);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        mActivity.startActivity(launchIntent);
                        return;
                    } catch (Throwable t) {
                        UIUtils.displayLongToast(R.string.failed_to_launch_app);
                        return;
                    }
                }
            }
            // No launcher activity (or app is disabled/uninstalled): fall back to selection
            toggleSelection(currentPosition);
            AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
        });
        // Long-pressing the icon shows a per-app quick-actions popup (App info / Open /
        // App Manager details / Uninstall / Block trackers). Bypasses selection mode
        // for users who want to act on a single app fast.
        holder.icon.setOnLongClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return false;
            ApplicationItem currentItem = getItemAtPosition(currentPosition);
            if (currentItem == null) return false;
            showQuickActionsPopup(holder.icon, currentItem);
            return true;
        });
        // Box-stroke colors: selected > uninstalled > disabled > force-stopped > regular
        if (item.isSelected) {
            cardView.setStrokeColor(mColorSelectedStroke);
        } else if (!item.isInstalled) {
            cardView.setStrokeColor(getStateStrokeColor(ColorCodes.getAppUninstalledIndicatorColor(context)));
        } else if (item.isDisabled) {
            cardView.setStrokeColor(getStateStrokeColor(ColorCodes.getAppDisabledIndicatorColor(context)));
        } else if (item.isStopped) {
            cardView.setStrokeColor(getStateStrokeColor(ColorCodes.getAppForceStoppedIndicatorColor(context)));
        } else {
            cardView.setStrokeColor(mColorRegularStroke);
        }
        // Display yellow star if the app is in debug mode
        holder.debugIcon.setVisibility(item.debuggable ? View.VISIBLE : View.GONE);
        // Set date and (if available,) days between first installation and last update
        String lastUpdateDate = DateUtils.formatDate(context, item.lastUpdateTime);
        if (item.firstInstallTime == item.lastUpdateTime) {
            holder.date.setText(lastUpdateDate);
        } else {
            long days = item.diffInstallUpdateInDays;
            SpannableString ssDate = new SpannableString(context.getResources()
                    .getQuantityString(R.plurals.main_list_date_days, (int) days, lastUpdateDate, days));
            ssDate.setSpan(new RelativeSizeSpan(.8f), lastUpdateDate.length(),
                    ssDate.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.date.setText(ssDate);
        }
        // Set date color to orange if app can read logs (and accepted)
        holder.date.setTextColor(item.canReadLogs ? mColorOrange : mColorSecondary);
        if (item.isInstalled) {
            // Set UID
            String userStateSummary = buildUserStateSummary(context, item);
            if (!TextUtils.isEmpty(userStateSummary)) {
                holder.userId.setText(userStateSummary);
            } else if (item.uidOrAppIds != null) {
                holder.userId.setText(item.uidOrAppIds);
            }
            // Set UID text color to orange if the package is shared
            holder.userId.setTextColor(item.sharedUserId != null
                    || item.disabledUserIds.length > 0
                    || item.uninstalledUserIds.length > 0
                    ? mColorOrange : mColorSecondary);
        } else holder.userId.setText("");
        if (item.sha != null) {
            // Set issuer
            holder.issuer.setVisibility(View.VISIBLE);
            holder.issuer.setText(item.issuerShortName);
            // Set signature type
            holder.sha.setVisibility(View.VISIBLE);
            holder.sha.setText(item.sha.second);
        } else {
            holder.issuer.setVisibility(View.GONE);
            holder.sha.setVisibility(View.GONE);
        }
        // Load app icon
        holder.icon.setTag(item.packageName);
        holder.icon.setContentDescription(context.getString(item.isInstalled && !item.isDisabled
                ? R.string.main_list_open_app
                : R.string.main_list_select_app, item.label));
        ImageLoader.getInstance().displayImage(item.packageName, item, holder.icon);
        // T21-G attention badge — a single severity-tinted dot when the row has
        // actionable state. Recent-OS-revert count is not yet wired into the
        // cache, so it is passed as 0 for now (the perm/disabled signals already
        // drive the badge); the OsRevertCountTracker hook is a follow-up.
        bindAttentionBadge(holder, item);
        // Set app label
        if (!TextUtils.isEmpty(mSearchQuery) && item.label.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.label.setText(UIUtils.getHighlightedText(item.label, mSearchQuery, mQueryStringHighlight));
        } else holder.label.setText(item.label);
        // Set app label color to red if clearing user data not allowed
        if (item.isInstalled && !item.allowClearingUserData) {
            holder.label.setTextColor(mColorError);
        } else holder.label.setTextColor(mColorPrimary);
        // Set package name
        if (!TextUtils.isEmpty(mSearchQuery) && item.packageName.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.packageName.setText(UIUtils.getHighlightedText(item.packageName, mSearchQuery, mQueryStringHighlight));
        } else holder.packageName.setText(item.packageName);
        // Set package name color + show a tracker badge. The visible label carries
        // enough context to scan without opening details; the tint encodes severity:
        // green <= 4, amber 5-19, red >= 20, and green when every tracker is blocked.
        if (item.trackerCount > 0) {
            int blocked = item.trackerBlockedCount != null ? item.trackerBlockedCount : 0;
            // clamp: blocked count comes from the rules db (which may have stale
            // entries for components no longer in the manifest), so cap at total.
            if (blocked > item.trackerCount) blocked = item.trackerCount;
            boolean allBlocked = blocked > 0 && blocked >= item.trackerCount;
            int trackerColor = allBlocked
                    ? ColorCodes.getComponentTrackerBlockedIndicatorColor(context)
                    : ColorCodes.getTrackerRiskIndicatorColor(context, item.trackerCount);
            holder.packageName.setTextColor(trackerColor);
            if (holder.trackerIndicator != null) {
                holder.trackerIndicator.setVisibility(View.VISIBLE);
                String label;
                String contentDesc;
                if (allBlocked) {
                    label = context.getResources().getQuantityString(
                            R.plurals.main_list_tracker_badge_all_blocked,
                            item.trackerCount, item.trackerCount);
                    contentDesc = context.getResources().getQuantityString(
                            R.plurals.main_list_tracker_count_all_blocked_a11y,
                            item.trackerCount, item.trackerCount);
                } else if (blocked > 0) {
                    label = context.getString(R.string.main_list_tracker_badge_partial,
                            blocked, item.trackerCount);
                    contentDesc = context.getString(
                            R.string.main_list_tracker_count_partial_a11y, blocked, item.trackerCount);
                } else {
                    label = context.getResources().getQuantityString(
                            R.plurals.main_list_tracker_badge_label,
                            item.trackerCount, item.trackerCount);
                    contentDesc = context.getResources().getQuantityString(
                            R.plurals.main_list_tracker_count_badge_a11y, item.trackerCount, item.trackerCount);
                }
                holder.trackerIndicator.setText(label);
                applyBadgeStyle(holder.trackerIndicator, trackerColor,
                        getTrackerBadgeTextColor(context, allBlocked, item.trackerCount));
                holder.trackerIndicator.setContentDescription(contentDesc);
                if (item.isInstalled) {
                    holder.trackerIndicator.setClickable(true);
                    holder.trackerIndicator.setFocusable(true);
                    holder.trackerIndicator.setOnClickListener(v -> {
                        int userId = item.userIds != null && item.userIds.length > 0
                                ? item.userIds[0]
                                : UserHandleHidden.myUserId();
                        Intent intent = AppDetailsActivity.getIntentForTrackers(
                                mActivity, item.packageName, userId);
                        mActivity.startActivity(intent);
                    });
                } else {
                    holder.trackerIndicator.setClickable(false);
                    holder.trackerIndicator.setOnClickListener(null);
                }
            }
        } else {
            holder.packageName.setTextColor(mColorSecondary);
            if (holder.trackerIndicator != null) {
                holder.trackerIndicator.setVisibility(View.GONE);
                holder.trackerIndicator.setOnClickListener(null);
                holder.trackerIndicator.setClickable(false);
            }
        }
        // Dangerous-permission badge: shows "G/T perms" and tints by grant ratio so
        // the row communicates trust posture without relying on numbers alone.
        if (holder.permIndicator != null) {
            int permTotal = item.dangerousPermTotal != null ? item.dangerousPermTotal : 0;
            int permGranted = item.dangerousPermGranted != null ? item.dangerousPermGranted : 0;
            if (permTotal > 0) {
                holder.permIndicator.setVisibility(View.VISIBLE);
                holder.permIndicator.setText(context.getString(
                        R.string.main_list_perm_badge_label, permGranted, permTotal));
                int permColor = ColorCodes.getPermissionRiskIndicatorColor(context, permGranted, permTotal);
                applyBadgeStyle(holder.permIndicator, permColor,
                        getPermissionBadgeTextColor(context, permGranted, permTotal));
                holder.permIndicator.setContentDescription(context.getString(
                        R.string.main_list_perm_count_a11y, permGranted, permTotal));
                if (item.isInstalled) {
                    holder.permIndicator.setClickable(true);
                    holder.permIndicator.setFocusable(true);
                    holder.permIndicator.setOnClickListener(v -> {
                        int userId = item.userIds != null && item.userIds.length > 0
                                ? item.userIds[0]
                                : UserHandleHidden.myUserId();
                        Intent intent = AppDetailsActivity.getIntentForPermissions(
                                mActivity, item.packageName, userId);
                        mActivity.startActivity(intent);
                    });
                } else {
                    holder.permIndicator.setClickable(false);
                    holder.permIndicator.setOnClickListener(null);
                }
            } else {
                holder.permIndicator.setVisibility(View.GONE);
                holder.permIndicator.setOnClickListener(null);
                holder.permIndicator.setClickable(false);
            }
        }
        installBadgeTouchDelegate(holder);
        bindTagIndicator(context, holder, item);
        // Set version (along with HW accelerated, debug and test only flags)
        holder.version.setText(item.versionTag);
        // Set version color to dark cyan if the app is inactive
        holder.version.setTextColor(item.isAppInactive ? mColorGreen : mColorSecondary);
        // Set app type: system or user app (along with large heap, suspended, multi-arch,
        // has code, vm safe mode)
        if (item.isInstalled) {
            String appType = context.getString(item.isSystem ? R.string.system : R.string.user);
            String isSystemApp = TextUtils.isEmpty(item.appTypePostfix)
                    ? appType
                    : appType + " \u00b7 " + item.appTypePostfix;
            holder.isSystemApp.setText(isSystemApp);
        } else {
            holder.isSystemApp.setText("-");
        }
        // Set app type text color to magenta if the app is persistent
        holder.isSystemApp.setTextColor(item.isPersistent ? mColorTertiary : mColorSecondary);
        // Set SDK
        if (item.sdkString != null) {
            holder.size.setText(item.sdkString);
        } else holder.size.setText("-");
        // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
        holder.size.setTextColor(item.usesCleartextTraffic ? mColorOrange : mColorSecondary);
        // Check for backup
        if (item.backup != null) {
            holder.backupIndicator.setVisibility(View.VISIBLE);
            holder.backupInfo.setVisibility(View.VISIBLE);
            holder.backupInfoExt.setVisibility(View.VISIBLE);
            holder.backupIndicator.setText(R.string.backup);
            int indicatorColor;
            if (item.isInstalled) {
                if (item.backup.versionCode >= item.versionCode) {
                    // Up-to-date backup
                    indicatorColor = ColorCodes.getBackupLatestIndicatorColor(context);
                } else {
                    // Outdated backup
                    indicatorColor = ColorCodes.getBackupOutdatedIndicatorColor(context);
                }
            } else {
                // App not installed
                indicatorColor = ColorCodes.getBackupUninstalledIndicatorColor(context);
            }
            applyBadgeStyle(holder.backupIndicator, indicatorColor,
                    getBackupBadgeTextColor(context, item.isInstalled, item.backup.versionCode >= item.versionCode));
            Backup backup = item.backup;
            long days = item.lastBackupDays;
            holder.backupInfo.setText(String.format("%s: %s, %s %s",
                    context.getString(R.string.latest_backup), context.getResources()
                            .getQuantityString(R.plurals.usage_days, (int) days, days),
                    context.getString(R.string.version), backup.versionName));
            holder.backupInfoExt.setText(item.backupFlagsStr);
        } else {
            holder.backupIndicator.setVisibility(View.GONE);
            holder.backupInfo.setVisibility(View.GONE);
            holder.backupInfoExt.setVisibility(View.GONE);
        }
        String installedState = context.getString(item.isInstalled
                ? R.string.main_list_installed_state : R.string.main_list_uninstalled_state);
        String sdkState = item.sdkString != null ? item.sdkString : "-";
        String contentDescription = context.getString(R.string.main_list_app_content_description,
                item.label, installedState, item.packageName, item.versionTag, sdkState);
        if (!item.userTags.isEmpty()) {
            contentDescription += " " + context.getString(R.string.main_list_tag_badge_a11y,
                    MainListTagChipFormatter.summaryFor(item.userTags));
        }
        cardView.setContentDescription(contentDescription);
        super.onBindViewHolder(holder, position);
    }

    private void bindTagIndicator(@NonNull Context context, @NonNull ViewHolder holder,
                                  @NonNull ApplicationItem item) {
        if (holder.tagIndicator == null) {
            return;
        }
        if (item.userTags.isEmpty()) {
            holder.tagIndicator.setVisibility(View.GONE);
            holder.tagIndicator.setText("");
            holder.tagIndicator.setContentDescription(null);
            holder.tagIndicator.setOnClickListener(null);
            holder.tagIndicator.setClickable(false);
            holder.tagIndicator.setFocusable(false);
            return;
        }
        String summary = MainListTagChipFormatter.summaryFor(item.userTags);
        holder.tagIndicator.setVisibility(View.VISIBLE);
        holder.tagIndicator.setText(MainListTagChipFormatter.labelFor(item.userTags));
        holder.tagIndicator.setContentDescription(context.getString(R.string.main_list_tag_badge_a11y, summary));
        holder.tagIndicator.setOnClickListener(null);
        holder.tagIndicator.setClickable(false);
        holder.tagIndicator.setFocusable(false);
        applyBadgeStyle(holder.tagIndicator, mColorSelectedStroke);
    }

    @Nullable
    private ApplicationItem getItemAtPosition(int position) {
        if (position == RecyclerView.NO_POSITION) {
            return null;
        }
        synchronized (mAdapterList) {
            if (position < 0 || position >= mAdapterList.size()) {
                return null;
            }
            return mAdapterList.get(position);
        }
    }

    private static boolean isAtTop(@Nullable RecyclerView.LayoutManager layoutManager) {
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return false;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return false;
        }
        View topView = layoutManager.findViewByPosition(firstVisibleItem);
        int topOffset = topView != null ? topView.getTop() : 0;
        return firstVisibleItem == 0 && topOffset >= 0;
    }

    private void restoreScrollAfterFilter(@Nullable RecyclerView.LayoutManager layoutManager,
                                          boolean wasAtTop,
                                          boolean restoreState) {
        if (mRecyclerView == null || layoutManager == null) {
            return;
        }
        mRecyclerView.post(() -> {
            if (mRecyclerView == null || mRecyclerView.getLayoutManager() != layoutManager) {
                return;
            }
            if (wasAtTop) {
                layoutManager.scrollToPosition(0);
                if (restoreState) {
                    mPreFilterScrollState = null;
                }
            } else if (restoreState && mPreFilterScrollState != null) {
                layoutManager.onRestoreInstanceState(mPreFilterScrollState);
                mPreFilterScrollState = null;
            }
        });
    }

    @GuardedBy("mAdapterList")
    @Override
    public long getItemId(int position) {
        synchronized (mAdapterList) {
            return mAdapterList.get(position).hashCode();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public int getItemCount() {
        synchronized (mAdapterList) {
            return mAdapterList.size();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public int getPositionForSection(int section) {
        synchronized (mAdapterList) {
            for (int i = 0; i < getItemCount(); i++) {
                String item = mAdapterList.get(i).label;
                if (!item.isEmpty()) {
                    if (item.charAt(0) == sSections.charAt(section))
                        return i;
                }
            }
            return 0;
        }
    }

    @Override
    public int getSectionForPosition(int i) {
        return 0;
    }

    @Override
    public Object[] getSections() {
        String[] sectionsArr = new String[sSections.length()];
        for (int i = 0; i < sSections.length(); i++)
            sectionsArr[i] = String.valueOf(sSections.charAt(i));

        return sectionsArr;
    }

    @ColorInt
    private static int getStateStrokeColor(@ColorInt int color) {
        return ColorUtils.setAlphaComponent(color, STATE_STROKE_ALPHA);
    }

    @ColorInt
    @VisibleForTesting
    static int getTrackerBadgeTextColor(@NonNull Context context, boolean allBlocked, int trackerCount) {
        if (allBlocked || trackerCount < 5) {
            return getBadgeSuccessContentColor(context);
        }
        if (trackerCount >= 20) {
            return getBadgeDangerContentColor(context);
        }
        return getBadgeWarningContentColor(context);
    }

    @ColorInt
    @VisibleForTesting
    static int getPermissionBadgeTextColor(@NonNull Context context, int granted, int total) {
        if (granted <= 0 || total <= 0) {
            return getBadgeSuccessContentColor(context);
        }
        float grantRatio = granted / (float) total;
        if (grantRatio >= 0.5f || granted >= 5) {
            return getBadgeDangerContentColor(context);
        }
        if (grantRatio >= 0.25f || granted >= 2) {
            return getBadgeWarningContentColor(context);
        }
        return getBadgeSuccessContentColor(context);
    }

    @ColorInt
    @VisibleForTesting
    static int getBackupBadgeTextColor(@NonNull Context context, boolean installed, boolean latestBackup) {
        if (!installed) {
            return getBadgeDangerContentColor(context);
        }
        return latestBackup ? getBadgeSuccessContentColor(context) : getBadgeWarningContentColor(context);
    }

    @ColorInt
    private static int getBadgeSuccessContentColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.premium_success_content);
    }

    @ColorInt
    private static int getBadgeWarningContentColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.premium_warning_content);
    }

    @ColorInt
    private static int getBadgeDangerContentColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.premium_danger_content);
    }

    private static void applyBadgeStyle(@NonNull TextView badge, @ColorInt int contentColor) {
        applyBadgeStyle(badge, contentColor, ColorUtils.setAlphaComponent(contentColor, 0xFF));
    }

    private static void applyBadgeStyle(@NonNull TextView badge, @ColorInt int contentColor,
                                        @ColorInt int textColor) {
        int opaqueContentColor = ColorUtils.setAlphaComponent(contentColor, 0xFF);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(ColorUtils.setAlphaComponent(opaqueContentColor, BADGE_BACKGROUND_ALPHA));
        background.setStroke(
                badge.getResources().getDimensionPixelSize(R.dimen.main_list_badge_stroke_width),
                ColorUtils.setAlphaComponent(opaqueContentColor, BADGE_STROKE_ALPHA));
        background.setCornerRadius(
                badge.getResources().getDimensionPixelSize(R.dimen.main_list_badge_corner_radius));
        badge.setBackground(background);
        badge.setTextColor(textColor);
    }

    /**
     * Per-app quick-actions popup anchored to the card icon, triggered by long-press
     * on the icon. Items render conditionally based on app state so the menu is never
     * misleading: launch / Android settings only show for installed apps; tracker
     * block only when the app has known tracker components.
     *
     * <p>Note: deliberately uses Android-stdlib intents (ACTION_DELETE,
     * ACTION_APPLICATION_DETAILS_SETTINGS, launcher intent) instead of routing
     * through the privileged BatchOpsManager path. The latter requires Root /
     * Shizuku / ADB and the popup is meant to work for everyone — privileged ops
     * remain accessible via the existing long-press-card multi-select toolbar.
     */
    private void showQuickActionsPopup(@NonNull View anchor, @NonNull ApplicationItem item) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(mActivity, anchor);
        popup.getMenuInflater().inflate(R.menu.main_quick_actions, popup.getMenu());
        boolean canLaunch = item.isInstalled && !item.isDisabled
                && io.github.muntashirakon.AppManager.compat.PackageManagerCompat
                        .getLaunchIntentForPackage(item.packageName,
                                item.userIds != null && item.userIds.length > 0
                                        ? item.userIds[0]
                                        : UserHandleHidden.myUserId()) != null;
        popup.getMenu().findItem(R.id.action_quick_open).setVisible(canLaunch);
        popup.getMenu().findItem(R.id.action_quick_app_info_system).setVisible(item.isInstalled);
        popup.getMenu().findItem(R.id.action_quick_uninstall).setVisible(item.isInstalled);
        popup.getMenu().findItem(R.id.action_quick_block_trackers).setVisible(
                item.isInstalled && item.trackerCount != null && item.trackerCount > 0);
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            int userId = item.userIds != null && item.userIds.length > 0
                    ? item.userIds[0]
                    : UserHandleHidden.myUserId();
            if (id == R.id.action_quick_open) {
                Intent launch = io.github.muntashirakon.AppManager.compat.PackageManagerCompat
                        .getLaunchIntentForPackage(item.packageName, userId);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        mActivity.startActivity(launch);
                    } catch (Throwable t) {
                        UIUtils.displayLongToast(R.string.failed_to_launch_app);
                    }
                }
                return true;
            } else if (id == R.id.action_quick_details) {
                mActivity.startActivity(io.github.muntashirakon.AppManager.details.AppDetailsActivity
                        .getIntent(mActivity, item.packageName, userId));
                return true;
            } else if (id == R.id.action_quick_app_info_system) {
                Intent settings = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                settings.setData(android.net.Uri.parse("package:" + item.packageName));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    mActivity.startActivity(settings);
                } catch (Throwable t) {
                    UIUtils.displayLongToast(R.string.error);
                }
                return true;
            } else if (id == R.id.action_quick_uninstall) {
                ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_uninstall, () -> {
                    Intent uninstall = new Intent(Intent.ACTION_DELETE);
                    uninstall.setData(android.net.Uri.parse("package:" + item.packageName));
                    uninstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        mActivity.startActivity(uninstall);
                    } catch (Throwable t) {
                        UIUtils.displayLongToast(R.string.error);
                    }
                });
                return true;
            } else if (id == R.id.action_quick_block_trackers) {
                io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnBackgroundThread(() -> {
                    try {
                        io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
                                .blockTrackingComponents(new io.github.muntashirakon.AppManager.types
                                        .UserPackagePair(item.packageName, userId));
                        io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnMainThread(() ->
                                UIUtils.displayShortToast(R.string.trackers_blocked_successfully));
                    } catch (Throwable t) {
                        io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnMainThread(() ->
                                UIUtils.displayLongToast(R.string.failed_to_block_trackers));
                    }
                });
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void handleClick(@NonNull ApplicationItem item) {
        if (!item.isInstalled || item.userIds.length == 0) {
            // The app should not be installed. But make sure this is really true. (For current user only)
            ApplicationInfo info;
            try {
                info = PackageManagerCompat.getApplicationInfo(item.packageName, MATCH_UNINSTALLED_PACKAGES
                                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                        UserHandleHidden.myUserId());
            } catch (RemoteException | PackageManager.NameNotFoundException e) {
                showBackupRestoreDialogOrAppNotInstalled(item);
                return;
            }
            // 1. Check if the app was really uninstalled.
            if (ApplicationInfoCompat.isInstalled(info)) {
                // The app is already installed, and we were wrong to assume that it was installed.
                // Update data before opening it.
                item.isInstalled = true;
                item.isOnlyDataInstalled = false;
                item.userIds = new int[]{UserHandleHidden.myUserId()};
                Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, UserHandleHidden.myUserId());
                mActivity.startActivity(intent);
                return;
            }
            // 2. If the app can be installed, offer it to install again.
            if (FeatureController.isInstallerEnabled()) {
                if (ApplicationInfoCompat.isSystemApp(info) && SelfPermissions.canInstallExistingPackages()) {
                    // Install existing app instead of installing as an update
                    mActivity.startActivity(PackageInstallerActivity.getLaunchableInstance(mActivity, item.packageName));
                    return;
                }
                // Otherwise, try with APK files
                Intent reinstallIntent = getReinstallApkSourceIntent(mActivity, info);
                if (reinstallIntent != null) {
                    mActivity.startActivity(reinstallIntent);
                    return;
                }
            }
            // 3. The app might be uninstalled without clearing data
            if (ApplicationInfoCompat.isSystemApp(info)) {
                // The app is a system app, there's no point in asking to uninstall it again
                showBackupRestoreDialogOrAppNotInstalled(item);
                return;
            }
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(mActivity.getString(R.string.uninstall_app, item.label))
                    .setMessage(R.string.uninstall_app_again_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, (dialog, which) ->
                            ActionAuthGate.authenticate(mActivity, R.string.authenticate_to_uninstall,
                                    () -> ThreadUtils.postOnBackgroundThread(() -> {
                                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                                        installer.setAppLabel(item.label);
                                        boolean uninstalled = installer.uninstall(item.packageName, UserHandleHidden.myUserId(), false);
                                        ThreadUtils.postOnMainThread(() -> {
                                            if (uninstalled) {
                                                displayLongToast(R.string.uninstalled_successfully, item.label);
                                            } else {
                                                displayLongToast(R.string.failed_to_uninstall, item.label);
                                            }
                                        });
                                    })))
                    .show();
            return;
        }
        // The app is installed
        if (item.userIds.length == 1) {
            int[] userHandles = Users.getUsersIds();
            if (ArrayUtils.contains(userHandles, item.userIds[0])) {
                Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[0]);
                mActivity.startActivity(intent);
                return;
            }
            // Outside our jurisdiction
            showBackupRestoreDialogOrAppNotInstalled(item);
            return;
        }
        // More than a user, ask the user to select one
        CharSequence[] userNames = new String[item.userIds.length];
        List<UserInfo> users = Users.getUsers();
        for (UserInfo info : users) {
            for (int i = 0; i < item.userIds.length; ++i) {
                if (info.id == item.userIds[i]) {
                    userNames[i] = mActivity.getString(R.string.package_user_state_dialog_label,
                            info.toLocalizedString(mActivity),
                            getPackageStateLabel(mActivity, item, item.userIds[i]));
                }
            }
        }
        for (int i = 0; i < userNames.length; ++i) {
            if (userNames[i] == null) {
                userNames[i] = mActivity.getString(R.string.package_user_state_dialog_label,
                        mActivity.getString(R.string.user_with_id, item.userIds[i]),
                        getPackageStateLabel(mActivity, item, item.userIds[i]));
            }
        }
        new SearchableItemsDialogBuilder<>(mActivity, userNames)
                .setTitle(R.string.select_user)
                .setOnItemClickListener((dialog, which, item1) -> {
                    Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[which]);
                    mActivity.startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showBackupRestoreDialogOrAppNotInstalled(@NonNull ApplicationItem item) {
        if (item.backup == null) {
            // No backups
            displayShortToast(R.string.app_not_installed);
            return;
        }
        // Has backups
        BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(
                Collections.singletonList(new UserPackagePair(
                        item.packageName, UserHandleHidden.myUserId())));
        fragment.setOnActionBeginListener(mode -> mActivity.showProgressIndicator(true));
        fragment.setOnActionCompleteListener((mode, failedPackages) -> mActivity.showProgressIndicator(false));
        fragment.show(mActivity.getSupportFragmentManager(), BackupRestoreDialogFragment.TAG);
    }

    @Nullable
    static Intent getReinstallApkSourceIntent(@NonNull Context context, @NonNull ApplicationInfo info) {
        if (info.publicSourceDir == null || !Paths.exists(info.publicSourceDir)) {
            return null;
        }
        return PackageInstallerActivity.getLaunchableInstance(context, ApkSource.getApkSource(info));
    }

    @NonNull
    private static String buildUserStateSummary(@NonNull Context context, @NonNull ApplicationItem item) {
        if (!item.hasPerUserPackageState()) {
            return "";
        }
        List<String> userStates = new ArrayList<>(item.userIds.length + item.uninstalledUserIds.length);
        appendUserStates(context, userStates, item.enabledUserIds, R.string.package_state_enabled);
        appendUserStates(context, userStates, item.disabledUserIds, R.string.package_state_disabled);
        appendUserStates(context, userStates, item.uninstalledUserIds, R.string.package_state_not_installed);
        return TextUtils.join(", ", userStates).toString();
    }

    private static void appendUserStates(@NonNull Context context, @NonNull List<String> userStates,
                                         @NonNull int[] userIds, int stateRes) {
        String state = context.getString(stateRes);
        for (int userId : userIds) {
            userStates.add(context.getString(R.string.package_user_state_short, userId, state));
        }
    }

    @NonNull
    private static String getPackageStateLabel(@NonNull Context context, @NonNull ApplicationItem item,
                                               int userId) {
        if (ArrayUtils.contains(item.disabledUserIds, userId)) {
            return context.getString(R.string.package_state_disabled);
        }
        if (ArrayUtils.contains(item.enabledUserIds, userId)) {
            return context.getString(R.string.package_state_enabled);
        }
        if (ArrayUtils.contains(item.uninstalledUserIds, userId)) {
            return context.getString(R.string.package_state_not_installed);
        }
        return context.getString(R.string.state_unknown);
    }

    // T21-G: render the single prioritised attention badge (or hide it) for a row.
    private void bindAttentionBadge(@NonNull ViewHolder holder, @NonNull ApplicationItem item) {
        if (holder.attentionBadge == null) {
            return;
        }
        int osRevertCount = item.packageName != null
                ? OsRevertCountTracker.getInstance().countRecent(item.packageName,
                        System.currentTimeMillis(), OsRevertCountTracker.DEFAULT_TTL_MILLIS)
                : 0;
        AttentionBadgeCalculator.Badge badge = AttentionBadgeSource.badgeFor(item, osRevertCount);
        if (badge.isNone()) {
            holder.attentionBadge.setVisibility(View.GONE);
            holder.attentionBadge.setContentDescription(null);
            return;
        }
        int colorAttr = badge.severity == AttentionBadgeCalculator.Severity.WARN
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorTertiary;
        int fallback = badge.severity == AttentionBadgeCalculator.Severity.WARN
                ? 0xFFB3261E : 0xFF7D5260;
        int color = MaterialColors.getColor(holder.attentionBadge, colorAttr, fallback);
        holder.attentionBadge.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        holder.attentionBadge.setVisibility(View.VISIBLE);
        Context ctx = holder.attentionBadge.getContext();
        int reasonRes;
        switch (badge.kind) {
            case OS_REVERT:
                reasonRes = R.string.attention_badge_os_revert;
                break;
            case DANGEROUS_PERMISSION:
                reasonRes = R.string.attention_badge_dangerous_permission;
                break;
            case DISABLED_COMPONENT:
            default:
                reasonRes = R.string.attention_badge_disabled_component;
                break;
        }
        holder.attentionBadge.setContentDescription(ctx.getString(R.string.attention_badge_content_description,
                AttentionBadgeCalculator.formatCount(badge.count), ctx.getString(reasonRes)));
    }

    private static void installBadgeTouchDelegate(@NonNull ViewHolder holder) {
        holder.itemView.post(() -> {
            int minTouchSize = holder.itemView.getResources()
                    .getDimensionPixelSize(R.dimen.premium_chip_touch_target);
            List<TouchDelegate> delegates = new ArrayList<>(2);
            addBadgeTouchDelegate(holder.itemView, delegates, holder.trackerIndicator, minTouchSize);
            addBadgeTouchDelegate(holder.itemView, delegates, holder.permIndicator, minTouchSize);
            holder.itemView.setTouchDelegate(delegates.isEmpty()
                    ? null
                    : new CompositeTouchDelegate(holder.itemView, delegates));
        });
    }

    private static void addBadgeTouchDelegate(@NonNull ViewGroup delegateParent,
                                              @NonNull List<TouchDelegate> delegates,
                                              @Nullable View target,
                                              int minTouchSize) {
        if (target == null || target.getVisibility() != View.VISIBLE || !target.isClickable()) {
            return;
        }
        Rect bounds = getTouchDelegateBounds(delegateParent, target, minTouchSize);
        delegates.add(new TouchDelegate(bounds, target));
    }

    @NonNull
    @VisibleForTesting
    static Rect getTouchDelegateBounds(@NonNull ViewGroup delegateParent, @NonNull View target, int minTouchSize) {
        Rect bounds = new Rect();
        target.getDrawingRect(bounds);
        delegateParent.offsetDescendantRectToMyCoords(target, bounds);
        expandTouchRectToMinimum(bounds, minTouchSize);
        return bounds;
    }

    @VisibleForTesting
    static void expandTouchRectToMinimum(@NonNull Rect bounds, int minSize) {
        int missingWidth = Math.max(0, minSize - bounds.width());
        int missingHeight = Math.max(0, minSize - bounds.height());
        int left = missingWidth / 2;
        int top = missingHeight / 2;
        bounds.left -= left;
        bounds.right += missingWidth - left;
        bounds.top -= top;
        bounds.bottom += missingHeight - top;
    }

    private static final class CompositeTouchDelegate extends TouchDelegate {
        @NonNull
        private final List<TouchDelegate> mDelegates;

        CompositeTouchDelegate(@NonNull View delegateParent, @NonNull List<TouchDelegate> delegates) {
            super(new Rect(), delegateParent);
            mDelegates = delegates;
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            for (TouchDelegate delegate : mDelegates) {
                if (delegate.onTouchEvent(event)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        MaterialCardView itemView;
        AppCompatImageView icon;
        AppCompatImageView attentionBadge;
        AppCompatImageView debugIcon;
        TextView label;
        TextView packageName;
        TextView version;
        TextView isSystemApp;
        TextView date;
        TextView size;
        TextView userId;
        TextView issuer;
        TextView sha;
        TextView backupIndicator;
        TextView backupInfo;
        TextView backupInfoExt;
        TextView trackerIndicator;
        TextView permIndicator;
        TextView tagIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            attentionBadge = itemView.findViewById(R.id.attention_badge);
            debugIcon = itemView.findViewById(R.id.debug_indicator);
            label = itemView.findViewById(R.id.label);
            packageName = itemView.findViewById(R.id.packageName);
            version = itemView.findViewById(R.id.version);
            isSystemApp = itemView.findViewById(R.id.isSystem);
            date = itemView.findViewById(R.id.date);
            size = itemView.findViewById(R.id.size);
            userId = itemView.findViewById(R.id.shareid);
            issuer = itemView.findViewById(R.id.issuer);
            sha = itemView.findViewById(R.id.sha);
            backupIndicator = itemView.findViewById(R.id.backup_indicator);
            backupInfo = itemView.findViewById(R.id.backup_info);
            backupInfoExt = itemView.findViewById(R.id.backup_info_ext);
            trackerIndicator = itemView.findViewById(R.id.tracker_indicator);
            permIndicator = itemView.findViewById(R.id.perm_indicator);
            tagIndicator = itemView.findViewById(R.id.tag_indicator);
        }
    }
}
