// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.TreeSet;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmItemTest {
    private static FmItem fileItem(File dir, String name) throws IOException {
        File f = new File(dir, name);
        assertTrue(f.getParentFile().exists() || f.getParentFile().mkdirs());
        assertTrue(f.createNewFile() || f.exists());
        return new FmItem(Paths.get(f));
    }

    @Test
    public void compareToIsConsistentWithEqualsForSameNameDifferentPaths() throws IOException {
        File base = Files.createTempDirectory("fmitem").toFile();
        File dirA = new File(base, "a");
        File dirB = new File(base, "b");
        FmItem a = fileItem(dirA, "same.txt");
        FmItem b = fileItem(dirB, "same.txt");

        // Distinct paths must never compare equal (that would break TreeSet/TreeMap), and ordering
        // must be antisymmetric.
        assertNotEquals(a, b);
        assertNotEquals(0, a.compareTo(b));
        assertEquals(-Integer.signum(a.compareTo(b)), Integer.signum(b.compareTo(a)));

        TreeSet<FmItem> set = new TreeSet<>();
        set.add(a);
        set.add(b);
        assertEquals("TreeSet must keep both distinct items", 2, set.size());
    }

    @Test
    public void compareToWithSelfIsZero() throws IOException {
        File base = Files.createTempDirectory("fmitem").toFile();
        FmItem a = fileItem(base, "file.txt");
        assertEquals(0, a.compareTo(a));
    }

    @Test
    public void directoriesSortBeforeFiles() throws IOException {
        File base = Files.createTempDirectory("fmitem").toFile();
        File dir = new File(base, "zeta");
        assertTrue(dir.mkdirs());
        FmItem dirItem = new FmItem(Paths.get(dir));
        FmItem fileItem = fileItem(base, "alpha.txt");

        // A directory ("zeta") must sort before a file ("alpha.txt") despite the later name.
        assertTrue(dirItem.compareTo(fileItem) < 0);
        assertTrue(fileItem.compareTo(dirItem) > 0);
    }
}
