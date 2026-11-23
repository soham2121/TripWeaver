package com.tripweaver.roadtrip;

import com.tripweaver.algorithms.tsp.DoubleTreeTsp;
import com.tripweaver.algorithms.tsp.TourResult;
import com.tripweaver.common.ApiException;
import com.tripweaver.common.Geo;
import com.tripweaver.place.PlaceHours;
import com.tripweaver.place.PlaceSummary;
import com.tripweaver.planning.Itinerary;
import com.tripweaver.planning.OpeningHours;
import com.tripweaver.recommendation.RecommendationEngine;
import com.tripweaver.recommendation.RecommendationRequest;
import com.tripweaver.recommendation.RecommendationResult;
import com.tripweaver.routing.EstimateTravel;
import com.tripweaver.routing.LatLon;
import com.tripweaver.routing.NavigationLinks;
import com.tripweaver.routing.Route;
import com.tripweaver.routing.RoutingService;
import com.tripweaver.routing.TransportMode;
import com.tripweaver.routing.TravelMatrix;
import com.tripweaver.routing.TravelProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

/**
 * Road-trip planner (plan section 7). Heuristic; no optimality is claimed.
 *
 * <ol>
 *   <li>Fixed waypoints (origin, user stops, destination) are kept in the given order. Each consecutive pair is a
 *       segment, routed directly first; a pair with no road makes the trip infeasible.
 *   <li>Detour candidates: activities matching the interests within a corridor around each segment route, ranked by
 *       {@link RecommendationEngine} (Quickselect + lazy-greedy Set Cover). One travel matrix per segment gives
 *       the added driving of each candidate; detours are accepted in rank order while each stays within {@code
 *       maxDetourMinutes} and the total within {@code maxDetours} and {@code maxTotalDetourMinutes}.
 *   <li>Detours of one segment are ordered between its two fixed endpoints only: the shorter of route order and a
 *       Double-Tree open path (no bound claimed for open paths).
 *   <li>Day simulation: driving in chunks with rest breaks after {@code maxContinuousDrivingMinutes}, a lunch break,
 *       visits, waypoint stays, user overnight waypoints, and suggested overnight stops (OSM-listed accommodation
 *       near the route that makes the most progress within the day's driving and time limits).
 *   <li>Repair: detours are optional, so the lowest-ranked detour is dropped while the plan is infeasible or needs
 *       more days than the plan without detours. The plan without detours must itself be feasible, otherwise the
 *       trip is infeasible with reasons.
 * </ol>
 *
 * Pure: all travel data comes from the injected {@link TravelProvider}.
 */
public final class RoadTripPlanner {

    static final int LUNCH_FROM = 12 * 60 + 30, LUNCH_LATE = 14 * 60 + 30;
    static final double HOTEL_CORRIDOR_KM = 5, ENDPOINT_EXCLUSION_KM = 3;
    static final int MAX_CANDIDATES_PER_SEGMENT = 60, HOTEL_BUCKETS = 20, HOTELS_PER_BUCKET = 3;
    static final int MAX_POLYLINE_POINTS = 400;
    static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Kolkata");
    static final String DISCLAIMER = "Road-trip planning is a heuristic: fixed waypoints keep their order, detours are"
            + " optional and bounded, and overnight stops are suggestions from OpenStreetMap listings. Routed travel"
            + " times assume free-flowing traffic.";

    private RoadTripPlanner() {}

    public static RoadTrip plan(
            List<PlaceSummary> places, Function<UUID, PlaceHours> hours, TravelProvider travel, RoadTripRequest req) {
        validate(req);
        return new Run(places, hours, travel, req).execute();
    }

    // ---------------------------------------------------------------- validation

