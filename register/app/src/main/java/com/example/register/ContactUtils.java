package com.example.register;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public class ContactUtils {
    public static boolean isNumberInContacts(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return false;

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup._ID};

        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}