package com.tripweaver.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session-cookie sign-in (D6). The cookie is HttpOnly and SameSite=Lax. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record Credentials(String username, String password, String displayName) {}

    private final UserService users;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<AppUser> register(
            @RequestBody Credentials c, HttpServletRequest req, HttpServletResponse res) {
        AppUser u = users.register(c.username(), c.password(), c.displayName());
        signIn(u, req, res);
        return ResponseEntity.status(HttpStatus.CREATED).body(u);
    }

    @PostMapping("/login")
    public AppUser login(@RequestBody Credentials c, HttpServletRequest req, HttpServletResponse res) {
        AppUser u = users.authenticate(c.username(), c.password());
        signIn(u, req, res);
        return u;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AppUser me() {
        return AppUser.current();
    }

    private void signIn(AppUser u, HttpServletRequest req, HttpServletResponse res) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                u, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(ctx);
        req.getSession(true);
        req.changeSessionId(); // no session fixation
        contexts.saveContext(ctx, req, res);
    }
}
