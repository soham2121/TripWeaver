# PDF export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Save as PDF" button on every itinerary and road-trip result that prints a light, map-free, all-days plan with clickable Google Maps links.

**Architecture:** Each result view renders a print-only sheet into `document.body` through a React portal. CSS hides `#root` while printing whenever a sheet exists, so only the sheet prints. The sheet listens for `beforeprint`/`afterprint` to switch to the light theme and set `document.title` (the default PDF file name), so Ctrl+P behaves the same as the button.

**Tech Stack:** React 19, TypeScript, Tailwind v4 (`print:` variant), Vitest + Testing Library (jsdom). No new dependency.

**Spec:** `docs/superpowers/specs/2026-11-25-pdf-export-design.md`

## Global Constraints

- No new npm dependency; no backend change.
- Button label exactly `Save as PDF`.
- Document title while printing: `TripWeaver – <title>` (en dash), restored afterwards.
- The theme is `light` while printing (`document.documentElement.dataset.theme`), restored afterwards.
- No map in the print sheet. Every day is listed, not only the selected tab.
- Google Maps links keep their `href`; text `Open day N in Google Maps`, plus ` (part k)` when a day has more than one URL.
- Dark mode works by remapping Tailwind scales under `:root[data-theme='dark']`; do not add `dark:` variants.
- Run `npx prettier --write` on touched files; `npm run lint`, `npx tsc -b` and `npx vitest run` must pass.

## Review Focus

1. Dark mode on when printing: the sheet must print light (Task 1 test: theme is `light` between `beforeprint` and `afterprint`).
2. User cancels the print dialog: theme and title come back (Task 1 test: `afterprint` restores both; browsers fire it on cancel too).
3. Ctrl+P on a page without a plan prints the page normally, not a blank sheet (Task 1: `#root` is hidden only when a `.print-sheet` exists; CSS uses `body:has(> .print-sheet)`).
4. A day with more than 9 waypoints has several Maps URLs: every part is in the PDF (Task 2 test with two URLs).
5. Read-only views (saved trip for viewers, shared link) still get the button (Task 2 and Task 3 tests render without edit callbacks).

---

### Task 1: Print sheet and button

**Files:**
- Create: `frontend/src/components/PrintSheet.tsx`
- Modify: `frontend/src/index.css` (add a `@media print` block at the end)
- Test: `frontend/src/components/PrintSheet.test.tsx`

**Interfaces:**
- Produces: `PrintSheet({ title, children }: { title: string; children: React.ReactNode }): JSX.Element` — portal into `document.body`, root element `<section className="print-sheet hidden print:block" aria-label="Printable plan">` with an `<h1>` of `title`, then `children`.
- Produces: `PrintButton(): JSX.Element` — `<button type="button" className="btn-sm print:hidden">Save as PDF</button>` calling `window.print()`.

- [ ] **Step 1: Write the failing tests**

```tsx
test('the sheet prints light with a file-friendly title, then restores both', () => {
  document.documentElement.dataset.theme = 'dark'
  document.title = 'TripWeaver'
  render(<PrintSheet title="3-day trip from Colva"><p>body</p></PrintSheet>)
  expect(screen.getByRole('region', { name: 'Printable plan' })).toHaveTextContent('3-day trip from Colva')
  act(() => window.dispatchEvent(new Event('beforeprint')))
  expect(document.documentElement.dataset.theme).toBe('light')
  expect(document.title).toBe('TripWeaver – 3-day trip from Colva')
  act(() => window.dispatchEvent(new Event('afterprint')))
  expect(document.documentElement.dataset.theme).toBe('dark')
  expect(document.title).toBe('TripWeaver')
})

test('Save as PDF opens the print dialog', async () => {
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  render(<PrintButton />)
  await userEvent.click(screen.getByRole('button', { name: 'Save as PDF' }))
  expect(print).toHaveBeenCalledOnce()
})
```

- [ ] **Step 2: Run** `npx vitest run src/components/PrintSheet.test.tsx` — expect FAIL (module not found).

