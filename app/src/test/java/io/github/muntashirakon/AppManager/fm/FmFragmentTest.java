// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class FmFragmentTest {
    @Test
    public void getSearchDisplayQueryFormatsSearchUiText() {
        assertEquals("' =payload query",
                FmFragment.getSearchDisplayQuery("\t=payload\nquery"));
    }

    @Test
    public void formatEmptyViewDetailsSanitizesThrowableReportLines() {
        IOException cause = new IOException("\n@cause\rpath");
        cause.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("pkg.Cause", "method", "Cause.java", 34)
        });
        IOException failure = new IOException("\n=payload\tpath");
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("pkg.Source", "method", "Source.java", 12)
        });
        failure.initCause(cause);

        assertEquals("java.io.IOException: \n"
                        + "'=payload path\n"
                        + "    at pkg.Source.method(Source.java:12)\n"
                        + " Caused by: java.io.IOException: \n"
                        + "'@cause path\n"
                        + "   at pkg.Cause.method(Cause.java:34)\n",
                FmFragment.formatEmptyViewDetails(failure));
    }

    @Test
    public void formatArchiveErrorMessageSanitizesLocalizedMessage() {
        assertEquals("' =payload path\n'@next",
                FmFragment.formatArchiveErrorMessage(new IOException("\t=payload\rpath\n@next")));
    }

    @Test
    public void formatArchiveErrorMessageAllowsEmptyMessages() {
        assertEquals("", FmFragment.formatArchiveErrorMessage(new IOException()));
    }
}
