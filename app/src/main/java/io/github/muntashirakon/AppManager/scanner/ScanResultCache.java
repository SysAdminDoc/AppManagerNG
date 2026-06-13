// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.CachedScanResult;

public final class ScanResultCache {
    private ScanResultCache() {
    }

    @Nullable
    @WorkerThread
    public static CachedScanResult get(@NonNull String packageName, long versionCode) {
        return AppsDb.getInstance().cachedScanResultDao().get(packageName, versionCode);
    }

    @WorkerThread
    public static void put(@NonNull String packageName, long versionCode,
                           @NonNull List<SignatureInfo> trackers,
                           @NonNull List<SignatureInfo> libraries) {
        CachedScanResult result = new CachedScanResult();
        result.packageName = packageName;
        result.versionCode = versionCode;
        result.scanTimestamp = System.currentTimeMillis();
        result.trackerCount = trackers.size();
        result.libraryCount = libraries.size();
        result.trackersJson = serializeSignatureInfoList(trackers);
        result.librariesJson = serializeSignatureInfoList(libraries);
        AppsDb.getInstance().cachedScanResultDao().insertOrReplace(result);
    }

    @WorkerThread
    public static void invalidate(@NonNull String packageName) {
        AppsDb.getInstance().cachedScanResultDao().deleteByPackage(packageName);
    }

    @WorkerThread
    public static void clearAll() {
        AppsDb.getInstance().cachedScanResultDao().deleteAll();
    }

    @NonNull
    public static List<SignatureInfo> deserializeTrackers(@Nullable String json) {
        return deserializeSignatureInfoList(json);
    }

    @NonNull
    public static List<SignatureInfo> deserializeLibraries(@Nullable String json) {
        return deserializeSignatureInfoList(json);
    }

    @Nullable
    private static String serializeSignatureInfoList(@NonNull List<SignatureInfo> list) {
        if (list.isEmpty()) return null;
        JSONArray array = new JSONArray();
        for (SignatureInfo info : list) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("sig", info.signature);
                obj.put("label", info.label);
                obj.put("type", info.type);
                obj.put("count", info.getCount());
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    @NonNull
    private static List<SignatureInfo> deserializeSignatureInfoList(@Nullable String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            JSONArray array = new JSONArray(json);
            List<SignatureInfo> list = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String sig = obj.getString("sig");
                String label = obj.getString("label");
                String type = obj.optString("type", "Tracker");
                int count = obj.optInt("count", 0);
                SignatureInfo info = new SignatureInfo(sig, label, type);
                info.setCount(count);
                list.add(info);
            }
            return list;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }
}
