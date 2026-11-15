// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.chunk.xml.ResXmlPullParser;
import com.reandroid.arsc.coder.ValueCoder;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.arsc.value.ValueType;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import io.github.muntashirakon.AppManager.utils.IntegerUtils;
import io.github.muntashirakon.io.IoUtils;

public class AndroidBinXmlDecoder {
    private static final String NEWLINE = "\n";
    private static final String TAG_MANIFEST = "manifest";
    private static final String ATTR_PLATFORM_BUILD_VERSION_CODE = "platformBuildVersionCode";

    public static boolean isBinaryXml(@NonNull ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.mark();
        int version = IntegerUtils.getUInt16(buffer);
        int header = IntegerUtils.getUInt16(buffer);
        buffer.reset();
        // 0x0000 is NULL header. The only example of application using a NULL header is NP Manager
        return (version == 0x0003 || version == 0x0000) && header == 0x0008;
    }

    @NonNull
    public static String decode(@NonNull byte[] data) throws IOException {
        return decode(ByteBuffer.wrap(data));
    }

    @NonNull
    public static String decode(@NonNull InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        int n;
        while (-1 != (n = is.read(buf))) {
            buffer.write(buf, 0, n);
        }
        return decode(buffer.toByteArray());
    }

    @NonNull
    public static String decode(@NonNull ByteBuffer byteBuffer) throws IOException {
        return decode(byteBuffer, FrameworkTables.UNKNOWN_API);
    }

