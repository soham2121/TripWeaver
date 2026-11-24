import type { Budget, DayRoute, Itinerary } from './itinerary'
import type { PlaceSummary } from './place'

export interface Waypoint {
  name: string
  lat?: number
  lon?: number
  placeId?: string
  overnight?: boolean
  stayMinutes?: number
}

export interface RoadTripRequest {
  origin: Waypoint
  destination: Waypoint
  waypoints: Waypoint[]
  departureDate?: string
  departureTime?: string
  arriveBy?: string
  maxDrivingHoursPerDay?: number
  maxContinuousDrivingMinutes?: number
  interests: Record<string, number>
  maxDetourMinutes?: number
  maxDetours?: number
  partySize?: number
  transport?: 'CAR' | 'SCOOTER' | 'TAXI'
  mileageKmPerLitre?: number
  fuelPricePerLitre?: number
  tollsTotal?: number
  accommodationPerNight?: number
  foodPerPersonPerDay?: number
  avoid?: string[]
  drivingDays?: number
  stayChoices?: Record<string, string>
  /** Days at the destination, planned like the Plan page (0–14). */
  stayDays?: number
  /** Also plan four ways home after the stay. */
  planReturn?: boolean
  /** The way home picked, 1–4. */
  returnChoice?: number
  /** Town keys not to suggest as town stays. */
  avoidTowns?: string[]
}

export interface RoadTripItem {
  type: 'DEPART' | 'DRIVE' | 'REST' | 'MEAL' | 'DETOUR' | 'WAYPOINT' | 'OVERNIGHT' | 'ARRIVE'
  name: string
  placeId: string | null
  lat: number | null
  lon: number | null
  start: string
  end: string
  minutes: number
  km: number
  note: string | null
}

export interface RoadTripDay {
  day: number
  date: string
  start: string
  end: string
  drivingMinutes: number
  km: number
  longestContinuousDrivingMinutes: number
  items: RoadTripItem[]
  overnight: {
    name: string
    placeId: string | null
    lat: number
    lon: number
    kind: 'USER' | 'TOWN' | 'SUGGESTED'
    confidence: string
    note: string
  } | null
  navigationUrls: string[]
}

export interface DetourStop {
  place: PlaceSummary
  addedDriveMinutes: number
  visitMinutes: number
  covers: string[]
  score: number
  reason: string
}

export interface Segment {
  index: number
  from: string
  to: string
  directMinutes: number
  directKm: number
  detours: DetourStop[]
  ordering: {
    method: string
    applied: boolean
    routeOrderMinutes: number
    doubleTreeMinutes: number | null
  }
  route: DayRoute | null
}

export interface RoadTrip {
  days: RoadTripDay[]
  segments: Segment[]
  budget: Budget
  departure: string
  arrival: string
  totalDrivingMinutes: number
  totalKm: number
  nights: number
  /** Absent on trips saved before town stays existed. */
  stayOptions?: NightStays[]
  detourCandidates: number
  warnings: string[]
  travelProvider: string
  travelBasis: string
  disclaimer: string
  seed: number
  /** Days at the destination; absent or null without stayDays and on trips saved before stays existed. */
  stay?: RoadTripStay | null
  /** The ways home; absent or null without planReturn and on older trips. */
  returnTrip?: ReturnTrip | null
}

/** The four ways home; chosen is the one shown (1–4). */
export interface ReturnTrip {
  chosen: number
  options: ReturnOption[]
}

/** One way home; plan null when it could not be built (note says why). */
export interface ReturnOption {
  number: number
  kind: 'DIRECT' | 'SAME_WAY' | 'NEW_STOPS' | 'DIFFERENT_TOWN'
  label: string
  via: string | null
  plan: RoadTrip | null
  note: string | null
}

/** The stay at the destination; itinerary null when the destination has no place data (note says why). */
export interface RoadTripStay {
  days: number
  firstDay: string
  lastDay: string
  /** Nights slept at the destination: from the arrival night to the morning after lastDay. */
  nights: number
  destination: string | null
  itinerary: Itinerary | null
  note: string | null
}

export interface RoadTripResponse {
  runId: string
  durationMicros: number
  roadTrip: RoadTrip
}

export interface TownOption {
  town: string
  /** Goes back in stayChoices. */
  key: string
  lat: number
  lon: number
  driveFromStartMinutes: number
  detourMinutes: number
  /** Things to do within 15 km. */
  sights: number
  topSights: string[]
  /** Places to stay listed in OpenStreetMap within 5 km. */
  stays: number
  stayNames: string[]
  /** 1–5, 5 = on the route. Absent on trips saved before ratings existed. */
  detourRating?: number
  /** Things to do within 15 km matching the interests (all interests when none are chosen). */
  interestMatches?: number
  /** 0–5 from interestMatches. */
  interestRating?: number
  chosen: boolean
}

export interface NightStays {
  night: number
  targetDriveMinutes: number
  options: TownOption[]
}
