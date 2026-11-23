import type { Destination, PlaceDetail, PlaceSummary } from '../types/place'

export function summary(over: Partial<PlaceSummary> = {}): PlaceSummary {
  return {
    id: 'p1',
    name: 'Baga Beach',
    lat: 15.556,
    lon: 73.751,
    primaryCategory: 'beach',
    categories: ['beach'],
    interests: ['beach'],
    regionSlug: 'north-coast',
    regionName: 'North Goa coast',
    neighbourhood: 'Baga',
    activity: true,
    visitMinutesMin: 90,
    visitMinutesMax: 180,
    intensity: 'LOW',
    costs: [],
    hasOpeningHours: false,
    dataQuality: 'PARTIAL',
    missingFields: ['cost', 'opening_hours'],
    ...over,
  }
}

export const museum = summary({
  id: 'p2',
  name: 'Archeological Museum',
  primaryCategory: 'museum',
  interests: ['culture', 'heritage'],
  costs: [
    {
      kind: 'ENTRY',
      min: 10,
      max: 10,
      currency: 'INR',
      basis: 'PER_PERSON',
      confidence: 'VERIFIED',
    },
  ],
  missingFields: ['description'],
})

export const goa: Destination = {
  slug: 'goa',
  name: 'Goa',
  bbox: [14.9, 73.6, 15.8, 74.3],
  placeCount: 5192,
  activityCount: 3683,
  regions: [{ slug: 'north-coast', name: 'North Goa coast', lat: 15.54, lon: 73.76 }],
}

export function detail(s: PlaceSummary): PlaceDetail {
  return {
    summary: s,
    description: null,
    website: null,
    osmRef: 'node/1',
    osmUrl: 'https://www.openstreetmap.org/node/1',
    seasonal: null,
    accessibility: [],
    costs: s.costs.map((c) => ({
      ...c,
      sourceType: 'OFFICIAL',
      sourceUrl: 'https://gov.example/x',
      verifiedAt: '2026-11-23T00:00:00Z',
      note: null,
    })),
    openingHours: [],
    sources: [
      {
        field: 'name,location,category',
        sourceType: 'OSM',
        sourceRef: 'node/1',
        sourceUrl: 'https://www.openstreetmap.org/node/1',
        confidence: 'PROVIDER_SOURCED',
        verifiedAt: null,
        note: null,
      },
    ],
    visitMinutesBasis: 'TripWeaver category default for beach',
    visitMinutesConfidence: 'CURATED_ESTIMATE',
    attribution: '© OpenStreetMap contributors (ODbL)',
  }
}
