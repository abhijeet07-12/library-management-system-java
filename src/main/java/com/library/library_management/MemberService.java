package com.library.library_management;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;

    public MemberService(MemberRepository memberRepository, LoanRepository loanRepository) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
    }

    @Transactional(readOnly = true)
    public List<Member> search(String q) {
        List<Member> all = memberRepository.findAll(Sort.by("name").ascending());
        if (q == null || q.isBlank()) {
            return all;
        }
        String s = q.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(m -> contains(m.getName(), s) || contains(m.getMembershipId(), s)
                || contains(m.getEmail(), s) || contains(m.getPhone(), s)).toList();
    }

    @Transactional(readOnly = true)
    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found."));
    }

    @Transactional
    public Member create(Member in) {
        Member m = new Member();
        apply(m, in, null);
        return memberRepository.save(m);
    }

    @Transactional
    public Member update(Long id, Member in) {
        Member m = get(id);
        apply(m, in, id);
        return memberRepository.save(m);
    }

    @Transactional
    public void delete(Long id) {
        Member m = get(id);
        if (loanRepository.existsByMemberId(id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This member has borrowing records and cannot be deleted.");
        }
        memberRepository.delete(m);
    }

    private void apply(Member target, Member in, Long selfId) {
        if (in == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Member details are required.");
        }
        String name = clean(in.getName());
        String membershipId = clean(in.getMembershipId());
        String email = clean(in.getEmail());
        String phone = clean(in.getPhone());
        if (name == null || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Member name is required (max 100 characters).");
        }
        if (membershipId == null || membershipId.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Member ID is required (max 30 characters).");
        }
        if (email != null && (email.length() > 120 || !EMAIL.matcher(email).matches())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (phone != null) {
            phone = phone.replaceAll("[\\s-]", "");
            if (!PHONE.matcher(phone).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Phone must be 7 to 15 digits (a leading + is allowed).");
            }
        }
        boolean taken = memberRepository.findByMembershipIdIgnoreCase(membershipId)
                .filter(other -> !other.getId().equals(selfId)).isPresent();
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "A member with this ID already exists.");
        }
        target.setName(name);
        target.setMembershipId(membershipId);
        target.setEmail(email);
        target.setPhone(phone);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }
}
