package com.noam.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionCompareTest {

    @Test
    public void tenIsNewerThanNine() {
        assertTrue(VersionCompare.isNewer("0.10", "0.9"));
        assertFalse(VersionCompare.isNewer("0.9", "0.10"));
    }

    @Test
    public void tagPrefixVIsIgnored() {
        assertTrue(VersionCompare.isNewer("v0.4", "0.3"));
        assertTrue(VersionCompare.isNewer("V1.0", "v0.9"));
    }

    @Test
    public void sameVersionIsNotNewer() {
        assertFalse(VersionCompare.isNewer("v0.3", "0.3"));
        assertFalse(VersionCompare.isNewer("0.3.0", "0.3"));
        assertFalse(VersionCompare.isNewer("0.3", "0.3.0"));
    }

    @Test
    public void missingPartsCountAsZero() {
        assertTrue(VersionCompare.isNewer("0.3.1", "0.3"));
        assertTrue(VersionCompare.isNewer("1", "0.9.9"));
    }

    @Test
    public void devBuildsAndOddNamesAreIgnored() {
        assertFalse(VersionCompare.isNewer("v0.4", "dev-12"));
        assertFalse(VersionCompare.isNewer("dev-12", "0.3"));
        assertFalse(VersionCompare.isNewer("v0.4-beta", "0.3"));
        assertFalse(VersionCompare.isNewer("", "0.3"));
        assertFalse(VersionCompare.isNewer(null, "0.3"));
        assertFalse(VersionCompare.isNewer("0.4", null));
        assertFalse(VersionCompare.isNewer("0..4", "0.3"));
    }

    @Test
    public void majorBeatsMinor() {
        assertTrue(VersionCompare.isNewer("2.0", "1.99"));
    }

    @Test
    public void displayStripsTheV() {
        assertEquals("0.4", VersionCompare.display("v0.4"));
        assertEquals("0.4", VersionCompare.display("0.4"));
        assertEquals("", VersionCompare.display(null));
    }
}
