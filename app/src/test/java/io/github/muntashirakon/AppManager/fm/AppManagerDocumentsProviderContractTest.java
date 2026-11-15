// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.Objects;

import io.github.muntashirakon.AppManager.profiles.ProfileManager;

/**
 * The provider as DocumentsUI and a client holding a tree grant see it: what the roots advertise,
 * and the framework's own tree check, which only works through {@code isChildDocument()}. Both
 * pieces date from API 21, so the oldest SDK Robolectric runs (23) takes the same path as 21 and
 * 22, with the pre-Android 8 query form.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.M, Build.VERSION_CODES.BAKLAVA})
public class AppManagerDocumentsProviderContractTest {
    private static final String AUTHORITY = AppManagerDocumentsProvider.AUTHORITY;

    private AppManagerDocumentsProvider mProvider;
    private File mProfiles;

    @Before
    public void setUp() throws IOException {
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.exported = true;
        info.grantUriPermissions = true;
        info.readPermission = Manifest.permission.MANAGE_DOCUMENTS;
        info.writePermission = Manifest.permission.MANAGE_DOCUMENTS;
        mProvider = Robolectric.buildContentProvider(AppManagerDocumentsProvider.class).create(info).get();
        mProfiles = Objects.requireNonNull(ProfileManager.getProfilesDir().getFile());
        write(new File(mProfiles, "nested/inside.am.json"));
        write(new File(mProfiles, "nested-sibling/outside.am.json"));
    }

    @After
    public void tearDown() {
        deleteRecursively(mProfiles);
    }

    @Test
    public void everyRootAdvertisesChildChecksAndNothingThatWrites() {
        int writeFlags = DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                | DocumentsContract.Root.FLAG_SUPPORTS_RECENTS
                | DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        boolean sawProfiles = false;
        try (Cursor roots = mProvider.queryRoots(null)) {
            int idColumn = roots.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_ROOT_ID);
            int flagsColumn = roots.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_FLAGS);
            while (roots.moveToNext()) {
                int flags = roots.getInt(flagsColumn);
                assertEquals(DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
                        flags & DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
                assertEquals(DocumentsContract.Root.FLAG_LOCAL_ONLY, flags & DocumentsContract.Root.FLAG_LOCAL_ONLY);
                assertEquals(0, flags & writeFlags);
                sawProfiles |= AppManagerDocumentsProvider.ROOT_ID_PROFILES.equals(roots.getString(idColumn));
            }
        }
        assertTrue(sawProfiles);
    }

    @Test
    public void isChildDocumentAnswersForDescendantsOfTheSameRootOnly() {
        assertTrue(mProvider.isChildDocument("profiles", "profiles:nested"));
        assertTrue(mProvider.isChildDocument("profiles", "profiles:nested/inside.am.json"));
        assertTrue(mProvider.isChildDocument("profiles:nested", "profiles:nested/inside.am.json"));

        assertFalse(mProvider.isChildDocument("profiles:nested", "profiles:nested"));
        assertFalse(mProvider.isChildDocument("profiles:nested", "profiles:nested-sibling/outside.am.json"));
        assertFalse(mProvider.isChildDocument("profiles:nested", "profiles:nested/missing.am.json"));
        assertFalse(mProvider.isChildDocument("profiles:nested", "profiles:nested/../nested-sibling/outside.am.json"));
        assertFalse(mProvider.isChildDocument("profiles:nested/inside.am.json", "profiles:nested/inside.am.json"));
        assertFalse(mProvider.isChildDocument("backups", "profiles:nested/inside.am.json"));
        assertFalse(mProvider.isChildDocument("unknown", "profiles:nested/inside.am.json"));
    }

    @Test
    public void aTreeGrantReachesItsOwnDocumentsAndNothingElse() throws FileNotFoundException {
        Uri tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "profiles:nested");
        Uri inside = DocumentsContract.buildDocumentUriUsingTree(tree, "profiles:nested/inside.am.json");
        Uri outside = DocumentsContract.buildDocumentUriUsingTree(tree, "profiles:nested-sibling/outside.am.json");

        try (Cursor cursor = query(inside)) {
            assertNotNull(cursor);
            assertEquals(1, cursor.getCount());
        }
        try (Cursor children = query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, "profiles:nested"))) {
            assertNotNull(children);
            assertEquals(1, children.getCount());
        }
        // The framework refuses before the provider reads anything
        assertThrows(SecurityException.class, () -> query(outside));
        assertThrows(SecurityException.class, () -> mProvider.openFile(outside, "r"));
    }

    @Test
    public void documentsStayReadOnly() throws FileNotFoundException {
        try (Cursor cursor = mProvider.queryDocument("profiles:nested/inside.am.json", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)));
        }
        for (String mode : new String[]{"w", "wt", "wa", "rw", "rwt"}) {
            assertThrows(mode, FileNotFoundException.class,
                    () -> mProvider.openDocument("profiles:nested/inside.am.json", mode, null));
        }
    }

    /** The query form a client's resolver sends on this release; DocumentsProvider takes only that one. */
    private Cursor query(@NonNull Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return mProvider.query(uri, null, null, null);
        }
        return mProvider.query(uri, null, null, null, null);
    }

    private static void write(@NonNull File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write('{');
            out.write('}');
        }
    }

    private static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
