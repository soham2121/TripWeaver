import type { ItineraryResponse, Stop } from '../types/itinerary'
import type { RoadTripItem, RoadTripResponse, RoadTripStay, ReturnTrip } from '../types/roadtrip'
import { summary } from './fixtures'

export const stop = (order: number, name: string, id: string): Stop => ({
  order,
  place: { ...summary(), id, name },
  arrive: '09:10:00',
  start: '09:10:00',
  end: '10:10:00',
  visitMinutes: 60,
  visitBasis: 'balanced pace from the 60–90 min category default range',
  travelMinutes: 10,
  travelKm: 3.2,
  waitMinutes: 0,
  kind: 'COVER',
  covers: ['beach'],
  cost: { min: null, max: null, unknown: true, confidence: 'UNKNOWN', note: 'unknown' },
  hours: { raw: null, confidence: 'UNKNOWN', status: 'UNKNOWN' },
  reason: 'covers beach',
})

const workload = {
  windowMinutes: 600,
  activityMinutes: 240,
  travelMinutes: 40,
  mealMinutes: 60,
  bufferMinutes: 20,
  waitMinutes: 0,
  freeMinutes: 90,
  utilisation: 0.6,
  stops: 2,
  highIntensityStops: 0,
  label: 'BALANCED',
}

const day = (n: number, stops: Stop[]) => ({
  day: n,
  date: `2026-12-1${3 + n}`,
  dayOfWeek: n === 1 ? 'MONDAY' : 'TUESDAY',
  start: '09:00:00',
  end: '19:00:00',
  pace: 'BALANCED' as const,
  returnToBase: true,
  stops,
  meals: [{ label: 'Lunch', start: '12:30:00', end: '13:30:00', place: null }],
  returnTravelMinutes: 12,
  endOfDay: '16:00:00',
  workload,
  warnings: [],
  ordering: {
    applied: 'DOUBLE_TREE',
    method: 'double-tree',
    insertionCost: 50,
    doubleTreeCost: 44,
    mstCost: 30,
    nearestNeighbourCost: 46,
    exactCost: 44,
    metric: true,
    guaranteeApplies: true,
    metricViolations: [],
    note: 'Double-Tree order saves 6 min over the insertion order.',
  },
  route: {
    provider: 'OSRM',
    basis: 'OSRM road network',
    geometry: [
      [73.76, 15.52],
      [73.77, 15.5],
      [73.76, 15.52],
    ] as [number, number][],
    totalMinutes: 40,
    totalKm: 12.4,
    warnings: [],
  },
  navigationUrls: ['https://www.google.com/maps/dir/?api=1&origin=15.52,73.76'],
})

export function itineraryResponse(): ItineraryResponse {
  return {
    runId: 'r1',
    durationMicros: 1000,
    itinerary: {
      days: [
        day(1, [stop(1, 'Candolim Beach', 'a'), stop(2, 'Fort Aguada', 'b')]),
        day(2, [stop(1, 'Calangute Beach', 'c')]),
      ],
      budget: {
        currency: 'INR',
        partySize: 2,
        lines: [
          {
            category: 'ENTRY_FEES',
            item: 'Fort Aguada',
            min: 100,
            max: 100,
            confidence: 'VERIFIED',
            basis: '₹50 × 2 people',
            note: '',
          },
          {
            category: 'FOOD',
            item: 'Meals',
            min: null,
            max: null,
            confidence: 'UNKNOWN',
            basis: 'not entered',
            note: 'Enter a daily food budget',
          },
        ],
        knownMin: 100,
        knownMax: 100,
        unknownItems: ['Meals'],
        complete: false,
        limit: null,
        limitBasis: null,
        strictness: null,
        status: 'NO_LIMIT_INCOMPLETE',
        knownMaxPerPerson: 50,
      },
      coverage: {
        requested: ['beach', 'nightlife'],
        covered: ['beach'],
        uncovered: ['nightlife'],
        reasons: { nightlife: 'no open venue fits the day window' },
      },
      unscheduled: [],
      repairs: [],
      warnings: ['1 cost item(s) are unknown, so totals are incomplete.'],
      travelProvider: 'OSRM',
      travelBasis: 'OSRM road network (OpenStreetMap, car profile) + 5 min per leg',
      disclaimer: 'Day allocation is a greedy insertion heuristic.',
      seed: 42,
    },
  }
}

const item = (
  type: RoadTripItem['type'],
  name: string,
  start: string,
  minutes = 0,
  km = 0,
): RoadTripItem => ({
  type,
  name,
  placeId: null,
  lat: 16,
  lon: 73.5,
  start: `${start}:00`, // LocalTime, as the API sends it
  end: `${start}:00`,
  minutes,
  km,
  note: null,
})

