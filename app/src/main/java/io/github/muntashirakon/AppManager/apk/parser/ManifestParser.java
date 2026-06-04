// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.arsc.value.ValueType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.logs.Log;

public class ManifestParser {
    public static final String TAG = ManifestParser.class.getSimpleName();

    // manifest
    private static final String TAG_MANIFEST = "manifest";
    private static final String ATTR_MANIFEST_PACKAGE = "package";
    // manifest -> application
    private static final String TAG_APPLICATION = "application";
    // manifest -> uses-sdk-library
    private static final String TAG_USES_SDK_LIBRARY = "uses-sdk-library";
    private static final String ATTR_VERSION_MAJOR = "versionMajor"; // android:versionMajor
    private static final String ATTR_CERT_DIGEST = "certDigest"; // android:certDigest
    // manifest -> application -> activity|activity-alias|service|receiver|provider
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_RECEIVER = "receiver";
    private static final String TAG_PROVIDER = "provider";
    private static final String ATTR_NAME = "name"; // android:name
    // manifest -> application|component -> meta-data
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_VALUE = "value"; // android:value
    private static final String ATTR_RESOURCE = "resource"; // android:resource
    // manifest -> application -> (component) -> intent-filter
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String ATTR_PRIORITY = "priority"; // android:priority
    // manifest -> application -> (component) -> intent-filter -> action|category|data
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_DATA = "data";

    private final @NonNull ByteBuffer mManifestBytes;
    private String mPackageName;

    public ManifestParser(@NonNull byte[] manifestBytes) {
        this(ByteBuffer.wrap(manifestBytes));
    }

    public ManifestParser(@NonNull ByteBuffer manifestBytes) {
        mManifestBytes = manifestBytes;
    }

    @NonNull
    public List<ManifestSdkLibrary> parseUsesSdkLibraries() throws IOException {
        try (BlockReader reader = new BlockReader(mManifestBytes.array())) {
            ResXmlDocument xmlBlock = new ResXmlDocument();
            xmlBlock.readBytes(reader);
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            if (!TAG_MANIFEST.equals(resManifestElement.getName())) {
                throw new IOException("\"manifest\" tag not found.");
            }
            List<ManifestSdkLibrary> sdkLibraries = new ArrayList<>();
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements(TAG_USES_SDK_LIBRARY);
            while (resXmlElementIt.hasNext()) {
                ResXmlElement elem = resXmlElementIt.next();
                String name = getAttributeValue(elem, ATTR_NAME);
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                sdkLibraries.add(new ManifestSdkLibrary(name, getLongAttributeValue(elem, ATTR_VERSION_MAJOR, -1),
                        getAttributeValue(elem, ATTR_CERT_DIGEST)));
            }
            return sdkLibraries;
        }
    }

