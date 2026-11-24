import type { Pace, Transport } from '../types/itinerary'

export interface Base {
  name: string
  lat: number
  lon: number
}

/** Everything the itinerary wizard collects. Empty strings mean "not entered". */
export interface PlanForm {
  weights: Record<string, number>
  base: Base
  startDate: string
  days: number
  dayStart: string
  dayEnd: string
  pace: Pace
  transport: Transport
  partySize: number
  budgetAmount: string
  budgetBasis: 'PER_PERSON' | 'TOTAL'
  hardBudget: boolean
  foodPerPersonPerDay: string
}

export const CANDOLIM: Base = { name: 'Candolim', lat: 15.5202, lon: 73.7659 }

export const DEFAULT_PLAN: PlanForm = {
  weights: {},
  base: CANDOLIM,
  startDate: '',
  days: 2,
  dayStart: '09:00',
  dayEnd: '19:00',
  pace: 'BALANCED',
  transport: 'CAR',
  partySize: 2,
  budgetAmount: '',
  budgetBasis: 'PER_PERSON',
  hardBudget: false,
  foodPerPersonPerDay: '',
}

export interface Demo {
  id: string
  title: string
  description: string
}

/** Preloaded itinerary scenarios. They use the bundled catalogue and work without provider keys. */
export const PLAN_DEMOS: (Demo & { form: Partial<PlanForm> })[] = [
  {
    id: 'goa-beach-weekend',
    title: 'Goa beach weekend',
    description: 'Two balanced days from Candolim: beaches, food and some nightlife by scooter.',
    form: {
      weights: { beach: 5, food: 3, nightlife: 2 },
      days: 2,
      transport: 'SCOOTER',
      budgetAmount: '6000',
      foodPerPersonPerDay: '900',
    },
  },
  {
    // The CIA demo scenario (IMPLEMENTATION.md §18). Must-visit and avoid are shown live with edits.
    id: 'goa-four-day-friends',
    title: 'Four days in Goa with friends',
    description:
      'Three friends based in Candolim: beaches, Goan food, forts and one adventure, on a hard ₹9,000 per-person budget.',
    form: {
      weights: { beach: 5, food: 4, heritage: 4, adventure: 3 },
      days: 4,
      transport: 'SCOOTER',
      partySize: 3,
      budgetAmount: '9000',
      hardBudget: true,
      foodPerPersonPerDay: '800',
    },
  },
  {
    id: 'old-goa-heritage',
    title: 'Old Goa heritage day',
    description: 'One relaxed day from Panaji: churches, forts and museums by taxi.',
    form: {
      weights: { heritage: 5, spiritual: 3, culture: 3 },
      base: { name: 'Panaji', lat: 15.4989, lon: 73.8278 },
      days: 1,
      pace: 'RELAXED',
      transport: 'TAXI',
    },
  },
  {
    id: 'south-goa-family',
    title: 'South Goa family trip',
    description: 'Three relaxed days from Colva for four people with a ₹20,000 group budget.',
    form: {
      weights: { beach: 4, nature: 4, food: 2 },
      base: { name: 'Colva', lat: 15.2793, lon: 73.922 },
      days: 3,
      pace: 'RELAXED',
      partySize: 4,
      budgetAmount: '20000',
      budgetBasis: 'TOTAL',
      foodPerPersonPerDay: '700',
    },
  },
]

export const ROAD_DEMOS: Demo[] = [
  {
    id: 'mumbai-goa',
    title: 'Mumbai to Goa drive',
    description:
      'Two driving days with heritage and nature detours and a suggested overnight stay.',
  },
]
