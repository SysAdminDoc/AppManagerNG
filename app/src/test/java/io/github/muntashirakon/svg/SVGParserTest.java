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
import org.robolectric.annotation.GraphicsMode;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void parsedSvgKeepsItsDeclaredSize() {
        // Element names only match when the parser reports local names
        SVG svg = SVGParser.getSVGFromString(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"12\" height=\"34\">"
                        + "<rect width=\"12\" height=\"34\" fill=\"#000000\"/>"
                        + "</svg>");

        assertEquals(12, svg.getPicture().getWidth());
        assertEquals(34, svg.getPicture().getHeight());
    }

    @Test
    public void readerWithOnlyStandardFeaturesIsConfigured() throws Exception {
        // Android's Expat reader refuses every feature outside http://xml.org/sax/features/,
        // which made each SVG fail to parse when those features were required
        XMLReader reader = new StandardFeaturesOnlyReader(newJdkReader());

        SVGParser.configureSecureReader(reader);

        assertTrue(reader.getProperty(SVGParser.LEXICAL_HANDLER) instanceof SVGParser.DoctypeRejecter);
        assertNotNull(reader.getEntityResolver());
        InputSource resolved = reader.getEntityResolver().resolveEntity(null, "file:///etc/passwd");
        assertEquals(-1, resolved.getCharacterStream().read());
    }

    @Test
    public void readerWithOnlyStandardFeaturesStillRefusesDoctype() throws Exception {
        XMLReader reader = new StandardFeaturesOnlyReader(newJdkReader());
        SVGParser.configureSecureReader(reader);
        reader.setContentHandler(new DefaultHandler());

        assertThrows(SAXException.class, () -> reader.parse(new InputSource(new StringReader(
                "<!DOCTYPE svg [<!ENTITY lol \"lol\">]>"
                        + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                        + "<text>&lol;</text></svg>"))));
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

    private static XMLReader newJdkReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newSAXParser().getXMLReader();
    }

    /**
     * Acts like Android's ExpatReader: standard SAX features only.
     */
    private static final class StandardFeaturesOnlyReader implements XMLReader {
        private final XMLReader mReader;

        StandardFeaturesOnlyReader(XMLReader reader) {
            mReader = reader;
        }

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            check(name);
            return mReader.getFeature(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            check(name);
            mReader.setFeature(name, value);
        }

        private static void check(String name) throws SAXNotRecognizedException {
            if (!name.startsWith("http://xml.org/sax/features/")) {
                throw new SAXNotRecognizedException(name);
            }
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return mReader.getProperty(name);
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            mReader.setProperty(name, value);
        }

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            mReader.setEntityResolver(resolver);
        }

        @Override
        public EntityResolver getEntityResolver() {
            return mReader.getEntityResolver();
        }

        @Override
        public void setDTDHandler(DTDHandler handler) {
            mReader.setDTDHandler(handler);
        }

        @Override
        public DTDHandler getDTDHandler() {
            return mReader.getDTDHandler();
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            mReader.setContentHandler(handler);
        }

        @Override
        public ContentHandler getContentHandler() {
            return mReader.getContentHandler();
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            mReader.setErrorHandler(handler);
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return mReader.getErrorHandler();
        }

        @Override
        public void parse(InputSource input) throws IOException, SAXException {
            mReader.parse(input);
        }

        @Override
        public void parse(String systemId) throws IOException, SAXException {
            mReader.parse(systemId);
        }
    }

}
