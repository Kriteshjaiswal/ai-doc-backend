package com.aidocqa.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Check if API Gateway forwarded X-User-Id / X-User-Email
        String gatewayUserId = request.getHeader("X-User-Id");
        String gatewayUserEmail = request.getHeader("X-User-Email");
        String gatewayUserName = request.getHeader("X-User-Name");
        String gatewayUserRole = request.getHeader("X-User-Role");

        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            try {
                Long uid = Long.parseLong(gatewayUserId);
                String email = (gatewayUserEmail != null && !gatewayUserEmail.isBlank())
                        ? gatewayUserEmail
                        : ("user_" + uid + "@aidoc.local");
                String name = (gatewayUserName != null && !gatewayUserName.isBlank())
                        ? gatewayUserName
                        : email.split("@")[0];
                String role = (gatewayUserRole != null && !gatewayUserRole.isBlank())
                        ? gatewayUserRole
                        : "USER";

                UserPrincipal principal = UserPrincipal.builder()
                        .id(uid)
                        .email(email)
                        .fullName(name)
                        .role(role)
                        .build();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                filterChain.doFilter(request, response);
                return;
            } catch (Exception e) {
                log.debug("Gateway header authentication parse error: {}", e.getMessage());
            }
        }

        // 2. Otherwise authenticate via JWT token header or query parameter
        String jwt = null;
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                jwt = tokenParam;
            }
        }

        if (jwt != null && !jwt.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtService.isTokenValid(jwt)) {
                    Claims claims = jwtService.extractAllClaims(jwt);
                    String email = claims.getSubject();
                    Long userId = null;

                    Object uidClaim = claims.get("userId");
                    if (uidClaim instanceof Number num) {
                        userId = num.longValue();
                    } else if (uidClaim instanceof String str && !str.isBlank()) {
                        try {
                            userId = Long.parseLong(str);
                        } catch (NumberFormatException ignored) {}
                    }

                    String fullName = claims.get("fullName", String.class);
                    String role = claims.get("role", String.class);

                    UserPrincipal principal = UserPrincipal.builder()
                            .id(userId)
                            .email(email)
                            .fullName(fullName != null ? fullName : (email != null ? email.split("@")[0] : ""))
                            .role(role != null ? role : "USER")
                            .build();

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                log.debug("JWT authentication parse failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
