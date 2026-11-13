// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Text plus the style spans Android's string pool can carry. Language files write those spans as
 * a few HTML-like tags ({@code <b>}, {@code <i>}, {@code <u>}, {@code <tt>} and
 * {@code <a href="...">}), the same tags the app's own {@code strings.xml} uses.
 */
public final class StyledText {
    /** Span names exactly as the platform's string block expects them. */
    public static final String BOLD = "b";
    public static final String ITALIC = "i";
    public static final String UNDERLINE = "u";
    public static final String MONOSPACE = "tt";
    public static final String LINK_PREFIX = "a;href=";

    public static final class Span {
        @NonNull
        public final String name;
        /** First UTF-16 index covered. */
        public final int start;
        /** One past the last UTF-16 index covered. */
        public final int end;

        public Span(@NonNull String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Span)) return false;
            Span span = (Span) o;
            return start == span.start && end == span.end && name.equals(span.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, start, end);
        }

        @NonNull
        @Override
        public String toString() {
            return name + "[" + start + "," + end + ")";
        }
    }

    public static final class MarkupException extends Exception {
        public MarkupException(@NonNull String message) {
            super(message);
        }
    }

    @NonNull
    public final String text;
    @NonNull
    public final List<Span> spans;

    public StyledText(@NonNull String text, @NonNull List<Span> spans) {
        this.text = text;
        List<Span> copy = new ArrayList<>(spans);
        copy.sort(SPAN_ORDER);
        this.spans = Collections.unmodifiableList(copy);
    }

    @NonNull
    public static StyledText plain(@NonNull String text) {
        return new StyledText(text, Collections.emptyList());
    }

    public boolean isStyled() {
        return !spans.isEmpty();
    }

    /**
     * Parses a translation written with the tags in this class. Only links whose address appears
     * in {@code allowedLinks} are accepted, so a language file can't point a link somewhere the
     * English text doesn't.
     */
    @NonNull
    public static StyledText parseMarkup(@NonNull String markup, @NonNull Set<String> allowedLinks)
            throws MarkupException {
        StringBuilder out = new StringBuilder(markup.length());
        List<Span> spans = new ArrayList<>();
        Deque<String> openNames = new ArrayDeque<>();
        Deque<String> openTags = new ArrayDeque<>();
        Deque<Integer> openStarts = new ArrayDeque<>();
        int i = 0;
        int n = markup.length();
        while (i < n) {
            char c = markup.charAt(i);
            if (c == '&') {
                int semi = markup.indexOf(';', i);
                String decoded = semi > i && semi - i <= 6 ? decodeEntity(markup.substring(i + 1, semi)) : null;
                if (decoded != null) {
                    out.append(decoded);
                    i = semi + 1;
                } else {
                    out.append(c);
                    ++i;
                }
                continue;
            }
            if (c != '<' || i + 1 >= n || !(isAsciiLetter(markup.charAt(i + 1)) || markup.charAt(i + 1) == '/')) {
                out.append(c);
                ++i;
                continue;
            }
            int close = markup.indexOf('>', i);
            if (close < 0) {
                throw new MarkupException("A tag isn't closed with >");
            }
            String tag = markup.substring(i + 1, close);
            if (tag.startsWith("/")) {
                String tagName = tag.substring(1);
                if (openTags.isEmpty() || !openTags.peek().equals(tagName)) {
                    throw new MarkupException("</" + tagName + "> doesn't match the open tag");
                }
                openTags.pop();
                String name = openNames.pop();
                int start = openStarts.pop();
                if (out.length() > start) {
                    spans.add(new Span(name, start, out.length()));
                }
            } else {
                String name = spanNameForTag(tag, allowedLinks);
                openTags.push(tag.startsWith("a ") ? "a" : tag);
                openNames.push(name);
                openStarts.push(out.length());
            }
            i = close + 1;
        }
        if (!openTags.isEmpty()) {
            throw new MarkupException("<" + openTags.peek() + "> is never closed");
        }
        return new StyledText(out.toString(), spans);
    }

    @NonNull
    private static String spanNameForTag(@NonNull String tag, @NonNull Set<String> allowedLinks)
            throws MarkupException {
        switch (tag) {
            case "b":
                return BOLD;
            case "i":
                return ITALIC;
            case "u":
                return UNDERLINE;
            case "tt":
                return MONOSPACE;
        }
        if (tag.startsWith("a href=\"") && tag.endsWith("\"") && tag.length() > 9) {
            String href = decodeAttribute(tag.substring(8, tag.length() - 1));
            if (href == null || !allowedLinks.contains(href)) {
                throw new MarkupException("Links can only use the addresses in the original");
            }
            return LINK_PREFIX + href;
        }
        throw new MarkupException("<" + tag + "> isn't a supported tag");
    }

    @Nullable
    private static String decodeAttribute(@NonNull String value) {
        if (value.indexOf('"') >= 0 || value.indexOf('<') >= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '&') {
                int semi = value.indexOf(';', i);
                String decoded = semi > i ? decodeEntity(value.substring(i + 1, semi)) : null;
                if (decoded == null) {
                    return null;
                }
                sb.append(decoded);
                i = semi + 1;
            } else {
                sb.append(c);
                ++i;
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String decodeEntity(@NonNull String name) {
        switch (name) {
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "amp":
                return "&";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                return null;
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Writes this text back as markup that {@link #parseMarkup} reads into the same text and
     * spans. Unstyled text comes back unchanged. Spans the language file format doesn't know are
     * reported through {@code null}.
     */
    @Nullable
    public String toMarkup() {
        if (spans.isEmpty()) {
            return text;
        }
        for (Span span : spans) {
            if (tagFor(span.name) == null) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder(text.length() + spans.size() * 8);
        Deque<Span> open = new ArrayDeque<>();
        int next = 0;
        for (int pos = 0; pos <= text.length(); ++pos) {
            // Close spans ending here. A span that ends while a later-opened one is still open
            // closes and reopens that inner span so tags stay nested.
            List<Span> reopen = new ArrayList<>();
            while (true) {
                Span ending = null;
                for (Span s : open) {
                    if (s.end == pos) {
                        ending = s;
                        break;
                    }
                }
                if (ending == null) {
                    break;
                }
                while (!open.isEmpty()) {
                    Span top = open.pop();
                    sb.append("</").append(closeTagFor(top.name)).append('>');
                    if (top == ending) {
                        break;
                    }
                    reopen.add(0, top);
                }
            }
            for (Span s : reopen) {
                if (s.end > pos) {
                    sb.append('<').append(tagFor(s.name)).append('>');
                    open.push(s);
                }
            }
            if (pos == text.length()) {
                break;
            }
            while (next < spans.size() && spans.get(next).start == pos) {
                Span s = spans.get(next++);
                sb.append('<').append(tagFor(s.name)).append('>');
                open.push(s);
            }
            char c = text.charAt(pos);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String tagFor(@NonNull String spanName) {
        switch (spanName) {
            case BOLD:
            case ITALIC:
            case UNDERLINE:
            case MONOSPACE:
                return spanName;
        }
        if (spanName.startsWith(LINK_PREFIX)) {
            String href = spanName.substring(LINK_PREFIX.length());
            if (href.indexOf('"') >= 0) {
                return null;
            }
            return "a href=\"" + href.replace("&", "&amp;").replace("<", "&lt;") + "\"";
        }
        return null;
    }

    @NonNull
    private static String closeTagFor(@NonNull String spanName) {
        return spanName.startsWith(LINK_PREFIX) ? "a" : spanName;
    }

    /** Link addresses used by this text's spans. */
    @NonNull
    public List<String> links() {
        List<String> out = new ArrayList<>();
        for (Span span : spans) {
            if (span.name.startsWith(LINK_PREFIX)) {
                out.add(span.name.substring(LINK_PREFIX.length()));
            }
        }
        return out;
    }

    private static final Comparator<Span> SPAN_ORDER = (a, b) -> {
        if (a.start != b.start) return Integer.compare(a.start, b.start);
        if (a.end != b.end) return Integer.compare(b.end, a.end);
        return a.name.compareTo(b.name);
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyledText)) return false;
        StyledText that = (StyledText) o;
        return text.equals(that.text) && spans.equals(that.spans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, spans);
    }

    @NonNull
    @Override
    public String toString() {
        return spans.isEmpty() ? text : text + " " + spans;
    }
}
