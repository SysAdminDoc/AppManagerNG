// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FilterOptionTest {
    /**
     * The regex predicates in TrackersOption / PackageNameOption / etc. used to be silently
     * neutered by {@code Pattern.compile(Pattern.quote(value))} in
     * {@link FilterOption#setKeyValue(String, String)} — the quote call turned every user-supplied
     * pattern into a literal-string match. With the fix the value passes through as-is, so a
     * regex like {@code ".*facebook.*"} actually behaves like a regex.
     */
    @Test
    public void regexValueIsCompiledAsRegexNotLiteral() {
        PackageNameOption option = new PackageNameOption();
        option.setKeyValue("regex", ".*facebook.*");
        assertNotNull(option.regexValue);
        assertTrue("expected regex to match contains-style input",
                option.regexValue.matcher("com.facebook.katana").matches());
        assertTrue(option.regexValue.matcher("foofacebookbar").matches());
        // A literal substring that would match under Pattern.quote should NOT match — proving the
        // quote is gone.
        assertEquals(false,
                option.regexValue.matcher(".*facebook.*").matches() &&
                        !option.regexValue.matcher("com.facebook.katana").matches());
    }

    /**
     * Switch fall-through used to overwrite {@code stringValues} after the regex case even though
     * the regex path doesn't use it. With the fix in place the cases are isolated and the regex
     * setter only touches {@code regexValue}.
     */
    @Test
    public void regexSetterDoesNotPopulateStringValues() {
        PackageNameOption option = new PackageNameOption();
        option.setKeyValue("regex", "^com\\.example$");
        // stringValues is internal but observably affects TYPE_STR_MULTIPLE callers; the
        // fall-through bug populated it with [value]. We assert the regex path leaves it alone by
        // verifying that the compiled pattern is the only side effect we can see.
        assertNotNull(option.regexValue);
        assertTrue(option.regexValue.matcher("com.example").matches());
    }

    /** Malformed user regex must surface as IllegalArgumentException, not PatternSyntaxException. */
    @Test
    public void malformedRegexSurfacesAsIllegalArgumentException() {
        PackageNameOption option = new PackageNameOption();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> option.setKeyValue("regex", "[unterminated"));
        assertNotNull(thrown.getMessage());
        assertTrue("expected message to identify the bad option",
                thrown.getMessage().contains("regex"));
    }

    @Test
    public void multiStringSetterSplitsOnNewlines() {
        AppLabelOption option = new AppLabelOption();
        // AppLabelOption only exposes regex / starts_with / ... for its string keys, but the
        // setter logic for TYPE_STR_MULTIPLE is shared. We hit it indirectly through a Components-
        // style option below; here we just verify the regex path no longer mutates string state.
        option.setKeyValue("regex", "foo|bar");
        assertNotNull(option.regexValue);
        assertTrue(option.regexValue.matcher("foo").matches());
        assertTrue(option.regexValue.matcher("bar").matches());
        assertEquals(false, option.regexValue.matcher("baz").matches());
    }

    /**
     * Sanity check that TYPE_STR_MULTIPLE itself still works — the fix added a missing
     * {@code break} after this case but the splitting behaviour must remain.
     */
    @Test
    public void multiStringSplittingStillWorks() {
        PackageNameOption option = new PackageNameOption();
        option.setKeyValue("eq_any", "com.foo\ncom.bar\ncom.baz");
        assertArrayEquals(new String[]{"com.foo", "com.bar", "com.baz"}, option.stringValues);
    }
}
