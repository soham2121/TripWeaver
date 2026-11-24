# Days at the destination (road trip) — design

Date: 2026-11-25. Second of three pieces (PDF export done; return routes next).

## Intent
A road trip today ends on arrival at B. Travellers also spend days at B and want those days planned like the Plan
page does it: a day-by-day itinerary from where they stay. The road trip, its stay and (later) the way home should be
one trip: one plan, one save, one share link, one PDF.

## Goals
- Road-trip form: **Days at destination** (optional, 0–14; blank or 0 = no stay).
- The server plans the drive, then plans the stay at B with the existing itinerary planner, in the same request.
- The result shows the stay under the drive ("Your stay in <B>"): the itinerary days with map, stops, meals, Maps
  links and its budget, read-only.
- Saved trips, shared links, re-planning a saved trip and the PDF include the stay with no extra work by the user.
- The stay's last day is known in the result, so the return trip (piece 3) can start from it.

## Non-goals
- Editing the stay's stops (lock, remove, replace, must-visit) in this version: the stay is read-only.
- Choosing a separate pace, day hours or base inside B: the stay uses the itinerary defaults (09:00–19:00, balanced
  pace) with B's coordinates as the base.
- One merged trip total: the drive budget and the stay budget stay separate tables.
- Planning days at places other than B (overnight towns on the way).

## Anti-goals
- A stay that silently plans nothing: when B has no place data, the result says so.
- Losing the road trip because the stay failed: a stay problem is a warning on the trip, never an error for the whole
  request.

## Constraints
- Place data exists only for the Goa and Konkan catalogues (`PlaceCatalogue` destinations `goa`, `konkan`).
- The itinerary planner requires at least one interest (`NO_INTERESTS`) and allows 1–14 days (`MAX_DAYS`).
- Saved trips store the request and result JSON; old saved road trips have no stay and must still load.

## Approaches
- **A. Server-side, part of the road trip (chosen).** `RoadTripRequest.stayDays`; `RoadTripService` calls the
  itinerary planner after the road-trip planner; `RoadTrip.stay` carries the result. One request, and save, share,
  PDF and the return trip all see one object. Cost: backend and frontend model changes.
- B. Browser stitches a second call to `/itineraries/generate`. No backend change, but saved/shared trips, the PDF
  and the return trip would each have to combine two results.
- C. A link to the Plan page with B and the dates filled in. Almost no code, but the stay is a separate plan.

## How (decided)
- **Arrival rule (user-confirmed):** if the road trip arrives at B by 14:00, the arrival date is stay day 1 and that
  day starts one hour after arrival (never before the 09:00 default); otherwise stay day 1 is the next date.
  The stay has `stayDays` days from that start date.
- **Which catalogue:** the destination whose catalogue has a place nearest to B, if that place is within 20 km.
  Otherwise there is no itinerary and the stay carries the note "No place data near <B>: the stay is counted, but its
  days are not planned." (Goa and the Konkan coast only today.)
- **Inputs carried over:** base = B (name, lat, lon); party size, transport, interests, food per person per day and
  stay per night from the road-trip request. **No interests ticked = every interest at equal weight** (the same rule
  the stay-option tags use). Seed = the road trip's seed, so a re-plan is repeatable.
- **Result shape:** `RoadTrip.stay` (null when `stayDays` is 0 or missing):
  `Stay(int days, LocalDate firstDay, LocalDate lastDay, String destination, Itinerary itinerary, String note)`;
  `destination` and `itinerary` are null when B has no place data. `lastDay` is what the return trip starts after.
- **Failure:** any `ApiException` from the itinerary planner becomes a road-trip warning plus a stay with
  `itinerary = null` and the message as `note`.
- **Frontend:** the field sits next to "Driving days"; the stay renders with `ItineraryView` (no `onEdit`) under the
  road-trip days, with a heading "Your stay in <B> · <firstDay>–<lastDay>".
- **PDF:** the road-trip PDF lists the stay days after the drive days. Only one print sheet may exist on a page
  (two sheets would fight over the theme and title), so the embedded `ItineraryView` does not render its own sheet;
  the road-trip sheet renders the stay days with the same day layout as the itinerary PDF.

## Calls made while writing this up (check these)
- `stayDays` outside 0–14 is a bad request (`BAD_STAY_DAYS`), like the itinerary's day limit.
- "Arrives by 14:00" is inclusive: 14:00 exactly counts the arrival day.
- The Mumbai → Goa demo scenario gets 2 days at the destination, so the demo shows the stay.
- Old saved road trips have no `stay`; the page shows them as before.

## Left to the builder
- Exact field and component names beyond those above, the form control's wording and help text, test fixtures.
- Whether the "nearest place within 20 km" lookup reuses `SpatialPlaces` or scans the catalogue (a few thousand
  places; either is fine).
