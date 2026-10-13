package com.library.library_management;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<Member> search(@RequestParam(required = false) String q) {
        return memberService.search(q);
    }

    @GetMapping("/{id}")
    public Member get(@PathVariable Long id) {
        return memberService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Member create(@RequestBody Member member) {
        return memberService.create(member);
    }

    @PutMapping("/{id}")
    public Member update(@PathVariable Long id, @RequestBody Member member) {
        return memberService.update(id, member);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        memberService.delete(id);
    }
}
