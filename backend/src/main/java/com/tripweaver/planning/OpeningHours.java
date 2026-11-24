package com.tripweaver.planning;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the common subset of the OSM {@code opening_hours} syntax: {@code 24/7}, rules separated by
 * {@code ;}, weekday ranges and lists ({@code Mo-Fr,Su}, wrap-around {@code Sa-Mo}), time lists
 * ({@code 11:00-14:30, 18:30-23:00}) and {@code off}/{@code closed}. Later rules replace earlier ones for the
 * days they name. {@code PH}/{@code SH} tokens are ignored. A window that closes after midnight runs into the
 * next day (close above 1440), so a plan day that ends after midnight can use it.
 *
 * <p>Anything else (months, weeks, sunrise, open-ended {@code +}, comments) returns {@code null}: the hours are
 * then treated as unknown rather than guessed.
 */
public final class OpeningHours {

    /** Open interval in minutes after midnight, close exclusive. */
    public record Window(int open, int close) {}

    private static final int SMALL_HOURS_END = 6 * 60;
    private static final List<String> DAYS = List.of("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");
    private static final Pattern TIME = Pattern.compile("(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})");

    private final Map<DayOfWeek, List<Window>> byDay;

    private OpeningHours(Map<DayOfWeek, List<Window>> byDay) {
        this.byDay = byDay;
    }

    public List<Window> windows(DayOfWeek d) {
        return byDay.getOrDefault(d, List.of());
    }

    /**
     * Earliest start ≥ {@code from} such that [start, start + minutes] fits in one window on day {@code d}, or
     * -1 if none.
     */
    public int earliestStart(DayOfWeek d, int from, int minutes) {
        // Day d's windows, then the next day's small hours (before 06:00) shifted by 24 h, merged where they touch,
        // so a plan day that ends after midnight can use them.
        List<Window> all = new ArrayList<>(windows(d));
        for (Window w : windows(d.plus(1)))
            if (w.open() < SMALL_HOURS_END)
                all.add(new Window(w.open() + 1440, Math.min(w.close(), SMALL_HOURS_END) + 1440));
        int open = -1, close = -1;
        for (Window w : all) {
            if (w.open() > close) {
                if (open >= 0 && Math.max(from, open) + minutes <= close) return Math.max(from, open);
                open = w.open();
            }
            close = Math.max(close, w.close());
        }
        return open >= 0 && Math.max(from, open) + minutes <= close ? Math.max(from, open) : -1;
    }

    public static OpeningHours parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        Map<DayOfWeek, List<Window>> m = new EnumMap<>(DayOfWeek.class);
        if (s.equals("24/7")) {
            for (DayOfWeek d : DayOfWeek.values()) m.put(d, List.of(new Window(0, 1440)));
            return new OpeningHours(m);
        }
        boolean any = false;
        for (String rule : s.split(";")) {
            rule = rule.trim();
            if (rule.isEmpty()) continue;
            String[] parts = rule.split("\\s+", 2);
            List<DayOfWeek> days;
            String times;
            if (Character.isDigit(parts[0].charAt(0))) {
                days = List.of(DayOfWeek.values());
                times = rule;
            } else {
                days = days(parts[0]);
                if (days == null || parts.length < 2) return null;
                times = parts[1].trim();
            }
            List<Window> ws = times(times);
            if (ws == null) return null;
            if (days.isEmpty()) continue; // e.g. "PH off"
            for (DayOfWeek d : days) m.put(d, ws);
            any = true;
        }
        return any ? new OpeningHours(m) : null;
    }

    private static List<DayOfWeek> days(String spec) {
        List<DayOfWeek> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            if (part.equals("PH") || part.equals("SH")) continue;
            String[] r = part.split("-");
            int a = DAYS.indexOf(r[0]);
            if (a < 0 || r.length > 2) return null;
            int b = r.length == 2 ? DAYS.indexOf(r[1]) : a;
            if (b < 0) return null;
            for (int i = a; ; i = (i + 1) % 7) {
                DayOfWeek d = DayOfWeek.of(i + 1);
                if (!out.contains(d)) out.add(d);
                if (i == b) break;
            }
        }
        return out;
    }

    private static List<Window> times(String spec) {
        if (spec.equals("off") || spec.equals("closed")) return List.of();
        List<Window> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            Matcher t = TIME.matcher(part.trim());
            if (!t.matches()) return null;
            int open = Integer.parseInt(t.group(1)) * 60 + Integer.parseInt(t.group(2));
            int close = Integer.parseInt(t.group(3)) * 60 + Integer.parseInt(t.group(4));
            if (open >= 1440 || close > 1440) return null;
            if (close <= open) close += 1440; // closes after midnight, on the next day
            out.add(new Window(open, close));
        }
        out.sort((x, y) -> Integer.compare(x.open(), y.open()));
        return List.copyOf(out);
    }
}
