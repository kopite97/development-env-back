package com.kopite.devspace.auth.infrastructure.oidc;
import com.kopite.devspace.auth.infrastructure.security.SecurityJsonResponseWriter;

import com.kopite.devspace.auth.application.InternalUserPrincipal;
import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserWorkspaceCreationService userWorkspaceCreationService;
    private final SafeReturnToPolicy safeReturnToPolicy;
    private final HttpSessionSecurityContextRepository securityContextRepository;

    @Value("${server.servlet.session.cookie.name:JSESSIONID}")
    private String sessionCookieName;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            failAuthentication(request, response);
            return;
        }

        URL issuer = oidcUser.getIssuer();
        String subject = oidcUser.getSubject();
        if (issuer == null || subject == null || subject.isBlank()) {
            failAuthentication(request, response);
            return;
        }

        UserWorkspaceCreationResult result;
        try {
            result = userWorkspaceCreationService.createOrReuse(
                    issuer.toString(),
                    subject,
                    displayName(oidcUser)
            );
        } catch (RuntimeException exception) {
            failAuthentication(request, response);
            return;
        }

        if (result.user().getDisabledAt() != null) {
            clearSession(request, response);
            SecurityJsonResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "The user account is disabled."
            );
            return;
        }

        InternalUserPrincipal principal = new InternalUserPrincipal(
                result.user().getId(),
                result.user().getDisplayName()
        );
        Authentication internalAuthentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        String returnTo = takeReturnTo(request);
        response.sendRedirect(safeReturnToPolicy.normalize(returnTo));
    }

    private String displayName(OidcUser user) {
        String fullName = nonBlank(user.getFullName());
        if (fullName != null) {
            return fullName;
        }
        String preferredUsername = nonBlank(user.getPreferredUsername());
        if (preferredUsername != null) {
            return preferredUsername;
        }
        String email = nonBlank(user.getEmail());
        if (email != null) {
            return email;
        }
        return "Google User";
    }

    private String nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String takeReturnTo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "/";
        }
        Object value = session.getAttribute(SafeAuthorizationRequestResolver.RETURN_TO_SESSION_ATTRIBUTE);
        session.removeAttribute(SafeAuthorizationRequestResolver.RETURN_TO_SESSION_ATTRIBUTE);
        return value instanceof String string ? string : "/";
    }

    private void failAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException {
        clearSession(request, response);
        response.sendRedirect("/?authError=login_failed");
    }

    private void clearSession(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, current);
        new CookieClearingLogoutHandler(sessionCookieName).logout(request, response, current);
        SecurityContextHolder.clearContext();
    }
}
