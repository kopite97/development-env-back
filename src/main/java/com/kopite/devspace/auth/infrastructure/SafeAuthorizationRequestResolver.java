package com.kopite.devspace.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SafeAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String RETURN_TO_SESSION_ATTRIBUTE =
            SafeAuthorizationRequestResolver.class.getName() + ".returnTo";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final SafeReturnToPolicy safeReturnToPolicy;

    private DefaultOAuth2AuthorizationRequestResolver delegate;

    @jakarta.annotation.PostConstruct
    void initialize() {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return resolveAndRemember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return resolveAndRemember(request, delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest resolveAndRemember(
            HttpServletRequest request,
            OAuth2AuthorizationRequest authorizationRequest
    ) {
        if (authorizationRequest != null) {
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    RETURN_TO_SESSION_ATTRIBUTE,
                    safeReturnToPolicy.normalize(request.getParameter("returnTo"))
            );
        }
        return authorizationRequest;
    }
}
