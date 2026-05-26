// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReceiverBroadcastUtils {
    private static final String PROTECTED_ANDROID_ACTION_PREFIX = "android.intent.action.";

    private ReceiverBroadcastUtils() {
    }

    @NonNull
    public static Intent buildBroadcastIntent(@NonNull String packageName, @NonNull String receiverName,
                                              @Nullable String action, @NonNull List<String> categories,
                                              @Nullable Bundle extras, boolean foreground) {
        Intent intent = new Intent();
        String normalizedAction = trimToNull(action);
        if (normalizedAction != null) {
            intent.setAction(normalizedAction);
        }
        intent.setComponent(new ComponentName(packageName, toQualifiedComponentName(packageName, receiverName)));
        for (String category : categories) {
            String normalizedCategory = trimToNull(category);
            if (normalizedCategory != null) {
                intent.addCategory(normalizedCategory);
            }
        }
        if (extras != null && !extras.isEmpty()) {
            intent.putExtras(extras);
        }
        if (foreground) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        return intent;
    }

    public static boolean isProtectedAndroidAction(@Nullable String action) {
        String normalizedAction = trimToNull(action);
        return normalizedAction != null && normalizedAction.startsWith(PROTECTED_ANDROID_ACTION_PREFIX);
    }

    public static boolean needsPrivilegedDispatch(@Nullable String action, boolean receiverExported,
                                                  int targetUserId, int currentUserId) {
        return targetUserId != currentUserId || !receiverExported || isProtectedAndroidAction(action);
    }

    @NonNull
    public static String toQualifiedComponentName(@NonNull String packageName, @NonNull String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.indexOf('.') == -1) {
            return packageName + "." + className;
        }
        return className;
    }

    @NonNull
    public static List<String> parseCategories(@Nullable CharSequence rawCategories) {
        Set<String> categories = new LinkedHashSet<>();
        if (rawCategories == null) {
            return new ArrayList<>(categories);
        }
        String[] lines = rawCategories.toString().replace('\r', '\n').split("[,\\n]");
        for (String line : lines) {
            String category = trimToNull(line);
            if (category != null) {
                categories.add(category);
            }
        }
        return new ArrayList<>(categories);
    }

    @NonNull
    public static Bundle parseExtras(@Nullable CharSequence rawExtras) {
        Bundle extras = new Bundle();
        if (rawExtras == null) {
            return extras;
        }
        String[] lines = rawExtras.toString().replace('\r', '\n').split("\\n");
        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (TextUtils.isEmpty(line) || line.startsWith("#")) {
                continue;
            }
            int valueSeparator = line.indexOf('=');
            if (valueSeparator <= 0) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": use key=value or key:type=value.");
            }
            String keyAndType = line.substring(0, valueSeparator).trim();
            String value = line.substring(valueSeparator + 1).trim();
            String key = keyAndType;
            String type = "string";
            int typeSeparator = keyAndType.indexOf(':');
            if (typeSeparator >= 0) {
                key = keyAndType.substring(0, typeSeparator).trim();
                type = keyAndType.substring(typeSeparator + 1).trim().toLowerCase(Locale.ROOT);
            }
            if (TextUtils.isEmpty(key)) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": extra key is empty.");
            }
            putTypedExtra(extras, key, type, value, i + 1);
        }
        return extras;
    }

    private static void putTypedExtra(@NonNull Bundle extras, @NonNull String key, @NonNull String type,
                                      @NonNull String value, int lineNumber) {
        try {
            switch (type) {
                case "string":
                case "str":
                    extras.putString(key, value);
                    break;
                case "int":
                case "integer":
                    extras.putInt(key, Integer.parseInt(value));
                    break;
                case "long":
                    extras.putLong(key, Long.parseLong(value));
                    break;
                case "float":
                    extras.putFloat(key, Float.parseFloat(value));
                    break;
                case "double":
                    extras.putDouble(key, Double.parseDouble(value));
                    break;
                case "bool":
                case "boolean":
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException("Expected true or false.");
                    }
                    extras.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type '" + type + "'.");
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Line " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
