import type { Budget, DayRoute } from './itinerary'
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
  chosen: boolean
}

export interface NightStays {
  night: number
  targetDriveMinutes: number
  options: TownOption[]
}
