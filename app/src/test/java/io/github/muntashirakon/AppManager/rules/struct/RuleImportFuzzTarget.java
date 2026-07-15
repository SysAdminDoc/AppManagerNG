// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import java.nio.charset.StandardCharsets;

public final class RuleImportFuzzTarget {
    private RuleImportFuzzTarget() {
    }

    public static void fuzzerTestOneInput(byte[] data) {
        String input = new String(data, StandardCharsets.UTF_8);
        String[] fields = input.split("\t", -1);
        if (fields.length < 3 || !isComponentType(fields[2])) {
            // Framework-backed rule families remain in Robolectric. Preserve a pure-JVM
            // coverage seam by mutating component status/field boundaries here.
            input = "com.example\t.Component\tACTIVITY\t" + input;
        }
        try {
            RuleEntry entry = RuleEntry.unflattenFromString(null, input, true);
            RuleEntry roundTrip = RuleEntry.unflattenFromString(
                    null, entry.flattenToString(true), true);
            if (!entry.equals(roundTrip)) {
                throw new AssertionError("Rule import/export round-trip changed the parsed rule");
            }
        } catch (IllegalArgumentException expectedParseError) {
            // Malformed TSV fields are an expected, classified import error.
        }
    }

    private static boolean isComponentType(String type) {
        return "ACTIVITY".equals(type)
                || "PROVIDER".equals(type)
                || "RECEIVER".equals(type)
                || "SERVICE".equals(type);
    }
}
