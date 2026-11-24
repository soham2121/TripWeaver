# Demo script (8 minutes) and viva notes

The scenario follows `IMPLEMENTATION.md` §18: a four-day Goa trip for three friends staying near Candolim. It runs on the bundled catalogue with a fixed seed. No provider key and no large download are needed.

## Before the demo (10 minutes earlier)

1. Start the services:
   - `docker compose up -d db`, or use the Supabase `.env`;
   - `docker compose --profile routing up -d osrm`, which is optional;
   - in `backend/`: `./mvnw spring-boot:run`;
   - in `frontend/`: `npm run dev`.
2. Check `http://localhost:8088/api/v1/health` returns `UP`. The header should show "API: connected".
3. Sign in to a demo account in one browser window. Keep a private window ready for the shared link.
4. Fallback: if OSRM is not running, the plan still works. Point at the "Road routing unavailable" notice and the labelled estimates, which show the outage handling.

## Timeline

| Time | Show | Say |
|---|---|---|
| 0:00–0:45 | Home page | The problem: many places, hard constraints, no honest planner. Three modes. Every number carries a source label. |
| 0:45–1:30 | Discover: type `fort ag` | **Trie** autocomplete. The prefix walk is O(m log σ), then enumeration stops at 10 results. Accents are folded ("Cafe" finds "Café"). Mention the benchmark: flat ~2 µs vs a linear scan that grows with N (553× faster at 100K). |
| 1:30–2:15 | Home, then "Four days in Goa with friends" | Inputs: beach 5, food 4, heritage 4, adventure 3; Candolim; 3 people; scooter; hard ₹9,000 per person. |
| 2:15–3:15 | Plan header and budget | **Quickselect** pruned thousands of scored places to a top-K pool in expected O(n), with category quotas and a reported seed. The same seed gives the same plan. Budget: the known total, the unknown items listed as unknown, and the hard limit shown as the group total with the per-person amount. |
| 3:15–4:15 | Coverage and the "Best value for uncovered interests" reasons on stops | **Set Cover** picks places by uncovered interest weight per cost. A ranking-only baseline would pick four beaches. The **indexed heap** runs lazy greedy, with the same picks as full recomputation and fewer gain evaluations (3–11×). |
| 4:15–5:15 | Day 1 map and order note | **Double-Tree**: MST, pre-order walk, shortcut. The 2× bound holds only on a validated symmetric metric. The note under the map compares Double-Tree, the exact optimum and nearest-neighbour. The order is used only if feasible and shorter. |
| 5:15–6:15 | Edits | 1. Remove one stop (it becomes an **avoid**). 2. Add a **must-visit** place with the picker (Fort Aguada). 3. Lock a stop and replan. Each edit re-runs the planner, so times, hours and budget stay valid. |
| 6:15–7:00 | Save, then the trip page | Save the trip. Show people and roles, the expense split, and the change history. Concurrent edits are protected by a version check (409 on a stale edit). |
| 7:00–7:40 | Read-only link | Create a link and open it in the private window. Revoke it and reload: the link fails. |
| 7:40–8:00 | "Open in Google Maps" and the road-trip demo card | The navigation handoff is a plain link with no Google API. The road trip keeps fixed stops, adds bounded detours and suggests an overnight stay. Close. |

If time is short, skip the road trip. If an examiner changes the inputs (days, pace, budget, interests), press **Generate plan**; every path is live.

## Viva notes (`IMPLEMENTATION.md` §19)

1. **Problem:** turning interests and constraints into a feasible, varied, well-ordered multi-day plan, with honest data.
2. **Data:**
   - Real: OSM places and OSRM times.
   - Curated: official fees and hours, with URLs.
   - Estimated: durations, and travel when OSRM is down.
   - User-entered: costs.
   - Unknown stays unknown.
3. **Why a Trie:** prefix queries cost depends on the prefix, not on N; that suits autocomplete on every keystroke.
4. **Trie complexity:** O(m log σ) to reach the prefix node, plus the visited subtree, capped at L results.
5. **Quickselect vs sorting:** only the top K are needed. Expected O(n) beats O(n log n), measured 4–8× faster.
6. **Quickselect complexity:** expected O(n); worst case O(n²). A random pivot lands in the middle half with probability ½, so the range shrinks geometrically.
7. **The seed:** it fixes the pivots, so the work is repeatable. The selected set is seed-independent because the order is total.
8. **Set Cover universe:** the requested interest tags, weighted 1–5. Each place covers its tags at a positive selection cost.
9. **Objective:** cover the interest weight at minimum total selection cost. Greedy takes argmax(uncovered weight / cost).
10. **Guarantee:** H(d)·OPT for equal weights, where d is the largest set. It says nothing about the weighted order or the itinerary as a whole.
11. **What the heap holds:** each candidate's last computed ratio. Lazy greedy pops, recomputes, and accepts if it still beats the next top.
12. **Why a stale priority is an upper bound:** uncovered weight only decreases as coverage grows, and costs are fixed and positive.
13. **Double-Tree assumptions:** finite, non-negative, symmetric distances, zero diagonal, and the triangle inequality. All are checked by `MetricValidator`.
14. **Why the bound fails for live times:** directed and time-dependent times are not symmetric. TripWeaver symmetrises them, so the bound covers only that objective.
15. **Budget and time:**
    - Each insertion is simulated against opening hours, meals, the day window and pace caps.
    - Known spend must stay within a hard budget.
    - Infeasible mandatory stops return 422 with reasons.
16. **Interests that do not fit:** they are listed as uncovered, with a reason; the plan is still returned.
17. **Share permissions:** owner, editor or viewer roles are checked on the server for every call. A read-only link is a random token that can be revoked or set to expire.
18. **Provider failure:** OSRM down gives labelled estimates and a notice. Tiles down: the map says so, and everything else works. There is no Google API at all.
19. **Benchmark against a baseline:** Trie vs scan (553× at 100K), Quickselect vs sort (4–8×), heap vs scan (13× at 100K). Also the honest cases where the baseline wins at small sizes.
20. **Contribution:** solo project (D45). Everything is described in `CONTRIBUTIONS.md`.
