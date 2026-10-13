package com.library.library_management;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One borrowing record. Status is never stored: it is derived from dueAt and returnedAt,
 * so a restart or page reload always recalculates it from the persisted timestamps.
 */
@Entity
@Table(name = "book_loan", indexes = {
        @Index(name = "idx_loan_book", columnList = "book_id"),
        @Index(name = "idx_loan_member", columnList = "member_id")})
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "loan_days", nullable = false)
    private int loanDays;

    /** Fine fields are filled in when the fine is settled (demo payment or return). Nullable so old rows still load. */
    @Column(name = "fine_per_day", precision = 10, scale = 2)
    private BigDecimal finePerDay;

    @Column(name = "fine_overdue_days")
    private Integer fineOverdueDays;

    @Column(name = "fine_amount", precision = 12, scale = 2)
    private BigDecimal fineAmount;

    @Column(name = "fine_paid_at")
    private Instant finePaidAt;

    @Version
    private Long version;

    protected Loan() {
    }

    public Loan(Book book, Member member, Instant issuedAt, Instant dueAt, int loanDays) {
        this.book = book;
        this.member = member;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
        this.loanDays = loanDays;
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public Member getMember() { return member; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getDueAt() { return dueAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public int getLoanDays() { return loanDays; }

    public BigDecimal getFinePerDay() { return finePerDay; }
    public Integer getFineOverdueDays() { return fineOverdueDays; }
    public BigDecimal getFineAmount() { return fineAmount; }
    public Instant getFinePaidAt() { return finePaidAt; }
    public void setFinePaidAt(Instant finePaidAt) { this.finePaidAt = finePaidAt; }

    /** Freezes the fine: after this, rate, days and amount never change. */
    public void settleFine(BigDecimal finePerDay, long overdueDays, BigDecimal fineAmount) {
        this.finePerDay = finePerDay;
        this.fineOverdueDays = (int) overdueDays;
        this.fineAmount = fineAmount;
    }
}
