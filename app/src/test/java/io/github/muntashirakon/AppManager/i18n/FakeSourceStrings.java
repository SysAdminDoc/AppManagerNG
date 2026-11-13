// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** English strings held in memory, with resource IDs laid out like an app's (plurals type 1, strings type 2). */
public final class FakeSourceStrings implements SourceStrings {
    static final int PACKAGE_ID = 0x7f;
    static final int PLURALS_TYPE = 1;
    static final int STRING_TYPE = 2;
    static final String LINK = "https://example.org/help";

    private final Map<String, SourceString> mStrings = new LinkedHashMap<>();
    private int mNextString;
    private int mNextPlural;

    /** A small set covering plain, formatted, styled, linked and plural strings. */
    @NonNull
    public static FakeSourceStrings sample() {
        return new FakeSourceStrings()
                .string("cancel", "Cancel")
                .string("apps_count", "%1$s has %2$d apps")
                .string("percent_done", "Half done (50%)")
                .styled("warning", new StyledText("Be careful here",
                        Collections.singletonList(new StyledText.Span(StyledText.BOLD, 0, 10))))
                .styled("help_link", new StyledText("Read the help first",
                        Collections.singletonList(new StyledText.Span(StyledText.LINK_PREFIX + LINK, 5, 9))))
                .plural("apps", "%d app", "%d apps")
                .plural("items_left", "One item left", "%d items left");
    }

    @NonNull
    public FakeSourceStrings string(@NonNull String key, @NonNull String text) {
        return styled(key, StyledText.plain(text));
    }

    @NonNull
    public FakeSourceStrings styled(@NonNull String key, @NonNull StyledText text) {
        int id = (PACKAGE_ID << 24) | (STRING_TYPE << 16) | mNextString++;
        mStrings.put(key, SourceString.ofString(key, id, text));
        return this;
    }

    @NonNull
    public FakeSourceStrings plural(@NonNull String key, @NonNull String one, @NonNull String other) {
        Map<String, StyledText> forms = new LinkedHashMap<>();
        forms.put("one", StyledText.plain(one));
        forms.put("other", StyledText.plain(other));
        int id = (PACKAGE_ID << 24) | (PLURALS_TYPE << 16) | mNextPlural++;
        mStrings.put(key, SourceString.ofPlural(key, id, forms));
        return this;
    }

    @NonNull
    @Override
    public List<String> keys() {
        return Collections.unmodifiableList(new ArrayList<>(mStrings.keySet()));
    }

    @Nullable
    @Override
    public SourceString get(@NonNull String key) {
        return mStrings.get(key);
    }

    @NonNull
    static List<String> typeNames() {
        return Arrays.asList("plurals", "string");
    }
}
