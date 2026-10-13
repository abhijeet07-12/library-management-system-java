package com.library.library_management;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /** GET /api/loans?status=all|open|active|overdue|returned&memberId=..&bookId=.. */
    @GetMapping
    public List<LoanView> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) Long memberId,
                               @RequestParam(required = false) Long bookId) {
        return loanService.list(status, memberId, bookId);
    }

    @GetMapping("/{id}")
    public LoanView get(@PathVariable Long id) {
        return loanService.get(id);
    }

    /** Issue a book: { "bookId": 1, "memberId": 2, "days": 7, "issuedAt": "2026-10-12T09:30:00Z" (optional) } */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanView issue(@RequestBody LoanService.IssueRequest request) {
        return loanService.issue(request);
    }

    @PostMapping("/{id}/return")
    public LoanView giveBack(@PathVariable Long id) {
        return loanService.giveBack(id);
    }

    /** Simulated (demo) fine payment. No real money is involved. */
    @PostMapping("/{id}/pay-fine")
    public LoanView payFine(@PathVariable Long id) {
        return loanService.payFine(id);
    }
}