export function roadTripResponse(): RoadTripResponse {
  return {
    runId: 'rt1',
    durationMicros: 2000,
    roadTrip: {
      days: [
        {
          day: 1,
          date: '2026-12-14',
          start: '07:00:00',
          end: '17:00:00',
          drivingMinutes: 350,
          km: 380,
          longestContinuousDrivingMinutes: 120,
          items: [
            item('DEPART', 'Mumbai (CSMT)', '07:00'),
            item('DRIVE', 'Drive', '07:00', 12, 9),
            item('DETOUR', 'Magen David Synagogue', '07:12', 33),
            item('REST', 'Rest break (roadside; location approximate)', '09:10', 15),
            item('OVERNIGHT', 'Manjunath', '17:00'),
          ],
          overnight: {
            name: 'Manjunath',
            placeId: 'h1',
            lat: 16.27,
            lon: 73.71,
            kind: 'SUGGESTED',
            confidence: 'PROVIDER_SOURCED',
            note: 'Listed in OpenStreetMap; availability and price unknown',
          },
          navigationUrls: ['https://www.google.com/maps/dir/?api=1&origin=18.9,72.8'],
        },
        {
          day: 2,
          date: '2026-12-15',
          start: '08:00:00',
          end: '11:44:00',
          drivingMinutes: 200,
          km: 230,
          longestContinuousDrivingMinutes: 120,
          items: [item('DEPART', 'Manjunath', '08:00'), item('ARRIVE', 'Candolim', '11:44')],
          overnight: null,
          navigationUrls: ['https://www.google.com/maps/dir/?api=1&origin=16.2,73.7'],
        },
      ],
      segments: [
        {
          index: 0,
          from: 'Mumbai (CSMT)',
          to: 'Candolim',
          directMinutes: 500,
          directKm: 590,
          detours: [
            {
              place: summary({ id: 'd1', name: 'Magen David Synagogue' }),
              addedDriveMinutes: 11,
              visitMinutes: 33,
              covers: ['heritage'],
              score: 0.76,
              reason: 'Ranked pool',
            },
          ],
          ordering: {
            method: 'route-order',
            applied: false,
            routeOrderMinutes: 511,
            doubleTreeMinutes: null,
          },
          route: {
            provider: 'OSRM',
            basis: 'OSRM',
            geometry: [
              [72.83, 18.93],
              [73.76, 15.52],
            ],
            totalMinutes: 511,
            totalKm: 600,
            warnings: [],
          },
        },
      ],
      budget: {
        currency: 'INR',
        partySize: 2,
        lines: [
          {
            category: 'FUEL',
            item: 'Fuel',
            min: 3826,
            max: 3826,
            confidence: 'CURATED_ESTIMATE',
            basis: '600 km / 15 km/l × ₹101',
            note: '',
          },
          {
            category: 'TOLLS',
            item: 'Tolls',
            min: null,
            max: null,
            confidence: 'UNKNOWN',
            basis: 'not entered',
            note: '',
          },
        ],
        knownMin: 3826,
        knownMax: 3826,
        unknownItems: ['Tolls'],
        complete: false,
        limit: null,
        limitBasis: null,
        strictness: null,
        status: 'NO_LIMIT_INCOMPLETE',
        knownMaxPerPerson: 1913,
      },
      departure: '2026-12-14T07:00:00',
      arrival: '2026-12-15T11:44:00',
      totalDrivingMinutes: 550,
      totalKm: 610,
      nights: 1,
      detourCandidates: 317,
      warnings: ["Interest 'beach' is not covered by any detour within the detour limits."],
      travelProvider: 'OSRM',
      travelBasis: 'OSRM road network',
      disclaimer: 'Detour selection and day split are heuristics.',
      seed: 7,
    },
  }
}

/** Two planned days at Candolim after the drive (shape of RoadTrip.stay). */
export function stayFixture(): RoadTripStay {
  return {
    days: 2,
    firstDay: '2026-12-15',
    lastDay: '2026-12-16',
    nights: 2,
    destination: 'goa',
    itinerary: itineraryResponse().itinerary,
    note: null,
  }
}

/** Four ways home: 1–3 planned (the outbound fixture reused), 4 could not be built. */
export function returnFixture(chosen = 1): ReturnTrip {
  const plan = { ...roadTripResponse().roadTrip, stay: null, returnTrip: null }
  return {
    chosen,
    options: [
      { number: 1, kind: 'DIRECT', label: 'Direct', via: null, plan, note: null },
      { number: 2, kind: 'SAME_WAY', label: 'Same way back', via: null, plan, note: null },
      { number: 3, kind: 'NEW_STOPS', label: 'Same road, new stops', via: null, plan, note: null },
      {
        number: 4,
        kind: 'DIFFERENT_TOWN',
        label: 'Through a different town',
        via: null,
        plan: null,
        note: 'No town off the outbound road within a third more driving',
      },
    ],
  }
}
