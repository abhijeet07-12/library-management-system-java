package com.library.library_management;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** Plain unit tests of the fine rule (no Spring, no database). */
class FineCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal FIVE = new BigDecimal("5.00");

    private static Instant ist(String isoLocal) {
        return java.time.LocalDateTime.parse(isoLocal).atZone(IST).toInstant();
    }

    @Test
    void noFineBeforeOrOnTheDueDate() {
        Instant due = ist("2026-10-12T18:00:00");
        assertThat(FineCalculator.overdueDays(due, ist("2026-10-10T09:00:00"), IST)).isZero();   // early
        assertThat(FineCalculator.overdueDays(due, ist("2026-10-12T17:59:00"), IST)).isZero();   // just before
        assertThat(FineCalculator.overdueDays(due, ist("2026-10-12T23:59:00"), IST)).isZero();   // same day, after the due time
    }

    @Test
    void finePerCalendarDayAfterTheDueDate() {
        Instant due = ist("2026-10-12T18:00:00");
        assertThat(FineCalculator.overdueDays(due, ist("2026-10-13T00:01:00"), IST)).isEqualTo(1);
        assertThat(FineCalculator.overdueDays(due, ist("2026-10-15T08:00:00"), IST)).isEqualTo(3);
        assertThat(FineCalculator.fine(0, FIVE)).isEqualByComparingTo("0");
        assertThat(FineCalculator.fine(3, FIVE)).isEqualByComparingTo("15");
        assertThat(FineCalculator.fine(7, FIVE)).isEqualByComparingTo("35");
        assertThat(FineCalculator.fine(3, new BigDecimal("2.50"))).isEqualByComparingTo("7.50");
    }
}
