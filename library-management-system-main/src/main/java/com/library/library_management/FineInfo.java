package com.library.library_management;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The fine position of one loan at a moment in time.
 *
 * Until a fine is settled (demo payment or return) it is calculated live from the due date and the
 * current configured rate. Once settled, the stored rate/days/amount are used and never change.
 * Loans returned before this feature existed are calculated up to their stored return time.
 */
public record FineInfo(long overdueDays, BigDecimal finePerDay, BigDecimal fineAmount,
                       String paymentStatus, Instant paidAt) {

    public static final String NONE = "NONE";
    public static final String PENDING = "PENDING";
    public static final String PAID_DEMO = "PAID_DEMO";

    public static FineInfo of(Loan loan, Instant now, BigDecimal currentRate, java.time.ZoneId zone) {
        long days;
        BigDecimal rate;
        BigDecimal amount;
        if (loan.getFineAmount() != null) {
            days = loan.getFineOverdueDays() == null ? 0 : loan.getFineOverdueDays();
            rate = loan.getFinePerDay();
            amount = loan.getFineAmount();
        } else {
            Instant end = loan.getReturnedAt() != null ? loan.getReturnedAt() : now;
            days = FineCalculator.overdueDays(loan.getDueAt(), end, zone);
            rate = currentRate;
            amount = FineCalculator.fine(days, rate);
        }
        String payment = amount.signum() == 0 ? NONE : loan.getFinePaidAt() != null ? PAID_DEMO : PENDING;
        return new FineInfo(days, rate, amount, payment, loan.getFinePaidAt());
    }
}
