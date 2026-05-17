// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.io.IoUtils;

@RunWith(AndroidJUnit4.class)
public class HiddenApiCompatibilityInstrumentedTest {
    private static final String BASELINE_ASSET = "api/api-versions-appmanagerng-hiddenapi.json";

    @Test
    public void activeSdkResolvesHiddenApiBaseline() throws Exception {
        exemptHiddenApiReflection();

        Context context = ApplicationProvider.getApplicationContext();
        JSONObject baseline;
        try (InputStream in = context.getAssets().open(BASELINE_ASSET)) {
            baseline = new JSONObject(new String(IoUtils.readFully(in, -1, true), StandardCharsets.UTF_8));
        }
        assertEquals(1, baseline.getInt("schema"));

        JSONArray failures = new JSONArray();
        JSONArray warnings = new JSONArray();
        int checkedClasses = 0;
        int checkedMembers = 0;
        JSONArray classes = baseline.getJSONArray("classes");
        ClassLoader classLoader = HiddenApiCompatibilityInstrumentedTest.class.getClassLoader();
        for (int i = 0; i < classes.length(); ++i) {
            JSONObject item = classes.getJSONObject(i);
            List<JSONObject> activeMembers = activeMembers(item.getJSONArray("members"));
            if (activeMembers.isEmpty()) {
                continue;
            }
            Class<?> runtimeClass;
            try {
                runtimeClass = Class.forName(item.getString("runtime"), false, classLoader);
                ++checkedClasses;
            } catch (Throwable e) {
                if (allDeprecated(activeMembers)) {
                    appendWarning(warnings, item, new JSONObject()
                                    .put("kind", "class")
                                    .put("name", item.getString("runtime")),
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                } else {
                    appendFailure(failures, item, "class", e);
                }
                continue;
            }

            for (JSONObject member : activeMembers) {
                boolean deprecated = member.optBoolean("deprecated");
                try {
                    boolean present = "field".equals(member.getString("kind"))
                            ? hasField(runtimeClass, member.getString("name"))
                            : hasMethod(runtimeClass, member);
                    if (present) {
                        ++checkedMembers;
                    } else if (deprecated) {
                        appendWarning(warnings, item, member, "deprecated member absent");
                    } else {
                        appendMissingMember(failures, item, member);
                    }
                } catch (Throwable e) {
                    if (deprecated) {
                        appendWarning(warnings, item, member, e.getClass().getSimpleName() + ": " + e.getMessage());
                    } else {
                        appendFailure(failures, item, member.getString("kind") + ":" + member.getString("name"), e);
                    }
                }
            }
        }

        JSONObject report = new JSONObject()
                .put("schema", 1)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("fingerprint", Build.FINGERPRINT)
                .put("baseline", BASELINE_ASSET)
                .put("checkedClasses", checkedClasses)
                .put("checkedMembers", checkedMembers)
                .put("warnings", warnings)
                .put("failures", failures);
        File reportFile = writeReport(context, report);

        assertTrue("No hidden API classes were checked; baseline or SDK gating is broken.",
                checkedClasses > 0);
        assertTrue("No hidden API members were checked; baseline or SDK gating is broken.",
                checkedMembers > 0);
        assertTrue("Hidden API compatibility failures on SDK " + Build.VERSION.SDK_INT
                        + ". Report: " + reportFile + "\n" + firstFailures(failures),
                failures.length() == 0);
    }

    private static void exemptHiddenApiReflection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            HiddenApiBypass.addHiddenApiExemptions("L");
        } catch (Throwable ignored) {
            // The assertion below reports concrete member failures if exemption failed.
        }
    }

    private static List<JSONObject> activeMembers(JSONArray members) throws Exception {
        List<JSONObject> active = new ArrayList<>();
        for (int i = 0; i < members.length(); ++i) {
            JSONObject member = members.getJSONObject(i);
            int minSdk = member.optInt("minSdk", 1);
            int maxSdk = member.optInt("maxSdk", Integer.MAX_VALUE);
            if (Build.VERSION.SDK_INT >= minSdk && Build.VERSION.SDK_INT <= maxSdk) {
                active.add(member);
            }
        }
        return active;
    }

    private static boolean allDeprecated(List<JSONObject> members) {
        if (members.isEmpty()) {
            return false;
        }
        for (JSONObject member : members) {
            if (!member.optBoolean("deprecated")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasField(Class<?> runtimeClass, String name) {
        for (Field field : runtimeClass.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(Class<?> runtimeClass, JSONObject expected) throws Exception {
        for (Method method : runtimeClass.getDeclaredMethods()) {
            if (!method.getName().equals(expected.getString("name"))) {
                continue;
            }
            Class<?>[] actualTypes = method.getParameterTypes();
            if (actualTypes.length != expected.getInt("parameterCount")) {
                continue;
            }
            if (!expected.has("parameters")) {
                return true;
            }
            JSONArray expectedTypes = expected.getJSONArray("parameters");
            if (expectedTypes.length() != actualTypes.length) {
                return true;
            }
            boolean allMatch = true;
            for (int i = 0; i < actualTypes.length; ++i) {
                if (!typeMatches(expectedTypes.getString(i), actualTypes[i])) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    private static boolean typeMatches(String expected, Class<?> actual) {
        String normalized = normalizeExpectedType(expected);
        return normalized.equals(actual.getName())
                || normalized.equals(actual.getCanonicalName())
                || normalized.equals(actual.getSimpleName());
    }

    private static String normalizeExpectedType(String type) {
        String normalized = type.replace("...", "[]").trim();
        normalized = normalized.replaceAll("@[A-Za-z0-9_.$]+(?:\\([^)]*\\))?\\s*", "");
        normalized = normalized.replaceAll("\\bfinal\\s+", "");
        normalized = normalized.replaceAll("<.*>", "");
        normalized = normalized.replace("?", "").trim();
        switch (normalized) {
            case "String":
                return "java.lang.String";
            case "CharSequence":
                return "java.lang.CharSequence";
            case "List":
                return "java.util.List";
            case "Map":
                return "java.util.Map";
            default:
                return normalized;
        }
    }

    private static void appendFailure(JSONArray failures, JSONObject item, String target, Throwable e) throws Exception {
        failures.put(new JSONObject()
                .put("sourceFile", item.getString("sourceFile"))
                .put("runtime", item.getString("runtime"))
                .put("target", target)
                .put("error", e.getClass().getSimpleName())
                .put("message", e.getMessage()));
    }

    private static void appendMissingMember(JSONArray failures, JSONObject item, JSONObject member) throws Exception {
        failures.put(new JSONObject()
                .put("sourceFile", item.getString("sourceFile"))
                .put("runtime", item.getString("runtime"))
                .put("kind", member.getString("kind"))
                .put("name", member.getString("name"))
                .put("parameterCount", member.optInt("parameterCount", -1))
                .put("minSdk", member.optInt("minSdk", 1))
                .put("error", "missing"));
    }

    private static void appendWarning(JSONArray warnings, JSONObject item, JSONObject member,
                                      String message) throws Exception {
        warnings.put(new JSONObject()
                .put("sourceFile", item.getString("sourceFile"))
                .put("runtime", item.getString("runtime"))
                .put("kind", member.getString("kind"))
                .put("name", member.getString("name"))
                .put("message", message));
    }

    private static File writeReport(Context context, JSONObject report) throws Exception {
        File baseDir = context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getCacheDir();
        }
        File dir = new File(baseDir, "hidden-api");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "hidden-api-compat-sdk" + Build.VERSION.SDK_INT + ".json");
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String firstFailures(JSONArray failures) throws Exception {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(20, failures.length());
        for (int i = 0; i < count; ++i) {
            sb.append(failures.getJSONObject(i).toString()).append('\n');
        }
        if (failures.length() > count) {
            sb.append("... ").append(failures.length() - count).append(" more");
        }
        return sb.toString();
    }
}
