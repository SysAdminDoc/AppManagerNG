// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.rules.RuleType;

public class AppOpReferenceRule extends RuleEntry {
    private final int mOp;
    @AppOpsManagerCompat.Mode
    private int mMode;

    public AppOpReferenceRule(@NonNull String packageName, int op, @AppOpsManagerCompat.Mode int mode) {
        super(packageName, String.valueOf(op), RuleType.APP_OP_REFERENCE);
        mOp = validateOp(op);
        mMode = validateMode(mode);
    }

    public AppOpReferenceRule(@NonNull String packageName, String opInt, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, opInt, RuleType.APP_OP_REFERENCE);
        mOp = validateOp(parseInt(opInt, "op"));
        if (tokenizer.hasMoreElements()) {
            mMode = validateMode(parseInt(tokenizer.nextElement().toString(), "mode"));
        } else throw new IllegalArgumentException("Invalid format: mode not found");
    }

    public int getOp() {
        return mOp;
    }

    @AppOpsManagerCompat.Mode
    public int getMode() {
        return mMode;
    }

    public void setMode(@AppOpsManagerCompat.Mode int mode) {
        mMode = validateMode(mode);
    }

    private static int parseInt(@NonNull String value, @NonNull String fieldName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format: " + fieldName + " is invalid", e);
        }
    }

    private static int validateOp(int op) {
        if (op < 0) {
            throw new IllegalArgumentException("Invalid format: op is invalid");
        }
        return op;
    }

    @AppOpsManagerCompat.Mode
    private static int validateMode(int mode) {
        if (!AppOpsManagerCompat.getModeConstants().contains(mode)) {
            throw new IllegalArgumentException("Invalid format: mode is invalid");
        }
        return mode;
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + mOp + "\t" + type.name() + "\t" + mMode;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppOpReferenceRule{" +
                "packageName='" + packageName + '\'' +
                ", op=" + mOp +
                ", mode=" + mMode +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppOpReferenceRule)) return false;
        if (!super.equals(o)) return false;
        AppOpReferenceRule that = (AppOpReferenceRule) o;
        return getOp() == that.getOp() && getMode() == that.getMode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getOp(), getMode());
    }
}
