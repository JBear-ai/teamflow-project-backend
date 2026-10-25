package com.example.test1.service;

import com.example.test1.dto.LoginReqDto;
import com.example.test1.dto.MemberResDto;
import com.example.test1.dto.SignupReqDto;
import com.example.test1.entity.Member;
import com.example.test1.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    // 회원가입
    @Transactional
    public MemberResDto signup(SignupReqDto dto) {

        String email = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        if (memberRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 가입된 이메일입니다."
            );
        }

        Member member = new Member();

        member.setEmail(email);
        member.setName(dto.getName().trim());

        // 비밀번호 해시 처리
        member.setPassword(
                passwordEncoder.encode(dto.getPassword())
        );

        Member saved = memberRepository.save(member);

        return convertToDto(saved);
    }

    // 로그인
    @Transactional(readOnly = true)
    public MemberResDto login(LoginReqDto dto) {

        String email = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        boolean matched = passwordEncoder.matches(
                dto.getPassword(),
                member.getPassword()
        );

        if (!matched) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "이메일 또는 비밀번호가 올바르지 않습니다."
            );
        }

        return convertToDto(member);
    }

    @Transactional(readOnly = true)
    public MemberResDto currentMember(String email) {
        return convertToDto(memberRepository.findByEmail(email).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }

    private MemberResDto convertToDto(Member member) {

        MemberResDto dto = new MemberResDto();

        dto.setId(member.getId());
        dto.setEmail(member.getEmail());
        dto.setName(member.getName());

        return dto;
    }
}
