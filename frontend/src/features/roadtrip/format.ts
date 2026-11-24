import type { RoadTripDay, RoadTripItem } from '../../types/roadtrip'

/** HH:MM of an API LocalTime ("07:00:00"). */
export const hhmm = (t: string) => t.slice(0, 5)

export const hours = (m: number) => `${Math.floor(m / 60)} h ${m % 60} min`

export const ITEM_TEXT: Record<RoadTripItem['type'], (i: RoadTripItem) => string> = {
  DEPART: (i) => `Leave ${i.name}`,
  DRIVE: (i) => `Drive ${i.minutes} min, ${i.km.toFixed(0)} km`,
  REST: (i) => `${i.name} (${i.minutes} min)`,
  MEAL: (i) => `${i.name} (${i.minutes} min)`,
  DETOUR: (i) => `Detour: ${i.name} (${i.minutes} min)`,
  WAYPOINT: (i) => `Stop: ${i.name}${i.minutes ? ` (${i.minutes} min)` : ''}`,
  OVERNIGHT: (i) => `Overnight: ${i.name}`,
  ARRIVE: (i) => `Arrive ${i.name}`,
}

/** How a day's overnight came about: the user's waypoint, a town splitting the drive, or a suggestion. */
export const stayKind = (o: NonNullable<RoadTripDay['overnight']>) =>
  o.kind === 'SUGGESTED' ? 'Suggested stay' : o.kind === 'TOWN' ? 'Town stay' : 'Your stay'
