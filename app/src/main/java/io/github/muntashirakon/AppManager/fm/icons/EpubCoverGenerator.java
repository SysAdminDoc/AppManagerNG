// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.compat.xml.Xml;

/**
 * Finds an eBook's cover image. Runs in the isolated thumbnail process.
 */
final class EpubCoverGenerator {
    /**
     * @return the cover image's bytes, or {@code null} if the book doesn't name one
     */
    @Nullable
    static byte[] readCover(@NonNull BoundedZipReader zip) throws IOException {
        String opfFile = getOpfFileLocation(zip.read("META-INF/container.xml", ThumbnailDecoder.MAX_METADATA_BYTES));
        if (opfFile == null) {
            return null;
        }
        byte[] opf = zip.read(opfFile, ThumbnailDecoder.MAX_METADATA_BYTES);
        String coverId = getCoverId(opf);
        if (coverId == null) {
            return null;
        }
        String coverImage = getCover(opf, coverId);
        if (coverImage == null) {
            return null;
        }
        int parentSeparator = opfFile.lastIndexOf('/');
        String parent = parentSeparator != -1 ? opfFile.substring(0, parentSeparator) : null;
        String coverImageLocation;
        if (parent != null) {
            coverImageLocation = parent + "/" + coverImage;
        } else coverImageLocation = coverImage;
        return zip.read(coverImageLocation, ThumbnailDecoder.MAX_ENTRY_BYTES);
    }

    @Nullable
    private static String getOpfFileLocation(@Nullable byte[] container) {
        if (container == null) {
            return null;
        }
        try {
            XmlPullParser parser = newParser(container);
            parser.require(XmlPullParser.START_TAG, null, "container");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("rootfile".equals(tagName)) {
                        return parser.getAttributeValue(null, "full-path");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }

    @Nullable
    private static String getCoverId(@Nullable byte[] opf) {
        if (opf == null) {
            return null;
        }
        try {
            XmlPullParser parser = newParser(opf);
            parser.require(XmlPullParser.START_TAG, null, "package");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("meta".equals(tagName) && "cover".equals(parser.getAttributeValue(null, "name"))) {
                        return parser.getAttributeValue(null, "content");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }

    @Nullable
    private static String getCover(@NonNull byte[] opf, @NonNull String coverId) {
        try {
            XmlPullParser parser = newParser(opf);
            parser.require(XmlPullParser.START_TAG, null, "package");
            int event = parser.next();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    String id = parser.getAttributeValue(null, "id");
                    if ("item".equals(tagName) && id == null) {
                        event = parser.next();
                        continue;
                    }
                    String properties = parser.getAttributeValue(null, "properties");
                    if (coverId.equals(id)) {
                        return parser.getAttributeValue(null, "href");
                    } else if ("cover-image".equals(properties)) {
                        return parser.getAttributeValue(null, "href");
                    }
                }
                event = parser.next();
            }
        } catch (IOException | XmlPullParserException ignore) {
        }
        return null;
    }

    @NonNull
    private static XmlPullParser newParser(@NonNull byte[] xml) throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newFastPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        parser.nextTag();
        return parser;
    }
}
