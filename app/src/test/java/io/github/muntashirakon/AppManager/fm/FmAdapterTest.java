// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmAdapterTest {
    private Path root;

    @Before
    public void setUp() throws IOException {
        root = Paths.get(Files.createTempDirectory("appmanagerng-fm-adapter").toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void setFmListIncrementsAttributeLoadGeneration() {
        FmAdapter adapter = new FmAdapter(null, null);

        assertEquals(0, adapter.getAttributeLoadGeneration());

        adapter.setFmList(Collections.emptyList());
        assertEquals(1, adapter.getAttributeLoadGeneration());

        adapter.setFmList(Collections.emptyList());
        assertEquals(2, adapter.getAttributeLoadGeneration());
    }

    @Test
    public void shouldApplyCachedAttributesRequiresCurrentGenerationAndSamePath() throws IOException {
        FmItem current = new FmItem(root.createNewFile("current.txt", null));
        Path reboundPath = root.createNewFile("rebound.txt", null);

        assertTrue(FmAdapter.shouldApplyCachedAttributes(2, 2, current.path, current));
        assertFalse(FmAdapter.shouldApplyCachedAttributes(3, 2, current.path, current));
        assertFalse(FmAdapter.shouldApplyCachedAttributes(2, 2, reboundPath, current));
        assertFalse(FmAdapter.shouldApplyCachedAttributes(2, 2, null, current));
    }
}
