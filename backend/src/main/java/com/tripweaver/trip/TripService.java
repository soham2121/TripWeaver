package com.tripweaver.trip;

import com.tripweaver.auth.AppUser;
import com.tripweaver.auth.UserService;
import com.tripweaver.common.ApiException;
import com.tripweaver.planning.ItineraryRequest;
import com.tripweaver.planning.ItineraryService;
import com.tripweaver.roadtrip.RoadTripRequest;
import com.tripweaver.roadtrip.RoadTripService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saved trips and collaboration (plan section 8). Every operation checks the caller's role on the trip here, on the
 * backend. Non-members get 404 so trip ids reveal nothing. Edits carry the version they were based on; a stale version
 * gets 409 VERSION_CONFLICT and nothing is overwritten.
 */
@Service
public class TripService {

    public enum Role {
        VIEWER,
        EDITOR,
        OWNER;

        boolean atLeast(Role r) {
            return compareTo(r) >= 0;
        }
    }

    public enum Kind {
        ITINERARY,
        ROAD_TRIP
    }

    public record UserRef(UUID id, String username, String displayName) {}

    public record Member(UUID userId, String username, String displayName, Role role) {}

    public record TripView(
            UUID id,
            String title,
            Kind kind,
            Role role,
            UserRef owner,
            List<Member> members,
            int version,
            JsonNode request,
            JsonNode result,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record TripSummary(
            UUID id, String title, Kind kind, Role role, String ownerUsername, int version, OffsetDateTime updatedAt) {}

    public record SharedTrip(String title, Kind kind, JsonNode result, OffsetDateTime updatedAt) {}

    public record ShareLink(
            UUID id,
            String token,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            boolean active) {}

    public record Suggestion(
            UUID id,
            UUID placeId,
            String placeName,
            Integer day,
            String note,
            String suggestedBy,
            OffsetDateTime createdAt,
            int up,
            int down,
            int score,
            int myVote) {}

    public record History(String action, String summary, String username, int version, OffsetDateTime at) {}

    public record Expense(
            UUID id, String description, double amount, UUID paidById, String paidBy, OffsetDateTime createdAt) {}

    public record Balance(UUID userId, String username, double paid, double share, double balance) {}

    public record Expenses(List<Expense> items, double total, double perPerson, List<Balance> balances) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final UserService users;
    private final ItineraryService itineraries;
    private final RoadTripService roadTrips;

    public TripService(
            JdbcTemplate jdbc,
            JsonMapper json,
            UserService users,
            ItineraryService itineraries,
            RoadTripService roadTrips) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.itineraries = itineraries;
        this.roadTrips = roadTrips;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------ access

    /** The caller's role on an active trip. 404 when the trip does not exist or the caller is not on it. */
    Role roleOf(UUID trip, UUID user) {
        List<Role> r = jdbc.query(
                """
                SELECT CASE WHEN t.owner_id = ? THEN 'OWNER' ELSE m.role END
                FROM trips t LEFT JOIN trip_members m ON m.trip_id = t.id AND m.user_id = ?
                WHERE t.id = ? AND NOT t.archived
                """, (rs, i) -> rs.getString(1) == null ? null : Role.valueOf(rs.getString(1)), user, user, trip);
        if (r.isEmpty() || r.getFirst() == null) throw ApiException.notFound("Trip", trip);
        return r.getFirst();
    }

    private Role require(UUID trip, AppUser u, Role min) {
        Role r = roleOf(trip, u.id());
        if (!r.atLeast(min)) throw ApiException.forbidden();
        return r;
    }

    private int version(UUID trip) {
        return jdbc.queryForObject("SELECT version FROM trips WHERE id = ?", Integer.class, trip);
    }

    private void history(UUID trip, AppUser u, String action, String summary) {
        jdbc.update(
                "INSERT INTO trip_change_history (trip_id, user_id, action, summary, version, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                trip,
                u == null ? null : u.id(),
                action,
                summary.length() > 500 ? summary.substring(0, 500) : summary,
                version(trip),
                now());
    }

    // ------------------------------------------------------------------ trips

    @Transactional
    public TripView create(AppUser u, String title, Kind kind, JsonNode request, JsonNode result) {
        if (kind == null) throw ApiException.badRequest("BAD_KIND", "kind is ITINERARY or ROAD_TRIP");
        if (request == null || !request.isObject())
            throw ApiException.badRequest("BAD_REQUEST_JSON", "request must be the planner input object");
        UUID id = UUID.randomUUID();
        OffsetDateTime t = now();
        jdbc.update(
                "INSERT INTO trips (id, owner_id, title, kind, request_json, result_json, version, archived,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, FALSE, ?, ?)",
                id,
                u.id(),
                title(title),
                kind.name(),
                json.writeValueAsString(request),
                result == null || result.isNull() ? null : json.writeValueAsString(result),
                t,
                t);
        history(id, u, "CREATED", "Created \"" + title(title) + "\"");
        return get(u, id);
    }

    private static String title(String t) {
        if (t == null || t.isBlank()) return "Untitled trip";
        String s = t.trim();
        if (s.length() > 120) throw ApiException.badRequest("BAD_TITLE", "Titles are at most 120 characters");
        return s;
    }

    public List<TripSummary> list(AppUser u) {
        return jdbc.query(
                """
                SELECT t.id, t.title, t.kind, CASE WHEN t.owner_id = ? THEN 'OWNER' ELSE m.role END, o.username,
                       t.version, t.updated_at
                FROM trips t
                JOIN users o ON o.id = t.owner_id
                LEFT JOIN trip_members m ON m.trip_id = t.id AND m.user_id = ?
                WHERE NOT t.archived AND (t.owner_id = ? OR m.user_id IS NOT NULL)
                ORDER BY t.updated_at DESC
                """,
                (rs, i) -> new TripSummary(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        Kind.valueOf(rs.getString(3)),
                        Role.valueOf(rs.getString(4)),
                        rs.getString(5),
                        rs.getInt(6),
                        rs.getObject(7, OffsetDateTime.class)),
                u.id(),
                u.id(),
                u.id());
    }

    public TripView get(AppUser u, UUID id) {
        Role role = roleOf(id, u.id());
        return jdbc.queryForObject(
                """
                SELECT t.title, t.kind, t.version, t.request_json, t.result_json, t.created_at, t.updated_at,
                       o.id, o.username, o.display_name
                FROM trips t JOIN users o ON o.id = t.owner_id WHERE t.id = ?
                """,
                (rs, i) -> new TripView(
                        id,
                        rs.getString(1),
                        Kind.valueOf(rs.getString(2)),
                        role,
                        new UserRef(rs.getObject(8, UUID.class), rs.getString(9), rs.getString(10)),
                        members(id),
                        rs.getInt(3),
                        json.readTree(rs.getString(4)),
                        rs.getString(5) == null ? null : json.readTree(rs.getString(5)),
                        rs.getObject(6, OffsetDateTime.class),
                        rs.getObject(7, OffsetDateTime.class)),
                id);
    }

    private List<Member> members(UUID trip) {
        return jdbc.query(
                """
                SELECT u.id, u.username, u.display_name, m.role FROM trip_members m JOIN users u ON u.id = m.user_id
                WHERE m.trip_id = ? ORDER BY u.username
                """,
                (rs, i) -> new Member(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), Role.valueOf(rs.getString(4))),
                trip);
    }

