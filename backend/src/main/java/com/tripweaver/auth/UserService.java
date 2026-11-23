package com.tripweaver.auth;

import com.tripweaver.common.ApiException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Username + password accounts (D6). Passwords are stored as BCrypt hashes only. */
@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_.-]{3,40}");
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Compared against when the username is unknown, so both failures take the same time. */
    private final String dummyHash = encoder.encode("not-a-real-password");

    public UserService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public AppUser register(String rawUsername, String password, String displayName) {
        String username = normalise(rawUsername);
        if (!USERNAME.matcher(username).matches())
            throw ApiException.badRequest(
                    "BAD_USERNAME", "Usernames are 3–40 characters: letters, digits, dot, dash or underscore");
        if (password == null || password.length() < 8 || password.length() > 100)
            throw ApiException.badRequest("BAD_PASSWORD", "Passwords are 8–100 characters");
        String name = displayName == null || displayName.isBlank() ? null : displayName.trim();
        if (name != null && name.length() > 80) throw ApiException.badRequest("BAD_NAME", "Display name is too long");
        if (find(username) != null)
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is taken");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, username, password_hash, display_name, created_at) VALUES (?, ?, ?, ?, ?)",
                id,
                username,
                encoder.encode(password),
                name,
                OffsetDateTime.now(ZoneOffset.UTC));
        return new AppUser(id, username, name);
    }

    /** The user when the password matches; 401 BAD_CREDENTIALS otherwise (same answer for unknown users). */
    public AppUser authenticate(String rawUsername, String password) {
        String username = normalise(rawUsername);
        List<String[]> rows = jdbc.query(
                "SELECT id, password_hash, display_name FROM users WHERE username = ?",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)},
                username);
        String hash = rows.isEmpty() ? dummyHash : rows.getFirst()[1];
        boolean ok = encoder.matches(password == null ? "" : password, hash);
        if (rows.isEmpty() || !ok)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Wrong username or password");
        return new AppUser(UUID.fromString(rows.getFirst()[0]), username, rows.getFirst()[2]);
    }

    /** null when unknown. */
    public AppUser find(String rawUsername) {
        return jdbc
                .query(
                        "SELECT id, username, display_name FROM users WHERE username = ?",
                        (rs, i) -> new AppUser(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                        normalise(rawUsername))
                .stream()
                .findFirst()
                .orElse(null);
    }
}
