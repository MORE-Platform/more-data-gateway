package io.redlink.more.data.configuration;

import io.redlink.more.data.model.GatewayUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationFacade {

    /**
     * Retrieve the current authentication context.
     * @return the {@link Authentication authentication context}.
     */
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Asserts that hte current authentication context contains the provided authority ("role").
     * @param authority the required authority
     * @return the authentication principal as {@link GatewayUserDetails}.
     * @throws IllegalArgumentException if authority is {@code null}
     * @throws AccessDeniedException if not authentication context is available or the required authority is not present.
     */
    public GatewayUserDetails assertAuthority(String authority) {
        if (authority == null) throw new IllegalArgumentException("authority must not be null");

        final Authentication authentication = getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals)) {
            final Object principal = authentication.getPrincipal();
            if (principal instanceof GatewayUserDetails userDetails) {
                return userDetails;
            }
        }
        throw new AccessDeniedException(String.format("Authority '%s' required", authority));
    }
}
