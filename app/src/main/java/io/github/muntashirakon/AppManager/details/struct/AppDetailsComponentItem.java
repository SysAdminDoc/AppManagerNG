// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem<ComponentInfo> {
    public CharSequence label;
    public boolean canLaunch;

    private boolean mIsTracker;
    @Nullable
    private String mTrackerLabel;
    @Nullable
    private ComponentRule mRule;
    private boolean mIsDisabled;
    @NonNull
    private List<String> mIntentActions = Collections.emptyList();
    @NonNull
    private List<String> mIntentCategories = Collections.emptyList();

    public AppDetailsComponentItem(@NonNull ComponentInfo componentInfo) {
        super(componentInfo);
        name = componentInfo.name;
        mIsDisabled = !componentInfo.isEnabled();
    }

    public boolean isTracker() {
        return mIsTracker;
    }

    public void setTracker(boolean tracker) {
        mIsTracker = tracker;
    }

    @Nullable
    public String getTrackerLabel() {
        return mTrackerLabel;
    }

    public void setTrackerLabel(@Nullable String trackerLabel) {
        mTrackerLabel = trackerLabel;
    }

    public boolean isBlocked() {
        if (mRule == null) {
            return false;
        }
        return mRule.isBlocked() && (mRule.isIfw() || isDisabled());
    }

    @Nullable
    public ComponentRule getRule() {
        return mRule;
    }

    public void setRule(@Nullable ComponentRule rule) {
        mRule = rule;
    }

    public boolean isDisabled() {
        return mIsDisabled;
    }

    public void setDisabled(boolean disabled) {
        mIsDisabled = disabled;
    }

    @NonNull
    public List<String> getIntentActions() {
        return new ArrayList<>(mIntentActions);
    }

    public void setIntentActions(@NonNull List<String> intentActions) {
        mIntentActions = new ArrayList<>(intentActions);
    }

    @NonNull
    public List<String> getIntentCategories() {
        return new ArrayList<>(mIntentCategories);
    }

    public void setIntentCategories(@NonNull List<String> intentCategories) {
        mIntentCategories = new ArrayList<>(intentCategories);
    }
}
