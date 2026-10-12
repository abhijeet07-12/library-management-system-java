package com.library.library_management;

import java.time.Duration;
import java.time.Instant;

/**
 * API representation of a loan. status and secondsRemaining are calculated by the server
 * from the stored timestamps; the browser countdown is display-only.
 */
public record LoanView(
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        Long memberId,
        String memberName,
        String membershipId,
        Instant issuedAt,
        Instant dueAt,
        Instant returnedAt,
        int loanDays,
        String status,
        boolean lateReturn,
        Long secondsRemaining,
        Instant serverNow) {

    public static LoanView of(Loan l, Instant now) {
        boolean returned = l.getReturnedAt() != null;
        String status = returned ? "RETURNED" : now.isAfter(l.getDueAt()) ? "OVERDUE" : "ACTIVE";
        Long remaining = returned ? null : Duration.between(now, l.getDueAt()).getSeconds();
        boolean late = returned && l.getReturnedAt().isAfter(l.getDueAt());
        return new LoanView(l.getId(), l.getBook().getId(), l.getBook().getTitle(), l.getBook().getAuthor(),
                l.getMember().getId(), l.getMember().getName(), l.getMember().getMembershipId(),
                l.getIssuedAt(), l.getDueAt(), l.getReturnedAt(), l.getLoanDays(),
                status, late, remaining, now);
    }
}
