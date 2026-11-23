package com.tripweaver.planning;

import com.tripweaver.algorithms.tsp.DoubleTreeTsp;
import com.tripweaver.algorithms.tsp.ExactTsp;
import com.tripweaver.algorithms.tsp.MetricReport;
import com.tripweaver.algorithms.tsp.MetricValidator;
import com.tripweaver.algorithms.tsp.NearestNeighbourTsp;
import com.tripweaver.algorithms.tsp.TourResult;
import com.tripweaver.common.ApiException;
import com.tripweaver.place.PlaceHours;
import com.tripweaver.place.PlaceSummary;
import com.tripweaver.recommendation.RecommendationEngine;
import com.tripweaver.recommendation.RecommendationRequest;
import com.tripweaver.recommendation.RecommendationResult;
import com.tripweaver.routing.LatLon;
import com.tripweaver.routing.NavigationLinks;
import com.tripweaver.routing.Route;
import com.tripweaver.routing.TransportMode;
import com.tripweaver.routing.TravelMatrix;
import com.tripweaver.routing.TravelProvider;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

/**
 * Multi-day scheduler (plan section 5.6). Supporting heuristic, not an assessed algorithm, and not globally
 * optimal:
 *
 * <ol>
 *   <li>Candidate selection by {@link RecommendationEngine} (Quickselect + lazy-greedy Set Cover).
 *   <li>Fixed bookings at their day and time (hard).
 *   <li>Kept stops from a previous plan on their day (re-planning).
 *   <li>Must-visits at their cheapest feasible insertion; if none, a bounded repair drops up to
 *       {@value #MAX_REPAIR_REMOVALS} lowest-value non-mandatory stops from one day.
 *   <li>Greedy insertion of optional candidates: every candidate × day × position is simulated; feasible
 *       insertions (day window, opening hours, seasonal closure, pace caps, hard budget) are ranked by
 *       uncovered-interest gain and relevance per minute of added burden.
 *   <li>Hard-budget repair: drop lowest-value optional stops until the known maximum fits.
 * </ol>
 *
 * Pure: no I/O.
 */
public final class ItineraryPlanner {

    static final int MAX_DAYS = 14, MAX_PARTY = 20, MAX_REPAIR_REMOVALS = 3, REGION_HOP_PENALTY = 30;
    static final int LUNCH_FROM = 12 * 60 + 30, LUNCH_LATE = 14 * 60 + 30, DINNER_FROM = 19 * 60 + 30;
    static final int MAX_FOOD_STOPS_PER_DAY = 2, MEAL_EARLY = 30, POOL_PER_DAY = 40, QUOTA_PER_DAY = 3;
    static final Set<String> FOOD_CATEGORIES = Set.of("restaurant", "cafe", "fast_food");
    static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Kolkata");
    static final String CURRENCY = "INR";
    static final String DISCLAIMER = "Day allocation is a greedy insertion heuristic with a bounded repair pass. It"
            + " respects hard constraints but does not guarantee a globally optimal itinerary.";

    private ItineraryPlanner() {}

    public static Itinerary plan(
            List<PlaceSummary> catalogue,
            Function<UUID, PlaceHours> hours,
            TravelProvider travel,
            ItineraryRequest req) {
        validate(req);
        return new Run(catalogue, hours, travel, req).execute();
    }

    // ---------------------------------------------------------------- validation

    static void validate(ItineraryRequest r) {
        if (r.baseLat() == null || r.baseLon() == null || Math.abs(r.baseLat()) > 90 || Math.abs(r.baseLon()) > 180)
            throw ApiException.badRequest("BAD_BASE", "A base location (baseLat, baseLon) is required");
        if (r.days() < 1 || r.days() > MAX_DAYS)
            throw ApiException.badRequest("BAD_DAYS", "days must be 1.." + MAX_DAYS);
        if (r.partySize() < 1 || r.partySize() > MAX_PARTY)
            throw ApiException.badRequest("BAD_PARTY", "partySize must be 1.." + MAX_PARTY);
        checkWindow(r.dayStart(), r.dayEnd());
        if (r.budget() != null) {
            var b = r.budget();
            if (b.amount() == null
                    || !(b.amount() > 0)
                    || b.amount().isInfinite()
                    || b.basis() == null
                    || b.strictness() == null)
                throw ApiException.badRequest("BAD_BUDGET", "budget needs a positive amount, a basis and a strictness");
        }
        var c = r.costs();
        for (Double v : new Double[] {c.foodPerPersonPerDay(), c.accommodationPerNight(), c.fuelPricePerLitre()})
            if (v != null && (!(v >= 0) || v.isInfinite()))
                throw ApiException.badRequest("BAD_COST", "Costs must be non-negative");
        if (c.mileageKmPerLitre() != null && !(c.mileageKmPerLitre() > 0))
            throw ApiException.badRequest("BAD_COST", "mileageKmPerLitre must be positive");
        if (c.miscBufferPercent() != null && !(c.miscBufferPercent() >= 0 && c.miscBufferPercent() <= 100))
            throw ApiException.badRequest("BAD_COST", "miscBufferPercent must be 0..100");
        for (Double v : r.userCosts().values())
            if (v == null || !(v >= 0)) throw ApiException.badRequest("BAD_COST", "userCosts must be non-negative");
        Set<Integer> seenDays = new HashSet<>();
        for (var o : r.dayOverrides()) {
            if (o.day() < 1 || o.day() > r.days() || !seenDays.add(o.day()))
                throw ApiException.badRequest("BAD_OVERRIDE", "Day override must name a unique trip day");
            checkWindow(o.start() == null ? r.dayStart() : o.start(), o.end() == null ? r.dayEnd() : o.end());
        }
        Set<UUID> pinned = new HashSet<>();
        for (var f : r.fixedStops()) {
            if (f.placeId() == null || f.day() < 1 || f.day() > r.days() || f.start() == null)
                throw ApiException.badRequest("BAD_FIXED_STOP", "Fixed stops need a place, a trip day and a start");
            if (f.minutes() != null && (f.minutes() < 15 || f.minutes() > 720))
                throw ApiException.badRequest("BAD_FIXED_STOP", "Fixed stop minutes must be 15..720");
            if (!pinned.add(f.placeId()))
                throw ApiException.badRequest("BAD_FIXED_STOP", "A place can be fixed only once");
        }
        for (var k : r.keepStops()) {
            if (k.placeId() == null || k.day() < 1 || k.day() > r.days())
                throw ApiException.badRequest("BAD_KEEP_STOP", "Kept stops need a place and a trip day");
            if (!pinned.add(k.placeId()))
                throw ApiException.badRequest("BAD_KEEP_STOP", "A place can be kept or fixed only once");
        }
    }

