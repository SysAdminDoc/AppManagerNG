// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

// Mostly taken from: https://github.com/zhanghai/MaterialFiles/blob/43a22b31d59a59e44a05a972269a3bd9cd0c9b8b/app/src/main/java/me/zhanghai/android/files/file/MimeTypeIcon.kt
// Others are freelanced.
final class FmIcons {
    private static final String TAG = FmIcons.class.getSimpleName();

    private static final int DRAWABLE_APK = R.drawable.ic_android;
    private static final int DRAWABLE_ARCHIVE = R.drawable.ic_archive;
    private static final int DRAWABLE_AUDIO = R.drawable.ic_audio_file;
    private static final int DRAWABLE_CALENDAR = R.drawable.ic_calendar_month;
    private static final int DRAWABLE_CERTIFICATE = R.drawable.ic_shield_key;
    private static final int DRAWABLE_CODE = R.drawable.ic_code;
    private static final int DRAWABLE_CONTACT = R.drawable.ic_contact_page;
    private static final int DRAWABLE_DATABASE = R.drawable.ic_database;
    private static final int DRAWABLE_DIRECTORY = R.drawable.ic_folder;
    private static final int DRAWABLE_DOCUMENT = R.drawable.ic_description;
    private static final int DRAWABLE_EBOOK = R.drawable.ic_book;
    private static final int DRAWABLE_EMAIL = R.drawable.ic_email;
    private static final int DRAWABLE_FONT = R.drawable.ic_font_download;
    private static final int DRAWABLE_GENERIC = R.drawable.ic_file;
    private static final int DRAWABLE_IMAGE = R.drawable.ic_image;
    private static final int DRAWABLE_PDF = R.drawable.ic_pdf_file;
    private static final int DRAWABLE_PRESENTATION = R.drawable.ic_presentation;
    private static final int DRAWABLE_SPREADSHEET = R.drawable.ic_table;
    private static final int DRAWABLE_TEXT = R.drawable.ic_file_document;
    private static final int DRAWABLE_VIDEO = R.drawable.ic_video_file;
    private static final int DRAWABLE_WORD = DRAWABLE_DOCUMENT;
    private static final int DRAWABLE_EXCEL = DRAWABLE_SPREADSHEET;
    private static final int DRAWABLE_POWERPOINT = DRAWABLE_PRESENTATION;