- [ ] **Step 3: Implement `PrintSheet` and `PrintButton`** in `PrintSheet.tsx`. `PrintSheet` registers `beforeprint`/`afterprint` listeners in a `useEffect` (store the previous theme and title on `beforeprint`, restore on `afterprint`; remove listeners on unmount). Print styles inside the sheet: plain `text-sm text-zinc-900`, `h2` per day, `break-inside-avoid` on each day block.

- [ ] **Step 4: Add the CSS** to `index.css`:

```css
@media print {
  body:has(> .print-sheet) > #root { display: none; }
  @page { margin: 14mm; }
}
```

- [ ] **Step 5: Run** the test file — expect PASS.

- [ ] **Step 6: Commit** `feat: print sheet and Save as PDF button`.

---

### Task 2: Itinerary print (Plan today and multi-day)

**Files:**
- Create: `frontend/src/features/plan/ItineraryPrint.tsx`
- Modify: `frontend/src/features/plan/ItineraryView.tsx` (move the stop/meal ordering into `dayEntries`, add `title` prop, render `PrintButton` in the top notice card and `ItineraryPrint`)
- Modify: `frontend/src/features/plan/PlanPage.tsx:423`, `frontend/src/features/trips/TripPage.tsx:189`, `frontend/src/features/trips/SharedPage.tsx:49` (pass `title`)
- Test: `frontend/src/features/plan/ItineraryPrint.test.tsx`

**Interfaces:**
- Consumes: `PrintSheet`, `PrintButton` (Task 1).
- Produces: `dayEntries(day: Day): { at: string; stop?: Stop; meal?: Day['meals'][number] }[]` — stops and meals sorted by start, times before `day.start` last (the existing `afterStart` rule). `ItineraryView` imports it instead of building entries inline.
- Produces: `ItineraryPrint({ it, title }: { it: Itinerary; title: string })`.
- `ItineraryView` gains `title?: string`; fallback `` `${it.days.length}-day trip` ``. PlanPage passes the same string it gives `SaveTrip` as `defaultTitle`; TripPage and SharedPage pass `trip.title`.

Per day the sheet shows: `Day N · <Weekday> <date> · <start>–<end>`; then each entry in `dayEntries` order: stop `HH:MM–HH:MM <place name>` with ` · <travelMinutes> min travel` when > 0; meal `<label> HH:MM–HH:MM` plus ` at <place>` or ` (break)`; then `<returnTravelMinutes> min back to base` when `returnToBase`; then the Maps links. After the days: `<h2>Budget</h2>` and `BudgetTable`, then `it.disclaimer` in small text.

- [ ] **Step 1: Write the failing tests** (use `itineraryResponse()` from `src/test/planFixtures`):

```tsx
test('the printable itinerary lists every day, stop, meal and Maps link', () => {
  const it = itineraryResponse().itinerary
  render(<ItineraryView it={it} base={{ lat: 15.52, lon: 73.76 }} title="2-day trip from Candolim" />)
  expect(screen.getByRole('button', { name: 'Save as PDF' })).toBeInTheDocument()
  const sheet = within(screen.getByRole('region', { name: 'Printable plan' }))
  expect(sheet.getByRole('heading', { name: /^Day 1/ })).toBeInTheDocument()
  expect(sheet.getByRole('heading', { name: /^Day 2/ })).toBeInTheDocument()
  for (const n of ['Candolim Beach', 'Fort Aguada', 'Calangute Beach']) expect(sheet.getByText(new RegExp(n))).toBeInTheDocument()
  expect(sheet.getAllByText(/Lunch 12:30–13:30/)).toHaveLength(2)
  expect(sheet.getByRole('link', { name: 'Open day 1 in Google Maps' })).toHaveAttribute('href', it.days[0].navigationUrls[0])
})

test('a day split into several Maps links prints every part', () => {
  const it = itineraryResponse().itinerary
  it.days[0].navigationUrls = ['https://www.google.com/maps/dir/?api=1&a', 'https://www.google.com/maps/dir/?api=1&b']
  render(<ItineraryView it={it} base={{ lat: 15.52, lon: 73.76 }} />)
  const sheet = within(screen.getByRole('region', { name: 'Printable plan' }))
  expect(sheet.getByRole('link', { name: 'Open day 1 in Google Maps (part 2)' })).toHaveAttribute('href', 'https://www.google.com/maps/dir/?api=1&b')
  expect(sheet.getByRole('heading', { name: '2-day trip' })).toBeInTheDocument()
})
```