    /**
     * @param compileSdk the API level the APK holding this file was compiled against, or 0 when
     *                   unknown. A manifest's own {@code compileSdkVersion} takes precedence.
     */
    @NonNull
    public static String decode(@NonNull ByteBuffer byteBuffer, int compileSdk) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            decode(byteBuffer, bos, compileSdk);
            byte[] bs = bos.toByteArray();
            return new String(bs, StandardCharsets.UTF_8);
        }
    }

    public static void decode(@NonNull ByteBuffer byteBuffer, @NonNull OutputStream os) throws IOException {
        decode(byteBuffer, os, FrameworkTables.UNKNOWN_API);
    }

    /**
     * Decodes binary XML with the framework table it was compiled against. A framework resource
     * ID that table can't name for certain (a private one that moved between SDKs, or any ID when
     * the API level isn't known and the SDKs disagree) is printed as hex rather than as another
     * resource's name.
     *
     * @param compileSdk the API level the APK holding this file was compiled against, or 0 when
     *                   unknown. A manifest's own {@code compileSdkVersion} takes precedence.
     * @throws IOException if the file isn't well-formed binary XML
     */
    public static void decode(@NonNull ByteBuffer byteBuffer, @NonNull OutputStream os, int compileSdk)
            throws IOException {
        ResXmlDocument document = readDocument(toByteArray(byteBuffer));
        int ownApi;
        try {
            ownApi = compileSdkOf(document);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
        FrameworkTables.Choice choice = FrameworkTables.forApi(
                ownApi != FrameworkTables.UNKNOWN_API ? ownApi : Math.max(compileSdk, FrameworkTables.UNKNOWN_API));
        // Outside the parse error handling: a missing table is a packaging bug, not a bad file
        PackageBlock framework = choice.packageBlock();
        try (PrintStream out = new PrintStream(os, false, StandardCharsets.UTF_8.name())) {
            document.setPackageBlock(framework);
            print(document, choice, out);
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        } catch (RuntimeException e) {
            throw malformed(e);
        }
    }

    /**
     * Reads a binary XML document after checking its structure, so a damaged or hostile file fails
     * with an {@link IOException} instead of exhausting memory or the stack. Chunks Android skips
     * are skipped here too.
     */
    @NonNull
    public static ResXmlDocument readDocument(@NonNull byte[] data) throws IOException {
        byte[] readable = BinaryXmlBounds.check(data);
        try (BlockReader reader = new BlockReader(readable)) {
            ResXmlDocument document = new ResXmlDocument();
            document.readBytes(reader);
            return document;
        } catch (RuntimeException e) {
            throw malformed(e);
        }
    }

    @NonNull
    private static IOException malformed(@NonNull Throwable cause) {
        return new IOException("Malformed binary XML", cause);
    }

    private static void print(@NonNull ResXmlDocument document, @NonNull FrameworkTables.Choice choice,
                              @NonNull PrintStream out) throws XmlPullParserException, IOException {
        try (ResXmlPullParser parser = new ResXmlPullParser(document)) {
            StringBuilder indent = new StringBuilder(10);
            final String indentStep = "  ";
            out.print("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEWLINE);
            XML_BUILDER:
            while (true) {
                int type = parser.next();
                switch (type) {
                    case START_TAG: {
                        out.printf("%s<%s%s", indent, getNamespacePrefix(parser.getPrefix()), parser.getName());
                        indent.append(indentStep);

                        int nsStart = parser.getNamespaceCount(parser.getDepth() - 1);
                        int nsEnd = parser.getNamespaceCount(parser.getDepth());
                        for (int i = nsStart; i < nsEnd; ++i) {
                            out.printf(NEWLINE + "%sxmlns:%s=\"%s\"", indent,
                                    parser.getNamespacePrefix(i),
                                    parser.getNamespaceUri(i));
                        }

                        for (int i = 0; i != parser.getAttributeCount(); ++i) {
                            ResXmlAttribute attribute = parser.getResXmlAttributeAt(i);
                            out.printf(NEWLINE + "%s%s%s=\"%s\"",
                                    indent,
                                    getNamespacePrefix(parser.getAttributePrefix(i)),
                                    attributeName(parser, i, attribute, choice),
                                    attributeValue(parser, i, attribute, choice));
                        }
                        out.print(">" + NEWLINE);
                        break;
                    }
                    case END_TAG: {
                        indent.setLength(indent.length() - indentStep.length());
                        out.printf("%s</%s%s>%s", indent, getNamespacePrefix(parser.getPrefix()), parser.getName(),
                                NEWLINE);
                        break;
                    }
                    case END_DOCUMENT:
                        break XML_BUILDER;
                    case START_DOCUMENT:
                        // Unreachable statement
                        break;
                }
            }
        }
    }

    /**
     * {@code attribute} is null for a namespace declaration, which the parser reports as one of the
     * first attributes ({@code xmlns:android}); it has no resource ID or typed value to check.
     */
    @NonNull
    private static String attributeName(@NonNull ResXmlPullParser parser, int index, @Nullable ResXmlAttribute attribute,
                                        @NonNull FrameworkTables.Choice choice) {
        if (attribute == null) {
            return parser.getAttributeName(index);
        }
        String name = parser.getAttributeName(index);
        int nameId = attribute.getNameId();
        if (nameId != 0 && !choice.canNameAttribute(nameId)) {
            // Keep the prefix in the form the parser gives it ("android:"), so the name stays in its namespace
            int colon = name == null ? -1 : name.indexOf(':');
            String raw = ValueCoder.decodeUnknownNameId(nameId);
            return colon < 0 ? raw : name.substring(0, colon + 1) + raw;
        }
        return name;
    }

    @Nullable
    private static String attributeValue(@NonNull ResXmlPullParser parser, int index, @Nullable ResXmlAttribute attribute,
                                         @NonNull FrameworkTables.Choice choice) {
        if (attribute == null) {
            return parser.getAttributeValue(index);
        }
        ValueType type = attribute.getValueType();
        int data = attribute.getData();
        if (type == ValueType.REFERENCE || type == ValueType.DYNAMIC_REFERENCE
                || type == ValueType.ATTRIBUTE || type == ValueType.DYNAMIC_ATTRIBUTE) {
            if (!choice.canNameReference(data)) {
                boolean reference = type == ValueType.REFERENCE || type == ValueType.DYNAMIC_REFERENCE;
                return ValueCoder.decodeUnknownResourceId(reference, data);
            }
            return parser.getAttributeValue(index);
        }
        int nameId = attribute.getNameId();
        if (type != null && type != ValueType.STRING && nameId != 0 && !choice.canNameAttribute(nameId)) {
            // Enum and flag names come from the attribute's definition, which this table may not have
            String plain = ValueCoder.decode(type, data);
            if (plain != null) {
                return plain;
            }
        }
        return parser.getAttributeValue(index);
    }

    /**
     * The API level a manifest says it was compiled against: {@code android:compileSdkVersion}, else
     * {@code platformBuildVersionCode}, both written by aapt2 for API 28 and later. 0 for anything
     * else, including every file that isn't a manifest.
     */
    @VisibleForTesting
    static int compileSdkOf(@NonNull ResXmlDocument document) {
        ResXmlElement root = document.getDocumentElement();
        if (root == null || !TAG_MANIFEST.equals(root.getName())) {
            return FrameworkTables.UNKNOWN_API;
        }
        int api = FrameworkTables.UNKNOWN_API;
        Iterator<ResXmlAttribute> it = root.getAttributes();
        while (it.hasNext()) {
            ResXmlAttribute attribute = it.next();
            int nameId = attribute.getNameId();
            if (nameId == FrameworkTables.ATTR_COMPILE_SDK_VERSION) {
                int value = apiLevel(attribute);
                if (value != FrameworkTables.UNKNOWN_API) {
                    return value;
                }
            } else if (nameId == 0 && TextUtils.isEmpty(attribute.getUri())
                    && ATTR_PLATFORM_BUILD_VERSION_CODE.equals(attribute.getName())) {
                api = apiLevel(attribute);
            }
        }
        return api;
    }

    private static int apiLevel(@NonNull ResXmlAttribute attribute) {
        ValueType type = attribute.getValueType();
        long value;
        if (type == ValueType.DEC || type == ValueType.HEX) {
            value = attribute.getData();
        } else if (type == ValueType.STRING) {
            String s = attribute.getValueAsString();
            if (s == null || s.isEmpty() || s.length() > 4 || !TextUtils.isDigitsOnly(s)) {
                return FrameworkTables.UNKNOWN_API;
            }
            value = Long.parseLong(s);
        } else {
            return FrameworkTables.UNKNOWN_API;
        }
        return value > 0 && value < 10_000 ? (int) value : FrameworkTables.UNKNOWN_API;
    }

    @NonNull
    private static String getNamespacePrefix(String prefix) {
        if (TextUtils.isEmpty(prefix)) {
            return "";
        }
        return prefix + ":";
    }

    /**
     * Copy a {@link ByteBuffer}'s remaining window into a fresh array. Unlike
     * {@link ByteBuffer#array()} this respects {@code position}/{@code limit}/
     * {@code arrayOffset} and works for read-only and direct (e.g. mmapped) buffers,
     * so callers can safely pass a sliced buffer.
     */
    @NonNull
    public static byte[] toByteArray(@NonNull ByteBuffer byteBuffer) {
        ByteBuffer dup = byteBuffer.duplicate();
        byte[] out = new byte[dup.remaining()];
        dup.get(out);
        return out;
    }

    /**
     * The newest SDK's framework package. Public framework IDs never change meaning, so it reads
     * and writes them correctly for any APK; only {@link #decode} needs the APK's own table.
     */
    @NonNull
    public static PackageBlock getFrameworkPackageBlock() {
        return FrameworkTables.newest();
    }
}
