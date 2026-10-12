package com.library.library_management;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Query("select l from Loan l join fetch l.book join fetch l.member order by l.issuedAt desc, l.id desc")
    List<Loan> findAllDetailed();

    @Query("select l from Loan l join fetch l.book join fetch l.member where l.id = :id")
    Optional<Loan> findDetailed(@Param("id") Long id);

    boolean existsByBookIdAndReturnedAtIsNull(Long bookId);

    boolean existsByBookId(Long bookId);

    boolean existsByMemberId(Long memberId);
}