    /** Owner renames; editors and owner change the planner input or result. Needs the version the edit is based on. */
    @Transactional
    public TripView update(AppUser u, UUID id, Integer version, String title, JsonNode request, JsonNode result) {
        boolean plan = request != null || result != null;
        Role role = require(id, u, plan ? Role.EDITOR : Role.VIEWER);
        if (title != null && role != Role.OWNER) throw ApiException.forbidden();
        if (title == null && !plan)
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Send a title, request or result");
        if (request != null && !request.isObject())
            throw ApiException.badRequest("BAD_REQUEST_JSON", "request must be the planner input object");
        bump(id, version, title == null ? null : title(title), request, result);
        if (plan) history(id, u, "ITINERARY_UPDATED", "Updated the itinerary");
        if (title != null) history(id, u, "RENAMED", "Renamed the trip to \"" + title(title) + "\"");
        return get(u, id);
    }

    /** Re-runs the planner on the saved (or given) input and saves input and result together. */
    @Transactional
    public TripView generate(AppUser u, UUID id, Integer version, JsonNode request) {
        require(id, u, Role.EDITOR);
        if (version == null) throw versionRequired();
        String kind = jdbc.queryForObject("SELECT kind FROM trips WHERE id = ?", String.class, id);
        JsonNode req = request != null
                ? request
                : json.readTree(jdbc.queryForObject("SELECT request_json FROM trips WHERE id = ?", String.class, id));
        if (!req.isObject()) throw ApiException.badRequest("BAD_REQUEST_JSON", "request must be an object");
        checkVersion(id, version); // fail fast before planning
        JsonNode result;
        try {
            result = Kind.valueOf(kind) == Kind.ITINERARY
                    ? json.valueToTree(itineraries
                            .generate(json.treeToValue(req, ItineraryRequest.class))
                            .itinerary())
                    : json.valueToTree(roadTrips
                            .plan(json.treeToValue(req, RoadTripRequest.class))
                            .roadTrip());
        } catch (JacksonException e) {
            throw ApiException.badRequest("BAD_REQUEST_JSON", "The saved planner input is not valid");
        }
        bump(id, version, null, req, result);
        history(id, u, "REPLANNED", "Generated a new plan");
        return get(u, id);
    }

