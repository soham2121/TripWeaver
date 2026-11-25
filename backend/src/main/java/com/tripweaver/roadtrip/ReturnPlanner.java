package com.tripweaver.roadtrip;

import com.tripweaver.common.ApiException;
import com.tripweaver.common.Geo;
import com.tripweaver.place.Town;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The four ways home after a road trip (and its stay), each a full road-trip plan from the destination back to the
 * origin, leaving the morning after the stay: 1 direct, 2 the outbound stops reversed, 3 the same road with stops the
 * outbound trip did not use, 4 through the gazetteer town farthest from the outbound road that adds at most a third
 * to the direct distance. An option that cannot be built carries a note instead of a plan. Pure: every leg is planned
 * by the injected {@code leg} function.
 */
public final class ReturnPlanner {

    /** A different town must be this far from every outbound route, and cost at most this factor of the direct line. */
    static final double MIN_OFF_ROUTE_KM = 40, MAX_DETOUR_FACTOR = 1.35;
    /** Different-town candidates tried in order before option 4 gives up. */
    static final int TOWN_TRIES = 3;

    static final String NO_NEW_STOPS = "No new stops found on this road";
    static final String NO_TOWN = "No town off the outbound road within a third more driving";
    static final String PLAIN_STOPS =
            "Outbound overnight stops did not fit the return timings; they are plain stops here.";

    private ReturnPlanner() {}

    /** The morning after the stay's last day, or after the arrival without a stay. */
    public static LocalDate departure(RoadTrip outbound) {
        return outbound.stay() != null
                ? outbound.stay().lastDay().plusDays(1)
                : outbound.arrival().toLocalDate().plusDays(1);
    }

