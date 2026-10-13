package com.library.library_management;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    public static final Set<Integer> ALLOWED_DAYS = Set.of(3, 7, 10, 14, 30);
    private static final Set<String> FILTERS = Set.of("all", "open", "active", "overdue", "returned");

    /** issuedAt is optional (defaults to now); dueAt is always calculated here, never accepted from the client. */
    public record IssueRequest(Long bookId, Long memberId, Integer days, Instant issuedAt) {
    }

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final FineService fineService;

    public LoanService(LoanRepository loanRepository, BookRepository bookRepository,
                       MemberRepository memberRepository, FineService fineService) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.fineService = fineService;
    }

    private LoanView view(Loan loan, Instant now, java.math.BigDecimal rate) {
        return LoanView.of(loan, now, FineInfo.of(loan, now, rate, fineService.zone()));
    }

    @Transactional
    public LoanView issue(IssueRequest r) {
        if (r == null || r.bookId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a book to issue.");
        }
        if (r.memberId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a member to issue the book to.");
        }
        if (r.days() == null || !ALLOWED_DAYS.contains(r.days())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Borrowing duration must be 3, 7, 10, 14 or 30 days.");
        }
        Instant now = Instant.now();
        Instant issuedAt = r.issuedAt() == null ? now : r.issuedAt();
        if (issuedAt.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The issue date cannot be in the future.");
        }
        if (issuedAt.isBefore(now.minus(365, ChronoUnit.DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The issue date cannot be more than a year in the past.");
        }
        Member member = memberRepository.findById(r.memberId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
        Book book = bookRepository.findByIdForUpdate(r.bookId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found."));
        if (loanRepository.existsByBookIdAndReturnedAtIsNull(book.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This book is already on loan.");
        }
        if (!book.isAvailable()) {
            throw new ApiException(HttpStatus.CONFLICT, "This book is marked unavailable and cannot be issued.");
        }
        Loan loan = new Loan(book, member, issuedAt, issuedAt.plus(r.days(), ChronoUnit.DAYS), r.days());
        book.setAvailable(false);
        bookRepository.save(book);
        loanRepository.save(loan);
        return view(loan, now, fineService.currentRate());
    }

    @Transactional
    public LoanView giveBack(Long id) {
        Loan loan = loanRepository.findDetailed(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrowing record not found."));
        if (loan.getReturnedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This book has already been returned.");
        }
        Book book = bookRepository.findByIdForUpdate(loan.getBook().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Book not found."));
        Instant now = Instant.now();
        java.math.BigDecimal rate = fineService.currentRate();
        FineInfo fine = FineInfo.of(loan, now, rate, fineService.zone());
        if (FineInfo.PENDING.equals(fine.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A fine of ₹" + fine.fineAmount().toPlainString() + " is pending. Collect it (demo payment) before confirming the return.");
        }
        if (loan.getFineAmount() == null) {      // no fine: record the final (zero) fine so it can never change later
            loan.settleFine(rate, fine.overdueDays(), fine.fineAmount());
        }
        loan.setReturnedAt(now);          // actual return time always comes from the server clock
        book.setAvailable(true);
        bookRepository.save(book);
        loanRepository.save(loan);
        return view(loan, now, rate);
    }

    /** Simulated payment: no money moves. Records the fine, the time, and freezes the amount. */
    @Transactional
    public LoanView payFine(Long id) {
        Loan loan = loanRepository.findDetailed(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrowing record not found."));
        Instant now = Instant.now();
        java.math.BigDecimal rate = fineService.currentRate();
        FineInfo fine = FineInfo.of(loan, now, rate, fineService.zone());
        if (fine.fineAmount().signum() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "No fine is due for this loan.");
        }
        if (loan.getFinePaidAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This fine has already been paid (demo).");
        }
        loan.settleFine(fine.finePerDay(), fine.overdueDays(), fine.fineAmount());
        loan.setFinePaidAt(now);
        loanRepository.save(loan);
        return view(loan, now, rate);
    }

    @Transactional(readOnly = true)
    public LoanView get(Long id) {
        return loanRepository.findDetailed(id)
                .map(l -> view(l, Instant.now(), fineService.currentRate()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Borrowing record not found."));
    }

    /** status: all | open (not returned) | active | overdue | returned. */
    @Transactional(readOnly = true)
    public List<LoanView> list(String status, Long memberId, Long bookId) {
        String f = status == null || status.isBlank() ? "all" : status.trim().toLowerCase(Locale.ROOT);
        if (!FILTERS.contains(f)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be all, open, active, overdue or returned.");
        }
        Instant now = Instant.now();
        java.math.BigDecimal rate = fineService.currentRate();
        return loanRepository.findAllDetailed().stream()
                .filter(l -> memberId == null || l.getMember().getId().equals(memberId))
                .filter(l -> bookId == null || l.getBook().getId().equals(bookId))
                .map(l -> view(l, now, rate))
                .filter(v -> switch (f) {
                    case "open" -> !v.status().equals("RETURNED");
                    case "all" -> true;
                    default -> v.status().equalsIgnoreCase(f);
                })
                .toList();
    }
}