    static void validate(RoadTripRequest r) {
        if (r.origin() == null || r.destination() == null)
            throw ApiException.badRequest("BAD_WAYPOINT", "origin and destination are required");
        List<RoadTripRequest.Waypoint> all = new ArrayList<>(r.waypoints());
        all.add(r.origin());
        all.add(r.destination());
        for (var w : all)
            if (w == null
                    || (w.placeId() == null
                            && (w.lat() == null
                                    || w.lon() == null
                                    || Math.abs(w.lat()) > 90
                                    || Math.abs(w.lon()) > 180)))
                throw ApiException.badRequest("BAD_WAYPOINT", "Each waypoint needs coordinates or a placeId");
        if (r.waypoints().size() > 20) throw ApiException.badRequest("BAD_WAYPOINT", "At most 20 waypoints");
        if (r.transport() == TransportMode.WALK)
            throw ApiException.badRequest("BAD_TRANSPORT", "Road trips need a car, scooter or taxi");
        if (!(r.maxDrivingHoursPerDay() > 0 && r.maxDrivingHoursPerDay() <= 14))
            throw ApiException.badRequest("BAD_DRIVING_LIMIT", "maxDrivingHoursPerDay must be in (0, 14]");
        if (r.maxContinuousDrivingMinutes() < 30 || r.maxContinuousDrivingMinutes() > 300)
            throw ApiException.badRequest("BAD_DRIVING_LIMIT", "maxContinuousDrivingMinutes must be 30..300");
        if (r.restBreakMinutes() < 5 || r.restBreakMinutes() > 120 || r.mealMinutes() < 15 || r.mealMinutes() > 120)
            throw ApiException.badRequest("BAD_BREAK", "Breaks must be 5..120 min (meals 15..120)");
        if (!r.dayStart().isBefore(r.dayEnd()) || !r.departureTime().isBefore(r.dayEnd()))
            throw ApiException.badRequest("BAD_DAY_WINDOW", "dayStart and departureTime must be before dayEnd");
        if (r.maxDetourMinutes() < 0
                || r.maxDetourMinutes() > 180
                || r.maxDetours() < 0
                || r.maxDetours() > 10
                || r.maxTotalDetourMinutes() < 0
                || r.maxTotalDetourMinutes() > 600)
            throw ApiException.badRequest("BAD_DETOUR_LIMIT", "Detour limits out of range");
        if (r.partySize() < 1 || r.partySize() > 20) throw ApiException.badRequest("BAD_PARTY", "partySize 1..20");
        for (Double v :
                new Double[] {r.fuelPricePerLitre(), r.tollsTotal(), r.accommodationPerNight(), r.foodPerPersonPerDay()
                }) if (v != null && !(v >= 0)) throw ApiException.badRequest("BAD_COST", "Costs must be non-negative");
        if (r.mileageKmPerLitre() != null && !(r.mileageKmPerLitre() > 0))
            throw ApiException.badRequest("BAD_COST", "mileageKmPerLitre must be positive");
    }

    // ---------------------------------------------------------------- model

    static final class Node {
        final String name, kind; // FIXED, DETOUR, HOTEL
        final double lat, lon;
        final PlaceSummary place;
        final boolean overnight;
        final int stay;

        double progress; // 0..1 along the segment route
        double value;
        int added, visit;
        String reason = "";
        Set<String> covers = Set.of();
        double score;

        Node(String name, String kind, double lat, double lon, PlaceSummary place, boolean overnight, int stay) {
            this.name = name;
            this.kind = kind;
            this.lat = lat;
            this.lon = lon;
            this.place = place;
            this.overnight = overnight;
            this.stay = stay;
        }

        LatLon ll() {
            return new LatLon(lat, lon);
        }
    }

    static final class Seg {
        int index;
        Node a, b;
        List<double[]> poly; // [lat, lon]
        final List<Node> nodes = new ArrayList<>(); // 0 = a, 1 = b, then candidates and hotels
        /** Matrix row per node. Per segment, because waypoints are shared by neighbouring segments. */
        final Map<Node, Integer> ix = new IdentityHashMap<>();

        final List<Node> candidates = new ArrayList<>(), hotels = new ArrayList<>();
        TravelMatrix m;

        int t(Node u, Node v) {
            return m.minutes()[ix.get(u)][ix.get(v)];
        }

        double km(Node u, Node v) {
            return m.km()[ix.get(u)][ix.get(v)];
        }
    }

    /** Mutable clock and driving counters; copied for dry runs. */
    static final class State {
        LocalDate date;
        int clock, dayDrive, cont, longest;
        boolean lunch;
        double dayKm;

        State copy() {
            State s = new State();
            s.date = date;
            s.clock = clock;
            s.dayDrive = dayDrive;
            s.cont = cont;
            s.longest = longest;
            s.lunch = lunch;
            s.dayKm = dayKm;
            return s;
        }
    }

    static final class DayAcc {
        final int day;
        final LocalDate date;
        final int start;
        final List<RoadTrip.Item> items = new ArrayList<>();
        final List<LatLon> points = new ArrayList<>();
        RoadTrip.Overnight overnight;
        int end, drive, longest;
        double km;

        DayAcc(int day, LocalDate date, int start) {
            this.day = day;
            this.date = date;
            this.start = start;
        }
    }

    /** Outcome of one day simulation; {@code conflict} non-null when infeasible. */
    record Sim(
            String conflict,
            List<String> relaxations,
            List<DayAcc> days,
            Map<Integer, List<Node>> paths,
            LocalDateTime arrival) {
        boolean ok() {
            return conflict == null;
        }
    }

    // ---------------------------------------------------------------- run

    static final class Run {
        final List<PlaceSummary> places;
        final Function<UUID, PlaceHours> hours;
        final TravelProvider travel;
        final RoadTripRequest req;
        final TransportMode mode;
        final List<Seg> segs = new ArrayList<>();
        final Set<String> warnings = new LinkedHashSet<>();
        final Map<UUID, PlaceSummary> byId = new HashMap<>();
        String provider = "OSRM", basis;
        int candidateCount;
        long seed;

        Run(List<PlaceSummary> places, Function<UUID, PlaceHours> hours, TravelProvider travel, RoadTripRequest req) {
            this.places = places;
            this.hours = hours;
            this.travel = travel;
            this.req = req;
            this.mode = req.transport();
            places.forEach(p -> byId.put(p.id(), p));
        }

