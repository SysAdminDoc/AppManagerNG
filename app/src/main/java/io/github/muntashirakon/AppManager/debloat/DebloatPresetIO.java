// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DebloatPresetIO {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void exportPreset(@NonNull Context context, @NonNull Uri uri,
                                    @NonNull Map<String, int[]> selectedPackages) throws IOException {
        DebloatPresetData data = new DebloatPresetData();
        data.version = VERSION;
        data.entries = new ArrayList<>(selectedPackages.size());
        for (Map.Entry<String, int[]> entry : selectedPackages.entrySet()) {
            DebloatPresetEntry presetEntry = new DebloatPresetEntry();
            presetEntry.packageName = entry.getKey();
            presetEntry.userIds = entry.getValue();
            data.entries.add(presetEntry);
        }
        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
    }

    @NonNull
    public static DebloatPresetData importPreset(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            DebloatPresetData data = GSON.fromJson(reader, DebloatPresetData.class);
            if (data == null || data.entries == null) {
                throw new IOException("Invalid debloat preset file");
            }
            return data;
        }
    }

    public static class DebloatPresetData {
        @SerializedName("version")
        public int version;
        @Nullable
        @SerializedName("entries")
        public List<DebloatPresetEntry> entries;
    }

    public static class DebloatPresetEntry {
        @Nullable
        @SerializedName("package_name")
        public String packageName;
        @Nullable
        @SerializedName("user_ids")
        public int[] userIds;
    }

    private DebloatPresetIO() {
    }
}
