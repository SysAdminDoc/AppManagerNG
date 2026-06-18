// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.svg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SVGParserTest {
    @Test
    public void getSvgFromStringParsesInlineSvg() {
        SVG svg = SVGParser.getSVGFromString(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                        + "<rect width=\"10\" height=\"10\" fill=\"#000000\"/>"
                        + "</svg>");

        assertNotNull(svg);
    }

    @Test
    public void getSvgFromStringRejectsDoctype() {
        assertThrows(SVGParseException.class,
                () -> SVGParser.getSVGFromString(
                        "<!DOCTYPE svg [<!ENTITY local SYSTEM \"file:///etc/passwd\">]>"
                                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                                + "<text>&local;</text>"
                                + "</svg>"));
    }
}
