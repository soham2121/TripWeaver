# Return routes (road trip) — design

Date: 2026-11-25. Third of three pieces (PDF export and days at the destination done).

## Intent
After the stay at B, travellers want to choose how to get home: straight back, the way they came, or something
new. The app offers four ways home, the traveller picks one, and the picked one becomes part of the one trip that
is shown, saved, shared and printed.

## Goals
- Road-trip form: **Plan the way home** (checkbox, off by default; on in the Mumbai → Goa demo).
- The server plans four return options in the same request, each a full road-trip plan (days, breaks, overnight
  stops, detours, budget, Maps links), in this order:
  1. **Direct** — B to A, no sightseeing detours.
  2. **Same way back** — the outbound route reversed: outbound waypoints, detours and overnight towns in reverse
     order, as fixed stops.
  3. **Same road, new stops** — B to A with fresh detours for the interests and an overnight town the outbound trip
     did not use; places visited on the way out are avoided.
  4. **Through a different town** — B to A through the gazetteer town farthest from the outbound route whose detour
     adds at most about a third to the direct drive, with fresh detours.
- The page shows the four as selectable cards (name, driving time, km, nights, stops), then the picked option's days
  under the stay. Switching is instant (no new request).
- The pick is part of the request (`returnChoice`, 1–4, default 1), so saved trips, share links, re-planning and the
  PDF show the picked route.

## Non-goals
- Alternative roads from OSRM (the public server returns one road for Goa ↔ Mumbai).
- Editing a return option's stops, choosing its overnight town from town options, or a separate return pace.
- Returning to somewhere other than A.

## Anti-goals
- A missing option without a reason: when an option cannot be built (no different town within the cap, no new
  stops on the road, an infeasible plan), its card says why and cannot be picked.
- Losing the outbound trip because a return option failed.

## Constraints
- Planning cost: one request plans the outbound trip, the stay and four returns. With OSRM each plan makes several
  calls; the public server allows light use.
- Saved trips store request and result JSON; old trips have no return and must still load.
- Interests: none ticked = every interest (the rule already used for town tags and the stay).

## Approaches
- **X. All four planned in the same request (chosen: recommended, not objected to).** Instant switching; the pick
  lives in the saved request. Cost: five route plans per request.
- Y. Four summaries, full plan on click: lighter first answer, a round trip per pick, the same planning work.
- Z. A separate "plan my return" step: simplest, but the return is not part of the saved trip.

## How (decided)
- **When:** the return departs the morning after the stay's last day (or the day after arrival without a stay) at
  the outbound departure time, with the outbound daily limits, driving days, party, vehicle and costs.
- **Option 2 ("same way back"):** fixed waypoints = reverse of [outbound user waypoints, chosen detour places,
  overnight stops], interests ignored (no new detours).
- **Option 3 ("same road, new stops"):** `avoid` = outbound detour places plus the user's avoid list; the overnight
  town excludes outbound overnight towns. If it ends up with no detours and the same overnight places as option 1,
  the card says "No new stops found on this road".
- **Option 4 ("different town"):** candidates = gazetteer cities and towns at least 40 km from every outbound route
  polyline, with haversine(B, X) + haversine(X, A) ≤ 1.35 × haversine(B, A); pick the one farthest from the
  outbound route (cities before towns on ties); plan B → X → A with X as a fixed waypoint (no stay there).
- **Result shape:** `RoadTrip.returnTrip: ReturnTrip(int chosen, List<ReturnOption> options)`;
  `ReturnOption(int number, String kind, String label, String via, RoadTrip plan, String note)` with kinds `DIRECT`,
  `SAME_WAY`, `NEW_STOPS`, `DIFFERENT_TOWN`; `plan` is null when the option could not be built (`note` says why).
  The nested `plan` never has its own `stay` or `returnTrip`.
- **Failure:** an option that throws becomes `plan = null` with the error message as `note`; the outbound trip and
  other options are unaffected.
- **PDF:** after the stay, "Way home: <label>" and the picked option's days ("Return day N") with Maps links and its
  budget. One print sheet.

## Calls made while writing this up (check these)
- The checkbox is off by default, so ordinary road trips cost no extra planning; the demo turns it on.
- An unbuildable option's card is shown greyed out with its reason, not hidden, so there are always four cards.
- `returnChoice` pointing at an unbuildable option falls back to option 1 with a warning.
- Option 2 keeps the outbound overnight stops as overnight waypoints (same nights, reversed). If that is infeasible
  on the return's timings, they become plain stops and the card notes it.
- Option 3 also avoids the outbound overnight places (suggested hotels) and town stays, so its nights are new too.

## Left to the builder
- Component and helper names, card layout, test fixtures; reuse of `RoadTripPlanner` internals for "distance to the
  outbound polyline".
