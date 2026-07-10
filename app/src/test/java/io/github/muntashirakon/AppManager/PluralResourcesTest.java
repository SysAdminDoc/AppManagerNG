// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

@RunWith(RobolectricTestRunner.class)
public class PluralResourcesTest {
    @Test
    public void quantityStrings_renderNaturalEnglish() {
        Resources resources = getEnglishResources();

        assertEquals("Found 1 installed app listing this package in <queries>:",
                resources.getQuantityString(R.plurals.package_visibility_callers_header, 1, 1));
        assertEquals("Found 2 installed apps listing this package in <queries>:",
                resources.getQuantityString(R.plurals.package_visibility_callers_header, 2, 2));
        assertEquals("Deleted 1 folder, reclaimed 12 MB.",
                resources.getQuantityString(R.plurals.leftover_files_deleted, 1, 1, "12 MB"));
        assertEquals("Deleted 2 folders, reclaimed 12 MB.",
                resources.getQuantityString(R.plurals.leftover_files_deleted, 2, 2, "12 MB"));
        assertEquals("Deleted 1 APK file, reclaimed 80 MB.",
                resources.getQuantityString(R.plurals.duplicate_apks_deleted, 1, 1, "80 MB"));
        assertEquals("Deleted 2 APK files, reclaimed 80 MB.",
                resources.getQuantityString(R.plurals.duplicate_apks_deleted, 2, 2, "80 MB"));
        assertEquals("Default temporary folders are skipped. 1 custom pattern configured.",
                resources.getQuantityString(R.plurals.pref_backup_exclusion_patterns_summary, 1, 1));
        assertEquals("Default temporary folders are skipped. 2 custom patterns configured.",
                resources.getQuantityString(R.plurals.pref_backup_exclusion_patterns_summary, 2, 2));
        assertEquals("Imported 1 key successfully.",
                resources.getQuantityString(R.plurals.keystore_import_success, 1, 1));
        assertEquals("Imported 2 keys; 1 failed.",
                resources.getQuantityString(R.plurals.keystore_import_partial, 2, 2, 1));
        assertEquals("1 module: Sui",
                resources.getQuantityString(R.plurals.privilege_health_modules_summary, 1, 1, "Sui"));
        assertEquals("2 modules: Sui, LSPosed",
                resources.getQuantityString(R.plurals.privilege_health_modules_summary, 2, 2, "Sui, LSPosed"));
        assertEquals("matched 1 relevant rule", resources.getQuantityString(
                R.plurals.privilege_health_capability_dropping_magisk_policy_matched, 1, 1));
        assertEquals("matched 2 relevant rules", resources.getQuantityString(
                R.plurals.privilege_health_capability_dropping_magisk_policy_matched, 2, 2));
        assertEquals("1 recent denial",
                resources.getQuantityString(R.plurals.privilege_health_kernelsu_sulog_denials, 1, 1));
        assertEquals("2 recent denials",
                resources.getQuantityString(R.plurals.privilege_health_kernelsu_sulog_denials, 2, 2));
        assertEquals("1 provider service",
                resources.getQuantityString(R.plurals.credential_provider_service_count, 1, 1));
        assertEquals("2 system-provider actions",
                resources.getQuantityString(R.plurals.credential_provider_system_action_count, 2, 2));
        assertEquals("Delete 1 redundant same-version backup, reclaiming about 400 MB? "
                        + "The kept copy of each app version is preserved.",
                resources.getQuantityString(R.plurals.delete_duplicate_backups_confirm_with_reclaim,
                        1, 1, "400 MB"));
        assertEquals("Removed 2 duplicate backups.",
                resources.getQuantityString(R.plurals.duplicate_backups_pruned, 2, 2));
        assertEquals("Deleted 2 duplicate backups, reclaimed 400 MB.",
                resources.getQuantityString(R.plurals.duplicate_backups_pruned_with_reclaim,
                        2, 2, "400 MB"));
        assertEquals("1 new dangerous permission: Camera",
                resources.getQuantityString(R.plurals.permission_change_monitor_body, 1, 1, "Camera"));
        assertEquals("2 new dangerous permissions: Camera, Location",
                resources.getQuantityString(R.plurals.permission_change_monitor_body,
                        2, 2, "Camera, Location"));
    }

    @Test
    public void resourceXml_hasNoParenthesizedPluralHacks() throws IOException {
        Path resources = findRepoRoot().resolve("app/src/main/res");
        try (Stream<Path> files = Files.walk(resources)) {
            files.filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .forEach(path -> assertFalse(path.toString(), read(path).contains("(s)")));
        }
    }

    @Test
    public void sentenceCaseLabels_remainNormalized() {
        Resources resources = getEnglishResources();

        assertEquals("Export diagnostic report", resources.getString(R.string.pref_export_diagnostics));
        assertEquals("Recent exits", resources.getString(R.string.exit_history));
        assertEquals("Native crash", resources.getString(R.string.exit_reason_crash_native));
        assertEquals("Package state change", resources.getString(R.string.exit_reason_package_state_change));
    }

    private static Resources getEnglishResources() {
        Context context = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.ENGLISH);
        return context.createConfigurationContext(configuration).getResources();
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/res"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
