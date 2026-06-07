// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.settings.Ops;

@RunWith(RobolectricTestRunner.class)
public class DexOptOptionsTest {
    @Test
    public void testParcelable() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.packages = new String[]{"android.package"};
        Parcel parcel = Parcel.obtain();
        dexOptOptions.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DexOptOptions dexOptOptions2 = DexOptOptions.CREATOR.createFromParcel(parcel);
        assertArrayEquals(dexOptOptions.packages, dexOptOptions2.packages);
        assertEquals(dexOptOptions.compilerFiler, dexOptOptions2.compilerFiler);
        assertEquals(dexOptOptions.compileLayouts, dexOptOptions2.compileLayouts);
        assertEquals(dexOptOptions.clearProfileData, dexOptOptions2.clearProfileData);
        assertEquals(dexOptOptions.checkProfiles, dexOptOptions2.checkProfiles);
        assertEquals(dexOptOptions.bootComplete, dexOptOptions2.bootComplete);
        assertEquals(dexOptOptions.forceCompilation, dexOptOptions2.forceCompilation);
        assertEquals(dexOptOptions.forceDexOpt, dexOptOptions2.forceDexOpt);
    }

    @Test
    public void sanitizeForExecutionKeepsRootOnlyOptionsForRootOrSystem() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.clearProfileData = true;
        dexOptOptions.forceDexOpt = true;

        DexOptOptions.SanitizationResult result = dexOptOptions.sanitizeForExecution(true);

        assertTrue(result.options.clearProfileData);
        assertTrue(result.options.forceDexOpt);
        assertFalse(result.hasSkippedRootOnlyOptions());
        assertTrue(DexOptOptions.canUseRootOnlyOptions(Ops.ROOT_UID));
        assertTrue(DexOptOptions.canUseRootOnlyOptions(Ops.SYSTEM_UID));
    }

    @Test
    public void sanitizeForExecutionStripsRootOnlyOptionsForAdbOrShizuku() {
        DexOptOptions dexOptOptions = DexOptOptions.getDefault();
        dexOptOptions.compileLayouts = true;
        dexOptOptions.clearProfileData = true;
        dexOptOptions.forceCompilation = true;
        dexOptOptions.forceDexOpt = true;

        DexOptOptions.SanitizationResult result = dexOptOptions.sanitizeForExecution(false);

        assertTrue(result.options.compileLayouts);
        assertTrue(result.options.forceCompilation);
        assertFalse(result.options.clearProfileData);
        assertFalse(result.options.forceDexOpt);
        assertTrue(result.hasSkippedRootOnlyOptions());
        assertEquals("clear_profile_data, force_dex_opt", result.getSkippedRootOnlyOptionsSummary());
        assertFalse(DexOptOptions.canUseRootOnlyOptions(Ops.SHELL_UID));
    }

    @Test
    public void jsonRestorationTrimsPackagesAndCompilerFilter() throws Exception {
        DexOptOptions dexOptOptions = new DexOptOptions(jsonOptions(
                new JSONArray().put(" com.example.app "), " speed "));

        assertArrayEquals(new String[]{"com.example.app"}, dexOptOptions.packages);
        assertEquals("speed", dexOptOptions.compilerFiler);
    }

    @Test
    public void jsonRestorationRejectsMalformedPackagesAndBlankCompilerFilter() {
        assertThrows(JSONException.class, () -> new DexOptOptions(jsonOptions(
                new JSONArray().put("bad package"), "speed")));
        assertThrows(JSONException.class, () -> new DexOptOptions(jsonOptions(
                new JSONArray().put(JSONObject.NULL), "speed")));
        assertThrows(JSONException.class, () -> new DexOptOptions(jsonOptions(
                new JSONArray().put("com.example.app"), "   ")));
    }

    @Test
    public void parcelRestorationTrimsPackagesAndCompilerFilter() {
        Parcel parcel = parcelOptions(new String[]{" com.example.app "}, " speed ");
        try {
            DexOptOptions dexOptOptions = DexOptOptions.CREATOR.createFromParcel(parcel);

            assertArrayEquals(new String[]{"com.example.app"}, dexOptOptions.packages);
            assertEquals("speed", dexOptOptions.compilerFiler);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRestorationRejectsMalformedPackagesAndBlankCompilerFilter() {
        Parcel badPackageParcel = parcelOptions(new String[]{"bad package"}, "speed");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> DexOptOptions.CREATOR.createFromParcel(badPackageParcel));
        } finally {
            badPackageParcel.recycle();
        }

        Parcel blankCompilerFilterParcel = parcelOptions(new String[]{"com.example.app"}, "   ");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> DexOptOptions.CREATOR.createFromParcel(blankCompilerFilterParcel));
        } finally {
            blankCompilerFilterParcel.recycle();
        }
    }

    private static JSONObject jsonOptions(JSONArray packages, String compilerFilter) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        if (packages != null) {
            jsonObject.put("packages", packages);
        }
        jsonObject.put("compiler_filter", compilerFilter);
        jsonObject.put("compile_layouts", false);
        jsonObject.put("clear_profile_data", false);
        jsonObject.put("check_profiles", false);
        jsonObject.put("boot_complete", true);
        jsonObject.put("force_compilation", false);
        jsonObject.put("force_dex_opt", false);
        return jsonObject;
    }

    private static Parcel parcelOptions(String[] packages, String compilerFilter) {
        Parcel parcel = Parcel.obtain();
        parcel.writeStringArray(packages);
        parcel.writeString(compilerFilter);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 1);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.setDataPosition(0);
        return parcel;
    }
}
