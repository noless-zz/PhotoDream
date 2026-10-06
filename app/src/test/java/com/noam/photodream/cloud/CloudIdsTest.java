package com.noam.photodream.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CloudIdsTest {

    @Test
    public void roundTripWithDrive() {
        String id = CloudIds.encode("9c6fce8a1e4e3aec", "9C6FCE8A1E4E3AEC!1234");
        CloudIds.Parts p = CloudIds.decode(id);
        assertEquals("9c6fce8a1e4e3aec", p.driveId);
        assertEquals("9C6FCE8A1E4E3AEC!1234", p.itemId);
    }

    @Test
    public void ownFilesKeepTheirIdUnchanged() {
        assertEquals("ABC!1", CloudIds.encode(null, "ABC!1"));
        assertEquals("ABC!1", CloudIds.encode("", "ABC!1"));
        CloudIds.Parts p = CloudIds.decode("ABC!1");
        assertNull(p.driveId);
        assertEquals("ABC!1", p.itemId);       // the "!" inside OneDrive ids is not a separator
    }

    @Test
    public void rootAndGoogleIdsAreUntouched() {
        assertNull(CloudIds.decode("root").driveId);
        assertEquals("1a2B3c-_x", CloudIds.decode("1a2B3c-_x").itemId);
    }

    @Test
    public void onlyTheFirstSeparatorSplits() {
        CloudIds.Parts p = CloudIds.decode("drive|item|with|bars");
        assertEquals("drive", p.driveId);
        assertEquals("item|with|bars", p.itemId);
    }

    @Test
    public void leadingSeparatorOrNullIsPlain() {
        assertNull(CloudIds.decode("|x").driveId);
        assertNull(CloudIds.decode(null).itemId);
    }
}
