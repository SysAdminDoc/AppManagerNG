// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class LeftoverExportFormatterTest {
    @Test
    public void toTsvIncludesStableColumnsAndRows() {
        File dataPath = new File("sdcard/Android/data/com.example.gone");
        File obbPath = new File("sdcard/Android/obb/com.example.other");
        OneClickOpsViewModel.LeftoverEntry data = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(dataPath, "com.example.gone", LeftoverScanner.KIND_DATA),
                42L);
        OneClickOpsViewModel.LeftoverEntry obb = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(obbPath, "com.example.other", LeftoverScanner.KIND_OBB),
                4096L);

        String export = LeftoverExportFormatter.toTsv(Arrays.asList(data, obb));

        assertEquals("package_name\tkind\tsize_bytes\tpath\n"
                        + "com.example.gone\tdata\t42\t" + dataPath.getPath() + "\n"
                        + "com.example.other\tobb\t4096\t" + obbPath.getPath(),
                export);
    }

    @Test
    public void toTsvDefusesFormulaLikeFieldsAndNormalizesLineBreaks() {
        OneClickOpsViewModel.LeftoverEntry entry = new OneClickOpsViewModel.LeftoverEntry(
                new LeftoverScanner.Leftover(new File("=cmd\npayload"),
                        "+pkg", LeftoverScanner.KIND_MEDIA),
                7L);

        String export = LeftoverExportFormatter.toTsv(Collections.singletonList(entry));

        assertTrue(export.contains("'+pkg\tmedia\t7\t'=cmd payload"));
    }
}
