// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.io.Path;

/** Validates the optional JSON manifest stored in a Swift Backup ZIP comment. */
final class SwiftBackupManifest {
    private static final int ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;
    private static final int ZIP_END_OF_CENTRAL_DIRECTORY_SIZE = 22;
    private static final int ZIP_MAX_COMMENT_LENGTH = 65_535;
    private static final int ZIP_TAIL_LENGTH = ZIP_END_OF_CENTRAL_DIRECTORY_SIZE + ZIP_MAX_COMMENT_LENGTH;
    private static final int MAX_READ_BUFFER = 16 * 1024;
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Set<String> PACKAGE_KEYS = new HashSet<>();

    static {
        PACKAGE_KEYS.add("package");
        PACKAGE_KEYS.add("packagename");
        PACKAGE_KEYS.add("packageid");
        PACKAGE_KEYS.add("apppackage");
        PACKAGE_KEYS.add("pkg");
        PACKAGE_KEYS.add("pkgname");
        PACKAGE_KEYS.add("applicationid");
        PACKAGE_KEYS.add("androidpackage");
    }

    private SwiftBackupManifest() {
    }

    static void validateArchives(@NonNull Iterable<Path> sourceFiles,
            @NonNull String expectedPackageName) throws BackupException {
        for (Path sourceFile : sourceFiles) {
            if (!isArchive(sourceFile.getName())) continue;
            final String comment;
            try {
                comment = readZipComment(sourceFile);
            } catch (IOException e) {
                throw new BackupException("Could not read Swift Backup manifest from "
                        + sourceFile.getName(), e);
            }
            if (comment == null || comment.trim().isEmpty()) continue;
            try {
                validateComment(comment, expectedPackageName);
            } catch (BackupException e) {
                throw new BackupException("Invalid Swift Backup manifest in "
                        + sourceFile.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    static void validateComment(@NonNull String comment, @NonNull String expectedPackageName)
            throws BackupException {
        String normalizedComment = comment.trim();
        if (normalizedComment.startsWith("\uFEFF")) {
            normalizedComment = normalizedComment.substring(1).trim();
        }
        if (normalizedComment.isEmpty()) return;
        final JSONObject manifest;
        try {
            manifest = new JSONObject(normalizedComment);
        } catch (JSONException e) {
            throw new BackupException("Malformed JSON", e);
        }

        Set<String> declaredPackages = new HashSet<>();
        Set<String> packageKeyValues = new HashSet<>();
        collectPackageNames(manifest, declaredPackages, packageKeyValues);
        Set<String> candidates = packageKeyValues.isEmpty() ? declaredPackages : packageKeyValues;
        if (candidates.isEmpty()) {
            throw new BackupException("Manifest does not declare a package name");
        }
        if (candidates.size() != 1 || !candidates.contains(expectedPackageName)) {
            throw new BackupException("Package name mismatch: expected=" + expectedPackageName
                    + ", declared=" + candidates);
        }
    }

    private static void collectPackageNames(@NonNull JSONObject object,
            @NonNull Set<String> declaredPackages, @NonNull Set<String> packageKeyValues)
            throws BackupException {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value;
            try {
                value = object.get(key);
            } catch (JSONException e) {
                throw new BackupException("Could not read manifest field " + key, e);
            }
            String normalizedKey = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (PACKAGE_KEYS.contains(normalizedKey)) {
                if (value instanceof String) {
                    String packageName = ((String) value).trim();
                    if (packageName.isEmpty()) {
                        throw new BackupException("Manifest package name is empty");
                    }
                    packageKeyValues.add(packageName);
                } else if (value instanceof JSONObject) {
                    collectPackageNames((JSONObject) value, declaredPackages, packageKeyValues);
                } else if (value instanceof JSONArray) {
                    collectPackageNames((JSONArray) value, declaredPackages, packageKeyValues);
                } else {
                    throw new BackupException("Manifest package name is not a string");
                }
            } else if (value instanceof JSONObject) {
                collectPackageNames((JSONObject) value, declaredPackages, packageKeyValues);
            } else if (value instanceof JSONArray) {
                collectPackageNames((JSONArray) value, declaredPackages, packageKeyValues);
            } else if (value instanceof String) {
                String stringValue = ((String) value).trim();
                if (PACKAGE_NAME_PATTERN.matcher(stringValue).matches()) {
                    declaredPackages.add(stringValue);
                }
            }
        }
    }

    private static void collectPackageNames(@NonNull JSONArray array,
            @NonNull Set<String> declaredPackages, @NonNull Set<String> packageKeyValues)
            throws BackupException {
        for (int i = 0; i < array.length(); ++i) {
            Object value;
            try {
                value = array.get(i);
            } catch (JSONException e) {
                throw new BackupException("Could not read manifest array", e);
            }
            if (value instanceof JSONObject) {
                collectPackageNames((JSONObject) value, declaredPackages, packageKeyValues);
            } else if (value instanceof JSONArray) {
                collectPackageNames((JSONArray) value, declaredPackages, packageKeyValues);
            } else if (value instanceof String) {
                String stringValue = ((String) value).trim();
                if (PACKAGE_NAME_PATTERN.matcher(stringValue).matches()) {
                    declaredPackages.add(stringValue);
                }
            }
        }
    }

    private static boolean isArchive(@NonNull String name) {
        return name.endsWith(".app") || name.endsWith(".dat") || name.endsWith(".extdat")
                || name.endsWith(".exp") || name.endsWith(".splits");
    }

    private static String readZipComment(@NonNull Path archive) throws IOException {
        String filePath = archive.getFilePath();
        if (filePath != null) {
            try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
                long fileLength = file.length();
                int tailLength = (int) Math.min(fileLength, ZIP_TAIL_LENGTH);
                if (tailLength < ZIP_END_OF_CENTRAL_DIRECTORY_SIZE) {
                    throw new IOException("ZIP is too short");
                }
                byte[] tail = new byte[tailLength];
                file.seek(fileLength - tailLength);
                file.readFully(tail);
                return parseZipComment(tail, tail.length);
            }
        }
        try (InputStream inputStream = archive.openInputStream()) {
            return readZipComment(inputStream);
        }
    }

    static String readZipComment(@NonNull InputStream inputStream) throws IOException {
        byte[] tail = new byte[ZIP_TAIL_LENGTH];
        byte[] buffer = new byte[MAX_READ_BUFFER];
        int tailLength = 0;
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            if (count == 0) continue;
            if (count >= tail.length) {
                System.arraycopy(buffer, count - tail.length, tail, 0, tail.length);
                tailLength = tail.length;
            } else if (tailLength + count <= tail.length) {
                System.arraycopy(buffer, 0, tail, tailLength, count);
                tailLength += count;
            } else {
                int overflow = tailLength + count - tail.length;
                System.arraycopy(tail, overflow, tail, 0, tailLength - overflow);
                System.arraycopy(buffer, 0, tail, tailLength - overflow, count);
                tailLength = tail.length;
            }
        }
        if (tailLength < ZIP_END_OF_CENTRAL_DIRECTORY_SIZE) {
            throw new IOException("ZIP is too short");
        }
        return parseZipComment(tail, tailLength);
    }

    private static String parseZipComment(@NonNull byte[] tail, int tailLength) throws IOException {
        for (int offset = tailLength - ZIP_END_OF_CENTRAL_DIRECTORY_SIZE; offset >= 0; --offset) {
            if (readInt(tail, offset) != ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue;
            int commentLength = readUnsignedShort(tail, offset + 20);
            int commentStart = offset + ZIP_END_OF_CENTRAL_DIRECTORY_SIZE;
            if (commentStart + commentLength != tailLength) continue;
            if (commentLength == 0) return null;
            return new String(tail, commentStart, commentLength, StandardCharsets.UTF_8);
        }
        throw new IOException("ZIP end record is missing");
    }

    private static int readInt(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int readUnsignedShort(@NonNull byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }
}
