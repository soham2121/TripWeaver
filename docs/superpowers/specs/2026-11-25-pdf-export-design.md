# PDF export of day-by-day plans — design (short)

Date: 2026-11-25. Size: small change. First of three road-trip/export pieces (then stay days at the destination, then return routes).

## Intent
Travellers want the plan on paper or as a file on their phone. One "Save as PDF" button on every plan result
(Plan today, multi-day trip, road trip) gives the whole trip, day by day, with the Google Maps links still working.

## Goals
- A "Save as PDF" button on the itinerary result and on the road-trip result. Because `ItineraryView` and
  `RoadTripView` are shared, saved trips (`/trips/:id`) and shared links (`/shared/:token`) get it too.
- The PDF lists every day (not only the selected tab): date, each stop or item with its times, travel between
  stops, meals, overnight stays, and the budget table.
- Each day keeps its "Open in Google Maps" links, clickable in the PDF (one link per part when a day has more than
  9 waypoints).

## Non-goals
- No map image in the PDF (MapLibre is a WebGL canvas; printing it is unreliable).
- No one-click file download, no QR codes, no server-side rendering.

## Anti-goals
- Light text on white paper: dark mode must not leak into the PDF.
- A PDF that shows only the day tab that happened to be open.

## Approaches
- **A. Print layout + browser "Save as PDF" (chosen by the user).** No new dependency; links stay clickable. The
  user picks "Save as PDF" in the print dialog.
- B. jsPDF in the browser: one-click download, but a new dependency and hand-drawn layout per plan type.
- C. Server-side PDF (Java): one-click, but a new backend library and endpoint, and slow on Render's free plan
  after it sleeps.

## How (decided)
- A print-only component per plan type (`hidden print:block`) renders all days in a plain, compact layout. The
  interactive result, the form panel, the site header and footer get `print:hidden`.
- The button switches the page to the light theme for the print, sets `document.title` to a useful name (e.g.
  "TripWeaver – 3-day trip from Colva", which becomes the default PDF file name), calls `window.print()`, and
  restores both afterwards.
- Each day starts on a block that avoids breaking across pages where it fits (`break-inside: avoid`).

## Left to the builder
- Exact wording and layout of the print component, reuse of existing helpers (`BudgetTable`, label/time
  formatters), and the test shape (one component test per view that the print block lists every day and the
  Maps links).