        RoadTrip execute() {
            List<Node> fixed = new ArrayList<>();
            fixed.add(resolve(req.origin(), "Origin"));
            for (int i = 0; i < req.waypoints().size(); i++)
                fixed.add(resolve(req.waypoints().get(i), "Waypoint " + (i + 1)));
            fixed.add(resolve(req.destination(), "Destination"));

            List<String> conflicts = new ArrayList<>();
            for (int i = 0; i + 1 < fixed.size(); i++) {
                Seg s = new Seg();
                s.index = i;
                s.a = fixed.get(i);
                s.b = fixed.get(i + 1);
                Route direct = travel.route(List.of(s.a.ll(), s.b.ll()), mode);
                if (RoutingService.NO_ROUTE.equals(direct.provider())) {
                    conflicts.add("No road connects " + s.a.name + " and " + s.b.name + ".");
                    continue;
                }
                warnings.addAll(direct.warnings());
                s.poly = polyline(direct, s.a, s.b);
                segs.add(s);
            }
            if (!conflicts.isEmpty())
                throw infeasible(conflicts, List.of("Move the waypoint onto the road network."), null);

            findCandidates(fixed);
            for (Seg s : segs) {
                s.nodes.add(s.a);
                s.nodes.add(s.b);
                s.nodes.addAll(s.candidates);
                s.nodes.addAll(s.hotels);
                List<LatLon> pts = new ArrayList<>();
                for (int i = 0; i < s.nodes.size(); i++) {
                    s.ix.put(s.nodes.get(i), i);
                    pts.add(s.nodes.get(i).ll());
                }
                s.m = travel.matrix(pts, mode);
                warnings.addAll(s.m.warnings());
                if (!"OSRM".equals(s.m.provider())) provider = s.m.provider();
                if (basis == null || !"OSRM".equals(s.m.provider())) basis = s.m.basis();
                int direct = s.t(s.a, s.b);
                for (Node c : s.candidates) c.added = s.t(s.a, c) + s.t(c, s.b) - direct;
            }

            List<Node> chosen = selectDetours();
            Sim base = simulate(List.of());
            if (!base.ok()) {
                Map<String, Object> extra = base.arrival() == null ? null : Map.of("estimatedArrival", base.arrival());
                throw infeasible(List.of(base.conflict()), base.relaxations(), extra);
            }
            Sim res = simulate(chosen);
            while (!chosen.isEmpty()
                    && (!res.ok() || res.days().size() > base.days().size())) {
                Node drop = chosen.stream()
                        .min(Comparator.comparingDouble((Node n) -> n.value).thenComparing(n -> n.name))
                        .orElseThrow();
                String why = !res.ok() ? res.conflict() : "it would add a driving day";
                warnings.add("Detour " + drop.name + " dropped: " + why);
                chosen = new ArrayList<>(chosen);
                chosen.remove(drop);
                res = simulate(chosen);
            }
            if (!res.ok()) res = base;
            return build(res, chosen);
        }

        Node resolve(RoadTripRequest.Waypoint w, String fallback) {
            PlaceSummary p = w.placeId() == null ? null : byId.get(w.placeId());
            if (w.placeId() != null && p == null)
                throw ApiException.badRequest("UNKNOWN_PLACE", "Waypoint place not found: " + w.placeId());
            String name = w.name() != null && !w.name().isBlank() ? w.name() : p != null ? p.name() : fallback;
            int stay = w.stayMinutes() == null ? 0 : Math.max(0, w.stayMinutes());
            return new Node(
                    name,
                    "FIXED",
                    p != null ? p.lat() : w.lat(),
                    p != null ? p.lon() : w.lon(),
                    p,
                    w.overnight() != null && w.overnight(),
                    stay);
        }

        // ------------------------------------------------------------ candidates

