package com.library.library_management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/** Service-level tests against a real (in-memory test) database. Each test rolls back. */
@SpringBootTest
@Transactional
class LoanServiceTest {

    @Autowired LoanService loans;
    @Autowired MemberService members;
    @Autowired BookController books;
    @Autowired BookRepository bookRepository;
    @Autowired FineService fines;

    private Member member(String id) {
        Member m = new Member();
        m.setName("Asha Rao");
        m.setMembershipId(id);
        m.setEmail("asha@example.com");
        m.setPhone("98765 43210");
        return members.create(m);
    }

    private Book book() {
        return books.addBook(new Book("Clean Code", "Robert C. Martin"));
    }

    private static void assertStatus(Runnable r, HttpStatus expected) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    @Test
    void booksStillWork() {
        Book b = book();
        assertThat(b.getId()).isNotNull();
        assertThat(books.getAllBooks()).extracting(Book::getTitle).contains("Clean Code");
        books.deleteBook(b.getId());
        assertThat(books.getAllBooks()).extracting(Book::getId).doesNotContain(b.getId());
    }

    @Test
    void membersCanBeCreatedSearchedAndMustBeUnique() {
        Member m = member("CS2024-001");
        assertThat(m.getPhone()).isEqualTo("9876543210");
        assertThat(members.search("cs2024")).extracting(Member::getId).contains(m.getId());
        assertThat(members.search("zzz-nothing")).isEmpty();
        assertStatus(() -> member("cs2024-001"), HttpStatus.CONFLICT);
        Member bad = new Member();
        bad.setName("  ");
        bad.setMembershipId("X1");
        assertStatus(() -> members.create(bad), HttpStatus.BAD_REQUEST);
    }

