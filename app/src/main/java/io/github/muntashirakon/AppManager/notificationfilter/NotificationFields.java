// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.app.Notification;
import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/** The parts of a notification rules look at, read from its extras the way the shade shows them. */
public final class NotificationFields {
    /** Each part is cut to this, so a huge notification can't slow every rule down. */
    @VisibleForTesting
    static final int MAX_LENGTH = 2_000;
    /** How many of the newest messages a conversation's copy shows when expanded. */
    private static final int SHOWN_MESSAGES = 6;
    // Keys of a MessagingStyle message bundle. NotificationCompat writes the same ones.
    private static final String KEY_TEXT = "text";
    private static final String KEY_SENDER = "sender";
    private static final String KEY_SENDER_PERSON = "sender_person";
    // Notification.EXTRA_CONVERSATION_TITLE needs Android 7, and NotificationCompat writes this key on every version.
    private static final String EXTRA_CONVERSATION_TITLE = "android.conversationTitle";
    // Notification.EXTRA_MESSAGES needs Android 7 as well, and NotificationCompat's MessagingStyle writes it on older ones.
    private static final String EXTRA_MESSAGES = "android.messages";

    @NonNull
    public final String title;
    @NonNull
    public final String text;
    /** Who the notification is from: the conversation, else the last message's sender, else the title. */
    @NonNull
    public final String sender;

    @VisibleForTesting
    NotificationFields(@NonNull String title, @NonNull String text, @NonNull String sender) {
        this.title = title;
        this.text = text;
        this.sender = sender;
    }

    @NonNull
    public static NotificationFields from(@NonNull Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            return new NotificationFields("", "", "");
        }
        String title = title(extras);
        List<String> parts = new ArrayList<>();
        addPart(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        addPart(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                addPart(parts, line);
            }
        }
        Bundle[] messages = messages(extras);
        Bundle last = messages.length > 0 ? messages[messages.length - 1] : null;
        if (last != null) {
            addPart(parts, last.getCharSequence(KEY_TEXT));
        }
        String sender = cut(extras.getCharSequence(EXTRA_CONVERSATION_TITLE));
        if (sender.isEmpty() && last != null) {
            sender = senderOf(last);
        }
        if (sender.isEmpty()) {
            sender = title;
        }
        return new NotificationFields(title, cut(join(parts)), sender);
    }

    /** The title the shade shows. */
    @NonNull
    static String title(@NonNull Bundle extras) {
        String title = cut(extras.getCharSequence(Notification.EXTRA_TITLE));
        return title.isEmpty() ? cut(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)) : title;
    }

    /**
     * What a copy shows when expanded: the long text, the inbox lines, or the newest messages of a conversation, in
     * that order, falling back to the short text.
     */
    @Nullable
    static CharSequence expandedText(@NonNull Bundle extras) {
        CharSequence big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (big != null && big.length() > 0) {
            return big;
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            List<String> parts = new ArrayList<>(lines.length);
            for (CharSequence line : lines) {
                addPart(parts, line);
            }
            return join(parts);
        }
        Bundle[] messages = messages(extras);
        if (messages.length > 0) {
            List<String> parts = new ArrayList<>();
            for (int i = Math.max(0, messages.length - SHOWN_MESSAGES); i < messages.length; ++i) {
                String text = cut(messages[i].getCharSequence(KEY_TEXT));
                String sender = senderOf(messages[i]);
                addPart(parts, sender.isEmpty() ? text : sender + ": " + text);
            }
            return join(parts);
        }
        return extras.getCharSequence(Notification.EXTRA_TEXT);
    }

    @NonNull
    private static Bundle[] messages(@NonNull Bundle extras) {
        Parcelable[] parcelables;
        try {
            parcelables = getParcelableArray(extras, EXTRA_MESSAGES);
        } catch (RuntimeException e) {
            // An app's own Parcelable in the extras can't be read here.
            return new Bundle[0];
        }
        if (parcelables == null) {
            return new Bundle[0];
        }
        List<Bundle> bundles = new ArrayList<>(parcelables.length);
        for (Parcelable parcelable : parcelables) {
            if (parcelable instanceof Bundle) {
                bundles.add((Bundle) parcelable);
            }
        }
        return bundles.toArray(new Bundle[0]);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Parcelable[] getParcelableArray(@NonNull Bundle extras, @NonNull String key) {
        return extras.getParcelableArray(key);
    }

    @NonNull
    private static String senderOf(@NonNull Bundle message) {
        String sender = cut(message.getCharSequence(KEY_SENDER));
        if (sender.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                @SuppressWarnings("deprecation")
                Parcelable person = message.getParcelable(KEY_SENDER_PERSON);
                if (person instanceof Person) {
                    sender = cut(((Person) person).getName());
                }
            } catch (RuntimeException ignore) {
                // Left empty, so the title stands in.
            }
        }
        return sender;
    }

    private static void addPart(@NonNull List<String> parts, @Nullable CharSequence part) {
        if (part == null) {
            return;
        }
        String text = part.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        // The long text usually repeats the short one, so only the longer of the two is kept.
        for (int i = 0; i < parts.size(); ++i) {
            String known = parts.get(i);
            if (known.contains(text)) {
                return;
            }
            if (text.contains(known)) {
                parts.set(i, text);
                return;
            }
        }
        parts.add(text);
    }

    @NonNull
    private static String join(@NonNull List<String> parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(part);
        }
        return joined.toString();
    }

    @NonNull
    private static String cut(@Nullable CharSequence text) {
        if (text == null) {
            return "";
        }
        String string = text.toString().trim();
        return string.length() > MAX_LENGTH ? string.substring(0, MAX_LENGTH) : string;
    }
}
