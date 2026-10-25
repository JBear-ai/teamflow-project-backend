package com.example.test1.controller;

import com.example.test1.dto.*;
import com.example.test1.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/signup")
    public ResponseEntity<MemberResDto> signup(@Valid @RequestBody SignupReqDto dto) {
        return ResponseEntity.ok(authService.signup(dto));
    }

    @PostMapping("/login")
    public MemberResDto login(@Valid @RequestBody LoginReqDto dto,
            HttpServletRequest request, HttpServletResponse response) {
        MemberResDto member = authService.login(dto);
        request.getSession();
        request.changeSessionId();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            member.getEmail(), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        return member;
    }

    @GetMapping("/me")
    public MemberResDto me(Authentication authentication) {
        return authService.currentMember(authentication.getName());
    }
}
