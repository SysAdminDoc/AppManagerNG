// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.ExtendedFile;
import io.github.muntashirakon.io.Path;

public class AppManagerDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".documents";

    @VisibleForTesting
    static final String ROOT_ID_BACKUPS = "backups";
    @VisibleForTesting
    static final String ROOT_ID_PROFILES = "profiles";

    private static final String DOCUMENT_ID_SEPARATOR = ":";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_ICON,
    };

    private static final RootDefinition[] ROOTS = new RootDefinition[]{
            new RootDefinition(ROOT_ID_BACKUPS, R.string.backup, R.drawable.ic_backup_restore) {
                @Nullable
                @Override
                File resolveRootFile(@NonNull Context context) {
                    return getLocalFile(Prefs.Storage.getAppManagerDirectory());
                }
            },
            new RootDefinition(ROOT_ID_PROFILES, R.string.profiles, R.drawable.ic_file_document_multiple) {
                @Nullable
                @Override
                File resolveRootFile(@NonNull Context context) {
                    return getLocalFile(ProfileManager.getProfilesDir());
                }
            },
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (!info.exported) {
            throw new SecurityException("DocumentsProvider must be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("DocumentsProvider must grant URI permissions");
        }
    }

    @NonNull
    @Override
    public Cursor queryRoots(@Nullable String[] projection) {
        String[] columns = resolveRootProjection(projection);
        MatrixCursor cursor = new MatrixCursor(columns);
        Context context = requireProviderContext();
        Map<String, File> roots = getAvailableRoots(context);
        for (RootDefinition root : ROOTS) {
            File rootFile = roots.get(root.id);
            if (rootFile == null) {
                continue;
            }
            String documentId;
            try {
                documentId = buildDocumentIdForFile(root.id, rootFile, rootFile);
            } catch (FileNotFoundException e) {
                continue;
            }
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                switch (column) {
                    case DocumentsContract.Root.COLUMN_ROOT_ID:
                        row.add(root.id);
                        break;
                    case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
                        row.add(documentId);
                        break;
                    case DocumentsContract.Root.COLUMN_TITLE:
                        row.add(context.getString(root.titleRes));
                        break;
                    case DocumentsContract.Root.COLUMN_ICON:
                        row.add(root.iconRes);
                        break;
                    case DocumentsContract.Root.COLUMN_FLAGS:
                        row.add(DocumentsContract.Root.FLAG_LOCAL_ONLY);
                        break;
                    case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
                        row.add(rootFile.getUsableSpace());
                        break;
                    default:
                        row.add(null);
                        break;
                }
            }
        }
        return cursor;
    }

    @NonNull
    @Override
    public Cursor queryDocument(@NonNull String documentId, @Nullable String[] projection) throws FileNotFoundException {
        File file = resolveDocumentFile(documentId, getAvailableRoots(requireProviderContext()));
        MatrixCursor cursor = new MatrixCursor(resolveDocumentProjection(projection));
        includeDocument(cursor, documentId, file);
        return cursor;
    }

    @NonNull
    @Override
    public Cursor queryChildDocuments(@NonNull String parentDocumentId, @Nullable String[] projection,
                                      @Nullable String sortOrder) throws FileNotFoundException {
        Map<String, File> roots = getAvailableRoots(requireProviderContext());
        RootIdAndFile parent = resolveDocumentFileWithRoot(parentDocumentId, roots);
        if (!parent.file.isDirectory()) {
            throw new FileNotFoundException(parentDocumentId + " is not a directory");
        }
        MatrixCursor cursor = new MatrixCursor(resolveDocumentProjection(projection));
        for (File child : listDocumentChildren(parent.file)) {
            includeDocument(cursor, buildDocumentIdForFile(parent.rootId, roots.get(parent.rootId), child), child);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getDocumentType(@NonNull String documentId) throws FileNotFoundException {
        File file = resolveDocumentFile(documentId, getAvailableRoots(requireProviderContext()));
        return getMimeType(file);
    }

    @NonNull
    @Override
    public ParcelFileDescriptor openDocument(@NonNull String documentId, @NonNull String mode,
                                             @Nullable CancellationSignal signal) throws FileNotFoundException {
        if (mode.indexOf('w') != -1 || mode.indexOf('a') != -1 || mode.indexOf('+') != -1) {
            throw new FileNotFoundException("AppManagerNG documents are read-only");
        }
        File file = resolveDocumentFile(documentId, getAvailableRoots(requireProviderContext()));
        if (!file.isFile()) {
            throw new FileNotFoundException(documentId + " is not a file");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void includeDocument(@NonNull MatrixCursor cursor, @NonNull String documentId,
                                        @NonNull File file) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        String[] columns = cursor.getColumnNames();
        boolean isDirectory = file.isDirectory();
        for (String column : columns) {
            switch (column) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
                    row.add(documentId);
                    break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                    row.add(file.getName());
                    break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:
                    row.add(getMimeType(file));
                    break;
                case DocumentsContract.Document.COLUMN_FLAGS:
                    row.add(0);
                    break;
                case DocumentsContract.Document.COLUMN_SIZE:
                    row.add(isDirectory ? null : file.length());
                    break;
                case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
                    row.add(file.lastModified());
                    break;
                case DocumentsContract.Document.COLUMN_ICON:
                    row.add(isDirectory ? R.drawable.ic_folder : R.drawable.ic_file_document);
                    break;
                default:
                    row.add(null);
                    break;
            }
        }
    }

    @NonNull
    @VisibleForTesting
    static String buildDocumentIdForFile(@NonNull String rootId, @NonNull File root, @NonNull File file)
            throws FileNotFoundException {
        try {
            File canonicalRoot = root.getCanonicalFile();
            File canonicalFile = file.getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String filePath = canonicalFile.getPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Path escapes root: " + file);
            }
            if (filePath.equals(rootPath)) {
                return rootId;
            }
            String relative = filePath.substring(rootPath.length() + 1)
                    .replace(File.separatorChar, '/');
            return rootId + DOCUMENT_ID_SEPARATOR + relative;
        } catch (IOException e) {
            FileNotFoundException fileNotFoundException = new FileNotFoundException(e.getMessage());
            fileNotFoundException.initCause(e);
            throw fileNotFoundException;
        }
    }

    @NonNull
    @VisibleForTesting
    static File resolveDocumentFile(@NonNull String documentId, @NonNull Map<String, File> roots)
            throws FileNotFoundException {
        return resolveDocumentFileWithRoot(documentId, roots).file;
    }

    @NonNull
    private static RootIdAndFile resolveDocumentFileWithRoot(@NonNull String documentId,
                                                             @NonNull Map<String, File> roots)
            throws FileNotFoundException {
        String rootId;
        String relativePath;
        int separator = documentId.indexOf(DOCUMENT_ID_SEPARATOR);
        if (separator == -1) {
            rootId = documentId;
            relativePath = "";
        } else {
            rootId = documentId.substring(0, separator);
            relativePath = documentId.substring(separator + 1);
        }
        File root = roots.get(rootId);
        if (root == null) {
            throw new FileNotFoundException("Unknown AppManagerNG document root: " + rootId);
        }
        if (relativePath.startsWith("/") || relativePath.indexOf('\0') != -1) {
            throw new FileNotFoundException("Invalid document path: " + documentId);
        }
        File file = relativePath.isEmpty() ? root : new File(root, relativePath.replace('/', File.separatorChar));
        String normalizedId = buildDocumentIdForFile(rootId, root, file);
        if (!documentId.equals(normalizedId)) {
            throw new FileNotFoundException("Invalid document path: " + documentId);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("Document does not exist: " + documentId);
        }
        return new RootIdAndFile(rootId, file);
    }

    @NonNull
    @VisibleForTesting
    static File[] listDocumentChildren(@NonNull File parent) {
        File[] children = parent.listFiles(file -> !file.isHidden());
        if (children == null) {
            return new File[0];
        }
        Arrays.sort(children, (first, second) -> {
            if (first.isDirectory() != second.isDirectory()) {
                return first.isDirectory() ? -1 : 1;
            }
            return first.getName().compareToIgnoreCase(second.getName());
        });
        return children;
    }

    @NonNull
    private static Map<String, File> getAvailableRoots(@NonNull Context context) {
        Map<String, File> roots = new HashMap<>();
        for (RootDefinition root : ROOTS) {
            File rootFile = root.resolveRootFile(context);
            if (rootFile == null) {
                continue;
            }
            if (!rootFile.exists() && !rootFile.mkdirs()) {
                continue;
            }
            if (rootFile.isDirectory() && rootFile.canRead()) {
                roots.put(root.id, rootFile);
            }
        }
        return roots;
    }

    @Nullable
    private static File getLocalFile(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        ExtendedFile file = path.getFile();
        return file != null ? file : null;
    }

    @NonNull
    private static String getMimeType(@NonNull File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1 && dot + 1 < name.length()) {
            ContentType2 type = ContentType2.fromFileExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (type != null) {
                return type.getMimeType();
            }
        }
        return ContentType2.OTHER.getMimeType();
    }

    @NonNull
    private static String[] resolveRootProjection(@Nullable String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    @NonNull
    private static String[] resolveDocumentProjection(@Nullable String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    @NonNull
    private Context requireProviderContext() {
        return Objects.requireNonNull(getContext());
    }

    private abstract static class RootDefinition {
        @NonNull
        final String id;
        @StringRes
        final int titleRes;
        @DrawableRes
        final int iconRes;

        RootDefinition(@NonNull String id, @StringRes int titleRes, @DrawableRes int iconRes) {
            this.id = id;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }

        @Nullable
        abstract File resolveRootFile(@NonNull Context context);
    }

    private static class RootIdAndFile {
        @NonNull
        final String rootId;
        @NonNull
        final File file;

        RootIdAndFile(@NonNull String rootId, @NonNull File file) {
            this.rootId = rootId;
            this.file = file;
        }
    }
}