    private static final Map<String, Integer> sMimeTypeToIconMap = new HashMap<String, Integer>() {
        {
            put("application/vnd.android.package-archive", DRAWABLE_APK);
            put("application/vnd.apkm", DRAWABLE_APK);
            put("application/x-apks", DRAWABLE_APK);
            put("application/xapk-package-archive", DRAWABLE_APK);
            put("application/gzip", DRAWABLE_ARCHIVE);
            // Not in IANA list, but Mozilla and Wikipedia say so.
            put("application/java-archive", DRAWABLE_ARCHIVE);
            put("application/mac-binhex40", DRAWABLE_ARCHIVE);
            // Not in IANA list, but AOSP MimeUtils says so.
            put("application/rar", DRAWABLE_ARCHIVE);
            put("application/zip", DRAWABLE_ARCHIVE);
            put("application/vnd.debian.binary-package", DRAWABLE_ARCHIVE);
            put("application/vnd.ms-cab-compressed", DRAWABLE_ARCHIVE);
            put("application/vnd.rar", DRAWABLE_ARCHIVE);
            put("application/x-7z-compressed", DRAWABLE_ARCHIVE);
            put("application/x-apple-diskimage", DRAWABLE_ARCHIVE);
            put("application/x-bzip", DRAWABLE_ARCHIVE);
            put("application/x-bzip2", DRAWABLE_ARCHIVE);
            put("application/x-compress", DRAWABLE_ARCHIVE);
            put("application/x-cpio", DRAWABLE_ARCHIVE);
            put("application/x-deb", DRAWABLE_ARCHIVE);
            put("application/x-debian-package", DRAWABLE_ARCHIVE);
            put("application/x-gtar", DRAWABLE_ARCHIVE);
            put("application/x-gtar-compressed", DRAWABLE_ARCHIVE);
            put("application/x-gzip", DRAWABLE_ARCHIVE);
            put("application/x-iso9660-image", DRAWABLE_ARCHIVE);
            put("application/x-java-archive", DRAWABLE_ARCHIVE);
            put("application/x-lha", DRAWABLE_ARCHIVE);
            put("application/x-lzh", DRAWABLE_ARCHIVE);
            put("application/x-lzma", DRAWABLE_ARCHIVE);
            put("application/x-lzx", DRAWABLE_ARCHIVE);
            put("application/x-rar", DRAWABLE_ARCHIVE);
            put("application/x-rar-compressed", DRAWABLE_ARCHIVE);
            put("application/x-stuffit", DRAWABLE_ARCHIVE);
            put("application/x-tar", DRAWABLE_ARCHIVE);
            put("application/x-webarchive", DRAWABLE_ARCHIVE);
            put("application/x-webarchive-xml", DRAWABLE_ARCHIVE);
            put("application/x-xz", DRAWABLE_ARCHIVE);
            put("application/ogg", DRAWABLE_AUDIO);
            put("application/x-flac", DRAWABLE_AUDIO);
            put("text/calendar", DRAWABLE_CALENDAR);
            put("text/x-vcalendar", DRAWABLE_CALENDAR);
            put("application/pem-certificate-chain", DRAWABLE_CERTIFICATE);
            put("application/pgp", DRAWABLE_CERTIFICATE);
            put("application/pgp-encrypted", DRAWABLE_CERTIFICATE);
            put("application/pgp-keys", DRAWABLE_CERTIFICATE);
            put("application/pgp-signature", DRAWABLE_CERTIFICATE);
            put("application/pkcs8", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs12", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-certificates", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-certreqresp", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-crl", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-mime", DRAWABLE_CERTIFICATE);
            put("application/x-pkcs7-signature", DRAWABLE_CERTIFICATE);
            put("application/x-x509-ca-cert", DRAWABLE_CERTIFICATE);
            put("application/x-x509-server-cert", DRAWABLE_CERTIFICATE);
            put("application/x-x509-user-cert", DRAWABLE_CERTIFICATE);
            put("application/ecmascript", DRAWABLE_CODE);
            put("application/javascript", DRAWABLE_CODE);
            put("application/json", DRAWABLE_CODE);
            put("application/toml", DRAWABLE_CODE);
            put("application/typescript", DRAWABLE_CODE);
            put("application/xml", DRAWABLE_CODE);
            put("application/x-csh", DRAWABLE_CODE);
            put("application/x-ecmascript", DRAWABLE_CODE);
            put("application/x-javascript", DRAWABLE_CODE);
            put("application/x-latex", DRAWABLE_CODE);
            put("application/x-perl", DRAWABLE_CODE);
            put("application/x-plist", DRAWABLE_CODE);
            put("application/x-python", DRAWABLE_CODE);
            put("application/x-ruby", DRAWABLE_CODE);
            put("application/x-sh", DRAWABLE_CODE);
            put("application/x-shellscript", DRAWABLE_CODE);
            put("application/x-smali", DRAWABLE_CODE);
            put("application/x-texinfo", DRAWABLE_CODE);
            put("application/x-yaml", DRAWABLE_CODE);
            put("text/css", DRAWABLE_CODE);
            put("text/html", DRAWABLE_CODE);
            put("text/ecmascript", DRAWABLE_CODE);
            put("text/javascript", DRAWABLE_CODE);
            put("text/jscript", DRAWABLE_CODE);
            put("text/livescript", DRAWABLE_CODE);
            put("text/xml", DRAWABLE_CODE);
            put("text/x-asm", DRAWABLE_CODE);
            put("text/x-c++hdr", DRAWABLE_CODE);
            put("text/x-c++src", DRAWABLE_CODE);
            put("text/x-chdr", DRAWABLE_CODE);
            put("text/x-csh", DRAWABLE_CODE);
            put("text/x-csharp", DRAWABLE_CODE);
            put("text/x-csrc", DRAWABLE_CODE);
            put("text/x-dsrc", DRAWABLE_CODE);
            put("text/x-ecmascript", DRAWABLE_CODE);
            put("text/x-haskell", DRAWABLE_CODE);
            put("text/x-java", DRAWABLE_CODE);
            put("text/x-java-source", DRAWABLE_CODE);
            put("text/x-javascript", DRAWABLE_CODE);
            put("text/x-kotlin", DRAWABLE_CODE);
            put("text/x-literate-haskell", DRAWABLE_CODE);
            put("text/x-lua", DRAWABLE_CODE);
            put("text/x-pascal", DRAWABLE_CODE);
            put("text/x-perl", DRAWABLE_CODE);
            put("text/x-php", DRAWABLE_CODE);
            put("text/x-python", DRAWABLE_CODE);
            put("text/x-ruby", DRAWABLE_CODE);
            put("text/x-shellscript", DRAWABLE_CODE);
            put("text/x-tcl", DRAWABLE_CODE);
            put("text/x-tex", DRAWABLE_CODE);
            put("text/x-yaml", DRAWABLE_CODE);
            put("text/vcard", DRAWABLE_CONTACT);
            put("text/x-vcard", DRAWABLE_CONTACT);
            put("application/vnd.sqlite3", DRAWABLE_DATABASE);
            put("application/x-sqlite3", DRAWABLE_DATABASE);
            put("inode/directory", DRAWABLE_DIRECTORY);
            put(DocumentsContract.Document.MIME_TYPE_DIR, DRAWABLE_DIRECTORY);
            put("resource/folder", DRAWABLE_DIRECTORY);
            put("application/rtf", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-master", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-template", DRAWABLE_DOCUMENT);
            put("application/vnd.oasis.opendocument.text-web", DRAWABLE_DOCUMENT);
            put("application/vnd.stardivision.writer", DRAWABLE_DOCUMENT);
            put("application/vnd.stardivision.writer-global", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer.global", DRAWABLE_DOCUMENT);
            put("application/vnd.sun.xml.writer.template", DRAWABLE_DOCUMENT);
            put("application/x-abiword", DRAWABLE_DOCUMENT);
            put("application/x-kword", DRAWABLE_DOCUMENT);
            put("text/rtf", DRAWABLE_DOCUMENT);
            put("application/epub+zip", DRAWABLE_EBOOK);
            put("application/vnd.amazon.ebook", DRAWABLE_EBOOK);
            put("application/x-cbr", DRAWABLE_EBOOK);
            put("application/x-cbz", DRAWABLE_EBOOK);
            put("application/x-ibooks+zip", DRAWABLE_EBOOK);
            put("application/x-mobipocket-ebook", DRAWABLE_EBOOK);
            put("application/vnd.ms-outlook", DRAWABLE_EMAIL);
            put("message/rfc822", DRAWABLE_EMAIL);
            put("application/font-cff", DRAWABLE_FONT);
            put("application/font-off", DRAWABLE_FONT);
            put("application/font-sfnt", DRAWABLE_FONT);
            put("application/font-ttf", DRAWABLE_FONT);
            put("application/font-woff", DRAWABLE_FONT);
            put("application/vnd.ms-fontobject", DRAWABLE_FONT);
            put("application/vnd.ms-opentype", DRAWABLE_FONT);
            put("application/x-font", DRAWABLE_FONT);
            put("application/x-font-otf", DRAWABLE_FONT);
            put("application/x-font-ttf", DRAWABLE_FONT);
            put("application/x-font-woff", DRAWABLE_FONT);
            put("application/vnd.oasis.opendocument.graphics", DRAWABLE_IMAGE);
            put("application/vnd.oasis.opendocument.graphics-template", DRAWABLE_IMAGE);
            put("application/vnd.oasis.opendocument.image", DRAWABLE_IMAGE);
            put("application/vnd.stardivision.draw", DRAWABLE_IMAGE);
            put("application/vnd.sun.xml.draw", DRAWABLE_IMAGE);
            put("application/vnd.sun.xml.draw.template", DRAWABLE_IMAGE);
            put("application/vnd.visio", DRAWABLE_IMAGE);
            put("application/pdf", DRAWABLE_PDF);
            put("application/vnd.oasis.opendocument.presentation", DRAWABLE_PRESENTATION);
            put("application/vnd.oasis.opendocument.presentation-template", DRAWABLE_PRESENTATION);
            put("application/vnd.stardivision.impress", DRAWABLE_PRESENTATION);
            put("application/vnd.sun.xml.impress", DRAWABLE_PRESENTATION);
            put("application/vnd.sun.xml.impress.template", DRAWABLE_PRESENTATION);
            put("application/x-kpresenter", DRAWABLE_PRESENTATION);
            put("application/vnd.oasis.opendocument.spreadsheet", DRAWABLE_SPREADSHEET);
            put("application/vnd.oasis.opendocument.spreadsheet-template", DRAWABLE_SPREADSHEET);
            put("application/vnd.stardivision.calc", DRAWABLE_SPREADSHEET);
            put("application/vnd.sun.xml.calc", DRAWABLE_SPREADSHEET);
            put("application/vnd.sun.xml.calc.template", DRAWABLE_SPREADSHEET);
            put("application/x-kspread", DRAWABLE_SPREADSHEET);
            put("application/x-quicktimeplayer", DRAWABLE_VIDEO);
            put("application/x-shockwave-flash", DRAWABLE_VIDEO);
            put("application/msword", DRAWABLE_WORD);
            put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", DRAWABLE_WORD);
            put("application/vnd.openxmlformats-officedocument.wordprocessingml.template", DRAWABLE_WORD);
            put("application/vnd.ms-excel", DRAWABLE_EXCEL);
            put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DRAWABLE_EXCEL);
            put("application/vnd.openxmlformats-officedocument.spreadsheetml.template", DRAWABLE_EXCEL);
            put("application/vnd.ms-powerpoint", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.presentation", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.slideshow", DRAWABLE_POWERPOINT);
            put("application/vnd.openxmlformats-officedocument.presentationml.template", DRAWABLE_POWERPOINT);
        }
    };

    private static final Map<String, Integer> sTypeToIconMap = new HashMap<String, Integer>() {
        {
            put("audio", DRAWABLE_AUDIO);
            put("font", DRAWABLE_FONT);
            put("image", DRAWABLE_IMAGE);
            put("text", DRAWABLE_TEXT);
            put("video", DRAWABLE_VIDEO);
        }
    };

    /**
     * Used when the content says nothing more than "some bytes" (or "some text"): the name still
     * tells what kind of file it is.
     */
    private static final Map<String, Integer> sExtensionToIconMap = new HashMap<>();

    static {
        putExtensions(DRAWABLE_APK, "apk", "apkm", "apks", "xapk");
        putExtensions(DRAWABLE_ARCHIVE, "7z", "bz2", "cab", "cpio", "deb", "dmg", "gz", "iso", "jar", "lz", "lz4",
                "lzma", "rar", "rpm", "tar", "tbz2", "tgz", "txz", "xz", "z", "zip", "zst");
        putExtensions(DRAWABLE_AUDIO, "aac", "aiff", "amr", "ape", "flac", "m4a", "mid", "midi", "mp3", "oga",
                "ogg", "opus", "wav", "wma");
        putExtensions(DRAWABLE_CALENDAR, "ics");
        putExtensions(DRAWABLE_CERTIFICATE, "cer", "crt", "der", "p12", "pem", "pfx");
        putExtensions(DRAWABLE_CODE, "bash", "bat", "c", "cc", "cpp", "cs", "css", "dart", "go", "gradle", "groovy",
                "h", "hpp", "htm", "html", "ini", "java", "js", "json", "kt", "kts", "lua", "mjs", "php", "pl",
                "properties", "ps1", "py", "rb", "rs", "scala", "sh", "smali", "sql", "swift", "toml", "ts", "xml",
                "yaml", "yml", "zsh");
        putExtensions(DRAWABLE_CONTACT, "vcf");
        putExtensions(DRAWABLE_DATABASE, "db", "sqlite", "sqlite3");
        putExtensions(DRAWABLE_DOCUMENT, "abw", "doc", "docm", "docx", "dot", "dotx", "odt", "ott", "rtf");
        putExtensions(DRAWABLE_EBOOK, "azw", "azw3", "cbr", "cbz", "epub", "fb2", "mobi");
        putExtensions(DRAWABLE_EMAIL, "eml", "msg");
        putExtensions(DRAWABLE_FONT, "otf", "ttf", "woff", "woff2");
        putExtensions(DRAWABLE_IMAGE, "avif", "bmp", "dng", "gif", "heic", "heif", "ico", "jpeg", "jpg", "png",
                "svg", "tif", "tiff", "webp");
        putExtensions(DRAWABLE_PDF, "pdf");
        putExtensions(DRAWABLE_PRESENTATION, "key", "odp", "ppt", "pptx");
        putExtensions(DRAWABLE_SPREADSHEET, "csv", "ods", "tsv", "xls", "xlsx");
        putExtensions(DRAWABLE_TEXT, "log", "markdown", "md", "nfo", "rst", "text", "txt");
        putExtensions(DRAWABLE_VIDEO, "3gp", "avi", "flv", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "webm",
                "wmv");
    }

    private static void putExtensions(@DrawableRes int drawable, @NonNull String... extensions) {
        for (String extension : extensions) {
            sExtensionToIconMap.put(extension, drawable);
        }
    }

    /**
     * The icon for a file whose preview isn't available. The detected type decides; the extension
     * fills in when the type is unknown, or narrows plain text down to source code.
     */
    @DrawableRes
    public static int getDrawable(@Nullable String mimeType, @Nullable String extension) {
        int drawable = getDrawableFromType(mimeType);
        if ((drawable != DRAWABLE_GENERIC && drawable != DRAWABLE_TEXT) || extension == null) {
            return drawable;
        }
        Integer byExtension = sExtensionToIconMap.get(extension.toLowerCase(Locale.ROOT));
        if (byExtension == null) {
            return drawable;
        }
        if (drawable == DRAWABLE_TEXT) {
            // Source code sniffs as plain text; anything else claimed by the name stays text
            return byExtension == DRAWABLE_CODE ? DRAWABLE_CODE : drawable;
        }
        return byExtension;
    }

    @DrawableRes
    public static int getDrawableFromType(@Nullable String mimeType) {
        if (mimeType == null) {
            return DRAWABLE_GENERIC;
        }
        Integer drawable = sMimeTypeToIconMap.get(mimeType);
        if (drawable != null) {
            return drawable;
        }
        String firstPart = mimeType.split("/")[0];
        drawable = sTypeToIconMap.get(firstPart);
        return drawable != null ? drawable : DRAWABLE_GENERIC;
    }

    public static boolean isApk(@DrawableRes int drawable) {
        return drawable == DRAWABLE_APK;
    }

    public static boolean isArchive(@DrawableRes int drawable) {
        return drawable == DRAWABLE_ARCHIVE;
    }

    public static boolean isImage(@DrawableRes int drawable) {
        return drawable == DRAWABLE_IMAGE;
    }

    public static boolean isAudio(@DrawableRes int drawable) {
        return drawable == DRAWABLE_AUDIO;
    }

    public static boolean isVideo(@DrawableRes int drawable) {
        return drawable == DRAWABLE_VIDEO;
    }

    public static boolean isMedia(@DrawableRes int drawable) {
        return drawable == DRAWABLE_AUDIO || drawable == DRAWABLE_VIDEO;
    }

    public static boolean isEbook(@DrawableRes int drawable) {
        return drawable == DRAWABLE_EBOOK;
    }

    public static boolean isFont(@DrawableRes int drawable) {
        return drawable == DRAWABLE_FONT;
    }

    public static boolean isPdf(@DrawableRes int drawable) {
        return drawable == DRAWABLE_PDF;
    }

    public static boolean isGeneric(@DrawableRes int drawable) {
        return drawable == DRAWABLE_GENERIC;
    }

    @Nullable
    public static Bitmap generateApkIcon(@NonNull Path path) {
        Pair<File, Boolean> file = getUsableFile(path);
        if (file == null) {
            return null;
        }
        try {
            return getApkIcon(file.first);
        } finally {
            if (file.second) {
                file.first.delete();
            }
        }
    }

    /**
     * The icon of an APKS file's base APK. The file's own icon.png is read by the isolated
     * thumbnail decoder first.
     */
    @Nullable
    public static Bitmap getApksBaseIcon(@NonNull Path path) {
        Pair<File, Boolean> file = getUsableFile(path);
        if (file == null) {
            return null;
        }
        try {
            // Load as ApkFile, read in place: a thumbnail needs no private copy of the whole bundle
            try (ApkFile apkFile = ApkFile.openForPreview(Uri.fromFile(file.first), path.getType())) {
                ApkFile.Entry baseEntry = apkFile.getBaseEntry();
                return getApkIcon(baseEntry.getFile(false));
            }
        } catch (IOException | ApkFile.ApkFileException e) {
            Log.w(TAG, e);
        } finally {
            if (file.second) {
                file.first.delete();
            }
        }
        return null;
    }

    @Nullable
    private static Bitmap getApkIcon(@NonNull File apkFile) {
        PackageManager pm = ContextUtils.getContext().getPackageManager();
        String f = apkFile.getAbsolutePath();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(f, 0);
        if (packageInfo != null) {
            ApplicationInfo info = packageInfo.applicationInfo;
            info.sourceDir = info.publicSourceDir = f;
            if (info.icon != 0) {
                return UIUtils.getBitmapFromDrawable(info.loadIcon(pm));
            }
        }
        return null;
    }

    /**
     * The file itself if this process can read it, otherwise a copy in the cache, flagged
     * {@code true} so the caller deletes it.
     */
    @Nullable
    static Pair<File, Boolean> getUsableFile(@NonNull Path path) {
        File f = path.getFile();
        if (f == null) {
            try {
                return new Pair<>(FileCache.getGlobalFileCache().getCachedFile(path), true);
            } catch (IOException ignore) {
                return null;
            }
        }
        f = new File(f.getPath());
        if (!f.canRead()) {
            try {
                return new Pair<>(FileCache.getGlobalFileCache().getCachedFile(path), true);
            } catch (IOException ignore) {
                return null;
            }
        }
        return new Pair<>(f, false);
    }
}