    private static void checkWindow(LocalTime s, LocalTime e) {
        if (!s.isBefore(e) || s.until(e, java.time.temporal.ChronoUnit.MINUTES) < 60)
            throw ApiException.badRequest("BAD_DAY_WINDOW", "Day end must be at least 60 minutes after day start");
    }

    // ---------------------------------------------------------------- model

    /** A candidate visit with its planning metadata. */
    static final class Visit {
        final PlaceSummary p;
        String kind;
        final double value;
        int idx; // row in the travel matrix (0 = base)
        final Set<String> covers;
        final Integer fixedStart, fixedMinutes;
        final OpeningHours oh;
        final PlaceHours ph;
        final Itinerary.Money cost;
        final String reason;

        Visit(
                PlaceSummary p,
                String kind,
                double value,
                Set<String> covers,
                Integer fixedStart,
                Integer fixedMinutes,
                PlaceHours ph,
                Itinerary.Money cost,
                String reason) {
            this.p = p;
            this.kind = kind;
            this.value = value;
            this.covers = covers;
            this.fixedStart = fixedStart;
            this.fixedMinutes = fixedMinutes;
            this.ph = ph;
            this.oh = ph == null ? null : OpeningHours.parse(ph.raw());
            this.cost = cost;
            this.reason = reason;
        }

        boolean mandatory() {
            return kind.equals("FIXED") || kind.equals("MUST_VISIT");
        }

        int minutes(Pace pace) {
            if (fixedMinutes != null) return fixedMinutes;
            if (p.visitMinutesMin() == null || p.visitMinutesMax() == null) return 60;
            return pace.visitMinutes(p.visitMinutesMin(), p.visitMinutesMax());
        }

        String basis(Pace pace) {
            if (fixedMinutes != null) return "Booked duration";
            if (p.visitMinutesMin() == null || p.visitMinutesMax() == null) return "Duration unknown; 60 min assumed";
            return String.format(
                    "%s pace from the %d–%d min category default range",
                    pace.name().toLowerCase(), p.visitMinutesMin(), p.visitMinutesMax());
        }

        double maxCost() {
            return cost.max() == null ? 0 : cost.max();
        }
    }

    record DayCtx(int index, LocalDate date, int start, int end, Pace pace, boolean returnToBase) {
        int window() {
            return end - start;
        }
    }

    record Timed(Visit v, int arrive, int start, int end, int minutes, int travel, double km, int waitMin) {}

