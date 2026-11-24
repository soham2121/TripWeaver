export type Confidence =
  'VERIFIED' | 'PROVIDER_SOURCED' | 'CURATED_ESTIMATE' | 'USER_ENTERED' | 'SYNTHETIC' | 'UNKNOWN'

export interface CostEstimate {
  kind: string
  min: number
  max: number
  currency: string
  basis: string
  confidence: Confidence
}

export interface PlaceSummary {
  id: string
  name: string
  lat: number
  lon: number
  primaryCategory: string
  categories: string[]
  interests: string[]
  regionSlug: string | null
  regionName: string | null
  neighbourhood: string | null
  activity: boolean
  visitMinutesMin: number | null
  visitMinutesMax: number | null
  intensity: string | null
  costs: CostEstimate[]
  hasOpeningHours: boolean
  dataQuality: string
  missingFields: string[]
  accessibility: string[]
  seasonalClosedMonths: number[]
}

export interface Provenance {
  sourceType: string
  sourceUrl: string | null
  confidence: Confidence
  verifiedAt: string | null
  note: string | null
}

export interface PlaceDetail {
  summary: PlaceSummary
  description: string | null
  website: string | null
  osmRef: string | null
  osmUrl: string | null
  seasonal: { notes: string; closedMonths: number[] } | null
  accessibility: string[]
  costs: (CostEstimate & Provenance)[]
  openingHours: ({ raw: string } & Provenance)[]
  sources: ({ field: string; sourceRef: string | null } & Provenance)[]
  visitMinutesBasis: string | null
  visitMinutesConfidence: Confidence
  attribution: string
}

export interface Suggestion {
  type: 'TOWN' | 'PLACE' | 'CATEGORY' | 'INTEREST' | 'CUISINE' | 'NEIGHBOURHOOD' | 'REGION'
  label: string
  value: string
  placeId: string | null
  detail: string
  /** Set for TOWN. */
  lat?: number | null
  lon?: number | null
}

export interface Page<T> {
  items: T[]
  limit: number
  offset: number
  hasMore: boolean
}

export interface Destination {
  slug: string
  name: string
  bbox: [number, number, number, number]
  placeCount: number
  activityCount: number
  regions: { slug: string; name: string; lat: number; lon: number }[]
}

export const INTERESTS = [
  'beach',
  'food',
  'nightlife',
  'heritage',
  'culture',
  'spiritual',
  'nature',
  'viewpoint',
  'adventure',
  'water_sports',
  'wildlife',
  'shopping',
  'family',
  'sightseeing',
] as const