    public static RoadTrip.ReturnTrip plan(
            RoadTripRequest out, RoadTrip outbound, List<Town> towns, Function<RoadTripRequest, RoadTrip> leg) {
        RoadTrip.Item depart = items(outbound).stream()
                .filter(i -> "DEPART".equals(i.type()))
                .findFirst()
                .orElseThrow();
        RoadTrip.Item arrive = items(outbound).stream()
                .filter(i -> "ARRIVE".equals(i.type()))
                .reduce((a, b) -> b)
                .orElseThrow();
        var from = new RoadTripRequest.Waypoint(
                name(out.destination(), arrive),
                arrive.lat(),
                arrive.lon(),
                out.destination().placeId(),
                null,
                null);
        var to = new RoadTripRequest.Waypoint(
                name(out.origin(), depart),
                depart.lat(),
                depart.lon(),
                out.origin().placeId(),
                null,
                null);
        LocalDate date = departure(outbound);

        List<RoadTrip.ReturnOption> options = new ArrayList<>();

        // 1. Direct: no sightseeing detours.
        RoadTripRequest direct = leg(out, from, to, List.of(), date, Map.of(), out.avoid(), Set.of());
        String[] directNote = {null};
        RoadTrip directPlan = run(leg, direct, directNote);
        options.add(new RoadTrip.ReturnOption(1, "DIRECT", "Direct", null, directPlan, directNote[0]));

        // 2. Same way back: the outbound stops reversed, overnight stops kept as overnights.
        List<RoadTripRequest.Waypoint> back = new ArrayList<>();
        for (RoadTrip.Item i : items(outbound))
            switch (i.type()) {
                case "WAYPOINT", "DETOUR" ->
                    back.add(new RoadTripRequest.Waypoint(
                            i.name(), i.lat(), i.lon(), i.placeId(), null, i.minutes() > 0 ? i.minutes() : null));
                case "OVERNIGHT" -> {
                    RoadTripRequest.Waypoint prev = back.isEmpty() ? null : back.getLast();
                    if (prev != null && same(prev, i))
                        back.set(
                                back.size() - 1,
                                new RoadTripRequest.Waypoint(
                                        prev.name(), prev.lat(), prev.lon(), prev.placeId(), true, prev.stayMinutes()));
                    else back.add(new RoadTripRequest.Waypoint(i.name(), i.lat(), i.lon(), i.placeId(), true, null));
                }
                default -> {}
            }
        List<RoadTripRequest.Waypoint> reversed = back.reversed();
        RoadTrip samePlan;
        String sameNote = null;
        RoadTripRequest same = leg(out, from, to, reversed, date, Map.of(), out.avoid(), Set.of());
        try {
            samePlan = leg.apply(same);
        } catch (ApiException e) {
            List<RoadTripRequest.Waypoint> plain = reversed.stream()
                    .map(w -> new RoadTripRequest.Waypoint(w.name(), w.lat(), w.lon(), w.placeId(), null, null))
                    .toList();
            RoadTripRequest retry = leg(out, from, to, plain, date, Map.of(), out.avoid(), Set.of());
            samePlan = run(leg, retry, null);
            sameNote = samePlan == null ? reason(e) : PLAIN_STOPS;
        } catch (RuntimeException e) {
            samePlan = null;
            sameNote = reason(e);
        }
        options.add(new RoadTrip.ReturnOption(2, "SAME_WAY", "Same way back", null, samePlan, sameNote));

        // 3. Same road, new stops: avoid what the outbound trip visited and slept at.
        Set<UUID> avoid = new HashSet<>(out.avoid());
        for (RoadTrip.Item i : items(outbound))
            if (("DETOUR".equals(i.type()) || "OVERNIGHT".equals(i.type())) && i.placeId() != null)
                avoid.add(i.placeId());
        Set<String> avoidTowns = new HashSet<>(out.avoidTowns());
        if (outbound.stayOptions() != null)
            for (RoadTrip.NightStays n : outbound.stayOptions())
                for (RoadTrip.TownOption o : n.options()) if (o.chosen()) avoidTowns.add(o.key());
        Map<String, Double> interests = StayPlanner.interestsOrAll(out.interests());
        RoadTripRequest fresh = leg(out, from, to, List.of(), date, interests, avoid, avoidTowns);
        RoadTrip freshPlan;
        String freshNote = null;
        try {
            freshPlan = leg.apply(fresh);
            if (directPlan != null
                    && detours(freshPlan) == 0
                    && overnights(freshPlan).equals(overnights(directPlan))) freshNote = NO_NEW_STOPS;
        } catch (RuntimeException e) {
            freshPlan = null;
            freshNote = reason(e);
        }
        options.add(new RoadTrip.ReturnOption(3, "NEW_STOPS", "Same road, new stops", null, freshPlan, freshNote));

        // 4. Through a different town.
        // 4. Through a different town: the best few candidates in order, the town as the night's stop when the
        // drive home takes more than a day (small inland towns have no listed stays to split the drive), else as a
        // plain stop; the first that plans wins. ponytail: one attempt per town, 3 towns, to spare the public OSRM.
        List<Town> candidates = differentTowns(outbound, from, to, towns);
        boolean multiDay = directPlan != null && directPlan.days().size() > 1;
        RoadTrip.ReturnOption four = null;
        String lastError = null;
        for (Town t : candidates.subList(0, Math.min(TOWN_TRIES, candidates.size()))) {
            var via = new RoadTripRequest.Waypoint(t.name(), t.lat(), t.lon(), null, multiDay ? true : null, null);
            String[] note = {null};
            RoadTrip p = run(leg, leg(out, from, to, List.of(via), date, interests, out.avoid(), Set.of()), note);
            if (p != null) {
                four = new RoadTrip.ReturnOption(4, "DIFFERENT_TOWN", "Through " + t.name(), t.name(), p, null);
                break;
            }
            lastError = note[0];
        }
        if (four == null)
            four = new RoadTrip.ReturnOption(
                    4,
                    "DIFFERENT_TOWN",
                    "Through a different town",
                    null,
                    null,
                    candidates.isEmpty() ? NO_TOWN : lastError);
        options.add(four);

        int pick = out.returnChoice();
        int chosen = options.get(pick - 1).plan() != null ? pick : 1;
        return new RoadTrip.ReturnTrip(chosen, List.copyOf(options));
    }

