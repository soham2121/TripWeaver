import type { Day, Stop } from '../../types/itinerary'

export type Entry = { at: string; stop?: Stop; meal?: Day['meals'][number] }

/** A day's stops and meals by start time; times before the day's start are after midnight, so they sort last. */
export function dayEntries(day: Day): Entry[] {
  const afterStart = (t: string) => (t < day.start ? '1' : '0') + t
  return [
    ...day.stops.map((s) => ({ at: s.start, stop: s })),
    ...day.meals.map((m) => ({ at: m.start, meal: m })),
  ].sort((a, b) => afterStart(a.at).localeCompare(afterStart(b.at)))
}
