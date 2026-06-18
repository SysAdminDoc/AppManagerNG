// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class PermissionReferenceRule extends RuleEntry {
    private boolean mGranted;

    public PermissionReferenceRule(@NonNull String packageName, @NonNull String permName, boolean granted) {
        super(packageName, permName, RuleType.PERMISSION_REFERENCE);
        mGranted = granted;
    }

    public PermissionReferenceRule(@NonNull String packageName, @NonNull String permName,
                                   @NonNull StringTokenizer tokenizer) throws IllegalArgumentException {
        super(packageName, permName, RuleType.PERMISSION_REFERENCE);
        if (tokenizer.hasMoreElements()) {
            mGranted = parseBoolean(tokenizer.nextElement().toString(), "granted");
        } else throw new IllegalArgumentException("Invalid format: granted not found");
    }

    public boolean isGranted() {
        return mGranted;
    }

    public void setGranted(boolean granted) {
        mGranted = granted;
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mGranted;
    }

    @NonNull
    @Override
    public String toString() {
        return "PermissionReferenceRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", granted=" + mGranted +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionReferenceRule)) return false;
        if (!super.equals(o)) return false;
        PermissionReferenceRule that = (PermissionReferenceRule) o;
        return isGranted() == that.isGranted();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isGranted());
    }
}
