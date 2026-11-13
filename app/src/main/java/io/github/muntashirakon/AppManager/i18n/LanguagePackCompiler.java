// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a language file into a resources table for this exact app build. Only entries that pass
 * {@link LanguagePackValidator} go in, each under the ID the running app gives that string.
 */
public final class LanguagePackCompiler {
    /** Names the resource types the table doesn't hold, which only name lookups ever see. */
    interface TypeNames {
        @NonNull
        String name(int packageId, int typeId);
    }

    private LanguagePackCompiler() {
    }

    @NonNull
    public static byte[] compile(@NonNull Resources res, @NonNull AndroidSourceStrings source,
                                 @NonNull LanguagePack pack) throws LanguagePackException {
        return compile(source.packageName(), source, pack, (packageId, type) -> typeName(res, packageId, type));
    }

    @NonNull
    static byte[] compile(@NonNull String packageName, @NonNull SourceStrings source, @NonNull LanguagePack pack,
                          @NonNull TypeNames otherTypes) throws LanguagePackException {
        List<ArscWriter.Entry> entries = new ArrayList<>(pack.entries.size());
        int packageId = -1;
        int stringType = 0;
        int pluralsType = 0;
        int maxType = 0;
        for (Map.Entry<String, LanguagePack.Entry> e : pack.entries.entrySet()) {
            String key = e.getKey();
            SourceString src = source.get(key);
            if (src == null) {
                continue;
            }
            LanguagePackValidator.Checked checked = LanguagePackValidator.checkEntry(key, e.getValue(), src);
            if (!checked.isUsable()) {
                continue;
            }
            int id = src.resId;
            int pkg = id >>> 24;
            int type = (id >>> 16) & 0xFF;
            int index = id & 0xFFFF;
            if (pkg == 0 || type == 0) {
                continue;
            }
            if (packageId == -1) {
                packageId = pkg;
            } else if (pkg != packageId) {
                continue;
            }
            maxType = Math.max(maxType, type);
            if (src.isPlural()) {
                pluralsType = type;
                entries.add(ArscWriter.Entry.plural(type, index, key, checked.forms));
            } else {
                stringType = type;
                entries.add(ArscWriter.Entry.string(type, index, key, checked.text));
            }
        }
        if (entries.isEmpty()) {
            throw new LanguagePackException(LanguagePackException.Reason.NOTHING_USABLE, "No usable entries");
        }
        List<String> typeNames = new ArrayList<>(maxType);
        for (int t = 1; t <= maxType; ++t) {
            if (t == stringType) {
                typeNames.add("string");
            } else if (t == pluralsType) {
                typeNames.add("plurals");
            } else {
                typeNames.add(otherTypes.name(packageId, t));
            }
        }
        return ArscWriter.write(packageName, packageId, typeNames, pack.code, entries);
    }

    @NonNull
    private static String typeName(@NonNull Resources res, int packageId, int type) {
        try {
            return res.getResourceTypeName((packageId << 24) | (type << 16));
        } catch (Resources.NotFoundException e) {
            // Only used for name lookups, which never reach this table for other types.
            return "t" + type;
        }
    }
}
