// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verifies that {@link PackageManagerCompat#extractList(Object)} normalizes every shape the hidden
 * {@code IPackageManager} list accessors can return. This is the forward-compatibility shim that
 * keeps the installed-app list populated (and the About-device settings screen from crashing) when a
 * future platform such as Android 17 changes the return type away from {@code ParceledListSlice}.
 */
public class PackageManagerCompatListExtractionTest {
    @Test
    public void nullResultYieldsEmptyList() {
        List<String> result = PackageManagerCompat.extractList(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void plainListIsReturnedAsIs() {
        List<String> source = Arrays.asList("a", "b", "c");
        List<String> result = PackageManagerCompat.extractList(source);
        assertSame(source, result);
    }

    @Test
    public void wrapperExposingGetListIsUnwrapped() {
        List<String> backing = new ArrayList<>(Arrays.asList("x", "y"));
        List<String> result = PackageManagerCompat.extractList(new FakeSlice(backing));
        assertEquals(backing, result);
    }

    @Test
    public void wrapperWithNonListGetListYieldsEmptyList() {
        List<String> result = PackageManagerCompat.extractList(new BadSlice());
        assertTrue(result.isEmpty());
    }

    @Test
    public void unknownObjectYieldsEmptyList() {
        List<String> result = PackageManagerCompat.extractList(new Object());
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unused")
    private static final class FakeSlice {
        private final List<String> list;

        FakeSlice(List<String> list) {
            this.list = list;
        }

        public List<String> getList() {
            return list;
        }
    }

    @SuppressWarnings("unused")
    private static final class BadSlice {
        public String getList() {
            return "not a list";
        }
    }
}