    /** Result of simulating one day; {@code failure} non-null when infeasible. */
    record Sim(
            String failure,
            List<Timed> stops,
            List<Itinerary.Meal> meals,
            int returnTravel,
            int endOfDay,
            int activity,
            int travel,
            int meal,
            int buffer,
            int waitMin,
            double km,
            int high,
            int transitions,
            int load) {
        static Sim fail(String why) {
            return new Sim(why, List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        boolean ok() {
            return failure == null;
        }

        int used() {
            return activity + travel + buffer + waitMin;
        }
    }

    record Insertion(int day, int pos, Sim sim, double burden) {}

    // ---------------------------------------------------------------- run

    static final class Run {
        final List<PlaceSummary> catalogue;
        final Function<UUID, PlaceHours> hours;
        final TravelProvider travel;
        TravelMatrix matrix;
        final List<String> routingWarnings = new ArrayList<>();
        final ItineraryRequest req;
        final TransportMode mode;
        final int party;
        final List<DayCtx> days = new ArrayList<>();
        final List<List<Visit>> plan = new ArrayList<>();
        final List<Sim> sims = new ArrayList<>();
        final Map<UUID, Visit> candidates = new LinkedHashMap<>();
        final Map<UUID, Set<String>> failures = new HashMap<>();
        final Map<UUID, String> removedFor = new HashMap<>();
        final List<Itinerary.Repair> repairs = new ArrayList<>();
        final Set<String> covered = new TreeSet<>();
        Map<String, Double> weights;
        RecommendationResult rec;
        Double limit;
        long evaluations;

        Run(
                List<PlaceSummary> catalogue,
                Function<UUID, PlaceHours> hours,
                TravelProvider travel,
                ItineraryRequest req) {
            this.travel = travel;
            this.catalogue = catalogue;
            this.hours = hours;
            this.req = req;
            this.mode = req.transport();
            this.party = req.partySize();
            if (req.budget() != null)
                limit = req.budget().amount()
                        * (req.budget().basis() == ItineraryRequest.BudgetBasis.PER_PERSON ? party : 1);
        }

        boolean hardBudget() {
            return limit != null && req.budget().strictness() == ItineraryRequest.Strictness.HARD;
        }

        Itinerary execute() {
            LocalDate start = req.startDate() == null ? LocalDate.now(TRIP_ZONE) : req.startDate();
            Map<Integer, ItineraryRequest.DayOverride> ov = new HashMap<>();
            req.dayOverrides().forEach(o -> ov.put(o.day(), o));
            for (int i = 1; i <= req.days(); i++) {
                var o = ov.get(i);
                LocalTime s = o != null && o.start() != null ? o.start() : req.dayStart();
                LocalTime e = o != null && o.end() != null ? o.end() : req.dayEnd();
                Pace pace = o != null && o.pace() != null ? o.pace() : req.pace();
                boolean back = o != null && o.returnToBase() != null ? o.returnToBase() : req.returnToBase();
                days.add(new DayCtx(i, start.plusDays(i - 1), minutes(s), minutes(e), pace, back));
                plan.add(new ArrayList<>());
                sims.add(simulate(days.getLast(), List.of()));
            }

            recommend(start);
            buildMatrix();
            placeFixed();
            placeKept();
            placeMustVisits();
            checkMandatoryBudget();
            fillOptional();
            enforceHardBudget();
            orderDays();
            return build();
        }

        // ------------------------------------------------------------ candidates

        void recommend(LocalDate start) {
            Set<UUID> must = new LinkedHashSet<>(req.mustVisit());
            req.fixedStops().forEach(f -> must.add(f.placeId()));
            req.keepStops().forEach(k -> must.add(k.placeId()));
            RecommendationRequest rr = RecommendationRequest.builder()
                    .destination(req.destination())
                    .interests(req.interests())
                    .base(req.baseLat(), req.baseLon())
                    .travelMonth(start.getMonthValue())
                    .maxEntryCostPerPerson(req.maxEntryCostPerPerson())
                    .dayMinutes(minutes(req.dayEnd()) - minutes(req.dayStart()))
                    .mustVisit(must)
                    .avoid(req.avoid())
                    .requireWheelchair(req.requireWheelchair())
                    .regions(req.regions())
                    .seed(req.seed())
                    .poolSize(Math.min(500, POOL_PER_DAY * req.days()))
                    .quotaPerInterest(Math.min(QUOTA_PER_DAY * req.days(), 15))
                    .build();
            rec = RecommendationEngine.recommend(catalogue, rr);
            weights = new TreeMap<>(req.interests());

            Map<UUID, ItineraryRequest.FixedStop> fixed = new HashMap<>();
            req.fixedStops().forEach(f -> fixed.put(f.placeId(), f));
            Set<UUID> kept = new HashSet<>();
            req.keepStops().forEach(k -> kept.add(k.placeId()));
            for (var pick : rec.selected()) {
                UUID id = pick.place().id();
                String kind = fixed.containsKey(id)
                        ? "FIXED"
                        : kept.contains(id) ? "KEEP" : req.mustVisit().contains(id) ? "MUST_VISIT" : "COVER";
                var f = fixed.get(id);
                String reason = switch (kind) {
                    case "FIXED" -> "Fixed booking";
                    case "KEEP" -> "Kept from the previous plan";
                    case "MUST_VISIT" -> "Must-visit";
                    default -> pick.reason();
                };
                candidates.put(
                        id,
                        visit(
                                pick.place(),
                                kind,
                                pick.score().total() + 1,
                                f == null ? null : minutes(f.start()),
                                f == null ? null : f.minutes(),
                                reason));
            }
            for (var s : rec.fill())
                candidates.putIfAbsent(
                        s.place().id(),
                        visit(
                                s.place(),
                                "FILL",
                                s.score().total(),
                                null,
                                null,
                                String.format(
                                        "Ranked pool (relevance %.2f)",
                                        s.score().total())));
        }

        Visit visit(PlaceSummary p, String kind, double value, Integer fixedStart, Integer fixedMin, String why) {
            Set<String> covers = new TreeSet<>(p.interests());
            covers.retainAll(req.interests().keySet());
            return new Visit(p, kind, value, covers, fixedStart, fixedMin, hours.apply(p.id()), cost(p), why);
        }

        Itinerary.Money cost(PlaceSummary p) {
            Double user = req.userCosts().get(p.id());
            if (user != null)
                return new Itinerary.Money(
                        user * party, user * party, false, "USER_ENTERED", "User-entered per-person cost × party");
            if (p.costs().isEmpty())
                return new Itinerary.Money(null, null, true, "UNKNOWN", "Entry/activity cost unknown");
            double min = 0, max = 0;
            Set<String> conf = new TreeSet<>(), notes = new TreeSet<>();
            for (var c : p.costs()) {
                int f = "PER_PERSON".equals(c.basis()) ? party : 1;
                min += c.min() * f;
                max += c.max() * f;
                conf.add(c.confidence());
                notes.add(c.kind().toLowerCase() + " " + c.basis().toLowerCase().replace('_', ' '));
            }
            return new Itinerary.Money(min, max, false, String.join(",", conf), String.join("; ", notes));
        }

        // ------------------------------------------------------------ simulation

        Sim simulate(DayCtx d, List<Visit> order) {
            Pace pace = d.pace();
            DayOfWeek dow = d.date().getDayOfWeek();
            int month = d.date().getMonthValue();
            // {label, earliest break start, latest start for a food stop to count as this meal}
            List<Object[]> pendingMeals = new ArrayList<>();
            if (d.start() < LUNCH_LATE && d.end() >= LUNCH_FROM + pace.mealMinutes())
                pendingMeals.add(new Object[] {"Lunch", LUNCH_FROM, LUNCH_LATE});
            if (d.end() >= DINNER_FROM + pace.mealMinutes())
                pendingMeals.add(new Object[] {"Dinner", DINNER_FROM, DINNER_FROM + 120});

            int t = d.start(), act = 0, travel = 0, meal = 0, buf = 0, wait = 0, high = 0, trans = 0, load = 0;
            double km = 0;
            int prev = 0;
            String prevRegion = null;
            List<Timed> out = new ArrayList<>();
            List<Itinerary.Meal> meals = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) {
                Visit v = order.get(i);
                boolean food = isFood(v.p);
                // A food stop next may itself be the meal, so do not insert a separate break before it.
                while (!food
                        && !pendingMeals.isEmpty()
                        && t >= (int) pendingMeals.getFirst()[1]) {
                    String label = (String) pendingMeals.removeFirst()[0];
                    meals.add(new Itinerary.Meal(label, time(t), time(t + pace.mealMinutes()), null));
                    t += pace.mealMinutes();
                    meal += pace.mealMinutes();
                }
                double legKm = matrix.km()[prev][v.idx];
                int tm = matrix.minutes()[prev][v.idx];
                t += tm;
                travel += tm;
                km += legKm;
                int arrive = t;
                if (v.p.seasonalClosedMonths().contains(month)) return Sim.fail("seasonal closure");
                int mins = v.minutes(pace);
                int s = t;
                if (v.fixedStart != null) {
                    if (t > v.fixedStart) return Sim.fail("cannot reach the booked time");
                    s = v.fixedStart;
                }
                if (v.oh != null) {
                    int e = v.oh.earliestStart(dow, s, mins);
                    if (e < 0 || (v.fixedStart != null && e != s) || e + mins > d.end())
                        return Sim.fail("opening hours");
                    s = e;
                }
                if (s + mins > d.end()) return Sim.fail("day time window");
                if (food && !pendingMeals.isEmpty()) {
                    Object[] m = pendingMeals.getFirst();
                    if (s >= (int) m[1] - MEAL_EARLY && s <= (int) m[2]) {
                        pendingMeals.removeFirst();
                        meals.add(new Itinerary.Meal((String) m[0], time(s), time(s + mins), v.p.name()));
                    }
                }
                wait += s - t;
                act += mins;
                t = s + mins;
                out.add(new Timed(v, arrive, s, t, mins, tm, legKm, s - arrive));
                if (i < order.size() - 1 || d.returnToBase()) {
                    t += pace.bufferMinutes();
                    buf += pace.bufferMinutes();
                }
                if (prevRegion != null && !prevRegion.equals(v.p.regionSlug())) trans++;
                prevRegion = v.p.regionSlug();
                if ("HIGH".equals(v.p.intensity())) high++;
                load += "HIGH".equals(v.p.intensity()) ? 3 : "MODERATE".equals(v.p.intensity()) ? 2 : 1;
                prev = v.idx;
            }
            int back = 0;
            if (d.returnToBase() && !order.isEmpty()) {
                double legKm = matrix.km()[prev][0];
                back = matrix.minutes()[prev][0];
                t += back;
                travel += back;
                km += legKm;
            }
            if (t > d.end()) return Sim.fail("day time window");
            if (order.isEmpty()) t = d.start();
            return new Sim(null, out, meals, back, t, act, travel, meal, buf, wait, km, high, trans, load);
        }

        boolean withinPace(DayCtx d, Sim s) {
            Pace p = d.pace();
            return s.stops().size() <= p.maxStops()
                    && s.high() <= p.maxHighIntensity()
                    && s.stops().stream().filter(x -> isFood(x.v().p)).count() <= MAX_FOOD_STOPS_PER_DAY
                    && s.activity() + s.travel() <= p.maxUtilisation() * d.window() + 1e-9;
        }

        double burden(int day, Sim after) {
            Sim before = sims.get(day);
            return after.used() - before.used() + REGION_HOP_PENALTY * (after.transitions() - before.transitions());
        }

        /** Cheapest feasible insertion over the given days, ignoring pace caps (mandatory stops). */
        Insertion bestMandatory(Visit v, List<Integer> dayIdx, Map<Integer, List<Visit>> override) {
            Insertion best = null;
            for (int d : dayIdx) {
                List<Visit> cur = override != null && override.containsKey(d) ? override.get(d) : plan.get(d);
                for (int pos = 0; pos <= cur.size(); pos++) {
                    List<Visit> trial = new ArrayList<>(cur);
                    trial.add(pos, v);
                    evaluations++;
                    Sim s = simulate(days.get(d), trial);
                    if (!s.ok()) {
                        note(v, s.failure());
                        continue;
                    }
                    double b = s.used() - simulate(days.get(d), cur).used();
                    if (best == null || b < best.burden() - 1e-9) best = new Insertion(d, pos, s, b);
                }
            }
            return best;
        }

        void apply(Insertion ins, Visit v) {
            plan.get(ins.day()).add(ins.pos(), v);
            sims.set(ins.day(), ins.sim());
            covered.addAll(v.covers);
        }

        void note(Visit v, String why) {
            failures.computeIfAbsent(v.p.id(), k -> new TreeSet<>()).add(why);
        }

        List<Integer> allDays() {
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < days.size(); i++) l.add(i);
            return l;
        }

        // ------------------------------------------------------------ placement steps

        void placeFixed() {
            List<String> conflicts = new ArrayList<>();
            List<ItineraryRequest.FixedStop> fs = new ArrayList<>(req.fixedStops());
            fs.sort(Comparator.comparingInt(ItineraryRequest.FixedStop::day)
                    .thenComparing(ItineraryRequest.FixedStop::start));
            for (var f : fs) {
                Visit v = candidates.get(f.placeId());
                int d = f.day() - 1;
                List<Visit> trial = new ArrayList<>(plan.get(d));
                trial.add(v);
                Sim s = simulate(days.get(d), trial);
                if (!s.ok()) {
                    conflicts.add(String.format(
                            "Fixed booking %s on day %d at %s: %s.", v.p.name(), f.day(), f.start(), s.failure()));
                    continue;
                }
                plan.set(d, trial);
                sims.set(d, s);
                covered.addAll(v.covers);
            }
            if (!conflicts.isEmpty())
                throw infeasible(
                        conflicts,
                        List.of("Move the booking inside the day window, after travel from the base, and within"
                                + " opening hours."));
        }

        void placeKept() {
            for (var k : req.keepStops()) {
                Visit v = candidates.get(k.placeId());
                Insertion ins = bestMandatory(v, List.of(k.day() - 1), null);
                if (ins == null) ins = bestMandatory(v, allDays(), null);
                if (ins != null) apply(ins, v);
            }
        }

        void placeMustVisits() {
            List<String> conflicts = new ArrayList<>();
            List<Visit> must = candidates.values().stream()
                    .filter(v -> v.kind.equals("MUST_VISIT"))
                    .sorted(Comparator.comparing(v -> v.p.id().toString()))
                    .toList();
            for (Visit v : must) {
                Insertion ins = bestMandatory(v, allDays(), null);
                if (ins != null) {
                    apply(ins, v);
                    continue;
                }
                if (!repairFor(v))
                    conflicts.add(String.format(
                            "%s (%d min) cannot fit on any trip day: %s.",
                            v.p.name(),
                            v.minutes(req.pace()),
                            String.join(", ", failures.getOrDefault(v.p.id(), Set.of()))));
            }
            if (!conflicts.isEmpty())
                throw infeasible(
                        conflicts,
                        List.of(
                                "Add a day or extend the day window.",
                                "Drop the must-visit or choose a lighter pace for that day.",
                                "Remove fixed bookings that block the day."));
        }

        /** Bounded repair: drop up to MAX_REPAIR_REMOVALS lowest-value non-mandatory stops from one day. */
        boolean repairFor(Visit v) {
            Insertion best = null;
            List<Visit> bestRemoved = null;
            double bestLoss = Double.MAX_VALUE;
            for (int d = 0; d < days.size(); d++) {
                List<Visit> removable = plan.get(d).stream()
                        .filter(x -> !x.mandatory())
                        .sorted(Comparator.comparingDouble((Visit x) -> x.value)
                                .thenComparing(x -> x.p.id().toString()))
                        .toList();
                for (int r = 1; r <= Math.min(MAX_REPAIR_REMOVALS, removable.size()); r++) {
                    List<Visit> drop = removable.subList(0, r);
                    List<Visit> trial = new ArrayList<>(plan.get(d));
                    trial.removeAll(drop);
                    Insertion ins = bestMandatory(v, List.of(d), Map.of(d, trial));
                    if (ins == null) continue;
                    double loss = drop.stream().mapToDouble(x -> x.value).sum();
                    if (loss < bestLoss) {
                        bestLoss = loss;
                        best = ins;
                        bestRemoved = drop;
                    }
                    break;
                }
            }
            if (best == null) return false;
            plan.get(best.day()).removeAll(bestRemoved);
            apply(best, v);
            for (Visit x : bestRemoved) {
                String why = "Removed to make room for must-visit " + v.p.name();
                removedFor.put(x.p.id(), why);
                repairs.add(new Itinerary.Repair("REMOVED", x.p.id(), x.p.name(), best.day() + 1, why));
            }
            recomputeCovered();
            return true;
        }

        void recomputeCovered() {
            covered.clear();
            plan.forEach(l -> l.forEach(v -> covered.addAll(v.covers)));
        }

        Set<UUID> scheduledIds() {
            Set<UUID> s = new HashSet<>();
            plan.forEach(l -> l.forEach(v -> s.add(v.p.id())));
            return s;
        }

        void checkMandatoryBudget() {
            if (!hardBudget()) return;
            double max = fixedCostsKnownMax() + placesKnownMax();
            if (max > limit + 1e-9) throw overBudget(max);
        }

        double placesKnownMax() {
            double m = 0;
            for (var l : plan) for (Visit v : l) m += v.maxCost();
            return m;
        }

        /** Food and accommodation are independent of the stops chosen. */
        double fixedCostsKnownMax() {
            double m = 0;
            var c = req.costs();
            if (c.foodPerPersonPerDay() != null) m += c.foodPerPersonPerDay() * party * req.days();
            if (c.accommodationPerNight() != null) m += c.accommodationPerNight() * (req.days() - 1);
            return m;
        }

        void fillOptional() {
            Set<UUID> scheduled = scheduledIds();
            List<Visit> pool = candidates.values().stream()
                    .filter(v -> !scheduled.contains(v.p.id()) && !v.mandatory())
                    .sorted(Comparator.comparing(v -> v.p.id().toString()))
                    .toList();
            Set<Visit> remaining = new LinkedHashSet<>(pool);
            Map<String, Integer> categoryCount = new HashMap<>();
            plan.forEach(l -> l.forEach(v -> categoryCount.merge(varietyKey(v.p), 1, Integer::sum)));
            double spent = fixedCostsKnownMax() + placesKnownMax();

            while (!remaining.isEmpty()) {
                Insertion best = null;
                Visit bestV = null;
                double bestValue = -1;
                for (Visit v : remaining) {
                    if (hardBudget() && spent + v.maxCost() > limit + 1e-9) {
                        note(v, "over the hard budget");
                        continue;
                    }
                    double gain = 0;
                    for (String c : v.covers) if (!covered.contains(c)) gain += weights.get(c);
                    double base = (gain + 0.5 * (v.value > 1 ? v.value - 1 : v.value))
                            / (1 + 0.5 * categoryCount.getOrDefault(varietyKey(v.p), 0));
                    for (int d = 0; d < days.size(); d++) {
                        DayCtx ctx = days.get(d);
                        List<Visit> cur = plan.get(d);
                        if (cur.size() >= ctx.pace().maxStops()) {
                            note(v, "pace limit");
                            continue;
                        }
                        for (int pos = 0; pos <= cur.size(); pos++) {
                            List<Visit> trial = new ArrayList<>(cur);
                            trial.add(pos, v);
                            evaluations++;
                            Sim s = simulate(ctx, trial);
                            if (!s.ok()) {
                                note(v, s.failure());
                                continue;
                            }
                            if (!withinPace(ctx, s)) {
                                note(v, "pace limit");
                                continue;
                            }
                            double b = burden(d, s);
                            double value = base / (1 + Math.max(0, b) / 120.0);
                            if (value > bestValue + 1e-12) {
                                bestValue = value;
                                best = new Insertion(d, pos, s, b);
                                bestV = v;
                            }
                        }
                    }
                }
                if (best == null) break;
                apply(best, bestV);
                remaining.remove(bestV);
                categoryCount.merge(varietyKey(bestV.p), 1, Integer::sum);
                spent += bestV.maxCost();
            }
        }

        /** Drop lowest-value optional stops while the known maximum (including fuel) exceeds a hard budget. */
        void enforceHardBudget() {
            if (!hardBudget()) return;
            while (true) {
                double max = budget().knownMax();
                if (max <= limit + 1e-9) return;
                Visit worst = null;
                int worstDay = -1;
                Sim worstSim = null;
                for (int d = 0; d < days.size(); d++)
                    for (Visit v : plan.get(d)) {
                        if (v.mandatory() || (worst != null && v.value >= worst.value)) continue;
                        List<Visit> trial = new ArrayList<>(plan.get(d));
                        trial.remove(v);
                        Sim s = simulate(days.get(d), trial);
                        if (!s.ok()) continue;
                        worst = v;
                        worstDay = d;
                        worstSim = s;
                    }
                if (worst == null) throw overBudget(max);
                plan.get(worstDay).remove(worst);
                sims.set(worstDay, worstSim);
                note(worst, "over the hard budget");
                repairs.add(new Itinerary.Repair(
                        "REMOVED", worst.p.id(), worst.p.name(), worstDay + 1, "Removed to fit the hard budget"));
                removedFor.put(worst.p.id(), "Removed to fit the hard budget");
                recomputeCovered();
            }
        }

        ApiException overBudget(double max) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("estimatedMaximum", max);
            d.put("budget", limit);
            d.put("currency", CURRENCY);
            return new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "BUDGET_INFEASIBLE",
                    "The fixed costs and mandatory stops exceed the hard budget.",
                    d);
        }

        static ApiException infeasible(List<String> conflicts, List<String> relax) {
            return new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INFEASIBLE_PLAN",
                    "The hard constraints cannot all be met.",
                    Map.of("conflicts", conflicts, "suggestedRelaxations", relax));
        }

        // ------------------------------------------------------------ budget

        double totalKm() {
            return sims.stream().mapToDouble(Sim::km).sum();
        }

        Itinerary.Budget budget() {
            List<Itinerary.BudgetLine> lines = new ArrayList<>();
            for (int d = 0; d < days.size(); d++)
                for (Visit v : plan.get(d)) {
                    String cat = v.cost.unknown()
                            ? "ENTRY_OR_ACTIVITY"
                            : v.p.costs().stream().allMatch(c -> c.kind().equals("ENTRY"))
                                    ? "ENTRY_FEES"
                                    : "ACTIVITIES";
                    lines.add(new Itinerary.BudgetLine(
                            cat,
                            v.p.name() + " (day " + (d + 1) + ")",
                            v.cost.min(),
                            v.cost.max(),
                            v.cost.confidence(),
                            "converted for " + party + " traveller(s)",
                            v.cost.note()));
                }
            var c = req.costs();
            if (c.foodPerPersonPerDay() != null) {
                double f = c.foodPerPersonPerDay() * party * req.days();
                lines.add(new Itinerary.BudgetLine(
                        "FOOD", "Food", f, f, "USER_ENTERED", "PER_PERSON_PER_DAY", "daily amount × party × days"));
            } else
                lines.add(new Itinerary.BudgetLine(
                        "FOOD", "Food", null, null, "UNKNOWN", null, "Enter a daily food budget per person"));
            int nights = req.days() - 1;
            if (nights > 0) {
                if (c.accommodationPerNight() != null) {
                    double a = c.accommodationPerNight() * nights;
                    lines.add(new Itinerary.BudgetLine(
                            "ACCOMMODATION", "Accommodation", a, a, "USER_ENTERED", "PER_NIGHT", nights + " night(s)"));
                } else
                    lines.add(new Itinerary.BudgetLine(
                            "ACCOMMODATION",
                            "Accommodation",
                            null,
                            null,
                            "UNKNOWN",
                            null,
                            "Enter the nightly accommodation cost"));
            }
            switch (mode) {
                case CAR, SCOOTER -> {
                    int vehicles = mode.vehicles(party);
                    double km = totalKm();
                    if (c.fuelPricePerLitre() != null && c.mileageKmPerLitre() != null) {
                        double f = km * vehicles / c.mileageKmPerLitre() * c.fuelPricePerLitre();
                        lines.add(new Itinerary.BudgetLine(
                                "FUEL",
                                "Fuel",
                                f,
                                f,
                                "CURATED_ESTIMATE",
                                "PER_VEHICLE",
                                String.format(
                                        "%.1f km × %d vehicle(s) / %.1f km/l × ₹%.2f/l (user-entered price and"
                                                + " mileage; distance is a straight-line estimate)",
                                        km, vehicles, c.mileageKmPerLitre(), c.fuelPricePerLitre())));
                    } else
                        lines.add(new Itinerary.BudgetLine(
                                "FUEL", "Fuel", null, null, "UNKNOWN", null, "Enter fuel price and vehicle mileage"));
                }
                case TAXI ->
                    lines.add(new Itinerary.BudgetLine(
                            "LOCAL_TRANSPORT",
                            "Taxi fares",
                            null,
                            null,
                            "UNKNOWN",
                            null,
                            "Taxi fares are not sourced; enter an estimate"));
                case WALK -> {}
            }
            double kMin = 0, kMax = 0;
            List<String> unknown = new ArrayList<>();
            for (var l : lines) {
                if (l.max() == null) unknown.add(l.item());
                else {
                    kMin += l.min();
                    kMax += l.max();
                }
            }
            if (c.miscBufferPercent() != null && c.miscBufferPercent() > 0) {
                double pct = c.miscBufferPercent() / 100;
                lines.add(new Itinerary.BudgetLine(
                        "MISC_BUFFER",
                        "Miscellaneous buffer",
                        kMin * pct,
                        kMax * pct,
                        "USER_ENTERED",
                        "PERCENT_OF_KNOWN",
                        c.miscBufferPercent() + "% of known costs"));
                kMin *= 1 + pct;
                kMax *= 1 + pct;
            }
            boolean complete = unknown.isEmpty();
            String status = limit == null
                    ? (complete ? "NO_LIMIT" : "NO_LIMIT_INCOMPLETE")
                    : kMax > limit + 1e-9 ? "OVER_BUDGET" : complete ? "WITHIN_BUDGET" : "INCOMPLETE";
            return new Itinerary.Budget(
                    CURRENCY,
                    party,
                    List.copyOf(lines),
                    round(kMin),
                    round(kMax),
                    List.copyOf(unknown),
                    complete,
                    limit,
                    req.budget() == null ? null : req.budget().basis().name(),
                    req.budget() == null ? null : req.budget().strictness().name(),
                    status,
                    round(kMax / party));
        }

        // ------------------------------------------------------------ travel and route ordering (Phase 7)

        /** One directed travel matrix over the base (row 0) and every candidate. */
        void buildMatrix() {
            List<LatLon> pts = new ArrayList<>();
            pts.add(new LatLon(req.baseLat(), req.baseLon()));
            for (Visit v : candidates.values()) {
                v.idx = pts.size();
                pts.add(new LatLon(v.p.lat(), v.p.lon()));
            }
            matrix = travel.matrix(pts, mode);
            routingWarnings.addAll(matrix.warnings());
        }

        final List<Itinerary.Ordering> orderings = new ArrayList<>();

        /**
         * Reorders each day with Metric TSP Double-Tree on the symmetrised travel minutes over base + stops: a closed
         * tour when the day returns to the base, otherwise an open path from the base to the current last stop (no
         * bound claimed). The new order is applied only if the day stays feasible with the directed legs (fixed
         * bookings, opening hours, day window) and the symmetric tour is shorter than the insertion order.
         * Nearest-neighbour and, for small days, the exact optimum are reported for comparison.
         */
        void orderDays() {
            for (int d = 0; d < days.size(); d++) {
                List<Visit> cur = plan.get(d);
                DayCtx ctx = days.get(d);
                if (cur.size() < 2) {
                    orderings.add(
                            cur.isEmpty()
                                    ? null
                                    : new Itinerary.Ordering(
                                            "TRIVIAL",
                                            "single stop",
                                            0,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true,
                                            false,
                                            List.of(),
                                            "One stop: nothing to order."));
                    continue;
                }
                int n = cur.size() + 1;
                int[] node = new int[n];
                for (int i = 1; i < n; i++) node[i] = cur.get(i - 1).idx;
                double[][] sym = new double[n][n];
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (i != j)
                            sym[i][j] = (matrix.minutes()[node[i]][node[j]] + matrix.minutes()[node[j]][node[i]]) / 2.0;
                MetricReport metric = MetricValidator.validate(sym);
                boolean closed = ctx.returnToBase();
                int[] insertion = new int[closed ? n + 1 : n];
                for (int i = 0; i < n; i++) insertion[i] = i;
                double insCost = TourResult.cost(sym, insertion);

                TourResult dt = closed ? DoubleTreeTsp.closedTour(sym, 0) : DoubleTreeTsp.openPath(sym, 0, n - 1);
                Double nn = closed ? NearestNeighbourTsp.closedTour(sym, 0).cost() : null;
                Double exact = closed && n <= 10 ? ExactTsp.closedTour(sym, 0).cost() : null;

                List<Visit> reordered = new ArrayList<>();
                for (int k : dt.tour()) if (k != 0) reordered.add(cur.get(k - 1));
                Sim sim = simulate(ctx, reordered);
                String applied, note;
                if (!sim.ok()) {
                    applied = "INSERTION";
                    note = "Double-Tree order breaks a hard constraint (" + sim.failure()
                            + "); kept the insertion order.";
                } else if (dt.cost() < insCost - 1e-9) {
                    applied = "DOUBLE_TREE";
                    note = String.format(
                            "Double-Tree order saves %.0f min over the insertion order.", insCost - dt.cost());
                    plan.set(d, reordered);
                    sims.set(d, sim);
                } else {
                    applied = "INSERTION";
                    note = "The insertion order was already as short as the Double-Tree order.";
                }
                boolean guarantee = closed && dt.approximationGuaranteeApplies();
                if (closed && !guarantee)
                    note += " No 2-approximation claim: the travel times break the triangle inequality.";
                if (!closed) note += " Open path: no approximation bound is claimed.";
                orderings.add(new Itinerary.Ordering(
                        applied,
                        dt.method(),
                        round(insCost),
                        round(dt.cost()),
                        round(dt.mstCost()),
                        nn == null ? null : round(nn),
                        exact == null ? null : round(exact),
                        metric.isMetric(),
                        guarantee,
                        metric.violations(),
                        note));
            }
        }

        // ------------------------------------------------------------ output

        Itinerary build() {
            List<String> warnings = new ArrayList<>();
            List<Itinerary.Day> outDays = new ArrayList<>();
            int unparsed = 0, unknownHours = 0;
            for (int d = 0; d < days.size(); d++) {
                DayCtx ctx = days.get(d);
                Sim s = sims.get(d);
                List<Itinerary.Stop> stops = new ArrayList<>();
                double dMin = 0, dMax = 0;
                boolean dUnknown = false;
                for (int i = 0; i < s.stops().size(); i++) {
                    Timed t = s.stops().get(i);
                    Visit v = t.v();
                    String status = v.ph == null ? "UNKNOWN" : v.oh == null ? "UNPARSED" : "CHECKED";
                    if (status.equals("UNPARSED")) unparsed++;
                    if (status.equals("UNKNOWN")) unknownHours++;
                    if (v.cost.unknown()) dUnknown = true;
                    else {
                        dMin += v.cost.min();
                        dMax += v.cost.max();
                    }
                    stops.add(new Itinerary.Stop(
                            i + 1,
                            v.p,
                            time(t.arrive()),
                            time(t.start()),
                            time(t.end()),
                            t.minutes(),
                            v.basis(ctx.pace()),
                            t.travel(),
                            round(t.km()),
                            t.waitMin(),
                            v.kind,
                            List.copyOf(v.covers),
                            v.cost,
                            new Itinerary.HoursInfo(
                                    v.ph == null ? null : v.ph.raw(),
                                    v.ph == null ? "UNKNOWN" : v.ph.confidence(),
                                    status),
                            v.reason));
                }
                List<LatLon> waypoints = new ArrayList<>();
                waypoints.add(new LatLon(req.baseLat(), req.baseLon()));
                for (Timed t : s.stops())
                    waypoints.add(new LatLon(t.v().p.lat(), t.v().p.lon()));
                if (ctx.returnToBase() && !s.stops().isEmpty()) waypoints.add(waypoints.getFirst());
                Route route = s.stops().isEmpty() ? null : travel.route(waypoints, mode);
                if (route != null) routingWarnings.addAll(route.warnings());
                List<String> dw = new ArrayList<>();
                for (var m : s.meals())
                    if (m.label().equals("Lunch") && minutes(m.start()) > LUNCH_LATE)
                        dw.add("Late lunch at " + m.start() + ".");
                boolean spansLunch = s.stops().stream().anyMatch(t -> t.end() > LUNCH_LATE && t.start() < LUNCH_LATE);
                if (spansLunch && s.meals().stream().noneMatch(m -> m.label().equals("Lunch")))
                    dw.add("No separate lunch break: a visit spans lunchtime.");
                int window = ctx.window();
                int free = Math.max(0, window - s.activity() - s.travel() - s.meal() - s.buffer() - s.waitMin());
                double util = window == 0 ? 0 : (double) (s.activity() + s.travel()) / window;
                outDays.add(new Itinerary.Day(
                        ctx.index(),
                        ctx.date(),
                        ctx.date().getDayOfWeek(),
                        time(ctx.start()),
                        time(ctx.end()),
                        ctx.pace(),
                        ctx.returnToBase(),
                        List.copyOf(stops),
                        s.meals(),
                        s.returnTravel(),
                        time(s.endOfDay()),
                        new Itinerary.Workload(
                                window,
                                s.activity(),
                                s.travel(),
                                s.meal(),
                                s.buffer(),
                                s.waitMin(),
                                free,
                                round(util),
                                s.stops().size(),
                                s.high(),
                                s.transitions(),
                                s.load(),
                                Pace.label(util).name()),
                        new Itinerary.Money(
                                dMin, dMax, dUnknown, dUnknown ? "INCOMPLETE" : "KNOWN", "Entry/activity costs"),
                        List.copyOf(dw),
                        orderings.get(d),
                        route,
                        NavigationLinks.google(waypoints, mode)));
            }

            Set<UUID> scheduled = scheduledIds();
            List<Itinerary.Unscheduled> unscheduled = new ArrayList<>();
            for (Visit v : candidates.values()) {
                if (scheduled.contains(v.p.id())) continue;
                if (!(v.kind.equals("COVER") || v.kind.equals("KEEP") || removedFor.containsKey(v.p.id()))) continue;
                String why = removedFor.containsKey(v.p.id())
                        ? removedFor.get(v.p.id())
                        : "Not scheduled: " + String.join(", ", failures.getOrDefault(v.p.id(), Set.of("no room")));
                unscheduled.add(new Itinerary.Unscheduled(v.p.id(), v.p.name(), v.kind, why));
            }

            List<String> uncovered = new ArrayList<>();
            Map<String, String> reasons = new TreeMap<>();
            for (String i : weights.keySet()) {
                if (covered.contains(i)) continue;
                uncovered.add(i);
                if (rec.coverage().uncovered().contains(i))
                    reasons.put(i, "No eligible place matches this interest under the current filters.");
                else {
                    Set<String> why = new TreeSet<>();
                    for (Visit v : candidates.values())
                        if (v.covers.contains(i)) why.addAll(failures.getOrDefault(v.p.id(), Set.of()));
                    reasons.put(i, "Candidates exist but could not be scheduled: " + String.join(", ", why) + ".");
                }
            }

            Itinerary.Budget budget = budget();
            if (budget.status().equals("OVER_BUDGET"))
                warnings.add(String.format(
                        "Estimated maximum ₹%.0f exceeds the flexible budget ₹%.0f by ₹%.0f.",
                        budget.knownMax(), budget.limit(), budget.knownMax() - budget.limit()));
            if (!budget.complete())
                warnings.add(budget.unknownItems().size() + " cost item(s) are unknown, so totals are incomplete"
                        + (hardBudget() ? "; the hard budget can only be confirmed once they are entered." : "."));
            for (String u : uncovered) warnings.add("Interest '" + u + "' is not covered: " + reasons.get(u));
            long budgetBlocked = candidates.values().stream()
                    .filter(v -> !scheduled.contains(v.p.id()))
                    .filter(v -> failures.getOrDefault(v.p.id(), Set.of()).contains("over the hard budget"))
                    .count();
            if (budgetBlocked > 0)
                warnings.add(budgetBlocked + " candidate(s) were left out to stay within the hard budget.");
            if (unparsed > 0)
                warnings.add(unparsed + " stop(s) have opening hours that could not be checked automatically.");
            if (unknownHours > 0)
                warnings.add(unknownHours + " stop(s) have no sourced opening hours; check before visiting.");
            for (String w : rec.warnings()) if (w.startsWith("Wheelchair")) warnings.add(w);
            for (var r : repairs) warnings.add(r.reason() + ": " + r.name() + " (day " + r.day() + ").");
            for (String w : routingWarnings) if (!warnings.contains(w)) warnings.add(w);

            return new Itinerary(
                    List.copyOf(outDays),
                    budget,
                    new Itinerary.Coverage(
                            List.copyOf(weights.keySet()), List.copyOf(covered), List.copyOf(uncovered), reasons),
                    List.copyOf(unscheduled),
                    List.copyOf(repairs),
                    List.copyOf(warnings),
                    matrix.provider(),
                    matrix.basis(),
                    DISCLAIMER,
                    rec.seed(),
                    new Itinerary.Stats(
                            candidates.size(),
                            evaluations,
                            repairs.size(),
                            rec.pool().eligible()));
        }
    }

    /** Restaurants, cafes and fast food: limited per day; one at mealtime replaces the meal break. */
    static boolean isFood(PlaceSummary p) {
        return FOOD_CATEGORIES.contains(p.primaryCategory());
    }

    /** Variety penalty groups all eating places together. */
    static String varietyKey(PlaceSummary p) {
        return isFood(p) ? "food" : p.primaryCategory();
    }

    static int minutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    static LocalTime time(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
