// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class DebloaterViewModelTest {
    @Test
    @SuppressWarnings("unchecked")
    public void selectByPackageNamesIgnoresMalformedEntries() throws Exception {
        DebloaterViewModel viewModel = new DebloaterViewModel(ApplicationProvider.getApplicationContext());
        Field objectsField = DebloaterViewModel.class.getDeclaredField("mDebloatObjects");
        objectsField.setAccessible(true);
        List<DebloatObject> objects = (List<DebloatObject>) objectsField.get(viewModel);
        objects.add(debloatObject("com.example.keep"));
        objects.add(debloatObject("com.example.match"));

        DebloatPresetIO.DebloatPresetEntry malformed = new DebloatPresetIO.DebloatPresetEntry();
        DebloatPresetIO.DebloatPresetEntry match = new DebloatPresetIO.DebloatPresetEntry();
        match.packageName = "com.example.match";
        DebloatPresetIO.DebloatPresetEntry missing = new DebloatPresetIO.DebloatPresetEntry();
        missing.packageName = "com.example.missing";

        int matched = viewModel.selectByPackageNames(Arrays.asList(malformed, match, missing));

        assertEquals(1, matched);
        assertEquals(1, viewModel.getSelectedItemCount());
        assertTrue(viewModel.getSelectedPackages().containsKey("com.example.match"));
    }

    private static DebloatObject debloatObject(String packageName) {
        DebloatObject debloatObject = new DebloatObject();
        debloatObject.packageName = packageName;
        return debloatObject;
    }
}
