// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Issue 42: App Info names the license an APK ships, matched offline against the common SPDX licenses. The APK is
 * someone else's file, so every read is bounded.
 */
public class LicenseDetectorTest {
    private static final String GPL3 = "                    GNU GENERAL PUBLIC LICENSE\n"
            + "                       Version 3, 29 June 2007\n\n Copyright (C) 2007 Free Software Foundation\n"
            + " ... use the GNU Lesser General Public License instead of this License.";
    private static final String GPL2 = "GNU GENERAL PUBLIC LICENSE\nVersion 2, June 1991\n"
            + "... the GNU Library General Public License instead.";
    private static final String LGPL3 = "GNU LESSER GENERAL PUBLIC LICENSE\n Version 3, 29 June 2007\n"
            + "This version of the GNU Lesser General Public License incorporates the terms of version 3 of the "
            + "GNU General Public License";
    private static final String LGPL21 = "GNU LESSER GENERAL PUBLIC LICENSE\n Version 2.1, February 1999";
    private static final String LGPL2 = "GNU LIBRARY GENERAL PUBLIC LICENSE\n Version 2, June 1991";
    private static final String AGPL3 = "GNU AFFERO GENERAL PUBLIC LICENSE\n Version 3, 19 November 2007";
    private static final String APACHE2 = "\n                                 Apache License\n"
            + "                           Version 2.0, January 2004\n                        "
            + "http://www.apache.org/licenses/";
    private static final String MPL2 = "Mozilla Public License Version 2.0\n==================================";
    private static final String MIT = "MIT License\n\nCopyright (c) 2024 Example\n\nPermission is hereby granted, "
            + "free of charge, to any person obtaining a copy\nof this software and associated documentation files";
    private static final String ISC = "Copyright (c) 2024 Example\n\nPermission to use, copy, modify, and/or "
            + "distribute this software for any purpose\nwith or without fee is hereby granted, provided that the "
            + "above copyright notice and this permission notice appear in all copies.";
    private static final String BSD_HEAD = "Copyright (c) 2024 Example\n\nRedistribution and use in source and "
            + "binary forms, with or without\nmodification, are permitted provided that the following conditions "
            + "are met:\n1. Redistributions of source code must retain the above copyright notice.\n"
            + "2. Redistributions in binary form must reproduce the above copyright notice.\n";
    private static final String BSD2 = BSD_HEAD + "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS";
    private static final String BSD3 = BSD_HEAD + "3. Neither the name of the copyright holder nor the names of its "
            + "contributors may be used to endorse or promote products derived from this software.";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void eachCommonLicenseIsNamed() {
        assertEquals("GPL-3.0", LicenseDetector.identify(GPL3));
        assertEquals("GPL-2.0", LicenseDetector.identify(GPL2));
        assertEquals("LGPL-3.0", LicenseDetector.identify(LGPL3));
        assertEquals("LGPL-2.1", LicenseDetector.identify(LGPL21));
        assertEquals("LGPL-2.0", LicenseDetector.identify(LGPL2));
        assertEquals("AGPL-3.0", LicenseDetector.identify(AGPL3));
        assertEquals("Apache-2.0", LicenseDetector.identify(APACHE2));
        assertEquals("MPL-2.0", LicenseDetector.identify(MPL2));
        assertEquals("MIT", LicenseDetector.identify(MIT));
        assertEquals("ISC", LicenseDetector.identify(ISC));
        assertEquals("BSD-2-Clause", LicenseDetector.identify(BSD2));
        assertEquals("BSD-3-Clause", LicenseDetector.identify(BSD3));
    }

    @Test
    public void anSpdxTagIsTakenAsWritten() {
        assertEquals("GPL-3.0-or-later", LicenseDetector.identify("// SPDX-License-Identifier: GPL-3.0-or-later\n"));
        assertEquals("Apache-2.0", LicenseDetector.identify("SPDX-License-Identifier:Apache-2.0"));
    }

    @Test
    public void textThatIsNoLicenseIsNotNamed() {
        assertNull(LicenseDetector.identify("This product includes software developed by Example."));
        assertNull(LicenseDetector.identify(""));
    }

    @Test
    public void theLicenseFilesInAnApkAreFoundAndNamed() throws IOException {
        try (ZipFile apk = apk("classes.dex", "dex", "META-INF/LICENSE", APACHE2, "META-INF/NOTICE.txt",
                "This product includes software developed by Example.", "assets/licenses/zxing.txt", MIT,
                "LICENSE.md", GPL3, "res/raw/licence.txt", BSD3, "com/example/license/Check.class", "class")) {
            LicenseDetector.Result result = LicenseDetector.detect(apk);

            assertEquals(Arrays.asList("META-INF/LICENSE", "META-INF/NOTICE.txt", "assets/licenses/zxing.txt",
                    "LICENSE.md", "res/raw/licence.txt"), new java.util.ArrayList<>(result.files.keySet()));
            assertEquals("Apache-2.0", result.files.get("META-INF/LICENSE"));
            assertNull(result.files.get("META-INF/NOTICE.txt"));
            assertEquals(Arrays.asList("Apache-2.0", "MIT", "GPL-3.0", "BSD-3-Clause"), result.licenses);
        }
    }

    @Test
    public void anApkWithoutLicenseFilesDeclaresNone() throws IOException {
        try (ZipFile apk = apk("classes.dex", "dex", "res/layout/main.xml", "xml")) {
            LicenseDetector.Result result = LicenseDetector.detect(apk);

            assertTrue(result.files.isEmpty());
            assertEquals(Collections.emptyList(), result.licenses);
        }
    }

    @Test
    public void aHugeLicenseFileIsReadOnlyUpToTheCap() throws IOException {
        char[] filler = new char[1024 * 1024];
        Arrays.fill(filler, 'A');
        try (ZipFile apk = apk("LICENSE", new String(filler) + APACHE2)) {
            LicenseDetector.Result result = LicenseDetector.detect(apk);

            assertEquals(Collections.singletonList("LICENSE"), new java.util.ArrayList<>(result.files.keySet()));
            assertEquals("the text past the cap is never read", Collections.emptyList(), result.licenses);
        }
    }

    @Test
    public void anApkFullOfLicenseFilesIsCappedToAFewDozen() throws IOException {
        String[] entries = new String[400];
        for (int i = 0; i < 200; ++i) {
            entries[2 * i] = "META-INF/lib" + i + ".LICENSE";
            entries[2 * i + 1] = MIT;
        }
        try (ZipFile apk = apk(entries)) {
            LicenseDetector.Result result = LicenseDetector.detect(apk);

            assertEquals(LicenseDetector.MAX_CANDIDATES, result.files.size());
            assertEquals(Collections.singletonList("MIT"), result.licenses);
        }
    }

    @NonNull
    private ZipFile apk(@NonNull String... nameAndContent) throws IOException {
        File file = mTemp.newFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i + 1 < nameAndContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameAndContent[i]));
                zos.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new ZipFile(file);
    }
}
