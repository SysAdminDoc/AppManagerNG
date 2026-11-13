// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class NotificationFieldsTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @NonNull
    private static Bundle message(@NonNull String text, @NonNull String sender) {
        Bundle message = new Bundle();
        message.putCharSequence("text", text);
        message.putCharSequence("sender", sender);
        message.putLong("time", 1L);
        return message;
    }

    @NonNull
    private static Notification withExtras(@NonNull Bundle extras) {
        Notification notification = new Notification();
        notification.extras = extras;
        return notification;
    }

    @Test
    public void conversationTitleIsTheSenderFirst() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, "Chat");
        extras.putCharSequence("android.conversationTitle", "Family");
        extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{message("hi", "Bob")});

        assertEquals("Family", NotificationFields.from(withExtras(extras)).sender);
    }

    @Test
    public void lastMessageSenderComesNext() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, "Chat");
        extras.putParcelableArray(Notification.EXTRA_MESSAGES,
                new Bundle[]{message("first", "Bob"), message("second", "Carol")});

        NotificationFields fields = NotificationFields.from(withExtras(extras));

        assertEquals("Carol", fields.sender);
        assertEquals("Chat", fields.title);
        assertTrue(fields.text, fields.text.contains("second"));
    }

    @Test
    public void senderPersonCountsWhenThereIsNoSenderText() {
        Bundle message = new Bundle();
        message.putCharSequence("text", "hi");
        message.putParcelable("sender_person", new Person.Builder().setName("Dana").build());
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, "Chat");
        extras.putParcelableArray(Notification.EXTRA_MESSAGES, new Bundle[]{message});

        assertEquals("Dana", NotificationFields.from(withExtras(extras)).sender);
    }

    @Test
    public void titleStandsInForTheSenderLast() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, "Erin");
        extras.putCharSequence(Notification.EXTRA_TEXT, "See you soon");

        NotificationFields fields = NotificationFields.from(withExtras(extras));

        assertEquals("Erin", fields.sender);
        assertEquals("See you soon", fields.text);
    }

    @Test
    public void realMessagingStyleIsReadThroughThePlatformKeys() {
        Person me = new Person.Builder().setName("Me").build();
        Person bob = new Person.Builder().setName("Bob").build();
        Person carol = new Person.Builder().setName("Carol").build();
        Notification direct = new Notification.Builder(mContext, "chat")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle(me)
                        .addMessage("Are you up?", 1L, bob)
                        .addMessage("Call me back", 2L, carol))
                .build();
        Notification group = new Notification.Builder(mContext, "chat")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(new Notification.MessagingStyle(me)
                        .setConversationTitle("Book club")
                        .setGroupConversation(true)
                        .addMessage("Chapter 3 tonight", 1L, carol))
                .build();

        NotificationFields directFields = NotificationFields.from(direct);
        NotificationFields groupFields = NotificationFields.from(group);

        assertEquals("Carol", directFields.sender);
        assertTrue(directFields.text, directFields.text.contains("Call me back"));
        assertEquals("Book club", groupFields.sender);
        assertTrue(groupFields.text, groupFields.text.contains("Chapter 3 tonight"));
    }

    @Test
    public void longTextOnlyAddsWhatTheShortTextLacks() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TEXT, "Your parcel");
        extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "Your parcel is at the door");
        extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, new CharSequence[]{"Line one", "Line two"});

        assertEquals("Your parcel is at the door\nLine one\nLine two", NotificationFields.from(withExtras(extras)).text);
    }

    @Test
    public void hugeTextIsCut() {
        char[] chars = new char[NotificationFields.MAX_LENGTH + 500];
        Arrays.fill(chars, 'x');
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, new String(chars));

        assertEquals(NotificationFields.MAX_LENGTH, NotificationFields.from(withExtras(extras)).title.length());
    }

    @Test
    public void notificationWithoutExtrasGivesEmptyFields() {
        Notification notification = new Notification();
        notification.extras = null;

        NotificationFields fields = NotificationFields.from(notification);

        assertEquals("", fields.title);
        assertEquals("", fields.text);
        assertEquals("", fields.sender);
    }
}
