// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ListImporter {
    private ListImporter() {
    }

    @NonNull
    public static Set<String> readPackageNames(@NonNull Reader reader) throws IOException {
        JsonElement root = JsonParser.parseReader(reader);
        JsonArray packages = getPackagesArray(root);
        LinkedHashSet<String> packageNames = new LinkedHashSet<>();
        for (JsonElement packageItem : packages) {
            String packageName = getPackageName(packageItem);
            if (packageName == null) {
                continue;
            }
            packageName = packageName.trim();
            if (validatePackageName(packageName)) {
                packageNames.add(packageName);
            }
        }
        return packageNames;
    }

    @NonNull
    private static JsonArray getPackagesArray(@NonNull JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement packages = object.get("packages");
            if (packages != null && packages.isJsonArray()) {
                return packages.getAsJsonArray();
            }
            JsonElement apps = object.get("apps");
            if (apps != null && apps.isJsonArray()) {
                return apps.getAsJsonArray();
            }
        }
        throw new JsonParseException("Unsupported app list JSON");
    }

    @Nullable
    private static String getPackageName(@NonNull JsonElement item) {
        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
            return item.getAsString();
        }
        if (item.isJsonObject()) {
            JsonObject object = item.getAsJsonObject();
            String packageName = getString(object, "name");
            if (packageName == null) {
                packageName = getString(object, "packageName");
            }
            return packageName;
        }
        return null;
    }

    @Nullable
    private static String getString(@NonNull JsonObject object, @NonNull String key) {
        JsonElement value = object.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        return null;
    }

    private static boolean validatePackageName(@NonNull String packageName) {
        if (packageName.equals("android")) {
            return true;
        }
        int length = packageName.length();
        boolean hasSep = false;
        boolean front = true;
        for (int i = 0; i < length; ++i) {
            char c = packageName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                front = false;
                continue;
            }
            if (!front && ((c >= '0' && c <= '9') || c == '_')) {
                continue;
            }
            if (c == '.') {
                // front == true here means the segment before this dot was
                // empty — i.e. a leading dot, an empty segment from consecutive
                // dots ("com..x"), or the string started with '.'. Reject it.
                if (front) {
                    return false;
                }
                hasSep = true;
                front = true;
                continue;
            }
            return false;
        }
        // front == true at the end means a trailing dot (or empty input).
        return hasSep && !front;
    }
}
