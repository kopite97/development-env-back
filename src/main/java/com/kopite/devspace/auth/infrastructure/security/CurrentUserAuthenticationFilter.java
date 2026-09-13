package com.kopite.devspace.auth.infrastructure.security;

import com.kopite.devspace.auth.application.AccountDisabledException;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.auth.application.InternalUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.NoSuchElementException;

@Component
@RequiredArgsConstructor
public class CurrentUserAuthenticationFilter extends OncePerRequestFilter {

    private final CurrentUserService currentUserService;

    @Value("${server.servlet.session.cookie.name:JSESSIONID}")
    private String sessionCookieName;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!(authentication.getPrincipal() instanceof InternalUserPrincipal principal)) {
            invalidate(request, response);
            SecurityJsonResponseWriter.write(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Authentication is required."
            );
            return;
        }

        try {
            currentUserService.resolve(principal.userId());
            filterChain.doFilter(request, response);
        } catch (AccountDisabledException exception) {
            invalidate(request, response);
            SecurityJsonResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "The user account is disabled."
            );
        } catch (NoSuchElementException exception) {
            invalidate(request, response);
            SecurityJsonResponseWriter.write(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Authentication is required."
            );
        }
    }

    private void invalidate(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
        securityContextLogoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        new CookieClearingLogoutHandler(sessionCookieName).logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication()
        );
        SecurityContextHolder.clearContext();
    }
}
