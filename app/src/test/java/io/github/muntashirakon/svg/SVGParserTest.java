// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.svg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

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

    @Test
    public void parsesAbsoluteAndRelativeArcs() {
        Path absolute = SVGParser.parsePath("M0 0 A10 10 0 0 1 20 0");
        Path relative = SVGParser.parsePath("M10 10 a5 5 0 0 1 10 0");
        Path repeated = SVGParser.parsePath("M0 0 A10 10 0 0 1 20 0 10 10 0 0 1 40 0");

        assertTrue(pathLength(absolute) > 25);
        assertTrue(pathLength(relative) > 10);
        assertEquals(40, bounds(repeated).right, 0.05);
        RectF relativeBounds = bounds(relative);
        assertEquals(20, relativeBounds.right, 0.05);
        assertEquals(10, relativeBounds.bottom, 0.05);
    }

    @Test
    public void normalizesRadiiAndHonorsLargeArcFlag() {
        Path normalized = SVGParser.parsePath("M0 0 A1 1 0 0 1 20 0");
        Path smallArc = SVGParser.parsePath("M0 0 A10 10 0 0 1 10 0");
        Path largeArc = SVGParser.parsePath("M0 0 A10 10 0 1 1 10 0");

        assertTrue(pathLength(normalized) > 25);
        assertTrue(pathLength(largeArc) > pathLength(smallArc) * 3);
    }

    @Test
    public void honorsRotationAndSweepFlag() {
        Path rotated = SVGParser.parsePath("M0 0 A20 10 45 0 1 30 20");
        Path unrotated = SVGParser.parsePath("M0 0 A20 10 0 0 1 30 20");
        Path sweepOne = SVGParser.parsePath("M0 0 A10 10 0 0 1 10 0");
        Path sweepZero = SVGParser.parsePath("M0 0 A10 10 0 0 0 10 0");
        RectF rotatedBounds = bounds(rotated);
        RectF unrotatedBounds = bounds(unrotated);
        RectF sweepOneBounds = bounds(sweepOne);
        RectF sweepZeroBounds = bounds(sweepZero);

        assertTrue(Math.abs(rotatedBounds.width() - unrotatedBounds.width()) > 1
                || Math.abs(rotatedBounds.height() - unrotatedBounds.height()) > 1);
        assertTrue(Math.abs(sweepOneBounds.top - sweepZeroBounds.top) > 1
                || Math.abs(sweepOneBounds.bottom - sweepZeroBounds.bottom) > 1);
    }

    @Test
    public void identicalEndpointsProduceNoArc() {
        assertEquals(0, pathLength(SVGParser.parsePath("M2 2 A5 5 0 1 1 2 2")), 0.001);
    }

    @Test
    public void zeroRadiusProducesStraightLine() {
        assertEquals(10, pathLength(SVGParser.parsePath("M0 0 A0 5 0 0 1 10 0")), 0.01);
    }

    @Test
    public void arcFixtureRendersToBitmap() {
        Path path = SVGParser.parsePath("M5 20 A15 15 0 1 1 35 20");
        Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        new Canvas(bitmap).drawPath(path, paint);
        assertEquals(40, bitmap.getWidth());
        assertEquals(40, bitmap.getHeight());
        assertTrue(pathLength(path) > 40);
        assertTrue(bounds(path).width() > 25);
    }

    private static float pathLength(Path path) {
        return new PathMeasure(path, false).getLength();
    }

    private static RectF bounds(Path path) {
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        return bounds;
    }

}
