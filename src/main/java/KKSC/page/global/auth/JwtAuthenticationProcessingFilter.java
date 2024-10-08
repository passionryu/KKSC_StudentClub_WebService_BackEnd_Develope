package KKSC.page.global.auth;

import KKSC.page.domain.member.entity.Member;
import KKSC.page.domain.member.entity.MemberDetails;
import KKSC.page.domain.member.repository.MemberRepository;
import KKSC.page.global.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 서블릿 컨테이너에서 요청 디스패치당 단일 실행을 보장하는 것을 목표로 하는 필터 기본 클래스입니다.
 * 출처: https://ttl-blog.tistory.com/273#AccessToken을 통한 인증 Filter 추가-1 [Shin._.Mallang:티스토리]
 *
 * 설계 로직
 * 1. AccessToken O, RefreshToken O -> 로그인 성공, AccessToken 재발급
 * 1. AccessToken X, RefreshToken O -> 로그인 성공, AccessToken 재발급
 * 1. AccessToken O, RefreshToken X -> 로그인 성공, AccessToken 재발급 X
 * 1. AccessToken X, RefreshToken X -> 로그인 실패, 403 UnAuthorized
 */
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationProcessingFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    private GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();

    private final String NO_CHECK_URL = "/member/login";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().equals(NO_CHECK_URL)) { // login으로 오는 경우 필터 통과
            filterChain.doFilter(request, response);
            return;
        }
        log.info("JwtAuthenticationProcessingFilter.doFilterInternal 진입");
        log.info("request URL = {}", request.getRequestURL());
        log.info("request Authorization = {}", request.getHeader("Authorization"));

        // request 에서 refreshToken 추출
        String refreshToken = jwtService.extractRefreshToken(request).filter(jwtService::isValid).orElse(null);

        // refreshToken 존재한다면 새 accessToken 발급
        if (refreshToken != null) {
            memberRepository.findByRefreshToken(refreshToken)
                    .ifPresent(member -> jwtService.sendAccessToken(response, jwtService.createAccessToken(member.getEmail())));
            filterChain.doFilter(request, response);
            return;
        }

        jwtService.extractAccessToken(request).filter(jwtService::isValid)
                .flatMap(accessToken -> jwtService.extractUsername(request)
                        .flatMap(memberRepository::findByEmail))
                .ifPresent(this::saveAuthentication);

        filterChain.doFilter(request, response);
    }

    private void saveAuthentication(Member member) {
        MemberDetails memberDetails = new MemberDetails(member);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                memberDetails, null, authoritiesMapper.mapAuthorities(memberDetails.getAuthorities()));

        log.info("Authentication = {} ", authentication);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
