// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class DexOptOptions implements Parcelable, IJsonSerializer {
    public static final String ROOT_ONLY_CLEAR_PROFILE_DATA = "clear_profile_data";
    public static final String ROOT_ONLY_FORCE_DEX_OPT = "force_dex_opt";

    @NonNull
    public static DexOptOptions getDefault() {
        DexOptOptions options = new DexOptOptions();
        options.compilerFiler = getDefaultCompilerFilterForInstallation();
        options.checkProfiles = SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false);
        options.bootComplete = true;
        return options;
    }

    @Nullable
    public String[] packages;
    @Nullable
    public String compilerFiler;
    public boolean compileLayouts;
    public boolean clearProfileData;
    public boolean checkProfiles;
    public boolean bootComplete;
    public boolean forceCompilation;
    public boolean forceDexOpt;

    private DexOptOptions() {
    }

    protected DexOptOptions(@NonNull Parcel in) {
        packages = requireValidPackages(in.createStringArray());
        compilerFiler = normalizeCompilerFilter(in.readString());
        compileLayouts = in.readByte() != 0;
        clearProfileData = in.readByte() != 0;
        checkProfiles = in.readByte() != 0;
        bootComplete = in.readByte() != 0;
        forceCompilation = in.readByte() != 0;
        forceDexOpt = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(requireValidPackages(packages));
        dest.writeString(normalizeCompilerFilter(compilerFiler));
        dest.writeByte((byte) (compileLayouts ? 1 : 0));
        dest.writeByte((byte) (clearProfileData ? 1 : 0));
        dest.writeByte((byte) (checkProfiles ? 1 : 0));
        dest.writeByte((byte) (bootComplete ? 1 : 0));
        dest.writeByte((byte) (forceCompilation ? 1 : 0));
        dest.writeByte((byte) (forceDexOpt ? 1 : 0));
    }

    protected DexOptOptions(@NonNull JSONObject jsonObject) throws JSONException {
        try {
            packages = deserializePackages(jsonObject.optJSONArray("packages"));
            compilerFiler = normalizeCompilerFilter(JSONUtils.optString(jsonObject, "compiler_filter"));
            if (compilerFiler == null) {
                compilerFiler = normalizeCompilerFilter(JSONUtils.optString(jsonObject, "compiler_filer"));
            }
            if (compilerFiler == null) {
                compilerFiler = getDefaultCompilerFilterForInstallation();
            }
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
        compileLayouts = jsonObject.getBoolean("compile_layouts");
        clearProfileData = jsonObject.getBoolean("clear_profile_data");
        checkProfiles = jsonObject.getBoolean("check_profiles");
        bootComplete = jsonObject.getBoolean("boot_complete");
        forceCompilation = jsonObject.getBoolean("force_compilation");
        forceDexOpt = jsonObject.getBoolean("force_dex_opt");
    }

    public static final JsonDeserializer.Creator<DexOptOptions> DESERIALIZER = DexOptOptions::new;

    @NonNull
    public SanitizationResult sanitizeForExecution(boolean rootOrSystem) {
        DexOptOptions options = copy();
        List<String> skippedRootOnlyOptions = new ArrayList<>(2);
        if (!rootOrSystem) {
            if (options.clearProfileData) {
                options.clearProfileData = false;
                skippedRootOnlyOptions.add(ROOT_ONLY_CLEAR_PROFILE_DATA);
            }
            if (options.forceDexOpt) {
                options.forceDexOpt = false;
                skippedRootOnlyOptions.add(ROOT_ONLY_FORCE_DEX_OPT);
            }
        }
        return new SanitizationResult(options, skippedRootOnlyOptions);
    }

    public static boolean canUseRootOnlyOptions(int uid) {
        return uid == Ops.ROOT_UID || uid == Ops.SYSTEM_UID;
    }

    @NonNull
    private DexOptOptions copy() {
        DexOptOptions options = new DexOptOptions();
        options.packages = requireValidPackages(packages);
        options.compilerFiler = normalizeCompilerFilter(compilerFiler);
        options.compileLayouts = compileLayouts;
        options.clearProfileData = clearProfileData;
        options.checkProfiles = checkProfiles;
        options.bootComplete = bootComplete;
        options.forceCompilation = forceCompilation;
        options.forceDexOpt = forceDexOpt;
        return options;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("packages", JSONUtils.getJSONArray(requireValidPackages(packages)));
        jsonObject.put("compiler_filter", normalizeCompilerFilter(compilerFiler));
        jsonObject.put("compile_layouts", compileLayouts);
        jsonObject.put("clear_profile_data", clearProfileData);
        jsonObject.put("check_profiles", checkProfiles);
        jsonObject.put("boot_complete", bootComplete);
        jsonObject.put("force_compilation", forceCompilation);
        jsonObject.put("force_dex_opt", forceDexOpt);
        return jsonObject;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DexOptOptions> CREATOR = new Creator<DexOptOptions>() {
        @NonNull
        @Override
        public DexOptOptions createFromParcel(@NonNull Parcel in) {
            return new DexOptOptions(in);
        }

        @NonNull
        @Override
        public DexOptOptions[] newArray(int size) {
            return new DexOptOptions[size];
        }
    };

    @NonNull
    static String getDefaultCompilerFilterForInstallation() {
        return getSystemCompilerFilter("pm.dexopt.install");
    }

    @NonNull
    static String getDefaultCompilerFilter() {
        return getSystemCompilerFilter("dalvik.vm.dex2oat-filter");
    }

    @NonNull
    private static String getSystemCompilerFilter(@NonNull String propertyName) {
        String profile = SystemProperties.get(propertyName);
        if (TextUtils.isEmpty(profile)) {
            return "speed";
        }
        profile = profile.trim();
        if (profile.isEmpty()) {
            return "speed";
        }
        return profile;
    }

    @Nullable
    private static String[] deserializePackages(@Nullable JSONArray packagesJson) throws JSONException {
        if (packagesJson == null) {
            return null;
        }
        String[] packages = new String[packagesJson.length()];
        for (int i = 0; i < packagesJson.length(); ++i) {
            Object value = packagesJson.get(i);
            if (!(value instanceof String)) {
                throw new JSONException("Invalid dexopt package name.");
            }
            packages[i] = (String) value;
        }
        try {
            return requireValidPackages(packages);
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    @Nullable
    private static String[] requireValidPackages(@Nullable String[] packages) {
        if (packages == null) {
            return null;
        }
        String[] validPackages = new String[packages.length];
        for (int i = 0; i < packages.length; ++i) {
            String packageName = packages[i];
            if (packageName == null) {
                throw new IllegalArgumentException("Invalid dexopt package name.");
            }
            String normalizedPackageName = packageName.trim();
            if (!PackageUtils.validateName(normalizedPackageName)) {
                throw new IllegalArgumentException("Invalid dexopt package name: " + packageName);
            }
            validPackages[i] = normalizedPackageName;
        }
        return validPackages;
    }

    @Nullable
    private static String normalizeCompilerFilter(@Nullable String compilerFilter) {
        if (compilerFilter == null) {
            return null;
        }
        String normalizedCompilerFilter = compilerFilter.trim();
        if (normalizedCompilerFilter.isEmpty()) {
            throw new IllegalArgumentException("DexOpt compiler filter must not be empty.");
        }
        return normalizedCompilerFilter;
    }

    public static final class SanitizationResult {
        @NonNull
        public final DexOptOptions options;
        @NonNull
        public final List<String> skippedRootOnlyOptions;

        private SanitizationResult(@NonNull DexOptOptions options,
                                   @NonNull List<String> skippedRootOnlyOptions) {
            this.options = options;
            this.skippedRootOnlyOptions = Collections.unmodifiableList(skippedRootOnlyOptions);
        }

        public boolean hasSkippedRootOnlyOptions() {
            return !skippedRootOnlyOptions.isEmpty();
        }

        @NonNull
        public String getSkippedRootOnlyOptionsSummary() {
            return TextUtils.join(", ", skippedRootOnlyOptions);
        }
    }
}
