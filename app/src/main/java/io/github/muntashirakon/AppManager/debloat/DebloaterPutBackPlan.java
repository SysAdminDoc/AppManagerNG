// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

final class DebloaterPutBackPlan {
    private static final int MAX_EXAMPLES = 5;

    @NonNull
    private final List<DebloatObject> mRestorableObjects;
    @NonNull
    private final List<DebloatObject> mSkippedInstalledObjects;

    private DebloaterPutBackPlan(@NonNull List<DebloatObject> restorableObjects,
                                 @NonNull List<DebloatObject> skippedInstalledObjects) {
        mRestorableObjects = Collections.unmodifiableList(restorableObjects);
        mSkippedInstalledObjects = Collections.unmodifiableList(skippedInstalledObjects);
    }

    @NonNull
    static DebloaterPutBackPlan fromSelection(@NonNull List<DebloatObject> selectedObjects) {
        List<DebloatObject> restorableObjects = new ArrayList<>();
        List<DebloatObject> skippedInstalledObjects = new ArrayList<>();
        for (DebloatObject selectedObject : selectedObjects) {
            if (isRestorable(selectedObject)) {
                restorableObjects.add(selectedObject);
            } else {
                skippedInstalledObjects.add(selectedObject);
            }
        }
        return new DebloaterPutBackPlan(restorableObjects, skippedInstalledObjects);
    }

    static boolean isRestorable(@NonNull DebloatObject debloatObject) {
        return !debloatObject.isInstalled();
    }

    boolean hasRestorableTargets() {
        return !mRestorableObjects.isEmpty();
    }

    int getRestorableCount() {
        return mRestorableObjects.size();
    }

    int getSkippedInstalledCount() {
        return mSkippedInstalledObjects.size();
    }

    @NonNull
    List<DebloatObject> getRestorableObjects() {
        return mRestorableObjects;
    }

    @NonNull
    String buildConfirmationMessage(@NonNull Context context) {
        int restorableCount = getRestorableCount();
        StringBuilder message = new StringBuilder(context.getResources().getQuantityString(
                R.plurals.debloat_put_back_confirmation, restorableCount, restorableCount));
        int skippedInstalledCount = getSkippedInstalledCount();
        if (skippedInstalledCount > 0) {
            message.append("\n\n")
                    .append(context.getResources().getQuantityString(
                            R.plurals.debloat_put_back_skip_installed,
                            skippedInstalledCount,
                            skippedInstalledCount,
                            joinLabels(mSkippedInstalledObjects)));
        }
        return message.toString();
    }

    @NonNull
    private static String joinLabels(@NonNull List<DebloatObject> debloatObjects) {
        List<String> labels = new ArrayList<>(Math.min(MAX_EXAMPLES, debloatObjects.size()));
        for (DebloatObject debloatObject : debloatObjects) {
            labels.add(debloatObject.getLabelOrPackageName().toString());
            if (labels.size() == MAX_EXAMPLES) {
                break;
            }
        }
        return TextUtils.join(", ", labels);
    }
}