    private static ApiException versionRequired() {
        return ApiException.badRequest("VERSION_REQUIRED", "Send the version your edit is based on");
    }

    private void checkVersion(UUID id, int version) {
        int current = version(id);
        if (current != version) throw conflict(current);
    }

    private static ApiException conflict(int current) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "VERSION_CONFLICT",
                "Someone else changed this trip. Reload it and apply your change again.",
                Map.of("currentVersion", current));
    }

    /** Compare-and-set on the version column: a stale version updates nothing and gets 409. */
    private void bump(UUID id, Integer version, String title, JsonNode request, JsonNode result) {
        if (version == null) throw versionRequired();
        int n = jdbc.update(
                """
                UPDATE trips SET title = COALESCE(?, title), request_json = COALESCE(?, request_json),
                       result_json = COALESCE(?, result_json), version = version + 1, updated_at = ?
                WHERE id = ? AND version = ?
                """,
                title,
                request == null ? null : json.writeValueAsString(request),
                result == null ? null : json.writeValueAsString(result),
                now(),
                id,
                version);
        if (n == 0) throw conflict(version(id));
    }

    @Transactional
    public void archive(AppUser u, UUID id) {
        require(id, u, Role.OWNER);
        jdbc.update("UPDATE trips SET archived = TRUE, updated_at = ? WHERE id = ?", now(), id);
        history(id, u, "ARCHIVED", "Deleted the trip");
    }

    // ------------------------------------------------------------------ members

    private static Role memberRole(String role) {
        if (!"EDITOR".equals(role) && !"VIEWER".equals(role))
            throw ApiException.badRequest("BAD_ROLE", "role is EDITOR or VIEWER");
        return Role.valueOf(role);
    }

    @Transactional
    public Member addMember(AppUser u, UUID trip, String username, String role) {
        require(trip, u, Role.OWNER);
        Role r = memberRole(role);
        AppUser m = users.find(username);
        if (m == null) throw ApiException.notFound("User", UserService.normalise(username));
        if (m.id().equals(u.id()))
            throw ApiException.badRequest("OWNER_IS_MEMBER", "The owner already has full access");
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trip_members WHERE trip_id = ? AND user_id = ?", Integer.class, trip, m.id());
        if (exists != null && exists > 0)
            throw new ApiException(
                    HttpStatus.CONFLICT, "ALREADY_MEMBER", "Already on this trip; change the role instead");
        jdbc.update(
                "INSERT INTO trip_members (trip_id, user_id, role, added_at) VALUES (?, ?, ?, ?)",
                trip,
                m.id(),
                r.name(),
                now());
        history(
                trip,
                u,
                "MEMBER_ADDED",
                "Added " + m.username() + " as " + r.name().toLowerCase());
        return new Member(m.id(), m.username(), m.displayName(), r);
    }

    @Transactional
    public void changeRole(AppUser u, UUID trip, UUID member, String role) {
        require(trip, u, Role.OWNER);
        Role r = memberRole(role);
        int n = jdbc.update(
                "UPDATE trip_members SET role = ? WHERE trip_id = ? AND user_id = ?", r.name(), trip, member);
        if (n == 0) throw ApiException.notFound("Member", member);
        history(
                trip,
                u,
                "MEMBER_ROLE_CHANGED",
                "Changed a member to " + r.name().toLowerCase());
    }

    @Transactional
    public void removeMember(AppUser u, UUID trip, UUID member) {
        require(trip, u, Role.OWNER);
        int n = jdbc.update("DELETE FROM trip_members WHERE trip_id = ? AND user_id = ?", trip, member);
        if (n == 0) throw ApiException.notFound("Member", member);
        history(trip, u, "MEMBER_REMOVED", "Removed a member");
    }

    // ------------------------------------------------------------------ share links

    static String sha256(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 256-bit random token, returned once; only its SHA-256 is stored. */
    @Transactional
    public ShareLink createShareLink(AppUser u, UUID trip, Integer expiresInHours) {
        require(trip, u, Role.OWNER);
        if (expiresInHours != null && (expiresInHours < 1 || expiresInHours > 24 * 365))
            throw ApiException.badRequest("BAD_EXPIRY", "expiresInHours is 1..8760");
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        UUID id = UUID.randomUUID();
        OffsetDateTime t = now();
        OffsetDateTime expires = expiresInHours == null ? null : t.plusHours(expiresInHours);
        jdbc.update(
                "INSERT INTO trip_share_links (id, trip_id, token_hash, created_by, created_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                trip,
                sha256(token),
                u.id(),
                t,
                expires);
        history(trip, u, "SHARE_CREATED", "Created a read-only link" + (expires == null ? "" : " (expires)"));
        return new ShareLink(id, token, t, expires, null, true);
    }

    public List<ShareLink> shareLinks(AppUser u, UUID trip) {
        require(trip, u, Role.OWNER);
        OffsetDateTime t = now();
        return jdbc.query(
                "SELECT id, created_at, expires_at, revoked_at FROM trip_share_links WHERE trip_id = ?"
                        + " ORDER BY created_at DESC",
                (rs, i) -> {
                    OffsetDateTime exp = rs.getObject(3, OffsetDateTime.class);
                    OffsetDateTime rev = rs.getObject(4, OffsetDateTime.class);
                    return new ShareLink(
                            rs.getObject(1, UUID.class),
                            null,
                            rs.getObject(2, OffsetDateTime.class),
                            exp,
                            rev,
                            rev == null && (exp == null || exp.isAfter(t)));
                },
                trip);
    }

    @Transactional
    public void revokeShareLink(AppUser u, UUID trip, UUID link) {
        require(trip, u, Role.OWNER);
        int n = jdbc.update(
                "UPDATE trip_share_links SET revoked_at = ? WHERE id = ? AND trip_id = ? AND revoked_at IS NULL",
                now(),
                link,
                trip);
        if (n == 0) throw ApiException.notFound("Share link", link);
        history(trip, u, "SHARE_REVOKED", "Revoked a read-only link");
    }

    /** Public, read-only. Unknown, revoked and expired tokens all get the same 404. */
    public SharedTrip shared(String token) {
        List<SharedTrip> r = jdbc.query(
                """
                SELECT t.title, t.kind, t.result_json, t.updated_at
                FROM trip_share_links l JOIN trips t ON t.id = l.trip_id
                WHERE l.token_hash = ? AND l.revoked_at IS NULL AND (l.expires_at IS NULL OR l.expires_at > ?)
                  AND NOT t.archived
                """,
                (rs, i) -> new SharedTrip(
                        rs.getString(1),
                        Kind.valueOf(rs.getString(2)),
                        rs.getString(3) == null ? null : json.readTree(rs.getString(3)),
                        rs.getObject(4, OffsetDateTime.class)),
                sha256(token == null ? "" : token),
                now());
        if (r.isEmpty())
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "SHARE_LINK_INVALID", "This link is invalid, revoked or expired");
        return r.getFirst();
    }

    // ------------------------------------------------------------------ suggestions and votes

    @Transactional
    public Suggestion suggest(AppUser u, UUID trip, UUID placeId, Integer day, String note) {
        require(trip, u, Role.EDITOR);
        if (placeId == null) throw ApiException.badRequest("BAD_PLACE", "placeId is required");
        List<String> name =
                jdbc.queryForList("SELECT name FROM places WHERE id = ? AND is_active", String.class, placeId);
        if (name.isEmpty()) throw ApiException.notFound("Place", placeId);
        if (day != null && (day < 1 || day > 30)) throw ApiException.badRequest("BAD_DAY", "day is 1..30");
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > 500)
            throw ApiException.badRequest("BAD_NOTE", "Notes are at most 500 characters");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO activity_suggestions (id, trip_id, place_id, day_number, note, suggested_by, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                trip,
                placeId,
                day,
                n,
                u.id(),
                now());
        history(trip, u, "SUGGESTED", "Suggested " + name.getFirst());
        return suggestions(u, trip).stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    public List<Suggestion> suggestions(AppUser u, UUID trip) {
        require(trip, u, Role.VIEWER);
        return jdbc.query(
                """
                SELECT s.id, s.place_id, p.name, s.day_number, s.note, su.username, s.created_at,
                       COALESCE(SUM(CASE WHEN v.vote = 1 THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN v.vote = -1 THEN 1 ELSE 0 END), 0),
                       COALESCE(MAX(CASE WHEN v.user_id = ? THEN v.vote END), 0)
                FROM activity_suggestions s
                JOIN places p ON p.id = s.place_id
                JOIN users su ON su.id = s.suggested_by
                LEFT JOIN activity_votes v ON v.suggestion_id = s.id
                WHERE s.trip_id = ?
                GROUP BY s.id, s.place_id, p.name, s.day_number, s.note, su.username, s.created_at
                ORDER BY COALESCE(SUM(v.vote), 0) DESC, s.created_at
                """,
                (rs, i) -> new Suggestion(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        (Integer) rs.getObject(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getObject(7, OffsetDateTime.class),
                        rs.getInt(8),
                        rs.getInt(9),
                        rs.getInt(8) - rs.getInt(9),
                        rs.getInt(10)),
                u.id(),
                trip);
    }

    /**
     * One vote per person per suggestion. The same vote again is rejected (409 DUPLICATE_VOTE); the other value
     * changes the vote. Returns true when a new vote was created.
     */
    @Transactional
    public boolean vote(AppUser u, UUID trip, UUID suggestion, Integer value) {
        require(trip, u, Role.EDITOR);
        if (value == null || (value != 1 && value != -1)) throw ApiException.badRequest("BAD_VOTE", "value is 1 or -1");
        Integer belongs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_suggestions WHERE id = ? AND trip_id = ?",
                Integer.class,
                suggestion,
                trip);
        if (belongs == null || belongs == 0) throw ApiException.notFound("Suggestion", suggestion);
        List<Integer> existing = jdbc.queryForList(
                "SELECT vote FROM activity_votes WHERE suggestion_id = ? AND user_id = ?",
                Integer.class,
                suggestion,
                u.id());
        if (!existing.isEmpty() && existing.getFirst().equals(value))
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_VOTE", "You already voted this way");
        if (existing.isEmpty()) {
            jdbc.update(
                    "INSERT INTO activity_votes (suggestion_id, user_id, vote, voted_at) VALUES (?, ?, ?, ?)",
                    suggestion,
                    u.id(),
                    value,
                    now());
            return true;
        }
        jdbc.update(
                "UPDATE activity_votes SET vote = ?, voted_at = ? WHERE suggestion_id = ? AND user_id = ?",
                value,
                now(),
                suggestion,
                u.id());
        return false;
    }

    // ------------------------------------------------------------------ history

    public List<History> history(AppUser u, UUID trip) {
        require(trip, u, Role.VIEWER);
        return jdbc.query(
                """
                SELECT h.action, h.summary, u.username, h.version, h.created_at
                FROM trip_change_history h LEFT JOIN users u ON u.id = h.user_id
                WHERE h.trip_id = ? ORDER BY h.id DESC LIMIT 200
                """,
                (rs, i) -> new History(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getObject(5, OffsetDateTime.class)),
                trip);
    }

    // ------------------------------------------------------------------ expenses

    private List<UserRef> participants(UUID trip) {
        List<UserRef> p = new ArrayList<>(jdbc.query(
                "SELECT u.id, u.username, u.display_name FROM trips t JOIN users u ON u.id = t.owner_id WHERE t.id = ?",
                (rs, i) -> new UserRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                trip));
        members(trip).forEach(m -> p.add(new UserRef(m.userId(), m.username(), m.displayName())));
        return p;
    }

    @Transactional
    public Expense addExpense(AppUser u, UUID trip, String description, Double amount, UUID paidBy) {
        require(trip, u, Role.EDITOR);
        if (description == null || description.isBlank() || description.trim().length() > 200)
            throw ApiException.badRequest("BAD_DESCRIPTION", "Describe the expense in 1–200 characters");
        if (amount == null || !(amount > 0) || amount > 1e9)
            throw ApiException.badRequest("BAD_AMOUNT", "amount must be a positive number of rupees");
        UUID payer = paidBy == null ? u.id() : paidBy;
        UserRef p = participants(trip).stream()
                .filter(x -> x.id().equals(payer))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("BAD_PAYER", "The payer must be on this trip"));
        UUID id = UUID.randomUUID();
        OffsetDateTime t = now();
        double rounded = Math.round(amount * 100) / 100.0;
        jdbc.update(
                "INSERT INTO trip_expenses (id, trip_id, paid_by, description, amount, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                trip,
                payer,
                description.trim(),
                rounded,
                t);
        history(
                trip,
                u,
                "EXPENSE_ADDED",
                "Added expense \"" + description.trim() + "\" (₹"
                        + java.math.BigDecimal.valueOf(rounded)
                                .stripTrailingZeros()
                                .toPlainString() + ")");
        return new Expense(id, description.trim(), rounded, payer, p.username(), t);
    }

    /** Equal split among everyone on the trip. balance &gt; 0: others owe this person. */
    public Expenses expenses(AppUser u, UUID trip) {
        require(trip, u, Role.VIEWER);
        List<Expense> items = jdbc.query(
                """
                SELECT e.id, e.description, e.amount, e.paid_by, u.username, e.created_at
                FROM trip_expenses e JOIN users u ON u.id = e.paid_by
                WHERE e.trip_id = ? ORDER BY e.created_at, e.id
                """,
                (rs, i) -> new Expense(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getDouble(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5),
                        rs.getObject(6, OffsetDateTime.class)),
                trip);
        List<UserRef> people = participants(trip);
        double total = items.stream().mapToDouble(Expense::amount).sum();
        double share = people.isEmpty() ? 0 : total / people.size();
        Map<UUID, Double> paid = new LinkedHashMap<>();
        items.forEach(e -> paid.merge(e.paidById(), e.amount(), Double::sum));
        Set<UUID> seen = new java.util.HashSet<>();
        List<Balance> balances = new ArrayList<>();
        for (UserRef p : people) {
            if (!seen.add(p.id())) continue;
            double pd = paid.getOrDefault(p.id(), 0.0);
            balances.add(new Balance(p.id(), p.username(), round(pd), round(share), round(pd - share)));
        }
        return new Expenses(items, round(total), round(share), balances);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