    /**
     * Towns at least {@link #MIN_OFF_ROUTE_KM} from every outbound route polyline whose straight line from → town →
     * to is at most {@link #MAX_DETOUR_FACTOR} × from → to, farthest from the outbound road first; cities before towns
     * on equal distance.
     */
    static List<Town> differentTowns(
            RoadTrip outbound, RoadTripRequest.Waypoint from, RoadTripRequest.Waypoint to, List<Town> towns) {
        List<List<double[]>> polys = new ArrayList<>();
        for (RoadTrip.Segment s : outbound.segments())
            if (s.route() != null && !s.route().geometry().isEmpty())
                polys.add(s.route().geometry().stream()
                        .map(c -> new double[] {c[1], c[0]})
                        .toList());
        double direct = Geo.haversineKm(from.lat(), from.lon(), to.lat(), to.lon());
        record Candidate(Town town, double offKm) {}
        List<Candidate> found = new ArrayList<>();
        for (Town t : towns) {
            double via = Geo.haversineKm(from.lat(), from.lon(), t.lat(), t.lon())
                    + Geo.haversineKm(t.lat(), t.lon(), to.lat(), to.lon());
            if (via > MAX_DETOUR_FACTOR * direct) continue;
            double off = Double.MAX_VALUE;
            for (List<double[]> p : polys)
                off = Math.min(off, RoadTripPlanner.distanceToPolyline(p, t.lat(), t.lon())[0]);
            if (off >= MIN_OFF_ROUTE_KM) found.add(new Candidate(t, off));
        }
        return found.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> -c.offKm())
                        .thenComparing(c -> !"city".equals(c.town().kind())))
                .map(Candidate::town)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    /** Plans a leg; a failure gives null and its message in {@code note[0]} (when given). */
    static RoadTrip run(Function<RoadTripRequest, RoadTrip> leg, RoadTripRequest r, String[] note) {
        try {
            return leg.apply(r);
        } catch (RuntimeException e) {
            if (note != null) note[0] = reason(e);
            return null;
        }
    }

    static String reason(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "Could not be planned" : e.getMessage();
    }

    static boolean same(RoadTripRequest.Waypoint w, RoadTrip.Item i) {
        return w.lat() != null
                && i.lat() != null
                && Math.abs(w.lat() - i.lat()) < 1e-6
                && Math.abs(w.lon() - i.lon()) < 1e-6;
    }

    static List<RoadTrip.Item> items(RoadTrip r) {
        return r.days().stream().flatMap(d -> d.items().stream()).toList();
    }

    static int detours(RoadTrip r) {
        return r.segments().stream().mapToInt(s -> s.detours().size()).sum();
    }

    static List<String> overnights(RoadTrip r) {
        return r.days().stream()
                .filter(d -> d.overnight() != null)
                .map(d -> d.overnight().name())
                .toList();
    }

    static String name(RoadTripRequest.Waypoint w, RoadTrip.Item fallback) {
        return w.name() != null && !w.name().isBlank() ? w.name() : fallback.name();
    }

    /** The outbound request turned around: same limits, party, vehicle and costs; no stay, no further return. */
    static RoadTripRequest leg(
            RoadTripRequest o,
            RoadTripRequest.Waypoint from,
            RoadTripRequest.Waypoint to,
            List<RoadTripRequest.Waypoint> waypoints,
            LocalDate date,
            Map<String, Double> interests,
            Set<UUID> avoid,
            Set<String> avoidTowns) {
        return new RoadTripRequest(
                from,
                to,
                waypoints,
                date,
                o.departureTime(),
                null,
                o.maxDrivingHoursPerDay(),
                o.maxContinuousDrivingMinutes(),
                o.restBreakMinutes(),
                o.mealMinutes(),
                o.dayStart(),
                o.dayEnd(),
                interests,
                o.maxDetourMinutes(),
                o.maxDetours(),
                o.maxTotalDetourMinutes(),
                o.suggestOvernight(),
                o.partySize(),
                o.transport(),
                o.mileageKmPerLitre(),
                o.fuelPricePerLitre(),
                o.tollsTotal(),
                o.accommodationPerNight(),
                o.foodPerPersonPerDay(),
                avoid,
                o.seed(),
                waypoints.stream().anyMatch(w -> Boolean.TRUE.equals(w.overnight())) ? null : o.drivingDays(),
                Map.of(),
                0,
                false,
                1,
                avoidTowns);
    }
}
