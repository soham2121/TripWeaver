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
  budget?: { amount: number; basis: 'PER_PERSON' | 'TOTAL'; strictness: 'HARD' | 'FLEXIBLE' }
  costs?: { foodPerPersonPerDay?: number }
  keepStops?: { placeId: string; day: number }[]
  mustVisit?: string[]
  avoid?: string[]
  fillFreeTime?: boolean
  seed?: number
}

export interface BudgetLine {
  category: string
  item: string
  min: number | null
  max: number | null
  confidence: string
  basis: string
  note: string
}

export interface Budget {
  currency: string
  partySize: number
  lines: BudgetLine[]
  knownMin: number
  knownMax: number
  unknownItems: string[]
  complete: boolean
  limit: number | null
  limitBasis: string | null
  strictness: string | null
  status: string
  knownMaxPerPerson: number
}

export interface Workload {
  windowMinutes: number
  activityMinutes: number
  travelMinutes: number
  mealMinutes: number
  bufferMinutes: number
  waitMinutes: number
  freeMinutes: number
  utilisation: number
  stops: number
  highIntensityStops: number
  label: string
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
  workload: Workload
  warnings: string[]
  ordering: Ordering | null
  route: DayRoute | null
  navigationUrls: string[]
}

export interface Itinerary {
  days: Day[]
  budget: Budget
  coverage: {
    requested: string[]
    covered: string[]
    uncovered: string[]
    reasons: Record<string, string>
  }
  unscheduled: { placeId: string; name: string; kind: string; reason: string }[]
  repairs: { action: string; placeId: string; name: string; day: number; reason: string }[]
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
