import type { Itinerary, ItineraryRequest } from './itinerary'
import type { RoadTrip, RoadTripRequest } from './roadtrip'

export interface AppUser {
  id: string
  username: string
  displayName: string | null
}

export type Role = 'OWNER' | 'EDITOR' | 'VIEWER'
export type TripKind = 'ITINERARY' | 'ROAD_TRIP'

export interface Member {
  userId: string
  username: string
  displayName: string | null
  role: Role
}

export interface Trip {
  id: string
  title: string
  kind: TripKind
  role: Role
  owner: AppUser
  members: Member[]
  version: number
  request: ItineraryRequest | RoadTripRequest
  result: Itinerary | RoadTrip | null
  createdAt: string
  updatedAt: string
}

export interface TripSummary {
  id: string
  title: string
  kind: TripKind
  role: Role
  ownerUsername: string
  version: number
  updatedAt: string
}

export interface SharedTrip {
  title: string
  kind: TripKind
  result: Itinerary | RoadTrip | null
  updatedAt: string
}

export interface ShareLink {
  id: string
  token: string | null
  createdAt: string
  expiresAt: string | null
  revokedAt: string | null
  active: boolean
}

export interface TripSuggestion {
  id: string
  placeId: string
  placeName: string
  day: number | null
  note: string | null
  suggestedBy: string
  createdAt: string
  up: number
  down: number
  score: number
  myVote: number
}

export interface HistoryEntry {
  action: string
  summary: string
  username: string | null
  version: number
  at: string
}

export interface Expenses {
  items: {
    id: string
    description: string
    amount: number
    paidById: string
    paidBy: string
    createdAt: string
  }[]
  total: number
  perPerson: number
  balances: { userId: string; username: string; paid: number; share: number; balance: number }[]
}