        void findCandidates(List<Node> fixed) {
            double corridorKm = Math.max(5, req.maxDetourMinutes() * 0.5);
            Map<UUID, Seg> segOf = new HashMap<>();
            Map<UUID, Double> progressOf = new HashMap<>();
            List<PlaceSummary> corridor = new ArrayList<>();
            Map<Seg, List<Object[]>> hotelsBySeg = new HashMap<>();
            for (PlaceSummary p : places) {
                if (req.avoid().contains(p.id())) continue;
                boolean hotel = "accommodation".equals(p.primaryCategory());
                boolean detour = !req.interests().isEmpty()
                        && p.activity()
                        && p.interests().stream().anyMatch(req.interests()::containsKey);
                if (!hotel && !detour) continue;
                if (fixed.stream()
                        .anyMatch(f -> Geo.haversineKm(f.lat, f.lon, p.lat(), p.lon()) < ENDPOINT_EXCLUSION_KM))
                    continue;
                Seg best = null;
                double bestD = Double.MAX_VALUE, bestT = 0;
                for (Seg s : segs) {
                    double[] dt = distanceToPolyline(s.poly, p.lat(), p.lon());
                    // A hotel can serve every segment it lies near (segments may overlap when backtracking).
                    if (hotel && dt[0] <= HOTEL_CORRIDOR_KM)
                        hotelsBySeg.computeIfAbsent(s, k -> new ArrayList<>()).add(new Object[] {p, dt[0], dt[1]});
                    if (dt[0] < bestD) {
                        bestD = dt[0];
                        bestT = dt[1];
                        best = s;
                    }
                }
                if (best == null) continue;
                if (detour && bestD <= corridorKm) {
                    corridor.add(p);
                    segOf.put(p.id(), best);
                    progressOf.put(p.id(), bestT);
                }
            }
            // Hotels: up to HOTELS_PER_BUCKET closest to the route in each slice of the segment.
            for (var e : hotelsBySeg.entrySet()) {
                Map<Integer, List<Object[]>> buckets = new HashMap<>();
                for (Object[] h : e.getValue())
                    buckets.computeIfAbsent(
                                    (int) Math.min(HOTEL_BUCKETS - 1, (double) h[2] * HOTEL_BUCKETS),
                                    k -> new ArrayList<>())
                            .add(h);
                buckets.keySet().stream()
                        .sorted()
                        .forEach(k -> buckets.get(k).stream()
                                .sorted(Comparator.comparingDouble((Object[] h) -> (double) h[1])
                                        .thenComparing(
                                                h -> ((PlaceSummary) h[0]).id().toString()))
                                .limit(HOTELS_PER_BUCKET)
                                .forEach(h -> {
                                    PlaceSummary p = (PlaceSummary) h[0];
                                    Node n = new Node(p.name(), "HOTEL", p.lat(), p.lon(), p, true, 0);
                                    n.progress = (double) h[2];
                                    e.getKey().hotels.add(n);
                                }));
            }
            candidateCount = corridor.size();
            if (req.interests().isEmpty()) return;
            if (corridor.isEmpty()) {
                warnings.add("No catalogue attraction matching the interests lies within " + (int) corridorKm
                        + " km of the route.");
                return;
            }
            RecommendationResult rec = RecommendationEngine.recommend(
                    corridor,
                    RecommendationRequest.builder()
                            .interests(req.interests())
                            .travelMonth(departureDate().getMonthValue())
                            .avoid(req.avoid())
                            .poolSize(Math.min(200, corridor.size()))
                            .seed(req.seed())
                            .build());
            seed = rec.seed();
            Map<Seg, Integer> perSeg = new HashMap<>();
            List<Object[]> ranked = new ArrayList<>();
            for (var pick : rec.selected())
                ranked.add(new Object[] {
                    pick.place(),
                    pick.score().total() + 1,
                    pick.reason(),
                    pick.score().total()
                });
            for (var s : rec.fill())
                ranked.add(new Object[] {
                    s.place(),
                    s.score().total(),
                    String.format("Ranked pool (relevance %.2f)", s.score().total()),
                    s.score().total()
                });
            for (Object[] r : ranked) {
                PlaceSummary p = (PlaceSummary) r[0];
                Seg s = segOf.get(p.id());
                if (perSeg.merge(s, 1, Integer::sum) > MAX_CANDIDATES_PER_SEGMENT) continue;
                Node n = new Node(p.name(), "DETOUR", p.lat(), p.lon(), p, false, 0);
                n.value = (double) r[1];
                n.reason = (String) r[2];
                n.score = (double) r[3];
                n.progress = progressOf.get(p.id());
                n.visit = p.visitMinutesMin() == null
                        ? 60
                        : (int) Math.round((p.visitMinutesMin() + p.visitMinutesMax()) / 2.0);
                Set<String> cov = new TreeSet<>(p.interests());
                cov.retainAll(req.interests().keySet());
                n.covers = cov;
                s.candidates.add(n);
            }
        }

        /** Rank order; each detour within its own limit, the set within count and total-time limits. */
        List<Node> selectDetours() {
            List<Node> all = new ArrayList<>();
            segs.forEach(s -> all.addAll(s.candidates));
            all.sort(Comparator.comparingDouble((Node n) -> -n.value)
                    .thenComparing(n -> n.place.id().toString()));
            List<Node> chosen = new ArrayList<>();
            int total = 0;
            for (Node n : all) {
                if (chosen.size() >= req.maxDetours()) break;
                if (n.added > req.maxDetourMinutes()) continue;
                if (total + n.added + n.visit > req.maxTotalDetourMinutes()) continue;
                chosen.add(n);
                total += n.added + n.visit;
            }
            return chosen;
        }

