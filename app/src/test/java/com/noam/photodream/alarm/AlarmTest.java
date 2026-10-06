package com.noam.photodream.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AlarmTest {

    @Test
    public void mapRoundTripKeepsEveryField() {
        Alarm a = new Alarm();
        a.id = 42;
        a.hour = 6;
        a.minute = 55;
        a.days = 0b0101010;
        a.enabled = false;
        a.label = "School";
        a.challenge = "flip";
        a.difficulty = Alarm.Difficulty.HARD;
        a.soundUri = "content://media/internal/audio/media/7";
        a.vibrate = false;
        a.snoozeMinutes = 9;
        a.rampSeconds = 45;

        Alarm b = Alarm.fromMap(a.toMap());
        assertEquals(42, b.id);
        assertEquals(6, b.hour);
        assertEquals(55, b.minute);
        assertEquals(0b0101010, b.days);
        assertFalse(b.enabled);
        assertEquals("School", b.label);
        assertEquals("flip", b.challenge);
        assertEquals(Alarm.Difficulty.HARD, b.difficulty);
        assertEquals("content://media/internal/audio/media/7", b.soundUri);
        assertFalse(b.vibrate);
        assertEquals(9, b.snoozeMinutes);
        assertEquals(45, b.rampSeconds);
    }

    @Test
    public void nullSoundMeansDefaultAndSurvivesRoundTrip() {
        Alarm a = new Alarm();
        assertNull(Alarm.fromMap(a.toMap()).soundUri);
    }

    @Test
    public void emptyOrBrokenMapGivesDefaults() {
        Alarm a = Alarm.fromMap(new HashMap<>());
        assertEquals(7, a.hour);
        assertEquals(0, a.minute);
        assertEquals(0, a.days);
        assertTrue(a.enabled);
        assertEquals(Alarm.Difficulty.MEDIUM, a.difficulty);
        assertEquals(Alarm.DEFAULT_SNOOZE_MINUTES, a.snoozeMinutes);
        assertEquals(Alarm.DEFAULT_RAMP_SECONDS, a.rampSeconds);
    }

    @Test
    public void outOfRangeValuesAreClamped() {
        Map<String, Object> m = new HashMap<>();
        m.put("hour", 99);
        m.put("minute", -4);
        m.put("days", 0xFFFF);
        m.put("snooze", 0);
        m.put("difficulty", "IMPOSSIBLE");
        m.put("enabled", "yes");
        Alarm a = Alarm.fromMap(m);
        assertEquals(23, a.hour);
        assertEquals(0, a.minute);
        assertEquals(0x7F, a.days);
        assertEquals(1, a.snoozeMinutes);
        assertEquals(Alarm.Difficulty.MEDIUM, a.difficulty);
        assertTrue(a.enabled);
    }

    @Test
    public void copyIsIndependent() {
        Alarm a = new Alarm();
        a.label = "x";
        Alarm c = a.copy();
        c.label = "y";
        assertEquals("x", a.label);
    }
}
