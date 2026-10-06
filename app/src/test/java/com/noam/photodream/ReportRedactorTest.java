package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportRedactorTest {

    private static String r(String s) { return ReportRedactor.redact(s); }

    @Test
    public void emailsAreRemoved() {
        String out = r("Connected as noam.lessner@a-y.org.il and other@example.com");
        assertFalse(out.contains("@a-y"));
        assertFalse(out.contains("example.com"));
        assertEquals("Connected as [email] and [email]", out);
    }

    @Test
    public void bearerTokensAndKeyValueTokensAreRemoved() {
        String out = r("Authorization: Bearer abc123.DEF-456_xyz\nrefresh_token=1//0gSecretSecret&x=1\n\"access_token\":\"EwB4A8l6\"");
        assertFalse(out, out.contains("abc123"));
        assertFalse(out, out.contains("1//0gSecret"));
        assertFalse(out, out.contains("EwB4A8l6"));
        assertTrue(out, out.contains("[token]"));
        assertTrue("non-secret parts stay", out.contains("x=1"));
    }

    @Test
    public void jwtAndLongOpaqueStringsAreRemoved() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJl";
        String opaque = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8S9t0U1v2";
        String out = r("id " + jwt + " key " + opaque);
        assertEquals("id [token] key [token]", out);
    }

    @Test
    public void photoFileNamesAndUrisAreRemoved() {
        String out = r("Skipping unreadable photo content://com.android.externalstorage.documents/tree/primary%3ADCIM/document/primary%3ADCIM%2FIMG_2024.jpg"
                + "\nCannot decode od_Family-Trip_01.HEIC\nfile:///data/user/0/com.noam.photodream/files/photo_cache/gdrive/gd_abc.jpg");
        assertFalse(out, out.contains("IMG_2024"));
        assertFalse(out, out.contains("Family-Trip"));
        assertFalse(out, out.contains("gd_abc"));
        assertFalse(out, out.contains("photo_cache"));
        assertTrue(out.contains("Skipping unreadable photo [uri]"));
        assertTrue(out.contains("Cannot decode [photo]"));
    }

    @Test
    public void harmlessTextIsUnchanged() {
        String text = "PhotoDream 0.3 · Pixel 8 · Android 15 · OneDrive: connected, 120 photos, last sync 12:30 – 120 photos on phone (3 new)";
        assertEquals(text, r(text));
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", r(null));
    }
}