- [ ] **Step 2: Run** `npx vitest run src/features/plan` — expect the new tests to FAIL, existing ones to pass.
- [ ] **Step 3: Implement** `dayEntries` + `ItineraryPrint`, wire `ItineraryView` and the three call sites.
- [ ] **Step 4: Run** `npx vitest run src/features/plan src/features/trips` — expect PASS.
- [ ] **Step 5: Commit** `feat: printable itinerary for Plan today and multi-day trips`.

---

### Task 3: Road-trip print

**Files:**
- Create: `frontend/src/features/roadtrip/format.ts` (move `ITEM_TEXT` and `hours` out of `RoadTripPage.tsx`, exported)
- Create: `frontend/src/features/roadtrip/RoadTripPrint.tsx`
- Modify: `frontend/src/features/roadtrip/RoadTripPage.tsx` (import from `format.ts`; `RoadTripView` gains `title?: string`, renders `PrintButton` at the top and `RoadTripPrint`)
- Modify: `frontend/src/features/trips/TripPage.tsx:211`, `frontend/src/features/trips/SharedPage.tsx:50` (pass `trip.title`)
- Test: `frontend/src/features/roadtrip/RoadTripPrint.test.tsx`

**Interfaces:**
- Consumes: `PrintSheet`, `PrintButton` (Task 1).
- Produces: `ITEM_TEXT: Record<RoadTripItem['type'], (i: RoadTripItem) => string>`, `hours(m: number): string` in `format.ts`.
- Produces: `RoadTripPrint({ trip, title }: { trip: RoadTrip; title: string })`.
- `RoadTripView` title fallback: `` `Road trip: ${trip.segments[0].from} to ${trip.segments.at(-1)!.to}` `` (fall back to `Road trip` when there are no segments).

Per day: `Day N · <date> · <hours(drivingMinutes)> driving, <km> km`; each item `HH:MM <ITEM_TEXT>` plus ` · <note>` when present; the overnight line (`Town stay` / `Suggested stay` / `Your stay`: name and note); the Maps links. Then the chosen stays are already in the days, so no stay-options list; then `Budget` + `BudgetTable` and `trip.disclaimer`.

- [ ] **Step 1: Write the failing test** (use `roadTripResponse()`):

```tsx
test('the printable road trip lists every day, item and Maps link', () => {
  const trip = roadTripResponse().roadTrip
  render(<RoadTripView trip={trip} />)
  expect(screen.getByRole('button', { name: 'Save as PDF' })).toBeInTheDocument()
  const sheet = within(screen.getByRole('region', { name: 'Printable plan' }))
  expect(sheet.getByRole('heading', { name: /^Road trip: Mumbai \(CSMT\) to/ })).toBeInTheDocument()
  for (const t of ['07:00 Leave Mumbai (CSMT)', 'Detour: Magen David Synagogue', 'Overnight: Manjunath', 'Arrive Candolim'])
    expect(sheet.getByText(new RegExp(t.replace(/[()]/g, '\\$&')))).toBeInTheDocument()
  expect(sheet.getByRole('link', { name: 'Open day 2 in Google Maps' })).toHaveAttribute('href', trip.days[1].navigationUrls[0])
})
```

- [ ] **Step 2: Run** `npx vitest run src/features/roadtrip` — expect FAIL.
- [ ] **Step 3: Implement** `format.ts`, `RoadTripPrint`, wire `RoadTripView` and the two call sites.
- [ ] **Step 4: Run** `npx vitest run` (whole suite), `npx tsc -b`, `npm run lint` — expect all green.
- [ ] **Step 5: Check in the browser** (local dev server): for a multi-day plan and the Mumbai–Goa road trip with Driving days 2, in dark mode, open print preview with the button; expect a light page, all days, no map, clickable links, and the title `TripWeaver – …` as the file name. Then cancel: the page is dark again.
- [ ] **Step 6: Commit** `feat: printable road trip`.
