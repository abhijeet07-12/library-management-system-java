package com.library.library_management;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Pure fine arithmetic (no Spring, no database), so the rule lives in exactly one place.
 *
 * Fine = overdue calendar days x fine per day.
 * Overdue calendar days = whole calendar dates (in the library's time zone) between the due DATE
 * and the date the book was returned (or today, while it is still out). A book returned any time
 * on its due date therefore has no fine; the first rupee is charged from the day after.
 */
public final class FineCalculator {

    public static final BigDecimal DEFAULT_RATE = new BigDecimal("5.00");

    private FineCalculator() {
    }

    public static long overdueDays(Instant dueAt, Instant endAt, ZoneId zone) {
        LocalDate due = dueAt.atZone(zone).toLocalDate();
        LocalDate end = endAt.atZone(zone).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(due, end));
    }

    public static BigDecimal fine(long overdueDays, BigDecimal finePerDay) {
        return finePerDay.multiply(BigDecimal.valueOf(overdueDays)).setScale(2, RoundingMode.HALF_UP);
    }
}
