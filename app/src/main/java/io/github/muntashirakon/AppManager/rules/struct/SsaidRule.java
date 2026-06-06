// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class SsaidRule extends RuleEntry {
    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final int APP_SSAID_HEX_LENGTH = 16;
    private static final int SYSTEM_SSAID_HEX_LENGTH = 64;

    @NonNull
    private String mSsaid;

    public SsaidRule(@NonNull String packageName, @NonNull String ssaid) {
        super(packageName, STUB, RuleType.SSAID);
        mSsaid = validateSsaid(packageName, ssaid);
    }

    public SsaidRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.SSAID);
        if (tokenizer.hasMoreElements()) {
            mSsaid = validateSsaid(packageName, tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: ssaid not found");
    }

    @NonNull
    public String getSsaid() {
        return mSsaid;
    }

    public void setSsaid(@NonNull String ssaid) {
        mSsaid = validateSsaid(packageName, ssaid);
    }

    @NonNull
    private static String validateSsaid(@NonNull String packageName, @NonNull String ssaid) {
        String normalizedSsaid = ssaid.trim();
        int expectedLength = SYSTEM_PACKAGE_NAME.equals(packageName)
                ? SYSTEM_SSAID_HEX_LENGTH
                : APP_SSAID_HEX_LENGTH;
        if (normalizedSsaid.length() != expectedLength || !normalizedSsaid.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException("Invalid format: ssaid is invalid");
        }
        return normalizedSsaid;
    }

    @NonNull
    @Override
    public String toString() {
        return "SsaidRule{" +
                "packageName='" + packageName + '\'' +
                ", ssaid='" + mSsaid + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mSsaid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SsaidRule)) return false;
        if (!super.equals(o)) return false;
        SsaidRule ssaidRule = (SsaidRule) o;
        return getSsaid().equals(ssaidRule.getSsaid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSsaid());
    }
}