    @Test
    void issueCalculatesDueDateAndLocksTheBook() {
        Member m = member("CS2024-002");
        Book b = book();
        Instant issued = Instant.now().minus(1, ChronoUnit.HOURS);

        LoanView v = loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 7, issued));

        assertThat(v.dueAt()).isEqualTo(issued.plus(7, ChronoUnit.DAYS));
        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThat(v.secondsRemaining()).isGreaterThan(Duration.ofDays(6).getSeconds());
        assertThat(bookRepository.findById(b.getId()).orElseThrow().isAvailable()).isFalse();
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 7, null)),
                HttpStatus.CONFLICT);
        assertStatus(() -> books.deleteBook(b.getId()), HttpStatus.CONFLICT);
        assertStatus(() -> members.delete(m.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void invalidIssueRequestsAreRejected() {
        Member m = member("CS2024-003");
        Book b = book();
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 5, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 7,
                Instant.now().plus(1, ChronoUnit.DAYS))), HttpStatus.BAD_REQUEST);
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(b.getId(), 999999L, 7, null)), HttpStatus.NOT_FOUND);
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(999999L, m.getId(), 7, null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void manuallyUnavailableBookCannotBeIssued() {
        Member m = member("CS2024-004");
        Book b = new Book("Hidden", "Nobody");
        b.setAvailable(false);
        Book saved = books.addBook(b);
        assertStatus(() -> loans.issue(new LoanService.IssueRequest(saved.getId(), m.getId(), 3, null)), HttpStatus.CONFLICT);
    }

    @Test
    void overdueThenReturnMakesBookAvailableAgainAndKeepsHistory() {
        Member m = member("CS2024-005");
        Book b = book();
        LoanView v = loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 3,
                Instant.now().minus(10, ChronoUnit.DAYS)));

        assertThat(v.status()).isEqualTo("OVERDUE");
        assertThat(v.secondsRemaining()).isNegative();
        assertThat(loans.list("overdue", null, null)).extracting(LoanView::id).contains(v.id());

        // 10 days ago + 3-day loan = 7 days overdue; the fine must be paid (demo) before the return
        assertThat(v.overdueDays()).isEqualTo(7);
        assertThat(v.fineAmount()).isEqualByComparingTo("35");
        assertStatus(() -> loans.giveBack(v.id()), HttpStatus.CONFLICT);
        loans.payFine(v.id());

        LoanView back = loans.giveBack(v.id());
        assertThat(back.status()).isEqualTo("RETURNED");
        assertThat(back.returnedAt()).isNotNull();
        assertThat(back.lateReturn()).isTrue();
        assertThat(back.secondsRemaining()).isNull();
        assertThat(bookRepository.findById(b.getId()).orElseThrow().isAvailable()).isTrue();
        assertThat(loans.list("returned", m.getId(), null)).extracting(LoanView::id).contains(v.id());
        assertThat(loans.list("open", null, null)).extracting(LoanView::id).doesNotContain(v.id());
        assertStatus(() -> loans.giveBack(v.id()), HttpStatus.CONFLICT);

        LoanView again = loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 14, null));
        assertThat(again.status()).isEqualTo("ACTIVE");
    }

    private LoanView issueDaysAgo(Member m, Book b, int loanDays, int issuedDaysAgo) {
        return loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), loanDays, Instant.now().minus(issuedDaysAgo, ChronoUnit.DAYS)));
    }

    @Test
    void onTimeReturnHasNoFine() {
        Member m = member("CS2024-006");
        Book b = book();
        LoanView v = loans.issue(new LoanService.IssueRequest(b.getId(), m.getId(), 7, null));
        assertThat(v.fineAmount()).isEqualByComparingTo("0");
        assertThat(v.paymentStatus()).isEqualTo("NONE");
        assertThat(v.readyToReturn()).isTrue();
        assertStatus(() -> loans.payFine(v.id()), HttpStatus.CONFLICT);   // nothing to pay

        LoanView back = loans.giveBack(v.id());
        assertThat(back.fineAmount()).isEqualByComparingTo("0");
        assertThat(back.paymentStatus()).isEqualTo("NONE");
        assertThat(back.returnedAt()).isNotNull();
    }

    @Test
    void threeDaysLateCostsFifteenRupeesAndPaymentIsRecordedOnce() {
        Member m = member("CS2024-007");
        Book b = book();
        LoanView v = issueDaysAgo(m, b, 3, 6);              // due exactly 3 days ago

        assertThat(v.overdueDays()).isEqualTo(3);
        assertThat(v.finePerDay()).isEqualByComparingTo("5");
        assertThat(v.fineAmount()).isEqualByComparingTo("15");
        assertThat(v.paymentStatus()).isEqualTo("PENDING");
        assertThat(v.readyToReturn()).isFalse();
        // reading it again (like a page refresh) gives the same answer and the loan is still pending
        assertThat(loans.get(v.id()).paymentStatus()).isEqualTo("PENDING");

        LoanView paid = loans.payFine(v.id());
        assertThat(paid.paymentStatus()).isEqualTo("PAID_DEMO");
        assertThat(paid.finePaidAt()).isNotNull();
        assertThat(paid.fineAmount()).isEqualByComparingTo("15");
        assertThat(paid.readyToReturn()).isTrue();
        assertStatus(() -> loans.payFine(v.id()), HttpStatus.CONFLICT);    // no duplicate payment

        // changing the rate afterwards must not touch a fine that has already been settled
        fines.setRate(new java.math.BigDecimal("9"));
        assertThat(loans.get(v.id()).fineAmount()).isEqualByComparingTo("15");

        LoanView back = loans.giveBack(v.id());
        assertThat(back.status()).isEqualTo("RETURNED");
        assertThat(back.fineAmount()).isEqualByComparingTo("15");
        assertThat(back.paymentStatus()).isEqualTo("PAID_DEMO");
        assertThat(bookRepository.findById(b.getId()).orElseThrow().isAvailable()).isTrue();
        assertStatus(() -> loans.giveBack(v.id()), HttpStatus.CONFLICT);   // cannot return twice
        assertStatus(() -> loans.payFine(v.id()), HttpStatus.CONFLICT);
    }

    @Test
    void fineRateIsConfigurableAndValidated() {
        assertThat(fines.currentRate()).isEqualByComparingTo("5");
        Member m = member("CS2024-008");
        Book b = book();
        LoanView v = issueDaysAgo(m, b, 3, 6);
        assertThat(v.fineAmount()).isEqualByComparingTo("15");

        fines.setRate(new java.math.BigDecimal("7"));
        assertThat(fines.currentRate()).isEqualByComparingTo("7");
        assertThat(loans.get(v.id()).fineAmount()).isEqualByComparingTo("21");   // unpaid fines use the new rate

        assertStatus(() -> fines.setRate(new java.math.BigDecimal("-1")), HttpStatus.BAD_REQUEST);
        assertStatus(() -> fines.setRate(null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> fines.setRate(new java.math.BigDecimal("1.234")), HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLoanIdsAreRejected() {
        assertStatus(() -> loans.payFine(987654L), HttpStatus.NOT_FOUND);
        assertStatus(() -> loans.giveBack(987654L), HttpStatus.NOT_FOUND);
    }
}
