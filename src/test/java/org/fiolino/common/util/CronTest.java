package org.fiolino.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CronTest {

    @Test
    void testNextMinute() {
        Cron c = Cron.forDateTime("50 * * * *");
        LocalDateTime d = LocalDateTime.of(2018, 3, 28, 20, 15, 33, 18496);
        LocalDateTime next = (LocalDateTime)c.adjustInto(d);
        assertEquals(0, next.getLong(ChronoField.NANO_OF_SECOND), "Nanos should be zero!");
        assertEquals(0, next.getLong(ChronoField.SECOND_OF_MINUTE), "Seconds should be zero!");
        assertEquals(50, next.getLong(ChronoField.MINUTE_OF_HOUR), "Minutes should be 50!");
        assertEquals(20, next.getLong(ChronoField.HOUR_OF_DAY), "Hours should stay the same");
        assertEquals(28, next.getLong(ChronoField.DAY_OF_MONTH), "Hours should stay the same");
        assertEquals(3, next.getLong(ChronoField.MONTH_OF_YEAR), "Hours should stay the same");
        assertEquals(2018, next.getLong(ChronoField.YEAR), "Hours should stay the same");
    }
}