    public List<ManifestComponent> parseComponents() throws IOException {
        try (BlockReader reader = new BlockReader(mManifestBytes.array())) {
            ResXmlDocument xmlBlock = new ResXmlDocument();
            xmlBlock.readBytes(reader);
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            // manifest
            if (!TAG_MANIFEST.equals(resManifestElement.getName())) {
                throw new IOException("\"manifest\" tag not found.");
            }
            String packageName = getAttributeValue(resManifestElement, ATTR_MANIFEST_PACKAGE);
            if (packageName == null) {
                throw new IOException("\"manifest\" does not have required attribute \"package\".");
            }
            mPackageName = packageName;
            // manifest -> application
            ResXmlElement resApplicationElement = null;
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements(TAG_APPLICATION);
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next();
            }
            if (resXmlElementIt.hasNext()) {
                throw new IOException("\"manifest\" has duplicate \"application\" tags.");
            }
            if (resApplicationElement == null) {
                Log.i(TAG, "package %s does not have \"application\" tag.", mPackageName);
                return Collections.emptyList();
            }
            // manifest -> application -> component
            List<ManifestComponent> componentIfList = new ArrayList<>(resApplicationElement.getElementsCount());
            String tagName;
            resXmlElementIt = resApplicationElement.getElements();
            while (resXmlElementIt.hasNext()) {
                ResXmlElement elem = resXmlElementIt.next();
                tagName = elem.getName();
                if (tagName != null) {
                    switch (tagName) {
                        case TAG_ACTIVITY:
                        case TAG_ACTIVITY_ALIAS:
                        case TAG_SERVICE:
                        case TAG_RECEIVER:
                        case TAG_PROVIDER:
                            componentIfList.add(parseComponentInfo(elem, tagName));
                            break;
                    }
                }
            }
            return componentIfList;
        }
    }

    @NonNull
    public List<ManifestMetadata> parseMetadata() throws IOException {
        try (BlockReader reader = new BlockReader(mManifestBytes.array())) {
            ResXmlDocument xmlBlock = new ResXmlDocument();
            xmlBlock.readBytes(reader);
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            if (!TAG_MANIFEST.equals(resManifestElement.getName())) {
                throw new IOException("\"manifest\" tag not found.");
            }
            String packageName = getAttributeValue(resManifestElement, ATTR_MANIFEST_PACKAGE);
            if (packageName == null) {
                throw new IOException("\"manifest\" does not have required attribute \"package\".");
            }
            mPackageName = packageName;
            ResXmlElement resApplicationElement = null;
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements(TAG_APPLICATION);
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next();
            }
            if (resXmlElementIt.hasNext()) {
                throw new IOException("\"manifest\" has duplicate \"application\" tags.");
            }
            if (resApplicationElement == null) {
                Log.i(TAG, "package %s does not have \"application\" tag.", mPackageName);
                return Collections.emptyList();
            }

            List<ManifestMetadata> metadata = new ArrayList<>(resApplicationElement.getElementsCount());
            collectMetadata(resApplicationElement, ManifestMetadata.OWNER_APPLICATION, mPackageName, metadata);
            resXmlElementIt = resApplicationElement.getElements();
            while (resXmlElementIt.hasNext()) {
                ResXmlElement elem = resXmlElementIt.next();
                String tagName = elem.getName();
                if (tagName == null) {
                    continue;
                }
                switch (tagName) {
                    case TAG_ACTIVITY:
                    case TAG_ACTIVITY_ALIAS:
                    case TAG_SERVICE:
                    case TAG_RECEIVER:
                    case TAG_PROVIDER:
                        String componentName = getAttributeValue(elem, ATTR_NAME);
                        if (componentName != null) {
                            collectMetadata(elem, tagName,
                                    new ComponentName(mPackageName, componentName).flattenToShortString(), metadata);
                        }
                        break;
                }
            }
            return metadata;
        }
    }

    @NonNull
    private ManifestComponent parseComponentInfo(@NonNull ResXmlElement componentElement,
                                                 @NonNull String componentType) throws IOException {
        String componentName = getAttributeValue(componentElement, ATTR_NAME);
        if (componentName == null) {
            throw new IOException("\"" + componentElement.getName() + "\" does not have  required attribute \"android:name\".");
        }
        ManifestComponent componentIf = new ManifestComponent(new ComponentName(mPackageName, componentName), componentType);
        // manifest -> application -> component -> intent-filter
        Iterator<ResXmlElement> resXmlElementIt = componentElement.getElements(TAG_INTENT_FILTER);
        while (resXmlElementIt.hasNext()) {
            ResXmlElement elem = resXmlElementIt.next();
            componentIf.intentFilters.add(parseIntentFilter(elem));
        }
        return componentIf;
    }

    @NonNull
    private ManifestIntentFilter parseIntentFilter(@NonNull ResXmlElement intentFilterElement) {
        ManifestIntentFilter intentFilter = new ManifestIntentFilter();
        String priorityString = getAttributeValue(intentFilterElement, ATTR_PRIORITY);
        if (priorityString != null) {
            intentFilter.priority = Integer.parseInt(priorityString);
        }
        // manifest -> application -> component -> intent-filter -> action|category|data
        Iterator<ResXmlElement> resXmlElementIt = intentFilterElement.getElements();
        String tagName;
        while (resXmlElementIt.hasNext()) {
            ResXmlElement elem = resXmlElementIt.next();
            tagName = elem.getName();
            if (tagName != null) {
                switch (tagName) {
                    case TAG_ACTION:
                        intentFilter.actions.add(Objects.requireNonNull(getAttributeValue(elem, ATTR_NAME)));
                        break;
                    case TAG_CATEGORY:
                        intentFilter.categories.add(Objects.requireNonNull(getAttributeValue(elem, ATTR_NAME)));
                        break;
                    case TAG_DATA:
                        intentFilter.data.add(parseData(elem));
                        break;
                }
            }
        }
        return intentFilter;
    }

    @NonNull
    private ManifestIntentFilter.ManifestData parseData(@NonNull ResXmlElement dataElement) {
        ManifestIntentFilter.ManifestData data = new ManifestIntentFilter.ManifestData();
        ResXmlAttribute attribute;
        for (int i = 0; i < dataElement.getAttributeCount(); ++i) {
            attribute = dataElement.getAttributeAt(i);
            if (attribute.equalsName("scheme")) {
                data.scheme = attribute.getValueAsString();
            } else if (attribute.equalsName("host")) {
                data.host = attribute.getValueAsString();
            } else if (attribute.equalsName("port")) {
                data.port = attribute.getValueAsString();
            } else if (attribute.equalsName("path")) {
                data.path = attribute.getValueAsString();
            } else if (attribute.equalsName("pathPrefix")) {
                data.pathPrefix = attribute.getValueAsString();
            } else if (attribute.equalsName("pathSuffix")) {
                data.pathSuffix = attribute.getValueAsString();
            } else if (attribute.equalsName("pathPattern")) {
                data.pathPattern = attribute.getValueAsString();
            } else if (attribute.equalsName("pathAdvancedPattern")) {
                data.pathAdvancedPattern = attribute.getValueAsString();
            } else if (attribute.equalsName("mimeType")) {
                data.mimeType = attribute.getValueAsString();
            } else {
                Log.i(TAG, "Unknown intent-filter > data attribute %s", attribute.getName());
            }
        }
        return data;
    }

    private void collectMetadata(@NonNull ResXmlElement ownerElement, @NonNull String ownerType,
                                 @Nullable String ownerName, @NonNull List<ManifestMetadata> out) {
        Iterator<ResXmlElement> resXmlElementIt = ownerElement.getElements(TAG_META_DATA);
        while (resXmlElementIt.hasNext()) {
            ManifestMetadata metadata = parseMetadata(ownerType, ownerName, resXmlElementIt.next());
            if (metadata != null) {
                out.add(metadata);
            }
        }
    }

    @Nullable
    private ManifestMetadata parseMetadata(@NonNull String ownerType, @Nullable String ownerName,
                                           @NonNull ResXmlElement metadataElement) {
        String name = getAttributeValue(metadataElement, ATTR_NAME);
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        ResXmlAttribute resourceAttribute = getAttribute(metadataElement, ATTR_RESOURCE);
        if (resourceAttribute != null) {
            return buildMetadata(ownerType, ownerName, name, resourceAttribute, true);
        }
        ResXmlAttribute valueAttribute = getAttribute(metadataElement, ATTR_VALUE);
        return buildMetadata(ownerType, ownerName, name, valueAttribute, false);
    }

    @NonNull
    private ManifestMetadata buildMetadata(@NonNull String ownerType, @Nullable String ownerName,
                                           @NonNull String name, @Nullable ResXmlAttribute valueAttribute,
                                           boolean resource) {
        String value = null;
        String valueType = null;
        if (valueAttribute != null) {
            value = valueAttribute.getValueAsString();
            if (value == null) {
                value = valueAttribute.decodeValue();
            }
            ValueType type = valueAttribute.getValueType();
            if (type != null) {
                valueType = type.name();
            }
        }
        return new ManifestMetadata(ownerType, ownerName, name, value, valueType, resource);
    }

    @Nullable
    private String getAttributeValue(@NonNull ResXmlElement element, @NonNull String attrName) {
        ResXmlAttribute attribute = getAttribute(element, attrName);
        return attribute != null ? attribute.getValueAsString() : null;
    }

    @Nullable
    private ResXmlAttribute getAttribute(@NonNull ResXmlElement element, @NonNull String attrName) {
        ResXmlAttribute attribute;
        for (int i = 0; i < element.getAttributeCount(); ++i) {
            attribute = element.getAttributeAt(i);
            if (attribute.equalsName(attrName)) {
                return attribute;
            }
        }
        return null;
    }

    private long getLongAttributeValue(@NonNull ResXmlElement element, @NonNull String attrName, long defaultValue) {
        String value = getAttributeValue(element, attrName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
