// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public abstract class RuleEntry {
    public static final String STUB = "STUB";

    /**
     * Name of the entry, unique for {@link RuleType#ACTIVITY}, {@link RuleType#PROVIDER}, {@link RuleType#RECEIVER},
     * {@link RuleType#SERVICE}, {@link RuleType#PERMISSION}, {@link RuleType#MAGISK_DENY_LIST} but not others.
     * In other cases, they can be {@link #STUB}.
     */
    @NonNull
    public final String name;
    /**
     * The package name this rule belong to.
     */
    @NonNull
    public final String packageName;
    /**
     * Type of the entry.
     */
    @NonNull
    public final RuleType type;

    public RuleEntry(@NonNull String packageName, @NonNull String name, @NonNull RuleType type) {
        this.packageName = packageName;
        this.name = name;
        this.type = type;
    }

    @NonNull
    @Override
    public String toString() {
        return "Entry{" +
                "name='" + name + '\'' +
                ", type=" + type +
                '}';
    }

    @NonNull
    public abstract String flattenToString(boolean isExternal);

    protected String addPackageWithTab(boolean isExternal) {
        return (isExternal ? packageName + "\t" : "");
    }

    @NonNull
    public static RuleEntry unflattenFromString(@Nullable String packageName, @NonNull String ruleLine,
                                                boolean isExternal) throws IllegalArgumentException {
        String[] fields = ruleLine.split("\t", -1);
        int nameIndex = isExternal ? 1 : 0;
        int typeIndex = nameIndex + 1;
        int valueIndex = typeIndex + 1;
        if (isExternal) {
            // External rules, the first part is the package name
            if (!hasField(fields, 0)) {
                throw new IllegalArgumentException("Invalid format: packageName not found for external rule.");
            }
            // Match package name
            String newPackageName = fields[0];
            if (packageName == null) packageName = newPackageName;
            if (!packageName.equals(newPackageName)) {
                throw new IllegalArgumentException("Invalid format: package names do not match.");
            }
        }
        if (packageName == null || packageName.isEmpty()) {
            // packageName can't be empty
            throw new IllegalArgumentException("Package name cannot be empty.");
        }
        String name;
        RuleType type;
        if (hasField(fields, nameIndex)) {
            name = fields[nameIndex];
        } else throw new IllegalArgumentException("Invalid format: name not found");
        if (hasField(fields, typeIndex)) {
            try {
                type = RuleType.valueOf(fields[typeIndex]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid format: Invalid type");
            }
        } else throw new IllegalArgumentException("Invalid format: entryType not found");
        if (!hasField(fields, valueIndex)) {
            throw new IllegalArgumentException("Invalid format: value not found");
        }
        return getRuleEntry(packageName, name, type, valueTokenizer(fields, valueIndex));
    }

    private static boolean hasField(@NonNull String[] fields, int index) {
        return index >= 0 && index < fields.length && !fields[index].isEmpty();
    }

    static boolean parseBoolean(@NonNull String value, @NonNull String fieldName) {
        String normalizedValue = value.trim();
        if ("true".equalsIgnoreCase(normalizedValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalizedValue)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid format: " + fieldName + " is not a boolean");
    }

    static int parseNonNegativeInt(@NonNull String value, @NonNull String fieldName) {
        try {
            return requireNonNegativeInt(Integer.parseInt(value.trim()), fieldName);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format: " + fieldName + " is invalid", e);
        }
    }

    static int requireNonNegativeInt(int value, @NonNull String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException("Invalid format: " + fieldName + " is invalid");
        }
        return value;
    }

    @NonNull
    private static StringTokenizer valueTokenizer(@NonNull String[] fields, int startIndex) {
        StringBuilder valueFields = new StringBuilder();
        for (int i = startIndex; i < fields.length; ++i) {
            if (i > startIndex) valueFields.append('\t');
            valueFields.append(fields[i]);
        }
        return new StringTokenizer(valueFields.toString(), "\t");
    }

    @NonNull
    private static RuleEntry getRuleEntry(@NonNull String packageName, @NonNull String name,
                                          @NonNull RuleType type, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        switch (type) {
            case ACTIVITY:
            case PROVIDER:
            case RECEIVER:
            case SERVICE:
                return new ComponentRule(packageName, name, type, tokenizer);
            case APP_OP:
                return new AppOpRule(packageName, name, tokenizer);
            case PERMISSION:
                return new PermissionRule(packageName, name, tokenizer);
            case MAGISK_HIDE:
                return new MagiskHideRule(packageName, name, tokenizer);
            case MAGISK_DENY_LIST:
                return new MagiskDenyListRule(packageName, name, tokenizer);
            case BATTERY_OPT:
                return new BatteryOptimizationRule(packageName, tokenizer);
            case NET_POLICY:
                return new NetPolicyRule(packageName, tokenizer);
            case NOTIFICATION:
                return new NotificationListenerRule(packageName, name, tokenizer);
            case URI_GRANT:
                return new UriGrantRule(packageName, tokenizer);
            case SSAID:
                return new SsaidRule(packageName, tokenizer);
            case FREEZE:
                return new FreezeRule(packageName, tokenizer);
            default:
                throw new IllegalArgumentException("Invalid type=" + type.name());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleEntry)) return false;
        RuleEntry ruleEntry = (RuleEntry) o;
        return name.equals(ruleEntry.name) && packageName.equals(ruleEntry.packageName) && type == ruleEntry.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, packageName, type);
    }
}
