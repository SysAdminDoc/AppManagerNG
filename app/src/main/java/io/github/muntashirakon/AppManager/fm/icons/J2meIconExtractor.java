// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Finds a J2ME app's icon. Runs in the isolated thumbnail process.
 */
final class J2meIconExtractor {
    /**
     * @return the icon's bytes, or {@code null} if the file isn't a J2ME JAR or names no icon
     */
    @Nullable
    static byte[] readIcon(@NonNull BoundedZipReader zip) throws IOException {
        String iconFile = getIconLocation(zip.read("META-INF/MANIFEST.MF", ThumbnailDecoder.MAX_METADATA_BYTES));
        if (iconFile == null) {
            // Not a J2ME JAR
            return null;
        }
        return zip.read(iconFile, ThumbnailDecoder.MAX_ENTRY_BYTES);
    }

    @Nullable
    private static String getIconLocation(@Nullable byte[] manifestBytes) {
        if (manifestBytes == null) {
            return null;
        }
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
            Attributes attributes = manifest.getMainAttributes();
            // The logic is derived from J2ME Loader (ru.woesss.j2me.jar.Descriptor#getIcon())
            String icon = attributes.getValue("MIDlet-Icon");
            if (icon == null || icon.trim().isEmpty()) {
                String midlet = "MIDlet-" + 1;
                icon = attributes.getValue(midlet);
                if (icon == null) {
                    return null;
                }
                int start = icon.indexOf(',');
                if (start != -1) {
                    int end = icon.indexOf(',', ++start);
                    if (end != -1)
                        icon = icon.substring(start, end);
                }
            }
            icon = icon.trim();
            while (!icon.isEmpty() && icon.charAt(0) == '/') {
                icon = icon.substring(1);
            }
            return icon.isEmpty() ? null : icon;
        } catch (IOException ignore) {
        }
        return null;
    }
}
