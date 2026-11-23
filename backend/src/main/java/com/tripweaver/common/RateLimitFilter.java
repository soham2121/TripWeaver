package com.tripweaver.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Per-client limit on public share links and sign-in (plan 8.2): at most N requests per minute per IP address on each
 * of those endpoints. Over the limit → 429 RATE_LIMITED.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // ponytail: in-memory fixed window per JVM; a shared store (e.g. Redis) if the API ever runs on several instances.
    private record Window(long minute, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;
    private final JsonMapper json;

    public RateLimitFilter(@Value("${tripweaver.rate-limit.per-minute:60}") int perMinute, JsonMapper json) {
        this.perMinute = perMinute;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !(p.startsWith("/api/v1/shared/") || p.equals("/api/v1/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long minute = System.currentTimeMillis() / 60_000;
        String key = (req.getRequestURI().startsWith("/api/v1/shared/") ? "shared:" : "login:") + req.getRemoteAddr();
        Window w = windows.merge(
                key,
                new Window(minute, 1),
                (old, n) -> old.minute() == minute ? new Window(minute, old.count() + 1) : n);
        if (windows.size() > 10_000) windows.values().removeIf(x -> x.minute() < minute);
        if (w.count() > perMinute) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(
                    res.getOutputStream(),
                    new ApiError("RATE_LIMITED", "Too many requests; try again in a minute", Map.of()));
            return;
        }
        chain.doFilter(req, res);
    }
}
