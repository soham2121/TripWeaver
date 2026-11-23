import type { PlaceSummary } from './place'

export type Pace = 'RELAXED' | 'BALANCED' | 'PACKED'
export type Transport = 'CAR' | 'SCOOTER' | 'TAXI' | 'WALK'

export interface ItineraryRequest {
  baseLat: number
  baseLon: number
  baseName?: string
  startDate?: string
  days: number
  partySize: number
  interests: Record<string, number>
  pace: Pace
  transport: Transport
  dayStart?: string
  dayEnd?: string
  returnToBase?: boolean
  seed?: number
}

export interface Money {
  min: number | null
  max: number | null
  unknown: boolean
  confidence: string
  note: string
}

export interface Stop {
  order: number
  place: PlaceSummary
  arrive: string
  start: string
  end: string
  visitMinutes: number
  visitBasis: string
  travelMinutes: number
  travelKm: number
  waitMinutes: number
  kind: string
  covers: string[]
  cost: Money
  hours: { raw: string | null; confidence: string; status: string }
  reason: string
}

export interface Ordering {
  applied: string
  method: string
  insertionCost: number
  doubleTreeCost: number | null
  mstCost: number | null
  nearestNeighbourCost: number | null
  exactCost: number | null
  metric: boolean
  guaranteeApplies: boolean
  metricViolations: string[]
  note: string
}

export interface DayRoute {
  provider: string
  basis: string
  geometry: [number, number][]
  totalMinutes: number
  totalKm: number
  warnings: string[]
}

export interface Day {
  day: number
  date: string
  dayOfWeek: string
  start: string
  end: string
  pace: Pace
  returnToBase: boolean
  stops: Stop[]
  meals: { label: string; start: string; end: string; place: string | null }[]
  returnTravelMinutes: number
  endOfDay: string
  workload: { utilisation: number; freeMinutes: number; travelMinutes: number; label: string }
  warnings: string[]
  ordering: Ordering | null
  route: DayRoute | null
  navigationUrls: string[]
}

export interface Itinerary {
  days: Day[]
  budget: { status: string; knownMax: number; unknownItems: string[]; complete: boolean }
  coverage: {
    requested: string[]
    covered: string[]
    uncovered: string[]
    reasons: Record<string, string>
  }
  unscheduled: { placeId: string; name: string; kind: string; reason: string }[]
  warnings: string[]
  travelProvider: string
  travelBasis: string
  disclaimer: string
  seed: number
}

export interface ItineraryResponse {
  runId: string
  durationMicros: number
  itinerary: Itinerary
}