        /** Detours of one segment, ordered between its fixed endpoints. */
        List<Node> order(Seg s, List<Node> chosen, Map<Integer, RoadTrip.Ordering> orderings) {
            List<Node> mine = chosen.stream()
                    .filter(s.candidates::contains)
                    .sorted(Comparator.comparingDouble((Node n) -> n.progress))
                    .toList();
            List<Node> seq = new ArrayList<>();
            seq.add(s.a);
            seq.addAll(mine);
            seq.add(s.b);
            double routeOrder = cost(s, seq);
            if (mine.size() < 2) {
                if (orderings != null)
                    orderings.put(
                            s.index, new RoadTrip.Ordering("route-order", "ROUTE_ORDER", routeOrder, null, false));
                return seq;
            }
            int n = seq.size();
            double[][] sym = new double[n][n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (i != j) sym[i][j] = (s.t(seq.get(i), seq.get(j)) + s.t(seq.get(j), seq.get(i))) / 2.0;
            TourResult dt = DoubleTreeTsp.openPath(sym, 0, n - 1);
            List<Node> dtSeq = new ArrayList<>();
            for (int k : dt.tour()) dtSeq.add(seq.get(k));
            double dtCost = cost(s, dtSeq);
            boolean useDt = dtCost < routeOrder - 1e-9;
            if (orderings != null)
                orderings.put(
                        s.index,
                        new RoadTrip.Ordering(
                                dt.method(), useDt ? "DOUBLE_TREE" : "ROUTE_ORDER", routeOrder, dtCost, false));
            return useDt ? dtSeq : seq;
        }

        static double cost(Seg s, List<Node> seq) {
            double c = 0;
            for (int i = 0; i + 1 < seq.size(); i++) c += s.t(seq.get(i), seq.get(i + 1));
            return c;
        }

        LocalDate departureDate() {
            return req.departureDate() == null ? LocalDate.now(TRIP_ZONE) : req.departureDate();
        }

        // ------------------------------------------------------------ simulation

        int maxDay() {
            return (int) Math.round(req.maxDrivingHoursPerDay() * 60);
        }

        int dayEnd() {
            return minutes(req.dayEnd());
        }

        boolean lunchPossible(int dayStart) {
            return dayStart < LUNCH_LATE && dayEnd() >= LUNCH_FROM + req.mealMinutes();
        }

        /** Drives {@code minutes}; adds DRIVE pieces, rest and lunch breaks to {@code day} when not null. */
        void drive(State st, DayAcc day, int minutes, double km, String from, String to) {
            int remaining = minutes;
            double kmPerMin = minutes == 0 ? 0 : km / minutes;
            while (remaining > 0) {
                if (!st.lunch && st.clock >= LUNCH_FROM && lunchPossible(day == null ? 0 : day.start)) {
                    add(
                            day,
                            item(
                                    "MEAL",
                                    "Lunch break (on the way; location approximate)",
                                    st.clock,
                                    req.mealMinutes()));
                    st.clock += req.mealMinutes();
                    st.cont = 0;
                    st.lunch = true;
                    continue;
                }
                if (st.cont >= req.maxContinuousDrivingMinutes()) {
                    add(
                            day,
                            item(
                                    "REST",
                                    "Rest break (roadside; location approximate)",
                                    st.clock,
                                    req.restBreakMinutes()));
                    st.clock += req.restBreakMinutes();
                    st.cont = 0;
                    continue;
                }
                int step = Math.min(remaining, req.maxContinuousDrivingMinutes() - st.cont);
                if (!st.lunch && st.clock < LUNCH_FROM) step = Math.min(step, LUNCH_FROM - st.clock);
                step = Math.max(1, step);
                if (day != null)
                    day.items.add(new RoadTrip.Item(
                            "DRIVE",
                            from + " → " + to,
                            null,
                            null,
                            null,
                            time(st.clock),
                            time(st.clock + step),
                            step,
                            round(step * kmPerMin),
                            null));
                st.clock += step;
                st.dayDrive += step;
                st.cont += step;
                st.longest = Math.max(st.longest, st.cont);
                st.dayKm += step * kmPerMin;
                remaining -= step;
            }
        }

        void add(DayAcc day, RoadTrip.Item i) {
            if (day != null) day.items.add(i);
        }

        RoadTrip.Item item(String type, String name, int start, int minutes) {
            return new RoadTrip.Item(
                    type, name, null, null, null, time(start), time(start + minutes), minutes, 0, null);
        }

        RoadTrip.Item at(String type, Node n, int start, int minutes, String note) {
            return new RoadTrip.Item(
                    type,
                    n.name,
                    n.place == null ? null : n.place.id(),
                    n.lat,
                    n.lon,
                    time(start),
                    time(start + minutes),
                    minutes,
                    0,
                    note);
        }

        boolean fits(State st, int minutes) {
            State c = st.copy();
            drive(c, null, minutes, 0, "", "");
            return c.dayDrive <= maxDay() && c.clock <= dayEnd();
        }

        Sim simulate(List<Node> chosen) {
            List<DayAcc> days = new ArrayList<>();
            Map<Integer, List<Node>> paths = new LinkedHashMap<>();
            State st = new State();
            st.date = departureDate();
            st.clock = minutes(req.departureTime());
            DayAcc day = new DayAcc(1, st.date, st.clock);
            days.add(day);
            Node first = segs.getFirst().a;
            day.items.add(at("DEPART", first, st.clock, 0, null));
            day.points.add(first.ll());

            for (Seg s : segs) {
                List<Node> seq = order(s, chosen, null);
                List<Node> path = new ArrayList<>();
                path.add(s.a);
                Node u = s.a;
                for (int k = 1; k < seq.size(); k++) {
                    Node v = seq.get(k);
                    // Overnight stops until the leg fits today.
                    while (!fits(st, s.t(u, v))) {
                        if (!req.suggestOvernight())
                            return fail(
                                    "Reaching " + v.name + " needs an overnight stop, and suggestions are off.", st);
                        Node h = bestHotel(s, st, u, v);
                        if (h == null)
                            return fail(
                                    String.format(
                                            "Day %d: no overnight stop with listed accommodation is reachable within"
                                                    + " %.1f h of driving and before %s between %s and %s.",
                                            day.day, req.maxDrivingHoursPerDay(), req.dayEnd(), u.name, v.name),
                                    st);
                        drive(st, day, s.t(u, h), s.km(u, h), u.name, h.name);
                        day.items.add(at("OVERNIGHT", h, st.clock, 0, "Suggested stay"));
                        day.overnight = new RoadTrip.Overnight(
                                h.name,
                                h.place.id(),
                                h.lat,
                                h.lon,
                                "SUGGESTED",
                                "PROVIDER_SOURCED",
                                "Listed in OpenStreetMap as accommodation near the route; availability and price unknown.");
                        day.points.add(h.ll());
                        path.add(h);
                        day = nextDay(days, day, st, h);
                        u = h;
                    }
                    drive(st, day, s.t(u, v), s.km(u, v), u.name, v.name);
                    path.add(v);
                    day.points.add(v.ll());
                    if (v.kind.equals("DETOUR")) {
                        int start = st.clock;
                        PlaceHours ph = hours.apply(v.place.id());
                        OpeningHours oh = ph == null ? null : OpeningHours.parse(ph.raw());
                        if (oh != null) {
                            start = oh.earliestStart(st.date.getDayOfWeek(), st.clock, v.visit);
                            if (start < 0) return fail("Detour " + v.name + " is closed when you would arrive.", st);
                        }
                        if (start + v.visit > dayEnd())
                            return fail("Detour " + v.name + " does not fit before " + req.dayEnd() + ".", st);
                        day.items.add(at(
                                "DETOUR",
                                v,
                                start,
                                v.visit,
                                start > st.clock ? "Waits " + (start - st.clock) + " min for opening" : null));
                        st.clock = start + v.visit;
                        st.cont = 0;
                        lunchAtStop(st, day);
                    } else if (v == segs.getLast().b) {
                        if (st.clock > dayEnd()) return fail("Arrival at " + v.name + " is after " + req.dayEnd(), st);
                        day.items.add(at("ARRIVE", v, st.clock, 0, null));
                    } else {
                        if (st.clock + v.stay > dayEnd())
                            return fail("Arrival at waypoint " + v.name + " is too late for the day.", st);
                        day.items.add(at("WAYPOINT", v, st.clock, v.stay, null));
                        st.clock += v.stay;
                        if (v.stay >= req.restBreakMinutes()) st.cont = 0;
                        if (v.overnight) {
                            day.items.add(at("OVERNIGHT", v, st.clock, 0, "Your overnight stop"));
                            day.overnight = new RoadTrip.Overnight(
                                    v.name,
                                    v.place == null ? null : v.place.id(),
                                    v.lat,
                                    v.lon,
                                    "USER",
                                    "USER_ENTERED",
                                    "Chosen by you.");
                            day = nextDay(days, day, st, v);
                        } else lunchAtStop(st, day);
                    }
                    u = v;
                }
                paths.put(s.index, path);
            }
            closeDay(day, st);
            LocalDateTime arrival = LocalDateTime.of(st.date, time(st.clock));
            if (req.arriveBy() != null && arrival.isAfter(req.arriveBy()))
                return new Sim(
                        "You would arrive at " + arrival + ", after the required arrival " + req.arriveBy() + ".",
                        List.of(
                                "Leave earlier or allow a later arrival.",
                                "Raise the daily driving limit or extend the day end.",
                                "Remove waypoints or stays."),
                        days,
                        paths,
                        arrival);
            return new Sim(null, List.of(), days, paths, arrival);
        }

        void lunchAtStop(State st, DayAcc day) {
            if (!st.lunch
                    && st.clock >= LUNCH_FROM
                    && lunchPossible(day.start)
                    && st.clock + req.mealMinutes() <= dayEnd()) {
                day.items.add(item("MEAL", "Lunch break", st.clock, req.mealMinutes()));
                st.clock += req.mealMinutes();
                st.lunch = true;
                st.cont = 0;
            }
        }

        Sim fail(String conflict, State st) {
            return new Sim(
                    conflict,
                    List.of(
                            "Add an overnight waypoint, or raise maxDrivingHoursPerDay.",
                            "Leave earlier or extend dayEnd.",
                            "Remove waypoints or detours."),
                    List.of(),
                    Map.of(),
                    null);
        }

        /** Accommodation reachable within today's limits that leaves the least driving to {@code v}. */
        Node bestHotel(Seg s, State st, Node u, Node v) {
            Node best = null;
            for (Node h : s.hotels) {
                if (s.t(h, v) >= s.t(u, v) || !fits(st, s.t(u, h))) continue;
                if (best == null
                        || s.t(h, v) < s.t(best, v)
                        || (s.t(h, v) == s.t(best, v)
                                && h.place
                                                .id()
                                                .toString()
                                                .compareTo(best.place.id().toString())
                                        < 0)) best = h;
            }
            return best;
        }

        DayAcc nextDay(List<DayAcc> days, DayAcc day, State st, Node at) {
            closeDay(day, st);
            st.date = st.date.plusDays(1);
            st.clock = minutes(req.dayStart());
            st.dayDrive = 0;
            st.cont = 0;
            st.longest = 0;
            st.lunch = false;
            st.dayKm = 0;
            DayAcc d = new DayAcc(day.day + 1, st.date, st.clock);
            d.items.add(at("DEPART", at, st.clock, 0, null));
            d.points.add(at.ll());
            days.add(d);
            return d;
        }

        void closeDay(DayAcc day, State st) {
            day.end = st.clock;
            day.drive = st.dayDrive;
            day.longest = st.longest;
            day.km = st.dayKm;
        }

        // ------------------------------------------------------------ output

        RoadTrip build(Sim res, List<Node> chosen) {
            Map<Integer, RoadTrip.Ordering> orderings = new HashMap<>();
            List<RoadTrip.Segment> segments = new ArrayList<>();
            for (Seg s : segs) {
                order(s, chosen, orderings);
                List<LatLon> pts =
                        res.paths().get(s.index).stream().map(Node::ll).toList();
                Route r = travel.route(pts, mode);
                if (RoutingService.NO_ROUTE.equals(r.provider())) {
                    warnings.add(
                            "No single road route through segment " + (s.index + 1) + "; drawn as straight lines.");
                    r = EstimateTravel.INSTANCE.route(pts, mode);
                }
                warnings.addAll(r.warnings());
                List<RoadTrip.DetourStop> detours = res.paths().get(s.index).stream()
                        .filter(n -> n.kind.equals("DETOUR"))
                        .map(n -> new RoadTrip.DetourStop(
                                n.place, n.added, n.visit, List.copyOf(n.covers), round(n.score), n.reason))
                        .toList();
                segments.add(new RoadTrip.Segment(
                        s.index,
                        s.a.name,
                        s.b.name,
                        s.t(s.a, s.b),
                        round(s.km(s.a, s.b)),
                        detours,
                        orderings.get(s.index),
                        r));
            }
            List<RoadTrip.Day> days = new ArrayList<>();
            int totalDrive = 0;
            double totalKm = 0;
            int nights = 0;
            for (DayAcc d : res.days()) {
                totalDrive += d.drive;
                totalKm += d.km;
                if (d.overnight != null) nights++;
                days.add(new RoadTrip.Day(
                        d.day,
                        d.date,
                        time(d.start),
                        time(d.end),
                        d.drive,
                        round(d.km),
                        d.longest,
                        List.copyOf(d.items),
                        d.overnight,
                        NavigationLinks.google(d.points, mode)));
            }
            Set<String> covered = new TreeSet<>();
            chosen.forEach(n -> covered.addAll(n.covers));
            for (String i : new TreeSet<>(req.interests().keySet()))
                if (!covered.contains(i))
                    warnings.add("Interest '" + i + "' is not covered by any detour within the detour limits.");
            if (!chosen.isEmpty() || !req.interests().isEmpty())
                warnings.add("Google Maps may choose a different road than TripWeaver's routing between waypoints.");
            return new RoadTrip(
                    List.copyOf(days),
                    List.copyOf(segments),
                    budget(chosen, totalKm, nights, days.size()),
                    LocalDateTime.of(departureDate(), req.departureTime()),
                    res.arrival(),
                    totalDrive,
                    round(totalKm),
                    nights,
                    candidateCount,
                    List.copyOf(warnings),
                    provider,
                    basis,
                    DISCLAIMER,
                    seed);
        }

        Itinerary.Budget budget(List<Node> chosen, double km, int nights, int dayCount) {
            int party = req.partySize();
            List<Itinerary.BudgetLine> lines = new ArrayList<>();
            int vehicles = mode.vehicles(party);
            if (mode == TransportMode.TAXI)
                lines.add(unknown("LOCAL_TRANSPORT", "Taxi fare", "Taxi fares are not sourced; enter an estimate"));
            else if (req.mileageKmPerLitre() != null && req.fuelPricePerLitre() != null) {
                double f = km * vehicles / req.mileageKmPerLitre() * req.fuelPricePerLitre();
                lines.add(new Itinerary.BudgetLine(
                        "FUEL",
                        "Fuel",
                        round(f),
                        round(f),
                        "CURATED_ESTIMATE",
                        "PER_VEHICLE",
                        String.format(
                                "%.0f km × %d vehicle(s) / %.1f km/l × ₹%.2f/l (user-entered price and mileage; %s"
                                        + " distance)",
                                km, vehicles, req.mileageKmPerLitre(), req.fuelPricePerLitre(), provider)));
            } else lines.add(unknown("FUEL", "Fuel", "Enter fuel price and vehicle mileage"));
            lines.add(
                    req.tollsTotal() != null
                            ? new Itinerary.BudgetLine(
                                    "TOLLS", "Tolls", req.tollsTotal(), req.tollsTotal(), "USER_ENTERED", "TRIP", null)
                            : unknown("TOLLS", "Tolls", "Toll charges are not sourced; enter them"));
            if (nights > 0)
                lines.add(
                        req.accommodationPerNight() != null
                                ? new Itinerary.BudgetLine(
                                        "ACCOMMODATION",
                                        "Overnight stay",
                                        req.accommodationPerNight() * nights,
                                        req.accommodationPerNight() * nights,
                                        "USER_ENTERED",
                                        "PER_NIGHT",
                                        nights + " night(s)")
                                : unknown(
                                        "ACCOMMODATION",
                                        "Overnight stay",
                                        "Suggested stays are OpenStreetMap listings; prices and availability unknown"));
            if (req.foodPerPersonPerDay() != null) {
                double f = req.foodPerPersonPerDay() * party * dayCount;
                lines.add(new Itinerary.BudgetLine(
                        "FOOD", "Food", f, f, "USER_ENTERED", "PER_PERSON_PER_DAY", "daily amount × party × days"));
            } else lines.add(unknown("FOOD", "Food", "Enter a daily food budget per person"));
            for (Node n : chosen) {
                if (n.place.costs().isEmpty()) {
                    lines.add(unknown("ENTRY_OR_ACTIVITY", n.name, "Entry/activity cost unknown"));
                    continue;
                }
                double min = 0, max = 0;
                Set<String> conf = new TreeSet<>();
                for (var c : n.place.costs()) {
                    int f = "PER_PERSON".equals(c.basis()) ? party : 1;
                    min += c.min() * f;
                    max += c.max() * f;
                    conf.add(c.confidence());
                }
                lines.add(new Itinerary.BudgetLine(
                        "ENTRY_FEES", n.name, min, max, String.join(",", conf), "converted for party", null));
            }
            double kMin = 0, kMax = 0;
            List<String> unknownItems = new ArrayList<>();
            for (var l : lines) {
                if (l.max() == null) unknownItems.add(l.item());
                else {
                    kMin += l.min();
                    kMax += l.max();
                }
            }
            boolean complete = unknownItems.isEmpty();
            return new Itinerary.Budget(
                    "INR",
                    party,
                    List.copyOf(lines),
                    round(kMin),
                    round(kMax),
                    List.copyOf(unknownItems),
                    complete,
                    null,
                    null,
                    null,
                    complete ? "NO_LIMIT" : "NO_LIMIT_INCOMPLETE",
                    round(kMax / party));
        }

        static Itinerary.BudgetLine unknown(String category, String item, String note) {
            return new Itinerary.BudgetLine(category, item, null, null, "UNKNOWN", null, note);
        }

        ApiException infeasible(List<String> conflicts, List<String> relax, Map<String, Object> extra) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("conflicts", conflicts);
            d.put("suggestedRelaxations", relax);
            if (extra != null) d.putAll(extra);
            return new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INFEASIBLE_PLAN",
                    "The road trip cannot meet its hard limits.",
                    d);
        }
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Route geometry as [lat, lon], at most {@value #MAX_POLYLINE_POINTS} points; the straight line if missing. */
    static List<double[]> polyline(Route r, Node a, Node b) {
        List<double[]> g = r.geometry();
        if (g == null || g.size() < 2) return List.of(new double[] {a.lat, a.lon}, new double[] {b.lat, b.lon});
        int step = Math.max(1, (int) Math.ceil(g.size() / (double) MAX_POLYLINE_POINTS));
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < g.size(); i += step) out.add(new double[] {g.get(i)[1], g.get(i)[0]});
        double[] last = g.getLast();
        out.add(new double[] {last[1], last[0]});
        return out;
    }

