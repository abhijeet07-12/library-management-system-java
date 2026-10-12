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
}
