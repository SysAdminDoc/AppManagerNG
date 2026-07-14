// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JSONUtilsArrayTest {
    @Test
    public void typedArrayRoundTrips() throws JSONException {
        JSONArray array = new JSONArray().put("a").put("b");
        String[] result = JSONUtils.getArray(String.class, array);
        assertArrayEquals(new String[]{"a", "b"}, result);
    }

    @Test
    public void nullArrayReturnsNull() throws JSONException {
        assertNull(JSONUtils.getArray(String.class, null));
    }

    @Test
    public void typeMismatchThrowsJSONExceptionNotClassCast() {
        // A numeric element in a String array (corrupt/hand-edited profile) must surface as a
        // JSONException — which callers already catch to skip one bad profile — not an unchecked
        // ClassCastException that would crash the whole profiles list.
        JSONArray array = new JSONArray().put("ok").put(42);
        try {
            JSONUtils.getArray(String.class, array);
            fail("Expected JSONException for a type-mismatched element");
        } catch (JSONException expected) {
            // correct
        } catch (ClassCastException e) {
            fail("Type mismatch leaked as an unchecked ClassCastException: " + e);
        }
    }
}
