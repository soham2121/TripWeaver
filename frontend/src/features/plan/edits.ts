import type { Itinerary, ItineraryRequest } from '../../types/itinerary'

/** A user edit on a generated itinerary. Every edit re-runs the planner (D32). */
export type Edit =
  | { kind: 'remove' | 'replace'; placeId: string }
  | { kind: 'move'; placeId: string; day: number }
  | { kind: 'replan'; locked: string[] }
  | { kind: 'must'; placeId: string }

/** Builds the next planner request from the last one (which carries earlier edits) and the plan on screen. */
export function applyEdit(base: ItineraryRequest, it: Itinerary, e: Edit): ItineraryRequest {
  const stops = it.days.flatMap((d) => d.stops.map((s) => ({ placeId: s.place.id, day: d.day })))
  const avoid = base.avoid ?? []
  const mustVisit = base.mustVisit ?? []
  switch (e.kind) {
    case 'remove':
    case 'replace':
      return {
        ...base,
        avoid: [...avoid, e.placeId],
        mustVisit: mustVisit.filter((m) => m !== e.placeId),
        keepStops: stops.filter((k) => k.placeId !== e.placeId),
        fillFreeTime: e.kind === 'replace',
      }
    case 'move':
      return {
        ...base,
        keepStops: stops.map((k) => (k.placeId === e.placeId ? { ...k, day: e.day } : k)),
        fillFreeTime: false,
      }
    case 'replan':
      return {
        ...base,
        keepStops: stops.filter((k) => e.locked.includes(k.placeId)),
        fillFreeTime: true,
      }
    case 'must':
      return {
        ...base,
        avoid: avoid.filter((a) => a !== e.placeId),
        mustVisit: [...new Set([...mustVisit, e.placeId])],
        keepStops: stops,
        fillFreeTime: false,
      }
  }
}
