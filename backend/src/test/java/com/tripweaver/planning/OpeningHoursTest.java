package com.tripweaver.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpeningHoursTest {

    static List<OpeningHours.Window> on(String raw, DayOfWeek d) {
        return OpeningHours.parse(raw).windows(d);
    }

    static OpeningHours.Window w(int open, int close) {
        return new OpeningHours.Window(open, close);
    }

    @Test
    void alwaysOpen() {
        assertEquals(List.of(w(0, 1440)), on("24/7", DayOfWeek.SUNDAY));
    }

    @Test
    void dailyRangeAndSplitShift() {
        assertEquals(List.of(w(540, 1380)), on("Mo-Su 09:00-23:00", DayOfWeek.WEDNESDAY));
        assertEquals(List.of(w(660, 870), w(1110, 1380)), on("Mo-Su 11:00-14:30, 18:30-23:00", DayOfWeek.MONDAY));
    }

    @Test
    void timesWithoutDaysApplyEveryDay() {
        assertEquals(List.of(w(600, 1020)), on("10:00-17:00", DayOfWeek.SATURDAY));
    }

    @Test
    void closingAfterMidnightIsCappedAtMidnight() {
        assertEquals(List.of(w(480, 1440)), on("Mo-Su 08:00-01:00", DayOfWeek.FRIDAY));
        assertEquals(List.of(w(540, 1440)), on("Mo-Su 09:00-00:00", DayOfWeek.FRIDAY));
    }

    @Test
    void laterRuleOverridesEarlierForItsDays() {
        String raw = "Mo-Su 09:00-17:00; Fr off";
        assertEquals(List.of(), on(raw, DayOfWeek.FRIDAY));
        assertEquals(List.of(w(540, 1020)), on(raw, DayOfWeek.THURSDAY));
        assertEquals(List.of(), on("Sa-Th 10:00-17:00; Fr closed", DayOfWeek.FRIDAY));
    }

    @Test
    void dayListsWrapAroundAndPublicHolidaysAreIgnored() {
        String raw = "Sa-Mo 10:00-12:00";
        assertEquals(List.of(w(600, 720)), on(raw, DayOfWeek.SUNDAY));
        assertEquals(List.of(), on(raw, DayOfWeek.TUESDAY));
        assertEquals(List.of(w(600, 1320)), on("Mo-Su,PH 10:00-22:00", DayOfWeek.TUESDAY));
        assertEquals(List.of(w(600, 720)), on("Mo,We 10:00-12:00", DayOfWeek.WEDNESDAY));
        assertEquals(List.of(), on("Mo,We 10:00-12:00", DayOfWeek.TUESDAY));
    }

    @Test
    void unsupportedSyntaxIsUnknownNotGuessed() {
        assertNull(OpeningHours.parse("sunrise-sunset"));
        assertNull(OpeningHours.parse("Jan-Mar Mo-Fr 09:00-17:00"));
        assertNull(OpeningHours.parse("Mo-Fr 09:00+"));
        assertNull(OpeningHours.parse(""));
        assertNull(OpeningHours.parse(null));
        assertNull(OpeningHours.parse("PH off"));
    }

    @Test
    void fitFindsEarliestStartInsideAWindow() {
        OpeningHours h = OpeningHours.parse("Mo-Su 11:00-14:30, 18:30-23:00");
        assertEquals(660, h.earliestStart(DayOfWeek.MONDAY, 600, 60)); // wait until opening
        assertEquals(700, h.earliestStart(DayOfWeek.MONDAY, 700, 60));
        assertEquals(1110, h.earliestStart(DayOfWeek.MONDAY, 840, 60)); // 30 min left: next window
        assertEquals(-1, h.earliestStart(DayOfWeek.MONDAY, 1350, 60)); // too late
    }
}