    /**
     * Distance (km) from a point to a polyline, and the position of the nearest point as a fraction of the polyline
     * length. Local equirectangular projection per piece; adequate within tens of km.
     */
    static double[] distanceToPolyline(List<double[]> poly, double lat, double lon) {
        double total = 0;
        double[] len = new double[poly.size()];
        for (int i = 1; i < poly.size(); i++) {
            total += Geo.haversineKm(poly.get(i - 1)[0], poly.get(i - 1)[1], poly.get(i)[0], poly.get(i)[1]);
            len[i] = total;
        }
        double best = Double.MAX_VALUE, bestPos = 0;
        double kx = 111.32 * Math.cos(Math.toRadians(lat)), ky = 110.57;
        for (int i = 1; i < poly.size(); i++) {
            double ax = (poly.get(i - 1)[1] - lon) * kx, ay = (poly.get(i - 1)[0] - lat) * ky;
            double bx = (poly.get(i)[1] - lon) * kx, by = (poly.get(i)[0] - lat) * ky;
            double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2));
            double px = ax + t * dx, py = ay + t * dy, d = Math.sqrt(px * px + py * py);
            if (d < best) {
                best = d;
                bestPos = len[i - 1] + t * (len[i] - len[i - 1]);
            }
        }
        return new double[] {best, total == 0 ? 0 : bestPos / total};
    }

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    static LocalTime time(int minutes) {
        int m = Math.floorMod(minutes, 1440);
        return LocalTime.of(m / 60, m % 60);
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
