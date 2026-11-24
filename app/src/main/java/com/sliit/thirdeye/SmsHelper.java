package com.sliit.thirdeye;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Voice-friendly SMS and WhatsApp helpers.
 *
 * Deleting SMS is deliberately absent: since Android 4.4 only the phone's default
 * SMS app may delete messages, and Third Eye is not one.
 */
final class SmsHelper {

    static final class Contact {
        final String name;
        final String number;

        Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    static final class Message {
        final String from;
        final String body;

        Message(String from, String body) {
            this.from = from;
            this.body = body;
        }
    }

    private SmsHelper() {
    }

    /**
     * Finds a contact by spoken name. Tries an exact (case-insensitive) match first, then
     * a name that starts with what was said, so "Amma" still finds "Amma Mobile".
     */
    static Contact findContact(Context context, String spokenName) {
        if (spokenName == null || spokenName.trim().isEmpty()) return null;
        String name = spokenName.trim();
        Contact exact = queryContact(context, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", name);
        if (exact != null) return exact;
        return queryContact(context, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", name + "%");
    }

    private static Contact queryContact(Context context, String selection, String arg) {
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor c = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, new String[]{arg},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (c != null && c.moveToFirst()) {
                return new Contact(c.getString(0), c.getString(1));
            }
        }
        return null;
    }

    /** Contact name for a phone number, or the number itself when it isn't saved. */
    static String nameForNumber(Context context, String number) {
        if (number == null) return "an unknown sender";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (IllegalArgumentException | SecurityException ignored) {
            // Short codes and senders like "DIALOG" aren't valid lookup keys; fall through.
        }
        return number;
    }

    /** Newest unread SMS first, at most {@code limit}. */
    static List<Message> unreadMessages(Context context, int limit) {
        List<Message> result = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        String[] projection = {Telephony.Sms.ADDRESS, Telephony.Sms.BODY};
        try (Cursor c = cr.query(Telephony.Sms.Inbox.CONTENT_URI, projection,
                Telephony.Sms.READ + " = 0", null, Telephony.Sms.DATE + " DESC")) {
            while (c != null && c.moveToNext() && result.size() < limit) {
                result.add(new Message(nameForNumber(context, c.getString(0)), c.getString(1)));
            }
        }
        return result;
    }

    static void sendSms(Context context, String number, String body) {
        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? context.getSystemService(SmsManager.class)
                : SmsManager.getDefault();
        // Long dictated messages exceed one SMS; multipart sends them as one message.
        ArrayList<String> parts = sms.divideMessage(body);
        sms.sendMultipartTextMessage(number, null, parts, null, null);
    }

    /**
     * Opens a WhatsApp chat with the message already typed. WhatsApp does not allow other
     * apps to send for the user, so they still have to press its Send button.
     * Returns false when WhatsApp isn't installed.
     */
    static boolean openWhatsApp(Context context, String number, String body) {
        Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + toInternational(number)
                + "&text=" + Uri.encode(body));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.whatsapp");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** WhatsApp needs country code and digits only; Sri Lankan local numbers start with 0. */
    static String toInternational(String number) {
        String digits = number.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits.substring(1);
        if (digits.startsWith("00")) return digits.substring(2);
        if (digits.startsWith("0")) return "94" + digits.substring(1);
        return digits;
    }
}
