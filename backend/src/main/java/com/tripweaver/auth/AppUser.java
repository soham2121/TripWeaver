package com.tripweaver.auth;

import com.tripweaver.common.ApiException;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user, kept in the session's security context. Never carries the password hash. */
public record AppUser(UUID id, String username, String displayName) implements Serializable {

    /** The current user, or 401 when nobody is signed in. */
    public static AppUser current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUser u) return u;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in first");
    }
}
